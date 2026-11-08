package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.nonprofitbookkeeping.service.FiscalPeriodRange;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Shared selected accounting-period start, independent of report browsing dates.
 */
public final class ActivePeriodContext
{
    private static final ObjectProperty<LocalDate> activeDate =
            new SimpleObjectProperty<>(LocalDate.now().withDayOfMonth(1));

    private ActivePeriodContext()
    {
    }

    public static LocalDate get()
    {
        return activeDate.get();
    }

    public static void set(LocalDate date)
    {
        activeDate.set(Objects.requireNonNull(date, "date is required"));
    }

    public static LocalDate periodStartFor(YearMonth period, int configuredStartDay)
    {
        return FiscalPeriodRange.startFor(period, configuredStartDay);
    }

    /** Applies loaded startup preferences before workspace consumers are created. */
    static void initialize(int configuredStartDay)
    {
        select(YearMonth.from(get()), configuredStartDay);
    }

    public static void select(YearMonth period, int configuredStartDay)
    {
        set(periodStartFor(period, configuredStartDay));
    }

    static java.util.List<YearMonth> periodChoicesFor(LocalDate anchorDate)
    {
        YearMonth anchor = YearMonth.from(Objects.requireNonNull(anchorDate, "anchorDate"));
        java.util.ArrayList<YearMonth> choices = new java.util.ArrayList<>();
        for (int offset = -12; offset <= 12; offset++)
        {
            choices.add(anchor.plusMonths(offset));
        }
        return java.util.List.copyOf(choices);
    }

    public static ReadOnlyObjectProperty<LocalDate> activeDateProperty()
    {
        return activeDate;
    }
}
