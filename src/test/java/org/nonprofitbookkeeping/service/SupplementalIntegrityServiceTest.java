package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.SupplementalOpenItemQueryServiceTest.*;

class SupplementalIntegrityServiceTest
{
    private static final SupplementalOpenItemQueryService.Kind KIND = SupplementalOpenItemQueryService.Kind.PAYABLE;
    private static final LocalDate DATE = LocalDate.of(2026, 1, 10);

    @Test
    void rejectsMissingAndPartialControlAllocationsAtomically(@TempDir Path dir)
    {
        try (Jpa jpa = new Jpa(dir.resolve("coverage")))
        {
            seed(jpa);
            var entry = new TransactionEntryService(jpa, () -> "TEST");
            var lines = List.of(new TransactionLineCommand(1006L, 1001L, null, null, null,
                    new BigDecimal("100"), BigDecimal.ZERO, false, null),
                    new TransactionLineCommand(1002L, 1001L, null, null, null,
                    BigDecimal.ZERO, new BigDecimal("100"), false, null));
            assertThrows(PostingException.class, () -> entry.enter(new TransactionCommand(DATE, null, "missing", null, lines)));
            var detail = new TransactionSupplementalLineCommand("PAYABLE", "A03", "Vendor", "Partial", null,
                    new BigDecimal("50"), null, null, null, null, null, UUID.randomUUID(), "INCREASE", 1);
            assertThrows(PostingException.class, () -> entry.enter(new TransactionCommand(DATE, null, "partial", null, lines, List.of(detail))));
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                var company = em.find(org.nonprofitbookkeeping.model.Company.class, 100L);
                assertThrows(PostingException.class, () -> entry.enter(em, company,
                        new TransactionCommand(DATE, null, "generated", null, lines), UUID.randomUUID(), "generator", "generated entry"));
                em.getTransaction().rollback();
            }
            assertTrue(entry.search(null, null, null, 100).isEmpty());
        }
    }

    @Test
    void sequentialAndCorrectionOverdrawRollBack(@TempDir Path dir)
    {
        try (Jpa jpa = new Jpa(dir.resolve("correction")))
        {
            seed(jpa);
            var entry = new TransactionEntryService(jpa, () -> "TEST");
            UUID item = UUID.randomUUID();
            var opening = enterIncrease(entry, KIND, item, DATE, new BigDecimal("100"));
            var payment = enterDecrease(entry, KIND, item, DATE.plusDays(2), new BigDecimal("60"));
            assertThrows(PostingException.class, () -> enterDecrease(entry, KIND, item, DATE.plusDays(1), new BigDecimal("60")));
            var corrections = new TransactionCorrectionService(jpa, () -> "TEST");
            assertThrows(PostingException.class, () -> corrections.reverse(opening.id(), DATE.plusDays(3), "tester", "invalid cancellation", false));
            assertThrows(PostingException.class, () -> corrections.delete(opening.id(), "tester", "invalid deletion"));
            assertThrows(PostingException.class, () -> corrections.directEdit(opening.id(), DATE.plusDays(3), "moved opening",
                    "invalid date change", "tester"));
            assertThrows(PostingException.class, () -> entry.update(payment.id(), decreaseCommand(KIND, item, DATE.plusDays(2), new BigDecimal("120"))));
            assertEquals(new BigDecimal("40.0000"), new SupplementalOpenItemQueryService(jpa, () -> "TEST").query(KIND, DATE.plusDays(3)).openAmount());
            assertEquals(2, entry.search(null, null, null, 100).size());
        }
    }

    @Test
    void legacyOffsetsCannotHideGrossGapsAndRepairRestoresReadiness(@TempDir Path dir)
    {
        try (Jpa jpa = new Jpa(dir.resolve("diagnostics")))
        {
            seed(jpa);
            var entry = new TransactionEntryService(jpa, () -> "TEST");
            UUID item = UUID.randomUUID();
            var opening = enterIncrease(entry, KIND, item, DATE, new BigDecimal("100"));
            var payment = enterDecrease(entry, KIND, item, DATE.plusDays(1), new BigDecimal("100"));
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.createQuery("delete from TxnSupplementalLine").executeUpdate();
                em.getTransaction().commit();
            }
            var projection = new SupplementalOpenItemQueryService(jpa, () -> "TEST").query(KIND, DATE.plusDays(1));
            assertFalse(projection.ready());
            var control = projection.reconciliation().controls().get(0);
            assertEquals(0, control.difference().signum());
            assertEquals(new BigDecimal("200.0000"), control.unmatched());
            assertEquals(2, projection.reconciliation().gaps().size());
            assertEquals(opening.id(), projection.reconciliation().gaps().get(0).transactionId());
            assertEquals(payment.id(), projection.reconciliation().gaps().get(1).transactionId());
            entry.update(opening.id(), increaseCommand(KIND, item, DATE, new BigDecimal("100")));
            entry.update(payment.id(), decreaseCommand(KIND, item, DATE.plusDays(1), new BigDecimal("100")));
            assertTrue(new SupplementalOpenItemQueryService(jpa, () -> "TEST").query(KIND, DATE.plusDays(1)).ready());
        }
    }

    @Test
    void competingApplicationsCannotOverdraw(@TempDir Path dir) throws Exception
    {
        try (Jpa jpa = new Jpa(dir.resolve("race")))
        {
            seed(jpa);
            var entry = new TransactionEntryService(jpa, () -> "TEST");
            UUID item = UUID.randomUUID();
            enterIncrease(entry, KIND, item, DATE, new BigDecimal("100"));
            var start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try
            {
                Callable<Boolean> apply = () ->
                {
                    start.await();
                    try
                    {
                        enterDecrease(new TransactionEntryService(jpa, () -> "TEST"), KIND, item, DATE.plusDays(1), new BigDecimal("60"));
                        return true;
                    }
                    catch (PostingException ex)
                    {
                        return false;
                    }
                };
                Future<Boolean> first = pool.submit(apply);
                Future<Boolean> second = pool.submit(apply);
                start.countDown();
                assertNotEquals(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
                assertEquals(new BigDecimal("40.0000"), new SupplementalOpenItemQueryService(jpa, () -> "TEST").query(KIND, DATE.plusDays(1)).openAmount());
            }
            finally
            {
                pool.shutdownNow();
            }
        }
    }
}
