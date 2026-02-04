package org.nonprofitbookkeeping.ui;

import java.time.LocalDate;
import java.time.Month;

/**
 * Date range calculations.
 *
 * NOTE: Fiscal year start month is configurable via a constant for now.
 * TODO: expose fiscal year start month in Settings and persist it.
 */
public final class DateRangeUtil
{
    // Default assumption: fiscal year == calendar year.
    // If your org uses Oct 1–Sep 30, set this to Month.OCTOBER (later: Settings).
    public static final Month FISCAL_YEAR_START_MONTH = Month.JANUARY;

    private DateRangeUtil() {}

    public static DateRange compute(DateRangePreset preset, LocalDate today, LocalDate customStart, LocalDate customEnd)
    {
        if (preset == null) return DateRange.ALL;
        if (today == null) today = LocalDate.now();

        return switch (preset)
        {
            case ALL -> DateRange.ALL;
            case CURRENT_QUARTER -> quarterFor(today.getYear(), quarterOf(today));
            case Q1 -> quarterFor(today.getYear(), 1);
            case Q2 -> quarterFor(today.getYear(), 2);
            case Q3 -> quarterFor(today.getYear(), 3);
            case Q4 -> quarterFor(today.getYear(), 4);
            case CURRENT_YEAR -> new DateRange(LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31));
            case CURRENT_FISCAL_YEAR -> fiscalYearFor(today);
            case PAST_FISCAL_YEAR -> pastFiscalYearFor(today);
            case CUSTOM -> new DateRange(customStart, customEnd);
        };
    }

    private static int quarterOf(LocalDate d)
    {
        int m = d.getMonthValue();
        if (m <= 3) return 1;
        if (m <= 6) return 2;
        if (m <= 9) return 3;
        return 4;
    }

    private static DateRange quarterFor(int year, int q)
    {
        return switch (q)
        {
            case 1 -> new DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 3, 31));
            case 2 -> new DateRange(LocalDate.of(year, 4, 1), LocalDate.of(year, 6, 30));
            case 3 -> new DateRange(LocalDate.of(year, 7, 1), LocalDate.of(year, 9, 30));
            case 4 -> new DateRange(LocalDate.of(year, 10, 1), LocalDate.of(year, 12, 31));
            default -> DateRange.ALL;
        };
    }

    private static DateRange fiscalYearFor(LocalDate today)
    {
        int startMonth = FISCAL_YEAR_START_MONTH.getValue();
        int yearStart = (today.getMonthValue() >= startMonth) ? today.getYear() : today.getYear() - 1;
        LocalDate start = LocalDate.of(yearStart, startMonth, 1);
        LocalDate end = start.plusYears(1).minusDays(1);
        return new DateRange(start, end);
    }

    private static DateRange pastFiscalYearFor(LocalDate today)
    {
        DateRange current = fiscalYearFor(today);
        LocalDate start = current.startInclusive().minusYears(1);
        LocalDate end = current.endInclusive().minusYears(1);
        return new DateRange(start, end);
    }
}
