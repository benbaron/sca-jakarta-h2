package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PeriodCloseCoverageTest
{
    @Test
    void inclusiveLeapDayAdjacencyAndClippingCoverTheWholeWindow()
    {
        LocalDate start = LocalDate.of(2024, 2, 28), end = LocalDate.of(2024, 3, 1);
        var result = PeriodCloseCoverage.of(start, end, List.of(
                range(start.plusDays(1), end.plusDays(20), "CLOSED"),
                range(start.minusDays(10), start, "CLOSED")));
        assertEquals(PeriodCloseCoverage.Status.CLOSED, result.status());
        assertEquals(2, result.ranges().size());
        assertThrows(UnsupportedOperationException.class, () -> result.ranges().clear());
    }

    @Test
    void oneDayGapAndReopenedHistoryRemainPartialWhileUnrelatedDatesStayOpen()
    {
        LocalDate start = LocalDate.of(2024, 2, 28), end = LocalDate.of(2024, 3, 1);
        var rows = List.of(range(start, start, "CLOSED"), range(end, end, "CLOSED"),
                range(start.plusDays(1), end.minusDays(1), "REOPENED"));
        assertEquals(PeriodCloseCoverage.Status.PARTIALLY_CLOSED, PeriodCloseCoverage.of(start, end, rows).status());
        assertEquals(PeriodCloseCoverage.Status.OPEN, PeriodCloseCoverage.of(end.plusDays(1), end.plusDays(1), rows).status());
    }

    @Test
    void maximumDateDoesNotOverflowAndReversedWindowRejects()
    {
        assertEquals(PeriodCloseCoverage.Status.CLOSED, PeriodCloseCoverage.of(LocalDate.MAX, LocalDate.MAX,
                List.of(range(LocalDate.MAX, LocalDate.MAX, "CLOSED"))).status());
        assertThrows(IllegalArgumentException.class, () -> PeriodCloseCoverage.of(LocalDate.MAX, LocalDate.MIN, List.of()));
    }

    private static PeriodCloseRangeView range(LocalDate start, LocalDate end, String status)
    {
        return new PeriodCloseRangeView(UUID.randomUUID(), "SCA", start, end, "CUSTOM", status,
                null, "tester", null, null, null, null);
    }
}
