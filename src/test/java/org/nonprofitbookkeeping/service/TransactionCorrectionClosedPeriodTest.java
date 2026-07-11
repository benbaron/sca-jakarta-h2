package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Disabled("Temporary isolation while converting P10 correction tests")
public class TransactionCorrectionClosedPeriodTest
{
    @Test
    public void closedOriginalRange_blocksDirectEditAndDeleteWithoutTransactionAudit(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("closed-original")))
        {
            seedBalancedTransaction(jpa);
            PeriodCloseRangeService periods = new PeriodCloseRangeService(jpa);
            periods.closeRange(
                    "DEFAULT",
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 31),
                    "CALCULATED",
                    "treasurer",
                    "January close");

            TransactionCorrectionService service = new TransactionCorrectionService(jpa);

            assertThrows(ClosedPeriodRangeException.class, () -> service.directEdit(
                    1L,
                    LocalDate.of(2026, 1, 15),
                    "Changed",
                    "Attempted edit",
                    "treasurer"));
            assertThrows(ClosedPeriodRangeException.class, () -> service.delete(
                    1L,
                    "treasurer",
                    "Attempted delete"));

            try (EntityManager em = jpa.em())
            {
                Txn txn = em.find(Txn.class, 1L);
                assertNotNull(txn);
                assertEquals("Original", txn.getMemo());
                assertEquals(0L, em.createQuery("""
                        select count(a)
                        from AuditEvent a
                        where a.entityType = 'Txn'
                        """, Long.class).getSingleResult());
            }
        }
    }

    @Test
    public void closedDestinationRange_blocksMoveAndReversalWithRollback(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("closed-destination")))
        {
            seedBalancedTransaction(jpa);
            PeriodCloseRangeService periods = new PeriodCloseRangeService(jpa);
            periods.closeRange(
                    "DEFAULT",
                    LocalDate.of(2026, 2, 1),
                    LocalDate.of(2026, 2, 28),
                    "CALCULATED",
                    "treasurer",
                    "February close");

            TransactionCorrectionService service = new TransactionCorrectionService(jpa);
            LocalDate closedDate = LocalDate.of(2026, 2, 10);

            assertThrows(ClosedPeriodRangeException.class, () -> service.directEdit(
                    1L,
                    closedDate,
                    "Moved",
                    null,
                    "treasurer"));
            assertThrows(ClosedPeriodRangeException.class, () -> service.reverse(
                    1L,
                    closedDate,
                    "treasurer",
                    "Attempted reversal",
                    true));

            try (EntityManager em = jpa.em())
            {
                Txn txn = em.find(Txn.class, 1L);
                assertEquals(LocalDate.of(2026, 1, 10), txn.getTxnDate());
                assertEquals("ENTERED", txn.getStatus());
                assertEquals(1L, em.createQuery("select count(t) from Txn t", Long.class)
                        .getSingleResult());
                assertEquals(0L, em.createQuery("""
                        select count(a)
                        from AuditEvent a
                        where a.entityType = 'Txn'
                        """, Long.class).getSingleResult());
            }
        }
    }

    @Test
    public void closedOriginalRange_canBeReversedIntoOpenRange(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("closed-original-reversal")))
        {
            seedBalancedTransaction(jpa);
            PeriodCloseRangeService periods = new PeriodCloseRangeService(jpa);
            PeriodCloseRangeView january = periods.closeRange(
                    "DEFAULT",
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 31),
                    "CALCULATED",
                    "treasurer",
                    "January close");

            TransactionCorrectionService service = new TransactionCorrectionService(jpa);
            TransactionCorrectionService.CorrectionResult result = service.reverse(
                    1L,
                    LocalDate.of(2026, 2, 10),
                    "treasurer",
                    "Reverse prior-period error",
                    false);

            assertNotNull(result.reversalTransactionId());
            try (EntityManager em = jpa.em())
            {
                assertEquals("REVERSED", em.find(Txn.class, 1L).getStatus());
                Txn reversal = em.find(Txn.class, result.reversalTransactionId());
                assertEquals(LocalDate.of(2026, 2, 10), reversal.getTxnDate());
            }

            periods.reopenRange(
                    january.id(),
                    "treasurer",
                    null,
                    ClosedPeriodPolicy.WARN_AND_REOPEN,
                    false);
            assertEquals("REOPENED", periods.loadRange(january.id()).status());
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
            em.createNativeQuery("ALTER TABLE txn ALTER COLUMN id RESTART WITH 2").executeUpdate();
            em.createNativeQuery("ALTER TABLE txn_split ALTER COLUMN id RESTART WITH 3").executeUpdate();
            em.getTransaction().commit();
        }
    }
}
