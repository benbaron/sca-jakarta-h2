package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.sclx.*;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.DatabaseTransferService;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.DatabaseSnapshotTestSupport.rows;

/** Exercises the real file boundary rather than invoking the historical import seam directly. */
class ClosePortabilityAuditTest
{
    @TempDir Path directory;
    private static final LocalDate START = LocalDate.of(2026, 6, 1);
    private static final LocalDate END = LocalDate.of(2026, 6, 30);

    @Test
    void actualSclxFileRetainsReviewCloseReopenProofAndRetryAcrossCompanyRemappingAndRestart() throws Exception
    {
        Path source = directory.resolve("source");
        Path target = directory.resolve("target");
        Path file = directory.resolve("reviewed.sclx");
        CloseReviewEvidenceService.Review review;
        List<PeriodCloseRangeView> ranges;
        List<PeriodCloseEventView> events;
        String proof;
        try (Jpa jpa = new Jpa(source))
        {
            PeriodCloseTestFixture.readyReview(jpa, source, "DEFAULT", START, END);
            // An outstanding bank receipt produces a real warning with an explicitly retained reason.
            try (var em = jpa.em())
            {
                long bank = em.createQuery("select b.account.id from CompanyBankAccount b where b.company.code='DEFAULT'", Long.class).getSingleResult();
                long income = em.createQuery("select a.id from Account a where a.accountType=org.nonprofitbookkeeping.model.AccountType.INCOME", Long.class).getSingleResult();
                long fund = em.createQuery("select f.id from Fund f where f.company.code='DEFAULT'", Long.class).getSingleResult();
                new TransactionEntryService(jpa).enter(new TransactionCommand(START, null, "Synthetic outstanding receipt", null,
                        List.of(FundTransferTestFixture.line(bank, fund, "1", "0"), FundTransferTestFixture.line(income, fund, "0", "1"))));
            }
            review = PeriodCloseTestFixture.readyReview(jpa, source, "DEFAULT", START, END);
            assertFalse(review.payload().warningReasons().isEmpty());
            var periods = new PeriodCloseRangeService(jpa);
            var closed = periods.closeRange("DEFAULT", START, END, "CUSTOM", "tester", "Reviewed", review.id());
            periods.reopenRange(closed.id(), "tester", "Retain correction history", ClosedPeriodPolicy.REQUIRE_REASON, true);
            ranges = periods.listRanges("DEFAULT");
            events = periods.listEvents("DEFAULT");
            proof = closeProof(jpa, "DEFAULT");
            var exporter = new SclxFileExportService(new SclxCoreSnapshotQueryService(jpa), () -> source);
            exporter.export(new SclxExportRequest(file, Instant.EPOCH, false));
        }
        try (Jpa jpa = new Jpa(target))
        {
            company(jpa, "COPY");
            var preview = new SclxImportPreviewService(jpa, () -> "COPY").preview(file);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            var imported = new SclxImportCommitService(jpa, () -> "COPY").commit(file, preview, "Importer", true, true);
            assertTrue(imported.committed(), imported.toString());
            var recorded = PeriodCloseTestFixture.evidence(jpa, target, "COPY").history(START, END).stream()
                    .filter(row -> row.id().equals(review.id())).findFirst().orElseThrow();
            assertEquals(review.id(), recorded.id());
            assertEquals("COPY", recorded.companyCode());
            assertEquals(review.payload(), recorded.payload(), "Keep source-local findings, revisions and references as historical text");
            assertEquals(review.actor(), recorded.actor());
            assertEquals(review.recordedAt(), recorded.recordedAt());
            assertEquals(proof, closeProof(jpa, "COPY"));
            var periods = new PeriodCloseRangeService(jpa);
            assertEquals(ranges.stream().map(PeriodCloseRangeView::id).toList(), periods.listRanges("COPY").stream().map(PeriodCloseRangeView::id).toList());
            assertEquals(events.stream().map(PeriodCloseEventView::id).toList(), periods.listEvents("COPY").stream().map(PeriodCloseEventView::id).toList());
            assertEquals("REOPENED", periods.listRanges("COPY").get(0).status());
            assertThrows(IllegalStateException.class, () -> periods.closeRange("COPY", START, END, "CUSTOM", "tester", "Reviewed", review.id()));
            assertTrue(PeriodCloseTestFixture.evidence(jpa, target, "DEFAULT").history(START, END).isEmpty());
        }
        try (Jpa jpa = new Jpa(target))
        {
            var before = rows(target);
            var preview = new SclxImportPreviewService(jpa, () -> "COPY").preview(file);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            assertTrue(new SclxImportCommitService(jpa, () -> "COPY").commit(file, preview, "Importer", true, true).committed());
            assertEquals(before, rows(target), "An exact file retry after restart must not manufacture history");
        }
    }

    @Test
    void importedReviewCannotWaiveCurrentBlockersOrAuthorizeChangedTargetContent() throws Exception
    {
        Path source = directory.resolve("open-source");
        Path target = directory.resolve("changed-target");
        Path file = directory.resolve("open-review.sclx");
        CloseReviewEvidenceService.Review review;
        try (Jpa jpa = new Jpa(source))
        {
            review = PeriodCloseTestFixture.readyReview(jpa, source, "DEFAULT", START, END);
            new SclxFileExportService(new SclxCoreSnapshotQueryService(jpa), () -> source)
                    .export(new SclxExportRequest(file, Instant.EPOCH, false));
        }
        try (Jpa jpa = new Jpa(target))
        {
            var preview = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            assertTrue(new SclxImportCommitService(jpa, () -> "DEFAULT").commit(file, preview, "Importer", true, true).committed());
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("update budget_plan set notes='Target changed after import'").executeUpdate();
                em.getTransaction().commit();
            }
            var periods = new PeriodCloseRangeService(jpa);
            var before = rows(target);
            assertThrows(IllegalStateException.class, () -> periods.closeRange("DEFAULT", START, END, "CUSTOM", "tester", null, review.id()));
            assertEquals(before, rows(target), "Rejection must leave no range, event or audit write");
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("update budget_plan set status='DRAFT'").executeUpdate();
                em.getTransaction().commit();
            }
            var current = PeriodCloseTestFixture.evidence(jpa, target, "DEFAULT").preview(START, END);
            assertTrue(current.blockers() > 0);
            assertEquals(0, review.blockers(), "Source review was ready, but cannot certify the target");
            before = rows(target);
            assertThrows(IllegalStateException.class, () -> periods.closeRange("DEFAULT", START, END, "CUSTOM", "tester", null, review.id()));
            assertEquals(before, rows(target));
            assertEquals(review.payload(), PeriodCloseTestFixture.evidence(jpa, target, "DEFAULT").history(START, END).get(0).payload());
        }
    }

    @Test
    void wholeDatabaseRecoveryPreservesEveryPersistedRowGuardAndNativeRetryIncludingDeferredTransferLinks() throws Exception
    {
        Path source = directory.resolve("backup-source");
        Path restored = directory.resolve("backup-restored");
        var request = UUID.randomUUID();
        FundTransferTestFixture fixture;
        FundTransferView transfer;
        PeriodCloseRangeView closed;
        Map<String, List<String>> expected;
        try (Jpa jpa = new Jpa(source))
        {
            fixture = FundTransferTestFixture.seed(jpa);
            transfer = new FundTransferService(jpa).save(fixture.command(request, "75"));
            new FundTransferService(jpa).reverse(transfer.id(), FundTransferTestFixture.DATE.plusDays(5), "Retained reversal");
            transfer = new FundTransferService(jpa).save(fixture.command(request, "75"));
            company(jpa, "REVIEW");
            var review = PeriodCloseTestFixture.readyReview(jpa, source, "REVIEW", START, END);
            closed = new PeriodCloseRangeService(jpa).closeRange("REVIEW", START, END, "CUSTOM", "tester", "Reviewed", review.id());
            expected = rows(source);
            var service = new DatabaseTransferService(() -> source, ignored -> fail("Recovery must not switch the active database"));
            var backup = service.backUpDatabase(directory.resolve("complete.zip"));
            assertTrue(service.restoreDatabaseCopy(backup.backupFile(), restored).validated());
            assertEquals(expected, rows(source), "Backup/restore must not mutate source state");
        }
        try (Jpa jpa = new Jpa(restored))
        {
            assertEquals(expected, rows(restored), "Compare all columns and rows, including identities, audit payloads and guard metadata");
            assertEquals(2, new FundTransferService(jpa).list().size());
            assertEquals(transfer, new FundTransferService(jpa).save(fixture.command(request, "75")));
            assertEquals(closed, new PeriodCloseRangeService(jpa).closeRange("REVIEW", START, END, "CUSTOM", "tester", "Reviewed", closed.id()));
            assertEquals(expected, rows(restored), "Native exact retries retain original facts without new writes");
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                assertThrows(ClosedPeriodRangeException.class, () -> PeriodCloseRangeService.requireOpen(em, "REVIEW", START, "Recovered write"));
                em.getTransaction().rollback();
            }
        }
    }

    private static void company(Jpa jpa, String code)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            var company = new Company(); company.setCode(code); company.setDisplayName("Synthetic " + code);
            em.persist(company); em.getTransaction().commit();
        }
    }

    private static String closeProof(Jpa jpa, String company)
    {
        try (var em = jpa.em())
        {
            return em.createQuery("select e.afterValue from AuditEvent e where e.company.code=:code and e.actionType='PERIOD_RANGE_CLOSED'", String.class)
                    .setParameter("code", company).getSingleResult();
        }
    }

}
