package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountClassification;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.ExpenseFunction;
import org.nonprofitbookkeeping.model.MerchantReceivableRole;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.SupplementalItemEffect;
import org.nonprofitbookkeeping.model.TxnSupplementalLine;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Previewed merchant settlement entries over the canonical Journal and open-item authorities. */
public final class MerchantSettlementAssistantService
{
    private static final BigDecimal ZERO = new BigDecimal("0.0000");

    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;
    private final TransactionEntryService transactionEntry;

    public MerchantSettlementAssistantService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            TransactionEntryService transactionEntry)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.transactionEntry = Objects.requireNonNull(transactionEntry, "transactionEntry");
    }

    public List<Receivable> openReceivables(LocalDate asOfDate)
    {
        Objects.requireNonNull(asOfDate, "asOfDate");
        try (EntityManager em = jpa.em())
        {
            Company company = company(em);
            return receivables(em, company, asOfDate);
        }
    }

    public List<NmrItem> openNmrItems(LocalDate asOfDate)
    {
        Objects.requireNonNull(asOfDate, "asOfDate");
        try (EntityManager em = jpa.em())
        {
            Company company = company(em);
            return nmrItems(em, company, asOfDate);
        }
    }

    public Preview preview(Request request)
    {
        Objects.requireNonNull(request, "request");
        try (EntityManager em = jpa.em())
        {
            Company company = company(em);
            return preview(em, company, request, UUID.randomUUID());
        }
    }

    public TransactionView save(Preview preview)
    {
        Objects.requireNonNull(preview, "preview");
        return transactionEntry.enterAfterLockedValidation(
                preview.command(),
                preview.requestId(),
                "save merchant settlement",
                (em, company) ->
                {
                    Preview current = preview(em, company, preview.request(), preview.requestId());
                    if (!current.fingerprint().equals(preview.fingerprint()))
                    {
                        throw new PostingException(
                                "Merchant settlement source facts changed after Preview. Refresh and preview again before saving.");
                    }
                });
    }

    private Preview preview(EntityManager em, Company company, Request request, UUID requestId)
    {
        Objects.requireNonNull(request.date(), "Settlement date is required.");
        Objects.requireNonNull(request.receivableItemId(), "A merchant receivable is required.");
        BigDecimal payout = amount(request.payout(), "Payout", true);
        BigDecimal fee = amount(request.fee(), "Fee", true);
        BigDecimal nmr = amount(request.nmrDischarge(), "NMR discharge", true);
        BigDecimal observed = request.withheldUnconfirmed() == null
                ? null : amount(request.withheldUnconfirmed(), "Observed withholding", true);
        BigDecimal total = payout.add(fee).add(nmr).setScale(4, RoundingMode.UNNECESSARY);
        if (total.signum() <= 0)
        {
            throw new PostingException("Enter a positive payout, fee, or evidenced NMR discharge.");
        }
        String evidence = text(request.evidenceReference());
        if (evidence != null && evidence.length() > 500)
        {
            throw new PostingException("Settlement evidence is limited to 500 characters.");
        }
        if (observed != null && evidence == null)
        {
            throw new PostingException("Observed withholding requires an external evidence reference.");
        }

        Receivable receivable = receivables(em, company, request.date()).stream()
                .filter(value -> value.itemId().equals(request.receivableItemId()))
                .findFirst()
                .orElseThrow(() -> new PostingException(
                        "The selected merchant receivable is no longer open and authoritative on the settlement date."));
        if (total.compareTo(receivable.openBalance()) > 0)
        {
            throw new PostingException("Settlement components exceed the selected receivable balance.");
        }

        TxnSupplementalLine receivableOpening = openingAllocation(
                em, receivable.openingTransactionId(), receivable.itemId(), "RECEIVABLE");
        var source = receivableOpening.getTxnSplit();
        Long categoryId = source.getBudgetCategory() == null ? null : source.getBudgetCategory().getId();
        Long merchantId = source.getMerchant().getId();
        long fundId = source.getFund().getId();
        long eventId = source.getActivity().getId();
        String note = "Merchant settlement; receivable=" + receivable.itemId();
        List<TransactionLineCommand> lines = new ArrayList<>();
        List<MerchantSettlementCommand.Component> components = new ArrayList<>();

        Long bankId = null;
        if (payout.signum() > 0)
        {
            Account bank = requireAccount(em, company, request.bankAccountId(), "Payout bank account");
            if (bank.getAccountType() != AccountType.ASSET || bank.getNormalBalance() != NormalBalance.DEBIT
                    || !AccountClassification.isBankLedgerAccount(bank) || !bank.isPosting() || !bank.isActive())
            {
                throw new PostingException("Payout requires an active posting BANK asset account.");
            }
            requireEffective(bank, request.date(), "Payout bank account");
            bankId = bank.getId();
            components.add(new MerchantSettlementCommand.Component("PAYOUT", lines.size()));
            lines.add(new TransactionLineCommand(bank.getId(), fundId, categoryId, eventId, merchantId,
                    payout, ZERO, false, note));
        }

        if (fee.signum() > 0)
        {
            Account feeAccount = requireAccount(em, company, request.feeAccountId(), "Fee expense account");
            if (feeAccount.getAccountType() != AccountType.EXPENSE || feeAccount.getNormalBalance() != NormalBalance.DEBIT
                    || !feeAccount.isPosting() || !feeAccount.isActive())
            {
                throw new PostingException("Processor fee requires an active posting EXPENSE account.");
            }
            if (request.feeExpenseFunction() == null)
            {
                throw new PostingException("Processor fee requires an explicit AR, OA, or FR expense function.");
            }
            requireEffective(feeAccount, request.date(), "Fee expense account");
            ExpenseFunctions.requireEligible(feeAccount, request.feeExpenseFunction());
            components.add(new MerchantSettlementCommand.Component("FEE", lines.size()));
            lines.add(new TransactionLineCommand(feeAccount.getId(), fundId, categoryId, eventId, merchantId,
                    fee, ZERO, false, note, null, request.feeExpenseFunction()));
        }

        NmrItem nmrItem = null;
        int nmrIndex = -1;
        if (nmr.signum() > 0)
        {
            if (request.nmrItemId() == null || evidence == null)
            {
                throw new PostingException("NMR discharge requires an open NMR item and external evidence.");
            }
            nmrItem = nmrItems(em, company, request.date()).stream()
                    .filter(value -> value.itemId().equals(request.nmrItemId()))
                    .findFirst()
                    .orElseThrow(() -> new PostingException(
                            "The selected NMR item is no longer open and authoritative on the settlement date."));
            if (nmr.compareTo(nmrItem.openBalance()) > 0)
            {
                throw new PostingException("NMR discharge exceeds the selected liability item balance.");
            }
            if (nmrItem.fundId() != fundId || nmrItem.eventId() != eventId)
            {
                throw new PostingException("NMR discharge must use the receivable's Fund and Event.");
            }
            Account liability = requireAccount(em, company, nmrItem.accountId(), "NMR liability account");
            requireEffective(liability, request.date(), "NMR liability account");
            nmrIndex = lines.size();
            components.add(new MerchantSettlementCommand.Component("NMR_DISCHARGE", nmrIndex));
            lines.add(new TransactionLineCommand(liability.getId(), fundId, categoryId, eventId, merchantId,
                    nmr, ZERO, true, note));
        }

        int receivableIndex = lines.size();
        lines.add(new TransactionLineCommand(receivable.accountId(), fundId, categoryId, eventId, merchantId,
                ZERO, total, false, note));

        MerchantSettlementCommand settlementFacts = new MerchantSettlementCommand(
                MerchantReceivableRole.SETTLEMENT.name(), observed, evidence, components);
        List<TransactionSupplementalLineCommand> details = new ArrayList<>();
        details.add(new TransactionSupplementalLineCommand(
                "RECEIVABLE",
                "MERCHANT-SETTLEMENT:" + receivable.itemId(),
                receivable.merchantName(),
                receivable.description(),
                receivable.reference(),
                total,
                null,
                null,
                null,
                note,
                0,
                receivable.itemId(),
                SupplementalItemEffect.DECREASE.name(),
                receivableIndex,
                null,
                null,
                null,
                settlementFacts));
        if (nmrItem != null)
        {
            details.add(new TransactionSupplementalLineCommand(
                    "OTHER_LIABILITY",
                    "MERCHANT-DISCHARGE:" + nmrItem.itemId(),
                    nmrItem.recipientName(),
                    nmrItem.description(),
                    evidence,
                    nmr,
                    null,
                    null,
                    null,
                    note,
                    1,
                    nmrItem.itemId(),
                    SupplementalItemEffect.DECREASE.name(),
                    nmrIndex,
                    nmrItem.recipientId(),
                    "MERCHANT_DISCHARGE",
                    evidence));
        }

        String memo = text(request.memo());
        if (memo == null)
        {
            memo = "Merchant settlement — " + receivable.merchantName();
        }
        TransactionCommand command = new TransactionCommand(
                request.date(), null, memo, bankId, lines, details);
        String fingerprint = fingerprint(
                JournalCreateRequests.reason(company, command)
                        + "|receivable=" + receivable.itemId() + ":" + receivable.openBalance().toPlainString()
                        + "|nmr=" + (nmrItem == null ? "none" : nmrItem.itemId() + ":" + nmrItem.openBalance().toPlainString()));
        return new Preview(requestId, request, receivable, nmrItem, payout, fee, nmr, observed,
                total, receivable.openBalance().subtract(total), fingerprint, command);
    }

    private List<Receivable> receivables(EntityManager em, Company company, LocalDate asOfDate)
    {
        var result = SupplementalOpenItemQueryService.query(
                em, company.getCode(), SupplementalOpenItemQueryService.Kind.RECEIVABLE, asOfDate, false);
        List<Receivable> values = new ArrayList<>();
        for (var row : result.authoritativeRows())
        {
            if (row.openBalance().signum() <= 0 || !"OPEN".equals(row.status()))
            {
                continue;
            }
            TxnSupplementalLine opening = openingAllocation(
                    em, row.openingTransactionId(), row.itemId(), "RECEIVABLE");
            var split = opening.getTxnSplit();
            if (opening.getMerchantRole() != MerchantReceivableRole.GROSS_RECEIPT
                    || split.getMerchant() == null || split.getActivity() == null)
            {
                continue;
            }
            values.add(new Receivable(
                    row.itemId(), row.openingTransactionId(), row.openingDate(), row.description(), row.reference(),
                    row.openBalance(), split.getAccount().getId(), split.getAccount().getCode(),
                    split.getFund().getId(), split.getFund().getCode(), split.getActivity().getId(),
                    split.getActivity().getCode(), split.getMerchant().getId(), split.getMerchant().getName()));
        }
        values.sort(Comparator.comparing(Receivable::openingDate).thenComparing(Receivable::description));
        return List.copyOf(values);
    }

    private List<NmrItem> nmrItems(EntityManager em, Company company, LocalDate asOfDate)
    {
        var result = SupplementalOpenItemQueryService.query(
                em, company.getCode(), SupplementalOpenItemQueryService.Kind.OTHER_LIABILITY, asOfDate, false);
        return result.authoritativeRows().stream()
                .filter(row -> row.openBalance().signum() > 0 && "OPEN".equals(row.status()) && row.nmr() != null)
                .map(row -> new NmrItem(row.itemId(), row.openingTransactionId(), row.openingDate(),
                        row.description(), row.openBalance(), row.nmr().recipientId(), row.nmr().recipientName(),
                        row.nmr().accountId(), row.nmr().accountCode(), row.nmr().fundId(), row.nmr().fundCode(),
                        row.nmr().eventId(), row.nmr().eventCode()))
                .sorted(Comparator.comparing(NmrItem::openingDate).thenComparing(NmrItem::description))
                .toList();
    }

    private static TxnSupplementalLine openingAllocation(
            EntityManager em,
            long transactionId,
            UUID itemId,
            String kind)
    {
        List<TxnSupplementalLine> matches = em.createQuery("""
                select l from TxnSupplementalLine l
                join fetch l.txn t
                join fetch l.txnSplit s
                join fetch s.account a
                join fetch s.fund f
                left join fetch s.activity ev
                left join fetch s.budgetCategory bc
                left join fetch s.merchant m
                left join fetch l.nmrRecipient nr
                where t.id = :transactionId
                  and l.itemId = :itemId
                  and l.kind = :kind
                  and l.itemEffect = :effect
                order by l.id
                """, TxnSupplementalLine.class)
                .setParameter("transactionId", transactionId)
                .setParameter("itemId", itemId)
                .setParameter("kind", kind)
                .setParameter("effect", SupplementalItemEffect.INCREASE)
                .setMaxResults(2)
                .getResultList();
        if (matches.size() != 1)
        {
            throw new PostingException("The selected " + kind + " item has no single authoritative opening allocation.");
        }
        return matches.get(0);
    }

    private Account requireAccount(EntityManager em, Company company, Long id, String label)
    {
        if (id == null)
        {
            throw new PostingException(label + " is required.");
        }
        Account account = em.find(Account.class, id);
        if (account == null)
        {
            throw new PostingException(label + " was not found.");
        }
        new CompanyOwnershipService(jpa).requireOwnedBy(company, account, label);
        return account;
    }

    private Company company(EntityManager em)
    {
        return new CompanyOwnershipService(jpa).requireCompany(em, companyCodeSupplier.get());
    }

    private static BigDecimal amount(BigDecimal value, String label, boolean allowZero)
    {
        BigDecimal result = value == null ? ZERO : value;
        try
        {
            result = result.setScale(4, RoundingMode.UNNECESSARY);
        }
        catch (ArithmeticException ex)
        {
            throw new PostingException(label + " supports at most four decimal places.");
        }
        if (result.signum() < 0 || !allowZero && result.signum() == 0 || result.precision() > 19)
        {
            throw new PostingException(label + " must be a valid nonnegative amount.");
        }
        return result;
    }

    private static void requireEffective(Account account, LocalDate date, String label)
    {
        if (account.getEffectiveFrom() != null && date.isBefore(account.getEffectiveFrom())
                || account.getEffectiveTo() != null && date.isAfter(account.getEffectiveTo()))
        {
            throw new PostingException(label + " is not effective on " + date + ".");
        }
    }

    private static String text(String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String fingerprint(String value)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    public record Request(
            UUID receivableItemId,
            LocalDate date,
            Long bankAccountId,
            BigDecimal payout,
            Long feeAccountId,
            BigDecimal fee,
            ExpenseFunction feeExpenseFunction,
            UUID nmrItemId,
            BigDecimal nmrDischarge,
            BigDecimal withheldUnconfirmed,
            String evidenceReference,
            String memo)
    {
    }

    public record Receivable(
            UUID itemId,
            long openingTransactionId,
            LocalDate openingDate,
            String description,
            String reference,
            BigDecimal openBalance,
            long accountId,
            String accountCode,
            long fundId,
            String fundCode,
            long eventId,
            String eventCode,
            long merchantId,
            String merchantName)
    {
    }

    public record NmrItem(
            UUID itemId,
            long openingTransactionId,
            LocalDate openingDate,
            String description,
            BigDecimal openBalance,
            long recipientId,
            String recipientName,
            long accountId,
            String accountCode,
            long fundId,
            String fundCode,
            long eventId,
            String eventCode)
    {
    }

    public record Preview(
            UUID requestId,
            Request request,
            Receivable receivable,
            NmrItem nmrItem,
            BigDecimal payout,
            BigDecimal fee,
            BigDecimal nmrDischarge,
            BigDecimal withheldUnconfirmed,
            BigDecimal total,
            BigDecimal remainingReceivable,
            String fingerprint,
            TransactionCommand command)
    {
    }
}
