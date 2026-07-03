package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountingPeriod;
import org.nonprofitbookkeeping.model.AccountingPeriodStatus;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.Merchant;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Canonical command/query service for the authoritative Txn ledger.
 */
@ApplicationScoped
public class TransactionEntryService
{
    private final Jpa jpa;
    private final TransactionCommandValidator validator;

    @Inject
    public TransactionEntryService(Jpa jpa)
    {
        this(jpa, new TransactionCommandValidator());
    }

    public TransactionEntryService(Jpa jpa, TransactionCommandValidator validator)
    {
        this.jpa = jpa;
        this.validator = validator;
    }

    public TransactionView enter(TransactionCommand command)
    {
        return saveNew(command, null);
    }

    public TransactionView update(long transactionId, TransactionCommand command)
    {
        validateCommand(command);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Txn txn = em.find(Txn.class, transactionId);
                if (txn == null)
                {
                    throw new PostingException("Transaction not found: " + transactionId);
                }
                if (!"ENTERED".equals(txn.getStatus()))
                {
                    throw new PostingException("Only ENTERED transactions can be updated by the entry service.");
                }
                requireNotReconciled(em, transactionId, "update transaction");
                requireOpenPeriodIfConfigured(em, txn.getTxnDate(), "update transaction");
                requireOpenPeriodIfConfigured(em, command.date(), "update transaction");
                String before = snapshot(txn);

                applyHeader(em, txn, command);
                em.createQuery("delete from TxnSplit s where s.txn = :txn")
                        .setParameter("txn", txn)
                        .executeUpdate();
                persistLines(em, txn, command.lines());
                txn.touchUpdatedAt();
                em.persist(audit("system", "TRANSACTION_UPDATED", txn, before, snapshot(txn), null));
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
            Txn txn = em.find(Txn.class, transactionId);
            if (txn == null)
            {
                throw new PostingException("Transaction not found: " + transactionId);
            }
            return toView(em, txn);
        }
    }

    public List<TransactionView> search(LocalDate fromDate, LocalDate toDate, String text, int maxRows)
    {
        int limit = maxRows <= 0 ? 100 : maxRows;
        String needle = text == null || text.isBlank() ? null : "%" + text.trim().toLowerCase() + "%";
        try (EntityManager em = jpa.em())
        {
            List<Txn> txns = em.createQuery(
                            "select distinct t from Txn t " +
                                    "left join t.payee p " +
                                    "where (:fromDate is null or t.txnDate >= :fromDate) " +
                                    "and (:toDate is null or t.txnDate <= :toDate) " +
                                    "and (:needle is null or lower(coalesce(t.memo, '')) like :needle " +
                                    "or lower(coalesce(p.displayName, '')) like :needle) " +
                                    "order by t.txnDate desc, t.id desc", Txn.class)
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
                requireOpenPeriodIfConfigured(em, command.date(), "enter transaction");
                Txn txn = new Txn();
                txn.setReplacementFor(replacementFor);
                applyHeader(em, txn, command);
                em.persist(txn);
                persistLines(em, txn, command.lines());
                em.persist(audit("system", "TRANSACTION_ENTERED", txn, null, snapshot(txn), null));
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
    }

    private void applyHeader(EntityManager em, Txn txn, TransactionCommand command)
    {
        txn.setTxnDate(command.date());
        txn.setPayee(command.payeeId() == null ? null : required(em, Counterparty.class, command.payeeId(), "Payee"));
        txn.setMemo(command.memo());
        txn.setBankAccount(command.bankAccountId() == null ? null : required(em, Account.class, command.bankAccountId(), "Bank account"));
    }

    private void persistLines(EntityManager em, Txn txn, List<TransactionLineCommand> lines)
    {
        for (TransactionLineCommand command : lines)
        {
            Account account = required(em, Account.class, command.accountId(), "Account");
            Fund fund = required(em, Fund.class, command.fundId(), "Fund");
            TxnSplit split = new TxnSplit();
            split.setTxn(txn);
            split.setAccount(account);
            split.setFund(fund);
            split.setBudgetCategory(command.budgetCategoryId() == null ? null : required(em, BudgetCategory.class, command.budgetCategoryId(), "Budget category"));
            split.setActivity(command.activityId() == null ? null : required(em, Activity.class, command.activityId(), "Activity"));
            split.setMerchant(command.merchantId() == null ? null : required(em, Merchant.class, command.merchantId(), "Merchant"));
            split.setNmr(command.nmr());
            split.setNotes(command.notes());
            split.setAmountSigned(toSignedAmount(account, command));
            em.persist(split);
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
                if (signed.compareTo(BigDecimal.ZERO) >= 0) debit = signed;
                else credit = signed.abs();
            }
            else
            {
                if (signed.compareTo(BigDecimal.ZERO) >= 0) credit = signed;
                else debit = signed.abs();
            }
            lines.add(new TransactionView.Line(
                    split.getId(), split.getAccount().getId(), split.getAccount().getCode(), split.getAccount().getName(),
                    split.getFund().getId(), split.getFund().getCode(), split.getFund().getName(),
                    split.getBudgetCategory() == null ? null : split.getBudgetCategory().getId(),
                    split.getActivity() == null ? null : split.getActivity().getId(),
                    split.getMerchant() == null ? null : split.getMerchant().getId(),
                    debit, credit, split.isNmr(), split.getNotes()));
        }
        Counterparty payee = txn.getPayee();
        Account bankAccount = txn.getBankAccount();
        return new TransactionView(
                txn.getId(), txn.getTxnDate(), payee == null ? null : payee.getId(),
                payee == null ? null : payee.getDisplayName(), txn.getMemo(),
                bankAccount == null ? null : bankAccount.getId(), bankAccount == null ? null : bankAccount.getName(),
                txn.getStatus(), lines);
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

    private static void requireOpenPeriodIfConfigured(EntityManager em, LocalDate date, String operation)
    {
        List<AccountingPeriod> periods = em.createQuery("""
                from AccountingPeriod p
                where p.startDate <= :date
                  and p.endDate >= :date
                order by p.fiscalYear, p.periodNumber
                """, AccountingPeriod.class)
                .setParameter("date", date)
                .setMaxResults(2)
                .getResultList();
        if (periods.size() > 1)
        {
            throw new PostingException("Multiple accounting periods contain date " + date);
        }
        if (!periods.isEmpty() && periods.get(0).getStatus() == AccountingPeriodStatus.CLOSED)
        {
            throw new ClosedAccountingPeriodException(periods.get(0).getId(), date, operation);
        }
    }

    private static org.nonprofitbookkeeping.model.AuditEvent audit(String actor, String action, Txn txn, String before, String after, String reason)
    {
        org.nonprofitbookkeeping.model.AuditEvent event = new org.nonprofitbookkeeping.model.AuditEvent();
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

    private <T> T required(EntityManager em, Class<T> type, Long id, String label)
    {
        T entity = em.find(type, id);
        if (entity == null)
        {
            throw new PostingException(label + " not found: " + id);
        }
        return entity;
    }

    private void rollbackIfActive(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }
}
