package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.Merchant;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.model.TxnSupplementalLine;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Canonical command/query service for the authoritative Txn ledger.
 */
@ApplicationScoped
public class TransactionEntryService
{
    private static final Set<String> SUPPLEMENTAL_KINDS = Set.of(
            "RECEIVABLE", "PAYABLE", "PREPAID_EXPENSE", "DEFERRED_REVENUE", "OTHER_ASSET", "OTHER_LIABILITY");

    private final Jpa jpa;
    private final TransactionCommandValidator validator;
    private final Supplier<String> companyCodeSupplier;

    @Inject
    public TransactionEntryService(Jpa jpa)
    {
        this(jpa, new TransactionCommandValidator(), () -> "DEFAULT");
    }

    public TransactionEntryService(Jpa jpa, TransactionCommandValidator validator)
    {
        this(jpa, validator, () -> "DEFAULT");
    }

    public TransactionEntryService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, new TransactionCommandValidator(), companyCodeSupplier);
    }

    public TransactionEntryService(
            Jpa jpa,
            TransactionCommandValidator validator,
            Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
    }

    public TransactionView enter(TransactionCommand command)
    {
        return saveNew(command, null);
    }

    /**
     * Caller-owned transaction variant for governed import services.
     *
     * <p>The caller must supply a managed company and an active JPA transaction.
     * Validation, company ownership, closed-period protection, canonical split
     * conversion, supplemental-detail persistence, and the transaction audit fact
     * remain owned by this service. The caller owns commit or rollback so the
     * canonical transaction participates in the import's larger atomic boundary.</p>
     */
    public Txn enter(
            EntityManager em,
            Company company,
            TransactionCommand command,
            UUID portableId,
            String actor)
    {
        return enter(em, company, command, portableId, actor, "caller-owned transactional import");
    }

    /**
     * Caller-owned transaction variant with an operation-specific factual audit reason.
     *
     * <p>All command, period, reference, and company-ownership validation completes
     * before the first entity is persisted. The caller still owns the single commit
     * or rollback decision for the larger operation.</p>
     */
    public Txn enter(
            EntityManager em,
            Company company,
            TransactionCommand command,
            UUID portableId,
            String actor,
            String auditReason)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(portableId, "portableId");
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Caller-owned transaction must be active.");
        }
        validateCommand(command);
        if (!em.contains(company) || company.getId() == null)
        {
            throw new IllegalArgumentException("Company must be managed by the caller-owned transaction.");
        }
        PeriodCloseRangeService.requireOpen(em, company.getCode(), command.date(), "enter transaction");
        validateReferences(em, company, command);

        Txn txn = new Txn();
        txn.setCompany(company);
        txn.setPortableId(portableId);
        applyHeader(em, company, txn, command);
        em.persist(txn);
        persistLines(em, company, txn, command.lines());
        persistSupplementalLines(em, txn, command.supplementalLines());
        em.persist(audit(
                company,
                actor == null || actor.isBlank() ? "system" : actor.trim(),
                "TRANSACTION_ENTERED",
                txn,
                null,
                snapshot(txn),
                blankToNull(auditReason)));
        return txn;
    }

    public TransactionView update(long transactionId, TransactionCommand command)
    {
        validateCommand(command);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = selectedCompany(em);
                Txn txn = em.find(Txn.class, transactionId);
                if (txn == null)
                {
                    throw new PostingException("Transaction not found: " + transactionId);
                }
                ownership().ensureOwnedBy(em, company, txn, "Transaction");
                if (!"ENTERED".equals(txn.getStatus()))
                {
                    throw new PostingException("Only ENTERED transactions can be updated by the entry service.");
                }
                requireNotReconciled(em, transactionId, "update transaction");
                requireOpenRange(em, txn.getTxnDate(), "update transaction");
                requireOpenRange(em, command.date(), "update transaction");
                String before = snapshot(txn);

                applyHeader(em, company, txn, command);
                em.createQuery("delete from TxnSupplementalLine s where s.txn = :txn")
                        .setParameter("txn", txn)
                        .executeUpdate();
                em.createQuery("delete from TxnSplit s where s.txn = :txn")
                        .setParameter("txn", txn)
                        .executeUpdate();
                persistLines(em, company, txn, command.lines());
                persistSupplementalLines(em, txn, command.supplementalLines());
                txn.touchUpdatedAt();
                em.persist(audit(company, "system", "TRANSACTION_UPDATED", txn, before, snapshot(txn), null));
                em.getTransaction().commit();
                return load(transactionId);
            }
            catch (RuntimeException ex)
            {
                rollbackIfActive(em);
                throw ex;
            }
        }
    }

    public TransactionView load(long transactionId)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = selectedCompany(em);
            Txn txn = em.find(Txn.class, transactionId);
            if (txn == null)
            {
                throw new PostingException("Transaction not found: " + transactionId);
            }
            ownership().ensureOwnedBy(em, company, txn, "Transaction");
            return toView(em, txn);
        }
    }

    public List<TransactionView> search(LocalDate fromDate, LocalDate toDate, String text, int maxRows)
    {
        int limit = maxRows <= 0 ? 100 : maxRows;
        String needle = text == null || text.isBlank() ? null : "%" + text.trim().toLowerCase() + "%";
        try (EntityManager em = jpa.em())
        {
            Company company = selectedCompany(em);
            List<Txn> txns = em.createQuery(
                            "select distinct t from Txn t " +
                                    "left join t.payee p " +
                                    "where t.company = :company " +
                                    "and (:fromDate is null or t.txnDate >= :fromDate) " +
                                    "and (:toDate is null or t.txnDate <= :toDate) " +
                                    "and (:needle is null or lower(coalesce(t.memo, '')) like :needle " +
                                    "or lower(coalesce(p.displayName, '')) like :needle) " +
                                    "order by t.txnDate desc, t.id desc", Txn.class)
                    .setParameter("company", company)
                    .setParameter("fromDate", fromDate)
                    .setParameter("toDate", toDate)
                    .setParameter("needle", needle)
                    .setMaxResults(limit)
                    .getResultList();
            List<TransactionView> views = new ArrayList<>();
            for (Txn txn : txns)
            {
                views.add(toView(em, txn));
            }
            return views;
        }
    }

    public AccountingJournalProjection journalView(long transactionId)
    {
        TransactionView view = load(transactionId);
        List<AccountingJournalProjection.Line> lines = new ArrayList<>();
        for (TransactionView.Line line : view.lines())
        {
            lines.add(new AccountingJournalProjection.Line(
                    line.accountCode(), line.accountName(), line.fundCode(), line.fundName(),
                    line.debit(), line.credit(), line.notes()));
        }
        return new AccountingJournalProjection(view.id(), view.date(), view.payeeName(), view.memo(), lines);
    }

    private TransactionView saveNew(TransactionCommand command, Txn replacementFor)
    {
        validateCommand(command);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = selectedCompany(em);
                requireOpenRange(em, command.date(), "enter transaction");
                Txn txn = new Txn();
                txn.setCompany(company);
                txn.setReplacementFor(replacementFor);
                if (replacementFor != null)
                {
                    ownership().ensureOwnedBy(em, company, replacementFor, "Replacement transaction");
                }
                applyHeader(em, company, txn, command);
                em.persist(txn);
                persistLines(em, company, txn, command.lines());
                persistSupplementalLines(em, txn, command.supplementalLines());
                em.persist(audit(company, "system", "TRANSACTION_ENTERED", txn, null, snapshot(txn), null));
                em.getTransaction().commit();
                return load(txn.getId());
            }
            catch (RuntimeException ex)
            {
                rollbackIfActive(em);
                throw ex;
            }
        }
    }

    private void validateCommand(TransactionCommand command)
    {
        TransactionValidationResult result = validator.validate(command);
        if (!result.valid())
        {
            throw new PostingException(String.join(" ", result.errors()));
        }
        validateSupplementalLines(command.supplementalLines());
    }

    private void validateSupplementalLines(List<TransactionSupplementalLineCommand> supplementalLines)
    {
        int row = 0;
        for (TransactionSupplementalLineCommand command : supplementalLines)
        {
            row++;
            String label = "Supplemental detail row " + row;
            if (command.kind() == null || !SUPPLEMENTAL_KINDS.contains(command.kind()))
            {
                throw new PostingException(label + " has an unsupported kind.");
            }
            if (command.description() == null || command.description().isBlank())
            {
                throw new PostingException(label + " requires a description.");
            }
            if (command.amount() == null || command.amount().signum() < 0)
            {
                throw new PostingException(label + " requires a non-negative amount.");
            }
            if (command.lineOrder() != null && command.lineOrder() < 0)
            {
                throw new PostingException(label + " requires a non-negative line order.");
            }
            if ((command.startDate() == null) != (command.endDate() == null))
            {
                throw new PostingException(label + " requires both start and end dates or neither.");
            }
            if (command.startDate() != null && command.startDate().isAfter(command.endDate()))
            {
                throw new PostingException(label + " start date must be on or before end date.");
            }
        }
    }

    private void validateReferences(EntityManager em, Company company, TransactionCommand command)
    {
        Counterparty payee = command.payeeId() == null
                ? null
                : required(em, Counterparty.class, command.payeeId(), "Payee");
        ownership().ensureOwnedBy(em, company, payee, "Payee");

        Account bankAccount = command.bankAccountId() == null
                ? null
                : required(em, Account.class, command.bankAccountId(), "Bank account");
        if (bankAccount != null)
        {
            ownership().ensureOwnedBy(em, company, bankAccount, "Bank account");
        }

        for (TransactionLineCommand line : command.lines())
        {
            Account account = required(em, Account.class, line.accountId(), "Account");
            Fund fund = required(em, Fund.class, line.fundId(), "Fund");
            BudgetCategory category = line.budgetCategoryId() == null
                    ? null
                    : required(em, BudgetCategory.class, line.budgetCategoryId(), "Budget category");
            Activity activity = line.activityId() == null
                    ? null
                    : required(em, Activity.class, line.activityId(), "Activity");
            Merchant merchant = line.merchantId() == null
                    ? null
                    : required(em, Merchant.class, line.merchantId(), "Merchant");
            ownership().ensureOwnedBy(em, company, account, "Account");
            ownership().ensureOwnedBy(em, company, fund, "Fund");
            ownership().ensureOwnedBy(em, company, category, "Budget category");
            ownership().ensureOwnedBy(em, company, activity, "Activity");
            ownership().ensureOwnedBy(em, company, merchant, "Merchant");
            toSignedAmount(account, line);
        }
    }

    private void applyHeader(EntityManager em, Company company, Txn txn, TransactionCommand command)
    {
        txn.setCompany(company);
        txn.setTxnDate(command.date());
        Counterparty payee = command.payeeId() == null ? null : required(em, Counterparty.class, command.payeeId(), "Payee");
        ownership().ensureOwnedBy(em, company, payee, "Payee");
        txn.setPayee(payee);
        txn.setMemo(command.memo());
        Account bankAccount = command.bankAccountId() == null ? null : required(em, Account.class, command.bankAccountId(), "Bank account");
        if (bankAccount != null)
        {
            ownership().ensureOwnedBy(em, company, bankAccount, "Bank account");
        }
        txn.setBankAccount(bankAccount);
    }

    private void persistLines(EntityManager em, Company company, Txn txn, List<TransactionLineCommand> lines)
    {
        for (TransactionLineCommand command : lines)
        {
            Account account = required(em, Account.class, command.accountId(), "Account");
            Fund fund = required(em, Fund.class, command.fundId(), "Fund");
            BudgetCategory category = command.budgetCategoryId() == null ? null : required(em, BudgetCategory.class, command.budgetCategoryId(), "Budget category");
            Activity activity = command.activityId() == null ? null : required(em, Activity.class, command.activityId(), "Activity");
            Merchant merchant = command.merchantId() == null ? null : required(em, Merchant.class, command.merchantId(), "Merchant");
            ownership().ensureOwnedBy(em, company, account, "Account");
            ownership().ensureOwnedBy(em, company, fund, "Fund");
            ownership().ensureOwnedBy(em, company, category, "Budget category");
            ownership().ensureOwnedBy(em, company, activity, "Activity");
            ownership().ensureOwnedBy(em, company, merchant, "Merchant");
            TxnSplit split = new TxnSplit();
            split.setTxn(txn);
            split.setAccount(account);
            split.setFund(fund);
            split.setBudgetCategory(category);
            split.setActivity(activity);
            split.setMerchant(merchant);
            split.setNmr(command.nmr());
            split.setNotes(command.notes());
            split.setAmountSigned(toSignedAmount(account, command));
            em.persist(split);
        }
    }

    private void persistSupplementalLines(EntityManager em, Txn txn, List<TransactionSupplementalLineCommand> lines)
    {
        int order = 0;
        for (TransactionSupplementalLineCommand command : lines)
        {
            TxnSupplementalLine line = new TxnSupplementalLine();
            line.setTxn(txn);
            line.setLineOrder(command.lineOrder() == null ? order : command.lineOrder());
            order++;
            line.setKind(command.kind());
            line.setEntryRef(blankToNull(command.entryRef()));
            line.setCounterparty(blankToNull(command.counterparty()));
            line.setDescription(command.description().trim());
            line.setReference(blankToNull(command.reference()));
            line.setAmount(command.amount().setScale(4, RoundingMode.HALF_UP));
            line.setDueDate(command.dueDate());
            line.setStartDate(command.startDate());
            line.setEndDate(command.endDate());
            line.setNotes(blankToNull(command.notes()));
            em.persist(line);
        }
    }

    private BigDecimal toSignedAmount(Account account, TransactionLineCommand command)
    {
        BigDecimal debit = command.debit() == null ? BigDecimal.ZERO : command.debit();
        BigDecimal credit = command.credit() == null ? BigDecimal.ZERO : command.credit();
        if (account.getNormalBalance() == NormalBalance.DEBIT)
        {
            return debit.subtract(credit);
        }
        return credit.subtract(debit);
    }

    private TransactionView toView(EntityManager em, Txn txn)
    {
        List<TxnSplit> splits = em.createQuery(
                        "from TxnSplit s " +
                                "join fetch s.account a " +
                                "join fetch s.fund f " +
                                "left join fetch s.budgetCategory " +
                                "left join fetch s.activity " +
                                "left join fetch s.merchant " +
                                "where s.txn = :txn order by s.id", TxnSplit.class)
                .setParameter("txn", txn)
                .getResultList();
        List<TransactionView.Line> lines = new ArrayList<>();
        for (TxnSplit split : splits)
        {
            BigDecimal debit = BigDecimal.ZERO;
            BigDecimal credit = BigDecimal.ZERO;
            BigDecimal signed = split.getAmountSigned();
            if (split.getAccount().getNormalBalance() == NormalBalance.DEBIT)
            {
                if (signed.compareTo(BigDecimal.ZERO) >= 0)
                {
                    debit = signed;
                }
                else
                {
                    credit = signed.abs();
                }
            }
            else
            {
                if (signed.compareTo(BigDecimal.ZERO) >= 0)
                {
                    credit = signed;
                }
                else
                {
                    debit = signed.abs();
                }
            }
            lines.add(new TransactionView.Line(
                    split.getId(), split.getAccount().getId(), split.getAccount().getCode(), split.getAccount().getName(),
                    split.getFund().getId(), split.getFund().getCode(), split.getFund().getName(),
                    split.getBudgetCategory() == null ? null : split.getBudgetCategory().getId(),
                    split.getActivity() == null ? null : split.getActivity().getId(),
                    split.getMerchant() == null ? null : split.getMerchant().getId(),
                    debit, credit, split.isNmr(), split.getNotes()));
        }
        List<TxnSupplementalLine> supplementalEntities = em.createQuery(
                        "from TxnSupplementalLine l where l.txn = :txn order by l.lineOrder, l.id", TxnSupplementalLine.class)
                .setParameter("txn", txn)
                .getResultList();
        List<TransactionSupplementalLineView> supplementalLines = new ArrayList<>();
        for (TxnSupplementalLine line : supplementalEntities)
        {
            supplementalLines.add(new TransactionSupplementalLineView(
                    line.getId(), line.getKind(), line.getEntryRef(), line.getCounterparty(), line.getDescription(),
                    line.getReference(), line.getAmount(), line.getDueDate(), line.getStartDate(), line.getEndDate(), line.getNotes()));
        }
        Counterparty payee = txn.getPayee();
        Account bankAccount = txn.getBankAccount();
        return new TransactionView(
                txn.getId(), txn.getTxnDate(), payee == null ? null : payee.getId(),
                payee == null ? null : payee.getDisplayName(), txn.getMemo(),
                bankAccount == null ? null : bankAccount.getId(), bankAccount == null ? null : bankAccount.getName(),
                txn.getStatus(), lines, supplementalLines);
    }

    private static void requireNotReconciled(EntityManager em, long transactionId, String operation)
    {
        Number protectedCount = (Number) em.createNativeQuery("""
                SELECT COUNT(*)
                FROM txn_reconciliation_protection p
                JOIN reconciliation_run r ON r.id = p.reconciliation_run_id
                WHERE p.txn_id = ?
                  AND r.status = 'COMPLETED'
                """)
                .setParameter(1, transactionId)
                .getSingleResult();
        if (protectedCount.longValue() > 0)
        {
            throw new PostingException("Cannot " + operation + " because transaction "
                    + transactionId + " is protected by a completed reconciliation.");
        }
    }

    private void requireOpenRange(EntityManager em, LocalDate date, String operation)
    {
        PeriodCloseRangeService.requireOpen(em, companyCodeSupplier.get(), date, operation);
    }

    private static org.nonprofitbookkeeping.model.AuditEvent audit(
            Company company,
            String actor,
            String action,
            Txn txn,
            String before,
            String after,
            String reason)
    {
        org.nonprofitbookkeeping.model.AuditEvent event = new org.nonprofitbookkeeping.model.AuditEvent();
        event.setCompany(company);
        event.setActor(actor);
        event.setActionType(action);
        event.setEntityType("Txn");
        event.setEntityId(txn.getId() == null ? null : Long.toString(txn.getId()));
        event.setSummary(action.replace('_', ' ').toLowerCase());
        event.setBeforeValue(before);
        event.setAfterValue(after);
        event.setReason(reason);
        return event;
    }

    private static String snapshot(Txn txn)
    {
        return "id=" + txn.getId() + ",date=" + txn.getTxnDate() + ",status=" + txn.getStatus()
                + ",memo=" + (txn.getMemo() == null ? "" : txn.getMemo());
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <T> T required(EntityManager em, Class<T> type, Long id, String label)
    {
        T entity = em.find(type, id);
        if (entity == null)
        {
            throw new PostingException(label + " not found: " + id);
        }
        return entity;
    }

    private Company selectedCompany(EntityManager em)
    {
        return ownership().requireCompany(em, companyCodeSupplier.get());
    }

    private CompanyOwnershipService ownership()
    {
        return new CompanyOwnershipService(jpa);
    }

    private void rollbackIfActive(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }
}
