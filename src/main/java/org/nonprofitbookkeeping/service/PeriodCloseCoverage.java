package org.nonprofitbookkeeping.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Read-only union of authoritative closed ranges for an inclusive date window. */
public record PeriodCloseCoverage(Status status, List<PeriodCloseRangeView> ranges)
{
    public enum Status { OPEN, PARTIALLY_CLOSED, CLOSED }

    public PeriodCloseCoverage
    {
        Objects.requireNonNull(status);
        ranges = List.copyOf(ranges);
    }

    public static PeriodCloseCoverage of(LocalDate start, LocalDate end, List<PeriodCloseRangeView> companyRanges)
    {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (end.isBefore(start))
        {
            throw new IllegalArgumentException("Coverage end must be on or after start.");
        }
        List<PeriodCloseRangeView> overlapping = companyRanges.stream()
                .filter(PeriodCloseRangeView::closed)
                .filter(range -> !range.startDate().isAfter(end) && !range.endDate().isBefore(start))
                .sorted(Comparator.comparing(PeriodCloseRangeView::startDate)
                        .thenComparing(PeriodCloseRangeView::endDate))
                .toList();
        LocalDate uncovered = start;
        for (PeriodCloseRangeView range : overlapping)
        {
            if (range.startDate().isAfter(uncovered))
            {
                return new PeriodCloseCoverage(Status.PARTIALLY_CLOSED, overlapping);
            }
            if (!range.endDate().isBefore(end))
            {
                return new PeriodCloseCoverage(Status.CLOSED, overlapping);
            }
            if (!range.endDate().isBefore(uncovered))
            {
                uncovered = range.endDate().plusDays(1);
            }
        }
        return new PeriodCloseCoverage(overlapping.isEmpty() ? Status.OPEN : Status.PARTIALLY_CLOSED, overlapping);
    }

    public String label()
    {
        return switch (status)
        {
            case OPEN -> "Open";
            case PARTIALLY_CLOSED -> "Partially closed";
            case CLOSED -> "Closed";
        };
    }
}
