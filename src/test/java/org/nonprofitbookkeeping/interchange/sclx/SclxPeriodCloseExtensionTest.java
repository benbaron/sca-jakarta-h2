package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.PeriodCloseEventView;
import org.nonprofitbookkeeping.service.PeriodCloseRangeView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SclxPeriodCloseExtensionTest
{
    @Test
    void mapsDeterministicRangesEventsAndCounts()
    {
        UUID rangeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID eventId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PeriodCloseRangeView range = new PeriodCloseRangeView(
                rangeId, "TEST", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                "CALCULATED", "CLOSED", Instant.parse("2026-02-01T00:00:00Z"), "treasurer",
                null, null, null, null);
        PeriodCloseEventView event = new PeriodCloseEventView(
                eventId, rangeId, "TEST", "CLOSED", "treasurer", null,
                Instant.parse("2026-02-01T00:00:00Z"));

        var value = new SclxPeriodCloseSnapshotAssembler().assemble("TEST", List.of(range), List.of(event));
        var extensions = new SclxExportDocument.Extensions(1,
                java.util.Map.of(SclxPeriodCloseExtension.KEY, value));
        var data = SclxPeriodCloseExtension.data(extensions);

        assertEquals(1, data.ranges().size());
        assertEquals(1, data.events().size());
        assertEquals(SclxPortableIdentity.periodCloseRange("TEST", rangeId.toString()),
                data.ranges().get(0).rangeId());
        assertEquals(data.ranges().get(0).rangeId(), data.events().get(0).rangeId());
    }

    @Test
    void rejectsCrossCompanyAndMissingRangeReferences()
    {
        UUID rangeId = UUID.randomUUID();
        PeriodCloseRangeView foreign = new PeriodCloseRangeView(
                rangeId, "OTHER", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                "CUSTOM", "CLOSED", Instant.EPOCH, "actor", null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> new SclxPeriodCloseSnapshotAssembler().assemble("TEST", List.of(foreign), List.of()));

        PeriodCloseEventView event = new PeriodCloseEventView(
                UUID.randomUUID(), rangeId, "TEST", "CLOSED", "actor", null, Instant.EPOCH);
        assertThrows(IllegalArgumentException.class,
                () -> new SclxPeriodCloseSnapshotAssembler().assemble("TEST", List.of(), List.of(event)));
    }
}
