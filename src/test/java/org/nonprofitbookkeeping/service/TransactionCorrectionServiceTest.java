package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TransactionCorrectionServiceTest
{
    @Test
    public void reverse_createsOppositeLinesAndOptionalReplacement(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("transaction-correction")))
        {
            seedBalancedTransaction(jpa);
            TransactionCorrectionService service = new TransactionCorrectionService(jpa);

            TransactionCorrectionService.CorrectionResult result = service.reverse(
                    1L, LocalDate.of(2026, 2, 1), "treasurer", "Correct coding", true);

            assertNotNull(result.reversalTransactionId());
            assertNotNull(result.replacementTransactionId());

            try (EntityManager em = jpa.em())
            {
                Txn original = em.find(Txn.class, 1L);
                assertEquals("REVERSED", original.getStatus());

                List<TxnSplit> reversalLines = em.createQuery(
                        "from TxnSplit s where s.txn.id = :id order by s.id", TxnSplit.class)
                        .setParameter("id", result.reversalTransactionId())
                        .getResultList();
                assertEquals(new BigDecimal("-100.0000"), reversalLines.get(0).getAmountSigned());
                assertEquals(new BigDecimal("100.0000"), reversalLines.get(1).getAmountSigned());

                List<AuditEvent> audits = em.createQuery(
                        "from AuditEvent a where a.actionType = 'TRANSACTION_REVERSED'", AuditEvent.class)
                        .getResultList();
                assertEquals(1, audits.size());
            }
        }
    }

    @Test
    public void reverse_rejectsUnbalancedTransactionAndRollsBack(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("transaction-unbalanced")))
        {
            seedBalancedTransaction(jpa);
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("UPDATE txn_split SET amount_signed = 90.0000 WHERE id = 2").executeUpdate();
                em.getTransaction().commit();
            }

            TransactionCorrectionService service = new TransactionCorrectionService(jpa);
            assertThrows(IllegalStateException.class, () -> service.reverse(
                    1L, LocalDate.of(2026, 2, 1), "treasurer", null, false));

            try (EntityManager em = jpa.em())
            {
                assertEquals("ENTERED", em.find(Txn.class, 1L).getStatus());
                assertEquals(1L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(a) from AuditEvent a", Long.class).getSingleResult());
            }
        }
    }

    @Test
    public void directEditAndDelete_writeAuditHistory(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("transaction-edit-delete")))
        {
            seedBalancedTransaction(jpa);
            TransactionCorrectionService service = new TransactionCorrectionService(jpa);

            Txn edited = service.directEdit(1L, LocalDate.of(2026, 1, 15), "Updated memo", "Correction", "treasurer");
            assertEquals("Updated memo", edited.getMemo());

            service.delete(1L, "treasurer", "Duplicate entry");

            try (EntityManager em = jpa.em())
            {
                assertNull(em.find(Txn.class, 1L));
                assertEquals(2L, em.createQuery("select count(a) from AuditEvent a", Long.class).getSingleResult());
            }
        }
    }

    private static void seedBalancedTransaction(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (1, 'Test', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (1, 1, '1000', 'Cash', 'ASSET', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (2, 1, '4000', 'Income', 'INCOME', 'CREDIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, code, name, fund_type) VALUES (1, 'OPERATING', 'Operating', 'UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn (id, txn_date, memo) VALUES (1, DATE '2026-01-10', 'Original')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (1, 1, 1, 1, 100.0000)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (2, 1, 2, 1, -100.0000)").executeUpdate();
            em.getTransaction().commit();
        }
    }
}
