package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TransactionCorrectionReconciliationProtectionTest
{
    @Test
    public void completedReconciliation_blocksEditDeleteReverseAndRollsBack(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("reconciled-protection")))
        {
            seedBalancedTransaction(jpa);
            protectWithCompletedReconciliation(jpa, 1L);
            TransactionCorrectionService corrections = new TransactionCorrectionService(jpa);
            TransactionEntryService entry = new TransactionEntryService(jpa);

            assertThrows(IllegalStateException.class, () -> corrections.directEdit(
                    1L,
                    LocalDate.of(2026, 1, 11),
                    "Changed",
                    "Correction",
                    "treasurer"));
            assertThrows(IllegalStateException.class, () -> corrections.delete(
                    1L,
                    "treasurer",
                    "Duplicate"));
            assertThrows(IllegalStateException.class, () -> corrections.reverse(
                    1L,
                    LocalDate.of(2026, 2, 1),
                    "treasurer",
                    "Reverse reconciled entry",
                    false));
            assertThrows(PostingException.class, () -> entry.update(
                    1L,
                    new TransactionCommand(
                            LocalDate.of(2026, 1, 12),
                            null,
                            "Entry service change",
                            null,
                            java.util.List.of(
                                    new TransactionLineCommand(1L, 1L, null, null, null, java.math.BigDecimal.TEN, null, false, null),
                                    new TransactionLineCommand(2L, 1L, null, null, null, null, java.math.BigDecimal.TEN, false, null)))));

            try (EntityManager em = jpa.em())
            {
                assertEquals("ENTERED", em.find(org.nonprofitbookkeeping.model.Txn.class, 1L).getStatus());
                assertEquals(1L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(a) from AuditEvent a where a.entityType = 'Txn'", Long.class).getSingleResult());
            }
        }
    }

    private static void protectWithCompletedReconciliation(Jpa jpa, long transactionId)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            UUID runId = UUID.randomUUID();
            em.createNativeQuery("INSERT INTO reconciliation_run (id, group_code, statement_ending_on, bank_format, imported_transaction_count, status, notes) VALUES (?, 'BARONY', DATE '2026-01-31', 'OFX', 1, 'COMPLETED', 'January')")
                    .setParameter(1, runId)
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO txn_reconciliation_protection (txn_id, reconciliation_run_id, protected_by, notes) VALUES (?, ?, 'treasurer', 'Cleared in January')")
                    .setParameter(1, transactionId)
                    .setParameter(2, runId)
                    .executeUpdate();
            em.getTransaction().commit();
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
