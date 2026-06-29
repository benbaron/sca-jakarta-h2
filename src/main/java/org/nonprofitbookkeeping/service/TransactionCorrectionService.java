package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Applies direct edits, deletions, reversals, and replacement transactions.
 */
@ApplicationScoped
public class TransactionCorrectionService
{
    private final Jpa jpa;

    @Inject
    public TransactionCorrectionService(Jpa jpa)
    {
        this.jpa = jpa;
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
                Txn txn = requireTransaction(em, transactionId);
                requireEntered(txn);
                String before = snapshot(txn);
                txn.setTxnDate(transactionDate);
                txn.setMemo(blankToNull(memo));
                txn.setCorrectionNote(blankToNull(correctionNote));
                txn.touchUpdatedAt();
                em.persist(audit(actor, "TRANSACTION_EDITED", txn, before, snapshot(txn), correctionNote));
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
                Txn txn = requireTransaction(em, transactionId);
                requireEntered(txn);
                AuditEvent event = audit(actor, "TRANSACTION_DELETED", txn, snapshot(txn), null, reason);
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
                Txn original = requireTransaction(em, transactionId);
                requireEntered(original);
                List<TxnSplit> originalSplits = em.createQuery(
                        "from TxnSplit s where s.txn.id = :id order by s.id", TxnSplit.class)
                        .setParameter("id", transactionId)
                        .getResultList();
                validateBalanced(originalSplits);

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

                em.persist(audit(actor, "TRANSACTION_REVERSED", original, snapshot(original), snapshot(reversal), reason));
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

    private static Txn copyHeader(Txn source, LocalDate date)
    {
        Txn target = new Txn();
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
        BigDecimal total = splits.stream()
                .map(TxnSplit::getAmountSigned)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) != 0)
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

    private static AuditEvent audit(String actor, String action, Txn txn, String before, String after, String reason)
    {
        AuditEvent event = new AuditEvent();
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
