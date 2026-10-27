package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AtomicPeriodCloseTest
{
    @TempDir Path directory;
    static final LocalDate START = LocalDate.of(2026, 6, 1), END = LocalDate.of(2026, 6, 30);

    @Test
    void reviewedCloseRetryRestartAndReopenRequireFreshReviewAndPreserveEvidence() throws Exception
    {
        Path db = directory.resolve("reviewed"); UUID reviewId;
        try (Jpa jpa = new Jpa(db))
        {
            var review = PeriodCloseTestFixture.readyReview(jpa, db, "DEFAULT", START, END); reviewId = review.id();
            var service = new PeriodCloseRangeService(jpa);
            var closed = service.closeRange("DEFAULT", START, END, "CUSTOM", "tester", "Reviewed close", reviewId);
            assertEquals(reviewId, closed.id());
            assertEquals(closed, service.closeRange("DEFAULT", START, END, "CUSTOM", "different fallback actor", "Reviewed close", reviewId));
            assertEquals(1, service.listEvents("DEFAULT").size());
            try (var em = jpa.em())
            {
                String proof = em.createQuery("select e.afterValue from AuditEvent e where e.actionType='PERIOD_RANGE_CLOSED'", String.class).getSingleResult();
                assertTrue(proof.contains(reviewId.toString())); assertTrue(proof.contains(review.payload().dataRevision()));
            }
            assertThrows(IllegalStateException.class, () -> service.closeRange("DEFAULT", START, END, "CUSTOM", "tester", "Changed", reviewId));
            service.reopenRange(closed.id(), "tester", "Correction", ClosedPeriodPolicy.REQUIRE_REASON, true);
            assertThrows(IllegalStateException.class, () -> service.closeRange("DEFAULT", START, END, "CUSTOM", "tester", "Reviewed close", reviewId));
            var fresh = PeriodCloseTestFixture.readyReview(jpa, db, "DEFAULT", START, END);
            assertNotEquals(reviewId, service.closeRange("DEFAULT", START, END, "CUSTOM", "tester", "Reclosed", fresh.id()).id());
            assertEquals(3, service.listEvents("DEFAULT").size());
            assertEquals(2, PeriodCloseTestFixture.evidence(jpa, db, "DEFAULT").history(START, END).size());
        }
        try (Jpa jpa = new Jpa(db))
        {
            assertEquals(2, new PeriodCloseRangeService(jpa).listRanges("DEFAULT").size());
            assertEquals(3, new PeriodCloseRangeService(jpa).listEvents("DEFAULT").size());
        }
    }

    @Test
    void missingReviewsStaleContentBlockersAndUnreasonedWarningsCannotClose() throws Exception
    {
        Path db = directory.resolve("blocked");
        try (Jpa jpa = new Jpa(db))
        {
            var service = new PeriodCloseRangeService(jpa);
            assertThrows(IllegalArgumentException.class, () -> service.closeRange("DEFAULT", START, END, "CUSTOM", "tester", null));
            var review = PeriodCloseTestFixture.readyReview(jpa, db, "DEFAULT", START, END);
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("update company set display_name='Changed after review' where code='DEFAULT'").executeUpdate();
                em.getTransaction().commit();
            }
            // A label outside rendered accounting tables is not itself a balance change; use a saved budget source field.
            try (var em = jpa.em())
            {
                em.getTransaction().begin(); em.createNativeQuery("update budget_plan set notes='Changed budget source'").executeUpdate(); em.getTransaction().commit();
            }
            assertThrows(IllegalStateException.class, () -> service.closeRange("DEFAULT", START, END, "CUSTOM", "tester", null, review.id()));
            PeriodCloseTestFixture.readyReview(jpa, db, "DEFAULT", START, END);
            new TransactionEntryService(jpa, () -> "DEFAULT").enter(receipt(jpa));
            var evidence = PeriodCloseTestFixture.evidence(jpa, db, "DEFAULT"); var preview = evidence.preview(START, END);
            assertEquals(0, preview.blockers(), () -> preview.findings().toString()); assertTrue(preview.warnings() > 0);
            var noReasons = evidence.save(new CloseReviewEvidenceService.Command(UUID.randomUUID(), START, END, preview.dataRevision(),
                    List.of(new CloseReviewEvidenceService.Reference(CloseReviewEvidenceService.ReferenceKind.OTHER, "synthetic/warnings", "Factual review")), Map.of(), "", true));
            assertThrows(IllegalStateException.class, () -> service.closeRange("DEFAULT", START, END, "CUSTOM", "tester", null, noReasons.id()));
            var reasons = new java.util.TreeMap<String, String>(); preview.findings().forEach(f -> reasons.put(f.id(), "Expected outstanding deposit and explicit non-event receipt"));
            var reasoned = evidence.save(new CloseReviewEvidenceService.Command(UUID.randomUUID(), START, END, preview.dataRevision(), noReasons.payload().references(), reasons, "", true));
            assertEquals(reasoned.id(), service.closeRange("DEFAULT", START, END, "CUSTOM", "tester", null, reasoned.id()).id());
        }
        try (Jpa jpa = new Jpa(directory.resolve("financial-blocker")))
        {
            Path path = directory.resolve("financial-blocker"); PeriodCloseTestFixture.readyReview(jpa, path, "DEFAULT", START, END);
            try (var em = jpa.em())
            {
                em.getTransaction().begin(); em.createNativeQuery("update account set opening_balance=10 where code='1001'").executeUpdate(); em.getTransaction().commit();
            }
            var evidence = PeriodCloseTestFixture.evidence(jpa, path, "DEFAULT"); var preview = evidence.preview(START, END); assertTrue(preview.blockers() > 0);
            var review = evidence.save(new CloseReviewEvidenceService.Command(UUID.randomUUID(), START, END, preview.dataRevision(),
                    List.of(new CloseReviewEvidenceService.Reference(CloseReviewEvidenceService.ReferenceKind.OTHER, "synthetic/blocker", "Recorded unresolved")), Map.of(), "", true));
            assertThrows(IllegalStateException.class, () -> new PeriodCloseRangeService(jpa).closeRange("DEFAULT", START, END, "CUSTOM", "tester", null, review.id()));
        }
    }

    @Test
    void closeHoldsGuardThroughCommitOperationalWriteWaitsAndFinancialWriterRechecksClosedRange() throws Exception
    {
        Path db = directory.resolve("close-first");
        try (Jpa jpa = new Jpa(db))
        {
            var review = PeriodCloseTestFixture.readyReview(jpa, db, "DEFAULT", START, END);
            CountDownLatch assessed = new CountDownLatch(1), release = new CountDownLatch(1), writerStarted = new CountDownLatch(1);
            var assessedAt = new java.util.concurrent.atomic.AtomicReference<java.time.Instant>();
            var close = new PeriodCloseRangeService(jpa, null, snapshot ->
            {
                assessedAt.set(snapshot.capturedAt());
                assessed.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); } catch (InterruptedException ex) { throw new RuntimeException(ex); }
            });
            var pool = Executors.newFixedThreadPool(3);
            try
            {
                Future<?> closing = pool.submit(() -> close.closeRange("DEFAULT", START, END, "CUSTOM", "tester", null, review.id()));
                assertTrue(assessed.await(10, TimeUnit.SECONDS));
                TransactionCommand receipt = receipt(jpa);
                Future<?> entering = pool.submit(() -> { writerStarted.countDown(); return new TransactionEntryService(jpa, () -> "DEFAULT").enter(receipt); });
                Future<?> budget = pool.submit(() ->
                {
                    try (var em = jpa.em())
                    {
                        em.getTransaction().begin(); em.createNativeQuery("set lock_timeout 10000").executeUpdate();
                        em.createNativeQuery("update budget_plan set notes='After close commit'").executeUpdate(); em.getTransaction().commit();
                    }
                });
                assertTrue(writerStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> entering.get(150, TimeUnit.MILLISECONDS));
                assertThrows(TimeoutException.class, () -> budget.get(150, TimeUnit.MILLISECONDS));
                release.countDown();
                var closed = (PeriodCloseRangeView) closing.get(10, TimeUnit.SECONDS);
                assertFalse(closed.closedAt().isBefore(assessedAt.get()), "Factual close time must follow guarded revalidation");
                budget.get(10, TimeUnit.SECONDS);
                var failed = assertThrows(ExecutionException.class, () -> entering.get(10, TimeUnit.SECONDS));
                assertInstanceOf(ClosedPeriodRangeException.class, failed.getCause());
                assertEquals(1, close.listEvents("DEFAULT").size());
            }
            finally { release.countDown(); pool.shutdownNow(); }
        }
    }

    @Test
    void earlierWriterCommitsBeforeReassessmentAndCloseFailureRollsBackWithoutHistory() throws Exception
    {
        Path db = directory.resolve("writer-first");
        try (Jpa jpa = new Jpa(db); var writer = jpa.em())
        {
            var review = PeriodCloseTestFixture.readyReview(jpa, db, "DEFAULT", START, END);
            writer.getTransaction().begin(); writer.createNativeQuery("update budget_plan set notes='Before close validation'").executeUpdate();
            var pool = Executors.newSingleThreadExecutor(); var started = new CountDownLatch(1);
            try
            {
                Future<?> close = pool.submit(() -> { started.countDown(); return new PeriodCloseRangeService(jpa).closeRange("DEFAULT", START, END, "CUSTOM", "tester", null, review.id()); });
                assertTrue(started.await(5, TimeUnit.SECONDS)); assertThrows(TimeoutException.class, () -> close.get(150, TimeUnit.MILLISECONDS));
                writer.getTransaction().commit();
                assertInstanceOf(IllegalStateException.class, assertThrows(ExecutionException.class, () -> close.get(10, TimeUnit.SECONDS)).getCause());
                assertTrue(new PeriodCloseRangeService(jpa).listRanges("DEFAULT").isEmpty());
                var fresh = PeriodCloseTestFixture.readyReview(jpa, db, "DEFAULT", START, END);
                var failing = new PeriodCloseRangeService(jpa, null, assessed -> { throw new IllegalStateException("Synthetic precommit failure"); });
                assertThrows(IllegalStateException.class, () -> failing.closeRange("DEFAULT", START, END, "CUSTOM", "tester", null, fresh.id()));
                assertTrue(failing.listEvents("DEFAULT").isEmpty());
            }
            finally { if (writer.getTransaction().isActive()) writer.getTransaction().rollback(); pool.shutdownNow(); }
        }
    }

    @Test
    void simultaneousClosesAreIdempotentAndPermissionOrPostPersistFailureLeavesNoPartialHistory() throws Exception
    {
        Path db = directory.resolve("simultaneous");
        try (Jpa jpa = new Jpa(db))
        {
            var review = PeriodCloseTestFixture.readyReview(jpa, db, "DEFAULT", START, END);
            var failing = new PeriodCloseRangeService(jpa, null, assessed -> { }, () -> { throw new IllegalStateException("Synthetic after-persist failure"); });
            assertThrows(IllegalStateException.class, () -> failing.closeRange("DEFAULT", START, END, "CUSTOM", "tester", null, review.id()));
            assertTrue(failing.listRanges("DEFAULT").isEmpty()); assertTrue(failing.listEvents("DEFAULT").isEmpty());
            var current = new java.util.concurrent.atomic.AtomicReference<java.util.Optional<AuthenticatedUserSession>>(
                    java.util.Optional.of(new AuthenticatedUserSession(9001, "accountant", "Accountant", "DEFAULT", java.util.Set.of(ReservedSecurityRole.ACCOUNTANT), java.time.Instant.EPOCH, java.time.Instant.EPOCH)));
            var guard = new AuthorizationGuard(jpa, current::get);
            var revoked = new PeriodCloseRangeService(jpa, guard, assessed -> current.set(java.util.Optional.empty()));
            assertThrows(AuthorizationException.class, () -> revoked.closeRange("DEFAULT", START, END, "CUSTOM", "forged actor", null, review.id()));
            assertTrue(revoked.listRanges("DEFAULT").isEmpty());
            var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
            try
            {
                Callable<PeriodCloseRangeView> action = () -> { start.await(); return new PeriodCloseRangeService(jpa).closeRange("DEFAULT", START, END, "CUSTOM", "tester", null, review.id()); };
                var one = pool.submit(action); var two = pool.submit(action); start.countDown();
                assertEquals(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
                assertEquals(1, failing.listEvents("DEFAULT").size());
                assertThrows(IllegalArgumentException.class, () -> failing.closeRange("DEFAULT", START.minusDays(1), END, "CUSTOM", "tester", null, review.id()));
            }
            finally { pool.shutdownNow(); }
        }
    }

    private static TransactionCommand receipt(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            long bank = em.createQuery("select a.id from Account a where a.code='1001'", Long.class).getSingleResult();
            long income = em.createQuery("select a.id from Account a where a.code='4001'", Long.class).getSingleResult();
            long fund = em.createQuery("select f.id from Fund f", Long.class).getResultList().get(0);
            return new TransactionCommand(START.plusDays(1), null, "Synthetic outstanding receipt", null,
                    List.of(new TransactionLineCommand(bank, fund, null, null, null, new BigDecimal("5"), BigDecimal.ZERO, false, ""),
                            new TransactionLineCommand(income, fund, null, null, null, BigDecimal.ZERO, new BigDecimal("5"), false, "")));
        }
    }
}
