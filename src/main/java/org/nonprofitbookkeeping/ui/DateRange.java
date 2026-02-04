package org.nonprofitbookkeeping.ui;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Inclusive date range. Null start/end means "unbounded" on that side.
 */
public final class DateRange
{
    private final LocalDate startInclusive;
    private final LocalDate endInclusive;

    public static final DateRange ALL = new DateRange(null, null);

    public DateRange(LocalDate startInclusive, LocalDate endInclusive)
    {
        this.startInclusive = startInclusive;
        this.endInclusive = endInclusive;
    }

    public LocalDate startInclusive() { return startInclusive; }
    public LocalDate endInclusive() { return endInclusive; }

    public boolean isAll() { return startInclusive == null && endInclusive == null; }

    @Override
    public String toString()
    {
        if (isAll()) return "All Dates";
        String s = startInclusive == null ? "…" : startInclusive.toString();
        String e = endInclusive == null ? "…" : endInclusive.toString();
        return s + " to " + e;
    }

    @Override public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof DateRange other)) return false;
        return Objects.equals(startInclusive, other.startInclusive) &&
               Objects.equals(endInclusive, other.endInclusive);
    }

    @Override public int hashCode()
    {
        return Objects.hash(startInclusive, endInclusive);
    }
}
