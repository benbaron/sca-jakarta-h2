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
}
