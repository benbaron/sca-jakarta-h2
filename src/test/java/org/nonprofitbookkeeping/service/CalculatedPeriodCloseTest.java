package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CalculatedPeriodCloseTest
{
    @TempDir Path directory;

    @Test
    void calculatedCloseUsesCompanyFiscalEndAndRejectsUncappedDatesWithoutHistory() throws Exception
    {
        Path db = directory.resolve("calculated");
        try (Jpa jpa = new Jpa(db))
        {
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                var company = new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT");
                company.setFiscalYearStartMonth(7);
                company.setFiscalYearStartDay(1);
                em.getTransaction().commit();
            }
            LocalDate start = LocalDate.of(2027, 6, 15);
            LocalDate end = LocalDate.of(2027, 6, 30);
            LocalDate uncapped = LocalDate.of(2027, 7, 14);
            var service = new PeriodCloseRangeService(jpa);
            // Both fiscal years need explicit budget evidence for a review spanning July 1.
            PeriodCloseTestFixture.readyReview(jpa, db, "DEFAULT", start, end);
            var wrong = PeriodCloseTestFixture.readyReview(jpa, db, "DEFAULT", start, uncapped);
            var failure = assertThrows(IllegalArgumentException.class, () ->
                    service.closeRange("DEFAULT", start, uncapped, "CALCULATED", "tester", null, wrong.id()));
            assertTrue(failure.getMessage().contains("2027-06-30"));
            assertTrue(service.listRanges("DEFAULT").isEmpty());
            assertTrue(service.listEvents("DEFAULT").isEmpty());

            // Exact-date review is required even when a matching calculated end is known.
            assertThrows(IllegalArgumentException.class, () ->
                    service.closeRange("DEFAULT", start, end, "CALCULATED", "tester", null, wrong.id()));
            var review = PeriodCloseTestFixture.readyReview(jpa, db, "DEFAULT", start, end);
            var closed = service.closeRange("DEFAULT", start, end, "CALCULATED", "tester", null, review.id());
            assertEquals(end, closed.endDate());
            assertTrue(service.findClosedRange("DEFAULT", end).isPresent());
            assertTrue(service.findClosedRange("DEFAULT", end.plusDays(1)).isEmpty());

            // Historical acknowledgements still work after company fiscal configuration changes.
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT").setFiscalYearStartMonth(1);
                em.getTransaction().commit();
            }
            assertEquals(closed, service.closeRange("DEFAULT", start, end, "CALCULATED", "tester", null, review.id()));
            assertEquals(1, service.listEvents("DEFAULT").size());
            service.reopenRange(closed.id(), "tester", null, ClosedPeriodPolicy.WARN_AND_REOPEN, false);
            assertTrue(service.findClosedRange("DEFAULT", end).isEmpty());
        }
    }

    @Test
    void customAndImportedHistoricalRangesKeepExplicitDates() throws Exception
    {
        Path db = directory.resolve("custom-history");
        try (Jpa jpa = new Jpa(db))
        {
            LocalDate start = LocalDate.of(2026, 6, 15);
            LocalDate end = start.plusDays(3);
            var service = new PeriodCloseRangeService(jpa);
            var review = PeriodCloseTestFixture.readyReview(jpa, db, "DEFAULT", start, end);
            assertEquals(end, service.closeRange("DEFAULT", start, end, "CUSTOM", "tester", null, review.id()).endDate());
            LocalDate historicalStart = LocalDate.of(2025, 2, 14);
            var historical = PeriodCloseTestFixture.historical(jpa, "DEFAULT", historicalStart,
                    historicalStart, "CALCULATED", "historical actor", "Original fact");
            assertEquals(historicalStart, service.loadRange(historical.id()).endDate());
            assertTrue(service.findClosedRange("DEFAULT", historicalStart).isPresent());
        }
        try (Jpa restarted = new Jpa(db))
        {
            assertEquals(2, new PeriodCloseRangeService(restarted).listRanges("DEFAULT").size());
        }
    }
}
