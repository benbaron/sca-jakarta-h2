package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.ExpenseFunction;
import org.nonprofitbookkeeping.model.SupplementalItemEffect;
import org.nonprofitbookkeeping.model.TxnSupplementalLine;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Transient straight-line proposals for canonical prepaid/deferred item recognition.
 * Accepted proposals are ordinary canonical transactions; no schedule balance is persisted.
 */
public final class SupplementalRecognitionService
{
    private static final BigDecimal ZERO = new BigDecimal("0.0000");
    private static final int MAX_INSTALLMENTS = 600;

    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;
    private final TransactionEntryService transactionEntry;

    public SupplementalRecognitionService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, companyCodeSupplier, new TransactionEntryService(jpa, companyCodeSupplier));
    }

    public SupplementalRecognitionService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            TransactionEntryService transactionEntry)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.transactionEntry = Objects.requireNonNull(transactionEntry, "transactionEntry");
    }

    public List<SupplementalOpenItemQueryService.Row> openItems(
            SupplementalOpenItemQueryService.Kind kind,
            LocalDate asOfDate)
    {
        requireRecognitionKind(kind);
        Objects.requireNonNull(asOfDate, "asOfDate");
        return new SupplementalOpenItemQueryService(jpa, companyCodeSupplier)
                .query(kind, asOfDate).authoritativeRows().stream()
                .filter(row -> row.openBalance().signum() > 0)
                .toList();
    }

    public Preview preview(Request request)
    {
        Objects.requireNonNull(request, "request");
        requireRecognitionKind(request.kind());
        Objects.requireNonNull(request.itemId(), "itemId");
        Objects.requireNonNull(request.serviceStart(), "serviceStart");
        Objects.requireNonNull(request.serviceEnd(), "serviceEnd");
        Objects.requireNonNull(request.runThrough(), "runThrough");
        Objects.requireNonNull(request.destinationAccountId(), "destinationAccountId");
        if (request.serviceStart().isAfter(request.serviceEnd()))
        {
            throw new IllegalArgumentException("Service start must be on or before service end.");
        }

        long months = ChronoUnit.MONTHS.between(
                YearMonth.from(request.serviceStart()), YearMonth.from(request.serviceEnd())) + 1;
        if (months <= 0 || months > MAX_INSTALLMENTS)
        {
            throw new IllegalArgumentException("Recognition range must contain between 1 and "
                    + MAX_INSTALLMENTS + " calendar months.");
        }

        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCodeSupplier.get());
            SupplementalOpenItemQueryService.Result result = SupplementalOpenItemQueryService.query(
                    em, company.getCode(), request.kind(), request.runThrough(), false);
            SupplementalOpenItemQueryService.Row row = result.authoritativeRows().stream()
                    .filter(value -> request.itemId().equals(value.itemId()))
                    .findFirst()
                    .orElseThrow(() -> new PostingException(
                            "The selected item is not an authoritative " + request.kind().displayName()
                                    + " item as of " + request.runThrough() + "."));
            if (row.openBalance().signum() < 0
                    || !("OPEN".equals(row.status()) || "CLOSED".equals(row.status())))
            {
                throw new PostingException("The selected item is inconsistent or over-applied and cannot be recognized.");
            }
            if (row.increases().signum() <= 0 || row.reductions().signum() < 0
                    || row.reductions().compareTo(row.increases()) > 0)
            {
                throw new PostingException("The selected item's recognition history is inconsistent or over-applied.");
            }

            TxnSupplementalLine opening = openingAllocation(em, row);
            var control = opening.getTxnSplit();
            Account controlAccount = control.getAccount();
            if (controlAccount == null || controlAccount.getSubtype() != request.kind().accountSubtype())
            {
                throw new PostingException("The selected item's opening control account does not match "
                        + request.kind().displayName() + ".");
            }
            if (control.getFund() == null)
            {
                throw new PostingException("The selected item's opening allocation has no authoritative Fund.");
            }

            Account destination = em.find(Account.class, request.destinationAccountId());
            if (destination == null)
            {
                throw new PostingException("Recognition destination account was not found.");
            }
            new CompanyOwnershipService(jpa).requireOwnedBy(company, destination, "Recognition destination account");
            AccountType requiredType = request.kind() == SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE
                    ? AccountType.EXPENSE : AccountType.INCOME;
            if (destination.getAccountType() != requiredType)
            {
                throw new PostingException(request.kind() == SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE
                        ? "Prepaid recognition requires an EXPENSE destination account."
                        : "Deferred-revenue recognition requires an INCOME destination account.");
            }
            if (!destination.isPosting() || !destination.isActive())
            {
                throw new PostingException("Recognition destination must be an active posting account.");
            }
            if (request.kind() == SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE
                    && request.expenseFunction() == null)
            {
                throw new PostingException("Prepaid recognition requires an explicit AR, OA, or FR expense function.");
            }
            if (request.kind() == SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE
                    && request.expenseFunction() != null)
            {
                throw new PostingException("Deferred-revenue recognition posts to INCOME and cannot carry an expense function.");
            }

            List<Installment> installments = installments(
                    request.serviceStart(), request.serviceEnd(), row.increases());
            if (installments.get(0).date().isBefore(row.openingDate()))
            {
                throw new PostingException("Recognition cannot precede the item's opening transaction date.");
            }

            BigDecimal reductionsToApply = money(row.reductions());
            List<Proposal> proposals = new ArrayList<>();
            for (int index = 0; index < installments.size(); index++)
            {
                Installment installment = installments.get(index);
                BigDecimal consumed = reductionsToApply.min(installment.amount());
                reductionsToApply = reductionsToApply.subtract(consumed);
                BigDecimal due = installment.amount().subtract(consumed).setScale(4, RoundingMode.UNNECESSARY);
                if (!installment.date().isAfter(request.runThrough()) && due.signum() > 0)
                {
                    validateAccountDate(destination, installment.date());
                    UUID requestId = UUID.randomUUID();
                    proposals.add(new Proposal(
                            requestId,
                            request.itemId(),
                            request.kind(),
                            installment.date(),
                            due,
                            index + 1,
                            installments.size(),
                            recognitionCommand(request, row, opening, destination, installment.date(), due,
                                    index + 1, installments.size())));
                }
            }
            if (reductionsToApply.signum() > 0)
            {
                throw new PostingException("Existing recognition exceeds the straight-line item total.");
            }
            BigDecimal proposed = proposals.stream().map(Proposal::amount).reduce(ZERO, BigDecimal::add);
            if (proposed.compareTo(row.openBalance()) > 0)
            {
                throw new PostingException("Recognition proposals exceed the item's available balance.");
            }
            return new Preview(
                    request,
                    row,
                    controlAccount.getId(),
                    controlAccount.getCode(),
                    controlAccount.getName(),
                    destination.getCode(),
                    destination.getName(),
                    installments,
                    proposals,
                    money(row.increases()),
                    money(row.reductions()),
                    money(row.openBalance()),
                    money(proposed));
        }
    }

    public TransactionView save(Proposal proposal)
    {
        Objects.requireNonNull(proposal, "proposal");
        return transactionEntry.enter(proposal.command(), proposal.requestId());
    }

    static List<Installment> installments(LocalDate start, LocalDate end, BigDecimal total)
    {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (start.isAfter(end))
        {
            throw new IllegalArgumentException("Service start must be on or before service end.");
        }
        BigDecimal exactTotal = money(total);
        if (exactTotal.signum() <= 0)
        {
            throw new IllegalArgumentException("Recognition total must be greater than zero.");
        }
        int count = Math.toIntExact(ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(end)) + 1);
        if (count <= 0 || count > MAX_INSTALLMENTS)
        {
            throw new IllegalArgumentException("Recognition range must contain between 1 and "
                    + MAX_INSTALLMENTS + " calendar months.");
        }
        BigDecimal ordinary = exactTotal.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
        List<Installment> result = new ArrayList<>(count);
        BigDecimal assigned = ZERO;
        YearMonth month = YearMonth.from(start);
        for (int index = 0; index < count; index++)
        {
            LocalDate date = month.atEndOfMonth();
            if (date.isAfter(end))
            {
                date = end;
            }
            BigDecimal amount = index == count - 1
                    ? exactTotal.subtract(assigned).setScale(4, RoundingMode.UNNECESSARY)
                    : ordinary;
            result.add(new Installment(index + 1, date, amount));
            assigned = assigned.add(amount);
            month = month.plusMonths(1);
        }
        return List.copyOf(result);
    }

    private static TxnSupplementalLine openingAllocation(
            EntityManager em,
            SupplementalOpenItemQueryService.Row row)
    {
        List<TxnSupplementalLine> matches = em.createQuery("""
                select l from TxnSupplementalLine l
                join fetch l.txn t
                join fetch l.txnSplit s
                join fetch s.account a
                join fetch s.fund f
                left join fetch s.activity ev
                left join fetch s.budgetCategory bc
                where t.id = :transactionId
                  and l.itemId = :itemId
                  and l.kind = :kind
                  and l.itemEffect = :effect
                order by l.id
                """, TxnSupplementalLine.class)
                .setParameter("transactionId", row.openingTransactionId())
                .setParameter("itemId", row.itemId())
                .setParameter("kind", row.kind().name())
                .setParameter("effect", SupplementalItemEffect.INCREASE)
                .setMaxResults(2)
                .getResultList();
        if (matches.size() != 1)
        {
            throw new PostingException("The selected item does not have one authoritative opening allocation.");
        }
        return matches.get(0);
    }

    private static TransactionCommand recognitionCommand(
            Request request,
            SupplementalOpenItemQueryService.Row row,
            TxnSupplementalLine opening,
            Account destination,
            LocalDate date,
            BigDecimal amount,
            int installment,
            int count)
    {
        var source = opening.getTxnSplit();
        Long fundId = source.getFund().getId();
        Long categoryId = source.getBudgetCategory() == null ? null : source.getBudgetCategory().getId();
        Long activityId = source.getActivity() == null ? null : source.getActivity().getId();
        Long controlAccountId = source.getAccount().getId();
        String note = "Straight-line recognition " + installment + "/" + count
                + "; item=" + request.itemId();
        List<TransactionLineCommand> lines;
        int controlIndex;
        if (request.kind() == SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE)
        {
            lines = List.of(
                    new TransactionLineCommand(destination.getId(), fundId, categoryId, activityId, null,
                            amount, ZERO, false, note, null, request.expenseFunction()),
                    new TransactionLineCommand(controlAccountId, fundId, categoryId, activityId, null,
                            ZERO, amount, false, note));
            controlIndex = 1;
        }
        else
        {
            lines = List.of(
                    new TransactionLineCommand(controlAccountId, fundId, categoryId, activityId, null,
                            amount, ZERO, false, note),
                    new TransactionLineCommand(destination.getId(), fundId, categoryId, activityId, null,
                            ZERO, amount, false, note));
            controlIndex = 0;
        }
        String reference = "RECOGNITION:" + request.itemId() + ":" + date;
        var detail = new TransactionSupplementalLineCommand(
                request.kind().name(), reference, row.counterparty(), row.description(), row.reference(), amount,
                null, request.serviceStart(), request.serviceEnd(), note, 0,
                request.itemId(), SupplementalItemEffect.DECREASE.name(), controlIndex);
        return new TransactionCommand(date, null,
                request.kind().displayName() + " recognition — " + row.description(), null,
                lines, List.of(detail));
    }

    private static void validateAccountDate(Account account, LocalDate date)
    {
        if (account.getEffectiveFrom() != null && date.isBefore(account.getEffectiveFrom())
                || account.getEffectiveTo() != null && date.isAfter(account.getEffectiveTo()))
        {
            throw new PostingException("Recognition destination account is not effective on " + date + ".");
        }
    }

    private static void requireRecognitionKind(SupplementalOpenItemQueryService.Kind kind)
    {
        if (kind != SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE
                && kind != SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE)
        {
            throw new IllegalArgumentException("Recognition supports PREPAID_EXPENSE and DEFERRED_REVENUE only.");
        }
    }

    private static BigDecimal money(BigDecimal value)
    {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    public record Request(
            SupplementalOpenItemQueryService.Kind kind,
            UUID itemId,
            LocalDate serviceStart,
            LocalDate serviceEnd,
            LocalDate runThrough,
            Long destinationAccountId,
            ExpenseFunction expenseFunction)
    {
    }

    public record Installment(int number, LocalDate date, BigDecimal amount)
    {
    }

    public record Proposal(
            UUID requestId,
            UUID itemId,
            SupplementalOpenItemQueryService.Kind kind,
            LocalDate recognitionDate,
            BigDecimal amount,
            int installment,
            int installmentCount,
            TransactionCommand command)
    {
    }

    public record Preview(
            Request request,
            SupplementalOpenItemQueryService.Row item,
            long controlAccountId,
            String controlAccountCode,
            String controlAccountName,
            String destinationAccountCode,
            String destinationAccountName,
            List<Installment> installments,
            List<Proposal> proposals,
            BigDecimal total,
            BigDecimal recognized,
            BigDecimal remaining,
            BigDecimal proposed)
    {
        public Preview
        {
            installments = List.copyOf(installments);
            proposals = List.copyOf(proposals);
        }
    }
}
