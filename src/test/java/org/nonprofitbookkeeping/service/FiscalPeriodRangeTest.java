package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FiscalPeriodRangeTest
{
    @Test
    void derivesNonJanuaryFiscalYearFromSelectedAccountingPeriod()
    {
        FiscalPeriodRange range = FiscalPeriodRange.of(7, 1, LocalDate.of(2027, 2, 15));

        assertEquals(2026, range.fiscalYear());
        assertEquals(LocalDate.of(2026, 7, 1), range.fiscalYearStart());
        assertEquals(LocalDate.of(2027, 6, 30), range.fiscalYearEnd());
        assertEquals(LocalDate.of(2027, 2, 15), range.periodStart());
        assertEquals(LocalDate.of(2027, 3, 14), range.periodEnd());
    }

    @Test
    void capsSelectedAccountingPeriodAtFiscalYearEnd()
    {
        FiscalPeriodRange range = FiscalPeriodRange.of(7, 1, LocalDate.of(2027, 6, 15));

        assertEquals(LocalDate.of(2027, 6, 30), range.periodEnd());
    }

    @Test
    void handlesLeapMonthYearRolloverAndNonFirstDayFiscalBoundary()
    {
        FiscalPeriodRange leap = FiscalPeriodRange.of(7, 1, LocalDate.of(2028, 2, 1));
        assertEquals(LocalDate.of(2028, 2, 29), leap.periodEnd());
        FiscalPeriodRange rollover = FiscalPeriodRange.of(7, 1, LocalDate.of(2027, 12, 15));
        assertEquals(LocalDate.of(2028, 1, 14), rollover.periodEnd());
        FiscalPeriodRange boundary = FiscalPeriodRange.of(7, 10, LocalDate.of(2027, 6, 15));
        assertEquals(LocalDate.of(2027, 7, 9), boundary.periodEnd());
        assertEquals(LocalDate.of(2026, 7, 10), boundary.fiscalYearStart());
    }
}
