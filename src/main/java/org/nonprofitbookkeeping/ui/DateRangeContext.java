package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Tiny shared context for the currently-selected date range.
 *
 * In a fuller app this would live in a proper app state container (CDI/DI),
 * but this keeps the skeleton simple while still allowing panels to react.
 */
public final class DateRangeContext
{
    private static final ObjectProperty<DateRange> selected = new SimpleObjectProperty<>(DateRange.ALL);

    private DateRangeContext() {}

    public static ObjectProperty<DateRange> selectedProperty() { return selected; }
    public static DateRange get() { return selected.get(); }
    public static void set(DateRange r) { selected.set(r == null ? DateRange.ALL : r); }
}
