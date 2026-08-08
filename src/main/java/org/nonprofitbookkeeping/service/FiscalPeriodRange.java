package org.nonprofitbookkeeping.service;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Objects;

/**
 * Immutable fiscal-year and selected-accounting-period range.
 *
 * <p>The fiscal-year label is the calendar year in which the fiscal year starts.
 * The selected accounting period is represented by its explicit start date and
 * the day before the next monthly period starts, capped at the fiscal-year end.</p>
 */
public record FiscalPeriodRange(
        int fiscalYear,
        LocalDate fiscalYearStart,
        LocalDate fiscalYearEnd,
        LocalDate periodStart,
        LocalDate periodEnd)
{
    public FiscalPeriodRange
    {
        Objects.requireNonNull(fiscalYearStart, "fiscalYearStart");
        Objects.requireNonNull(fiscalYearEnd, "fiscalYearEnd");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        if (fiscalYear != fiscalYearStart.getYear())
        {
            throw new IllegalArgumentException("fiscalYear must equal the fiscal-year start year");
        }
        if (fiscalYearEnd.isBefore(fiscalYearStart))
        {
            throw new IllegalArgumentException("fiscalYearEnd must be on or after fiscalYearStart");
        }
        if (periodStart.isBefore(fiscalYearStart) || periodStart.isAfter(fiscalYearEnd))
        {
            throw new IllegalArgumentException("periodStart must be inside the fiscal year");
        }
        if (periodEnd.isBefore(periodStart) || periodEnd.isAfter(fiscalYearEnd))
        {
            throw new IllegalArgumentException("periodEnd must be inside the fiscal year and on or after periodStart");
        }
    }

    public static FiscalPeriodRange forCompany(CompanyView company, LocalDate selectedPeriodStart)
    {
        Objects.requireNonNull(company, "company");
        return of(company.fiscalYearStartMonth(), company.fiscalYearStartDay(), selectedPeriodStart);
    }

    public static FiscalPeriodRange of(int fiscalStartMonth, int fiscalStartDay, LocalDate selectedPeriodStart)
    {
        Objects.requireNonNull(selectedPeriodStart, "selectedPeriodStart");
        MonthDay fiscalStart = MonthDay.of(fiscalStartMonth, fiscalStartDay);
        LocalDate candidate = fiscalStart.atYear(selectedPeriodStart.getYear());
        if (candidate.isAfter(selectedPeriodStart))
        {
            candidate = fiscalStart.atYear(selectedPeriodStart.getYear() - 1);
        }
        LocalDate fiscalEnd = fiscalStart.atYear(candidate.getYear() + 1).minusDays(1);
        LocalDate uncappedPeriodEnd = selectedPeriodStart.plusMonths(1).minusDays(1);
        LocalDate periodEnd = uncappedPeriodEnd.isAfter(fiscalEnd) ? fiscalEnd : uncappedPeriodEnd;
        return new FiscalPeriodRange(candidate.getYear(), candidate, fiscalEnd, selectedPeriodStart, periodEnd);
    }

    public String displayLabel()
    {
        return "FY " + fiscalYear + " (" + fiscalYearStart + " to " + fiscalYearEnd + ")";
    }
}
