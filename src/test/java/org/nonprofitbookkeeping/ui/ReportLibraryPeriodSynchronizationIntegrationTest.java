package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.FiscalPeriodRange;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportLibraryPeriodSynchronizationIntegrationTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    void defaultDatesFollowShellPeriodUntilOperatorEditsThem()
    {
        FxTestSupport.onFx(() -> {
            LocalDate originalActiveDate = ActivePeriodContext.get();
            DateRange originalRange = DateRangeContext.get();
            try
            {
                DateRangeContext.set(DateRange.ALL);
                LocalDate initialPeriod = LocalDate.of(2026, 2, 1);
                ActivePeriodContext.set(initialPeriod);

                ReportLibraryPanel panel = new ReportLibraryPanel();
                FiscalPeriodRange initialRange = UiServiceRegistry.budgetPlan().fiscalRange(initialPeriod);
                assertTrue(panel.followsActivePeriodForTests());
                assertEquals(initialRange.fiscalYearStart(), panel.startDateForTests());
                assertEquals(initialRange.periodEnd(), panel.endDateForTests());

                LocalDate laterPeriod = LocalDate.of(2026, 6, 1);
                FiscalPeriodRange laterRange = UiServiceRegistry.budgetPlan().fiscalRange(laterPeriod);
                ActivePeriodContext.set(laterPeriod);
                assertEquals(laterRange.fiscalYearStart(), panel.startDateForTests());
                assertEquals(laterRange.periodEnd(), panel.endDateForTests());

                LocalDate customStart = LocalDate.of(2026, 3, 15);
                LocalDate customEnd = LocalDate.of(2026, 5, 20);
                panel.setReportDatesForTests(customStart, customEnd);
                assertFalse(panel.followsActivePeriodForTests());

                ActivePeriodContext.set(LocalDate.of(2026, 8, 1));
                assertEquals(customStart, panel.startDateForTests());
                assertEquals(customEnd, panel.endDateForTests());
            }
            finally
            {
                DateRangeContext.set(originalRange);
                ActivePeriodContext.set(originalActiveDate);
            }
            return null;
        });
    }

    @Test
    void explicitDateRangeStartsDetachedFromShellPeriod()
    {
        FxTestSupport.onFx(() -> {
            LocalDate originalActiveDate = ActivePeriodContext.get();
            DateRange originalRange = DateRangeContext.get();
            try
            {
                LocalDate customStart = LocalDate.of(2025, 11, 10);
                LocalDate customEnd = LocalDate.of(2026, 1, 20);
                DateRangeContext.set(new DateRange(customStart, customEnd));
                ActivePeriodContext.set(LocalDate.of(2026, 2, 1));

                ReportLibraryPanel panel = new ReportLibraryPanel();
                assertFalse(panel.followsActivePeriodForTests());
                assertEquals(customStart, panel.startDateForTests());
                assertEquals(customEnd, panel.endDateForTests());

                ActivePeriodContext.set(LocalDate.of(2026, 9, 1));
                assertEquals(customStart, panel.startDateForTests());
                assertEquals(customEnd, panel.endDateForTests());
            }
            finally
            {
                DateRangeContext.set(originalRange);
                ActivePeriodContext.set(originalActiveDate);
            }
            return null;
        });
    }
}
