package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TransactionCorrectionRequirementTest
{
    @Test
    public void alreadyReversedTransaction_cannotBeEditedDeletedOrReversedAgain(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("correction-state")))
        {
            seedReversedTransaction(jpa);
            TransactionCorrectionService service = new TransactionCorrectionService(jpa);

            assertThrows(IllegalStateException.class, () -> service.directEdit(
                    1L, LocalDate.of(2026, 1, 2), "Changed", null, "treasurer"));
            assertThrows(IllegalStateException.class, () -> service.delete(
                    1L, "treasurer", "Delete reversed transaction"));
            assertThrows(IllegalStateException.class, () -> service.reverse(
                    1L, LocalDate.of(2026, 1, 2), "treasurer", null, false));

            try (EntityManager em = jpa.em())
            {
                assertEquals(1L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(a) from AuditEvent a", Long.class).getSingleResult());
            }
        }
    }

    @Test
    public void missingTransaction_correctionDoesNotCreateAuditHistory(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("correction-missing")))
        {
            TransactionCorrectionService service = new TransactionCorrectionService(jpa);
            assertThrows(IllegalArgumentException.class, () -> service.delete(999L, "treasurer", "Missing"));

            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, em.createQuery("select count(a) from AuditEvent a", Long.class).getSingleResult());
            }
        }
    }

    private static void seedReversedTransaction(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO txn (id, txn_date, memo, status) VALUES (1, DATE '2026-01-01', 'Reversed', 'REVERSED')").executeUpdate();
            em.getTransaction().commit();
        }
    }
}
