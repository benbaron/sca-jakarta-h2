package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Shared application context for the explicitly selected accounting as-of date.
 */
public final class ActivePeriodContext
{
    private static final ObjectProperty<LocalDate> activeDate =
            new SimpleObjectProperty<>(LocalDate.now());

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
        Objects.requireNonNull(period, "period is required");
        if (configuredStartDay < 1 || configuredStartDay > 28)
        {
            throw new IllegalArgumentException("configuredStartDay must be between 1 and 28");
        }
        return period.atDay(configuredStartDay);
    }

    public static ReadOnlyObjectProperty<LocalDate> activeDateProperty()
    {
        return activeDate;
    }
}
