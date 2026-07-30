package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.PeriodCloseEventView;
import org.nonprofitbookkeeping.service.PeriodCloseRangeView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxPeriodCloseExportIntegrationTest
{
    @Test
    void validatesAndCountsAuthoritativeCloseFacts()
    {
        UUID rangeKey = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID closeEventKey = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reopenEventKey = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Instant closedAt = Instant.parse("2026-02-01T00:00:00Z");
        Instant reopenedAt = Instant.parse("2026-02-05T00:00:00Z");

        PeriodCloseRangeView range = new PeriodCloseRangeView(
                rangeKey, "TEST", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                "CALCULATED", "REOPENED", closedAt, "treasurer", "month end",
                reopenedAt, "deputy", "late correction");
        PeriodCloseEventView closed = new PeriodCloseEventView(
                closeEventKey, rangeKey, "TEST", "CLOSED", "treasurer", "month end", closedAt);
        PeriodCloseEventView reopened = new PeriodCloseEventView(
                reopenEventKey, rangeKey, "TEST", "REOPENED", "deputy", "late correction", reopenedAt);

        Map<String, Object> value = new SclxPeriodCloseSnapshotAssembler().assemble(
                "TEST", List.of(range), List.of(reopened, closed));
        SclxExportDocument document = document(value);

        new SclxExportDocumentValidator().validate(document);
        SclxExportCounts counts = SclxExportCounts.from(document, 0L, 0L);
        assertEquals(1L, counts.periodCloseRanges());
        assertEquals(2L, counts.periodCloseEvents());
        assertEquals(4L, counts.totalEntities());
        assertTrue(SclxExportSection.PERIOD_CLOSE.includedByCurrentSnapshot());
        assertFalse(SclxExportSection.PERIOD_CLOSE.deferred());
    }

    @Test
    void validatorRejectsInvalidStateAndMissingRangeReference()
    {
        String rangeId = SclxPortableIdentity.periodCloseRange(
                "TEST", "11111111-1111-1111-1111-111111111111");
        Map<String, Object> invalidClosedRange = SclxPeriodCloseExtension.rangeEntry(
                rangeId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                "CALCULATED", "CLOSED", Instant.EPOCH, "actor", null,
                Instant.EPOCH.plusSeconds(1), "actor", null);
        Map<String, Object> value = SclxPeriodCloseExtension.value(List.of(invalidClosedRange), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new SclxExportDocumentValidator().validate(document(value)));

        Map<String, Object> event = SclxPeriodCloseExtension.eventEntry(
                SclxPortableIdentity.periodCloseEvent("TEST", UUID.randomUUID().toString()),
                SclxPortableIdentity.periodCloseRange("TEST", UUID.randomUUID().toString()),
                "CLOSED", "actor", null, Instant.EPOCH);
        assertThrows(IllegalArgumentException.class,
                () -> new SclxExportDocumentValidator().validate(
                        document(SclxPeriodCloseExtension.value(List.of(), List.of(event)))));
    }

    private static SclxExportDocument document(Map<String, Object> periodClose)
    {
        return new SclxExportDocument(
                "sclx", "1.3", Instant.EPOCH,
                new SclxExportDocument.Organization(
                        SclxPortableIdentity.organization("TEST"), "TEST", "Test", "USD",
                        LocalDate.of(2026, 1, 1)),
                List.of(), List.of(), List.of(), List.of(),
                new SclxExportDocument.Extensions(1, Map.of(
                        SclxPeriodCloseExtension.KEY, periodClose)));
    }
}
