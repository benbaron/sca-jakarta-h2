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

    public static ReadOnlyObjectProperty<LocalDate> activeDateProperty()
    {
        return activeDate;
    }
}
