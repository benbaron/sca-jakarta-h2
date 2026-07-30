package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.service.PeriodCloseEventView;
import org.nonprofitbookkeeping.service.PeriodCloseRangeView;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Maps authoritative selected-company close ranges and factual events into SCLX. */
final class SclxPeriodCloseSnapshotAssembler
{
    Map<String, Object> assemble(String companyCode, List<PeriodCloseRangeView> ranges,
            List<PeriodCloseEventView> events)
    {
        List<Map<String, Object>> exportedRanges = ranges.stream()
                .peek(range -> requireRangeOwnership(range, companyCode))
                .sorted(Comparator.comparing(PeriodCloseRangeView::startDate)
                        .thenComparing(PeriodCloseRangeView::endDate)
                        .thenComparing(range -> range.id().toString()))
                .map(range -> SclxPeriodCloseExtension.rangeEntry(
                        SclxPortableIdentity.periodCloseRange(companyCode, range.id().toString()),
                        range.startDate(), range.endDate(), range.rangeKind(), range.status(),
                        range.closedAt(), range.closedBy(), range.closeReason(), range.reopenedAt(),
                        range.reopenedBy(), range.reopenReason()))
                .toList();
        Set<String> rangeIds = exportedRanges.stream()
                .map(entry -> String.valueOf(entry.get("rangeId")))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<Map<String, Object>> exportedEvents = events.stream()
                .peek(event -> requireEventOwnership(event, companyCode))
                .sorted(Comparator.comparing(PeriodCloseEventView::eventAt)
                        .thenComparing(event -> event.id().toString()))
                .map(event ->
                {
                    String rangeId = SclxPortableIdentity.periodCloseRange(
                            companyCode, event.closeRangeId().toString());
                    if (!rangeIds.contains(rangeId))
                    {
                        throw new IllegalArgumentException(
                                "period-close event references a range outside the exported snapshot");
                    }
                    return SclxPeriodCloseExtension.eventEntry(
                            SclxPortableIdentity.periodCloseEvent(companyCode, event.id().toString()),
                            rangeId, event.eventType(), event.actor(), event.reason(), event.eventAt());
                })
                .toList();
        return SclxPeriodCloseExtension.value(exportedRanges, exportedEvents);
    }

    private static void requireRangeOwnership(PeriodCloseRangeView range, String companyCode)
    {
        Objects.requireNonNull(range, "period-close range");
        if (!companyCode.equals(range.companyCode()))
        {
            throw new IllegalArgumentException("period-close range is outside the selected company");
        }
    }

    private static void requireEventOwnership(PeriodCloseEventView event, String companyCode)
    {
        Objects.requireNonNull(event, "period-close event");
        if (!companyCode.equals(event.companyCode()))
        {
            throw new IllegalArgumentException("period-close event is outside the selected company");
        }
    }
}
