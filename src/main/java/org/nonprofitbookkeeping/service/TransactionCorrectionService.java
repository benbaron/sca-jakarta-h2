package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Applies direct edits, deletions, reversals, and replacement transactions.
 */
@ApplicationScoped
public class TransactionCorrectionService
{
    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;

    @Inject
    public TransactionCorrectionService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public TransactionCorrectionService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
    }

    public Txn directEdit(long transactionId, LocalDate transactionDate, String memo, String correctionNote, String actor)
    {
        requireText(actor, "actor");
        if (transactionDate == null)
        {
            throw new IllegalArgumentException("transactionDate is required");
        }

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = selectedCompany(em);
                Txn txn = requireTransaction(em, transactionId);
                ownership().ensureOwnedBy(em, company, txn, "Transaction");
                requireEntered(txn);
                requireNotReconciled(em, transactionId, "edit transaction");
                requireOpenRange(em, txn.getTxnDate(), "edit transaction");
                requireOpenRange(em, transactionDate, "move transaction");

                String before = snapshot(txn);
                txn.setTxnDate(transactionDate);
                txn.setMemo(blankToNull(memo));
                txn.setCorrectionNote(blankToNull(correctionNote));
                txn.touchUpdatedAt();
                em.persist(audit(company, actor, "TRANSACTION_EDITED", txn, before, snapshot(txn), correctionNote));
                em.getTransaction().commit();
                return txn;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public void delete(long transactionId, String actor, String reason)
    {
        requireText(actor, "actor");
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = selectedCompany(em);
                Txn txn = requireTransaction(em, transactionId);
                ownership().ensureOwnedBy(em, company, txn, "Transaction");
                requireEntered(txn);
                requireNotReconciled(em, transactionId, "delete transaction");
                requireOpenRange(em, txn.getTxnDate(), "delete transaction");

                AuditEvent event = audit(company, actor, "TRANSACTION_DELETED", txn, snapshot(txn), null, reason);
                event.setEntityId(Long.toString(transactionId));
                em.persist(event);
                em.flush();
                em.remove(txn);
                em.getTransaction().commit();
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public CorrectionResult reverse(long transactionId, LocalDate reversalDate, String actor, String reason, boolean createReplacement)
    {
        requireText(actor, "actor");
        if (reversalDate == null)
        {
            throw new IllegalArgumentException("reversalDate is required");
        }

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = selectedCompany(em);
                Txn original = requireTransaction(em, transactionId);
                ownership().ensureOwnedBy(em, company, original, "Transaction");
                requireEntered(original);
                requireNotReconciled(em, transactionId, "reverse transaction");
                requireOpenRange(em, reversalDate, "create reversal");

                List<TxnSplit> originalSplits = em.createQuery(
                                "from TxnSplit s where s.txn.id = :id order by s.id",
                                TxnSplit.class)
                        .setParameter("id", transactionId)
                        .getResultList();
                validateBalanced(originalSplits);

                validateSplitOwnership(em, company, originalSplits);
                String before = snapshot(original);
                Txn reversal = copyHeader(original, reversalDate);
                reversal.setReversalOf(original);
                reversal.setCorrectionNote(blankToNull(reason));
                em.persist(reversal);
                for (TxnSplit split : originalSplits)
                {
                    em.persist(copySplit(split, reversal, split.getAmountSigned().negate()));
                }

                original.setStatus("REVERSED");
                original.touchUpdatedAt();

                Txn replacement = null;
                if (createReplacement)
                {
                    replacement = copyHeader(original, reversalDate);
                    replacement.setReplacementFor(original);
                    replacement.setStatus("ENTERED");
                    replacement.setCorrectionNote(blankToNull(reason));
                    em.persist(replacement);
                    for (TxnSplit split : originalSplits)
                    {
                        em.persist(copySplit(split, replacement, split.getAmountSigned()));
                    }
                }

                em.persist(audit(company, actor, "TRANSACTION_REVERSED", original, before, snapshot(reversal), reason));
                em.getTransaction().commit();
                return new CorrectionResult(reversal.getId(), replacement == null ? null : replacement.getId());
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    /**
     * Creates one canonical reversal inside a caller-owned transaction.
     * The caller may atomically attach a domain correction fact before committing.
     */
    public Txn reverse(
            EntityManager em,
            Company company,
            Txn original,
            LocalDate reversalDate,
            String actor,
            String reason,
            UUID reversalPortableId)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(reversalPortableId, "reversalPortableId");
        String normalizedActor = requireText(actor, "actor");
        if (reversalDate == null)
        {
            throw new IllegalArgumentException("reversalDate is required");
        }
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Caller-owned transaction is required.");
        }
        if (!em.contains(company) || !em.contains(original))
        {
            throw new IllegalArgumentException("Company and original transaction must be managed by the caller.");
        }

        ownership().ensureOwnedBy(em, company, original, "Transaction");
        requireEntered(original);
        requireNotReconciled(em, original.getId(), "reverse transaction");
        PeriodCloseRangeService.requireOpen(em, company.getCode(), reversalDate, "create reversal");
        Long identityCount = em.createQuery(
                        "select count(t) from Txn t where t.portableId = :portableId", Long.class)
                .setParameter("portableId", reversalPortableId)
                .getSingleResult();
        if (identityCount > 0)
        {
            throw new IllegalStateException(
                    "Reversal transaction portable identity is already in use: " + reversalPortableId);
        }

        List<TxnSplit> originalSplits = em.createQuery(
                        "from TxnSplit s where s.txn = :txn order by s.id", TxnSplit.class)
                .setParameter("txn", original)
                .getResultList();
        validateBalanced(originalSplits);
        validateSplitOwnership(em, company, originalSplits);
        String before = snapshot(original);

        Txn reversal = copyHeader(original, reversalDate);
        reversal.setPortableId(reversalPortableId);
        reversal.setReversalOf(original);
        reversal.setCorrectionNote(blankToNull(reason));
        em.persist(reversal);
        for (TxnSplit split : originalSplits)
        {
            em.persist(copySplit(split, reversal, split.getAmountSigned().negate()));
        }
        original.setStatus("REVERSED");
        original.touchUpdatedAt();
        em.persist(audit(
                company, normalizedActor, "TRANSACTION_REVERSED", original, before, snapshot(reversal), reason));
        return reversal;
    }

    /**
     * Restores one already-authoritative correction relationship during a caller-owned import
     * transaction. This does not replay a correction command, create transactions, or synthesize
     * audit history.
     */
    public void restoreRelationshipForImport(
            EntityManager em,
            Company company,
            Txn correction,
            String correctionType,
            Txn correctedTransaction)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(correction, "correction");
        Objects.requireNonNull(correctedTransaction, "correctedTransaction");
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Caller-owned transaction is required.");
        }
        ownership().ensureOwnedBy(em, company, correction, "Correction transaction");
        ownership().ensureOwnedBy(em, company, correctedTransaction, "Corrected transaction");
        if (correction == correctedTransaction)
        {
            throw new IllegalArgumentException("A transaction cannot correct itself.");
        }
        if (correction.getReversalOf() != null || correction.getReplacementFor() != null)
        {
            throw new IllegalStateException("Correction transaction already has a correction relationship.");
        }
        if ("REVERSAL".equals(correctionType))
        {
            correction.setReversalOf(correctedTransaction);
        }
        else if ("REPLACEMENT".equals(correctionType))
        {
            correction.setReplacementFor(correctedTransaction);
        }
        else
        {
            throw new IllegalArgumentException("Unsupported correctionType: " + correctionType);
        }
    }

    private void validateSplitOwnership(EntityManager em, Company company, List<TxnSplit> splits)
    {
        for (TxnSplit split : splits)
        {
            ownership().ensureOwnedBy(em, company, split.getAccount(), "Transaction line account");
            ownership().ensureOwnedBy(em, company, split.getFund(), "Transaction line fund");
            ownership().ensureOwnedBy(em, company, split.getBudgetCategory(), "Transaction line budget category");
            ownership().ensureOwnedBy(em, company, split.getActivity(), "Transaction line activity");
            ownership().ensureOwnedBy(em, company, split.getMerchant(), "Transaction line merchant");
        }
    }

    private Company selectedCompany(EntityManager em)
    {
        return ownership().requireCompany(em, companyCodeSupplier.get());
    }

    private CompanyOwnershipService ownership()
    {
        return new CompanyOwnershipService(jpa);
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
            throw new IllegalStateException("Cannot " + operation + " because transaction "
                    + transactionId + " is protected by a completed reconciliation.");
        }
        Number finalizedCount = (Number) em.createNativeQuery("""
                SELECT COUNT(*)
                FROM bank_reconciliation_session s
                JOIN bank_reconciliation_match m ON m.session_id = s.id
                JOIN txn_split ts ON ts.id = m.txn_split_id
                WHERE ts.txn_id = ?
                  AND s.status = 'FINALIZED'
                """)
                .setParameter(1, transactionId)
                .getSingleResult();
        if (finalizedCount.longValue() > 0)
        {
            throw new IllegalStateException("Cannot " + operation + " because transaction "
                    + transactionId + " is protected by a finalized reconciliation.");
        }
    }

    private void requireOpenRange(EntityManager em, LocalDate date, String operation)
    {
        PeriodCloseRangeService.requireOpen(em, companyCodeSupplier.get(), date, operation);
    }

    private static Txn copyHeader(Txn source, LocalDate date)
    {
        Txn target = new Txn();
        target.setCompany(source.getCompany());
        target.setTxnDate(date);
        target.setPayee(source.getPayee());
        target.setMemo(source.getMemo());
        target.setBankAccount(source.getBankAccount());
        return target;
    }

    private static TxnSplit copySplit(TxnSplit source, Txn target, BigDecimal amount)
    {
        TxnSplit copy = new TxnSplit();
        copy.setTxn(target);
        copy.setAccount(source.getAccount());
        copy.setFund(source.getFund());
        copy.setBudgetCategory(source.getBudgetCategory());
        copy.setActivity(source.getActivity());
        copy.setMerchant(source.getMerchant());
        copy.setNmr(source.isNmr());
        copy.setNotes(source.getNotes());
        copy.setAmountSigned(amount);
        return copy;
    }

    private static void validateBalanced(List<TxnSplit> splits)
    {
        if (splits.size() < 2)
        {
            throw new IllegalStateException("An entered transaction requires at least two lines");
        }
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (TxnSplit split : splits)
        {
            BigDecimal amount = split.getAmountSigned();
            boolean debit = split.getAccount().getNormalBalance() == NormalBalance.DEBIT
                    ? amount.signum() >= 0
                    : amount.signum() < 0;
            if (debit)
            {
                debits = debits.add(amount.abs());
            }
            else
            {
                credits = credits.add(amount.abs());
            }
        }
        if (debits.compareTo(credits) != 0)
        {
            throw new IllegalStateException("Transaction is not balanced");
        }
    }

    private static Txn requireTransaction(EntityManager em, long id)
    {
        Txn txn = em.find(Txn.class, id);
        if (txn == null)
        {
            throw new IllegalArgumentException("Unknown transaction: " + id);
        }
        return txn;
    }

    private static void requireEntered(Txn txn)
    {
        if (!"ENTERED".equals(txn.getStatus()))
        {
            throw new IllegalStateException("Only entered transactions may be corrected");
        }
    }

    private static AuditEvent audit(Company company, String actor, String action, Txn txn, String before, String after, String reason)
    {
        AuditEvent event = new AuditEvent();
        event.setCompany(company);
        event.setActor(requireText(actor, "actor"));
        event.setActionType(action);
        event.setEntityType("Txn");
        event.setEntityId(txn.getId() == null ? null : Long.toString(txn.getId()));
        event.setSummary(action.replace('_', ' ').toLowerCase());
        event.setBeforeValue(before);
        event.setAfterValue(after);
        event.setReason(blankToNull(reason));
        return event;
    }

    private static String snapshot(Txn txn)
    {
        return "id=" + txn.getId() + ",date=" + txn.getTxnDate() + ",status=" + txn.getStatus()
                + ",memo=" + (txn.getMemo() == null ? "" : txn.getMemo());
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void rollback(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }

    public record CorrectionResult(Long reversalTransactionId, Long replacementTransactionId)
    {
    }
}
