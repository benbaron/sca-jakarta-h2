package org.nonprofitbookkeeping.interchange.sclx;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Governed authoritative period-close ranges and factual close/reopen history for SCLX 1.3. */
public final class SclxPeriodCloseExtension
{
    public static final String KEY = "periodClose";

    private SclxPeriodCloseExtension()
    {
    }

    public static Map<String, Object> value(List<Map<String, Object>> ranges, List<Map<String, Object>> events)
    {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("version", 1);
        value.put("ranges", List.copyOf(ranges));
        value.put("events", List.copyOf(events));
        return Map.copyOf(value);
    }

    public static Map<String, Object> rangeEntry(String rangeId, LocalDate startDate, LocalDate endDate,
            String rangeKind, String status, Instant closedAt, String closedBy, String closeReason,
            Instant reopenedAt, String reopenedBy, String reopenReason)
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("rangeId", rangeId);
        entry.put("startDate", startDate);
        entry.put("endDate", endDate);
        entry.put("rangeKind", rangeKind);
        entry.put("status", status);
        entry.put("closedAt", closedAt);
        entry.put("closedBy", closedBy);
        putOptional(entry, "closeReason", closeReason);
        putOptional(entry, "reopenedAt", reopenedAt);
        putOptional(entry, "reopenedBy", reopenedBy);
        putOptional(entry, "reopenReason", reopenReason);
        return Map.copyOf(entry);
    }

    public static Map<String, Object> eventEntry(String eventId, String rangeId, String eventType,
            String actor, String reason, Instant eventAt)
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("eventId", eventId);
        entry.put("rangeId", rangeId);
        entry.put("eventType", eventType);
        entry.put("actor", actor);
        putOptional(entry, "reason", reason);
        entry.put("eventAt", eventAt);
        return Map.copyOf(entry);
    }

    private static void putOptional(Map<String, Object> entry, String key, Object value)
    {
        if (value != null)
        {
            entry.put(key, value);
        }
    }

    public static Data data(SclxExportDocument.Extensions extensions)
    {
        Object raw = extensions.scaJakartaH2().get(KEY);
        if (raw == null)
        {
            return new Data(List.of(), List.of());
        }
        if (!(raw instanceof Map<?, ?> root))
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.periodClose must be an object");
        }
        if (!root.keySet().equals(Set.of("version", "ranges", "events")))
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.periodClose has unsupported fields");
        }
        if (SclxExtensionValueReader.integer(root, "version", "extensions.scaJakartaH2.periodClose") != 1)
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.periodClose.version must be 1");
        }

        List<RangeEntry> ranges = new ArrayList<>();
        List<Map<?, ?>> rangeObjects = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "ranges", "extensions.scaJakartaH2.periodClose"),
                "extensions.scaJakartaH2.periodClose.ranges",
                Set.of("rangeId", "startDate", "endDate", "rangeKind", "status", "closedAt", "closedBy",
                        "closeReason", "reopenedAt", "reopenedBy", "reopenReason"));
        for (int index = 0; index < rangeObjects.size(); index++)
        {
            Map<?, ?> value = rangeObjects.get(index);
            String path = "extensions.scaJakartaH2.periodClose.ranges[" + index + ']';
            ranges.add(new RangeEntry(
                    SclxExtensionValueReader.text(value, "rangeId", path),
                    SclxExtensionValueReader.date(value, "startDate", path, false),
                    SclxExtensionValueReader.date(value, "endDate", path, false),
                    SclxExtensionValueReader.text(value, "rangeKind", path),
                    SclxExtensionValueReader.text(value, "status", path),
                    SclxExtensionValueReader.instant(value, "closedAt", path, false),
                    SclxExtensionValueReader.text(value, "closedBy", path),
                    SclxExtensionValueReader.optionalText(value, "closeReason", path),
                    SclxExtensionValueReader.instant(value, "reopenedAt", path, true),
                    SclxExtensionValueReader.optionalText(value, "reopenedBy", path),
                    SclxExtensionValueReader.optionalText(value, "reopenReason", path)));
        }

        List<EventEntry> events = new ArrayList<>();
        List<Map<?, ?>> eventObjects = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "events", "extensions.scaJakartaH2.periodClose"),
                "extensions.scaJakartaH2.periodClose.events",
                Set.of("eventId", "rangeId", "eventType", "actor", "reason", "eventAt"));
        for (int index = 0; index < eventObjects.size(); index++)
        {
            Map<?, ?> value = eventObjects.get(index);
            String path = "extensions.scaJakartaH2.periodClose.events[" + index + ']';
            events.add(new EventEntry(
                    SclxExtensionValueReader.text(value, "eventId", path),
                    SclxExtensionValueReader.text(value, "rangeId", path),
                    SclxExtensionValueReader.text(value, "eventType", path),
                    SclxExtensionValueReader.text(value, "actor", path),
                    SclxExtensionValueReader.optionalText(value, "reason", path),
                    SclxExtensionValueReader.instant(value, "eventAt", path, false)));
        }
        return new Data(ranges, events);
    }

    public static Set<String> uniqueRangeIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        for (RangeEntry range : data.ranges())
        {
            if (!ids.add(range.rangeId()))
            {
                throw new IllegalArgumentException("duplicate period-close range identity: " + range.rangeId());
            }
        }
        return Set.copyOf(ids);
    }

    public static void requireUniqueEventIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        for (EventEntry event : data.events())
        {
            if (!ids.add(event.eventId()))
            {
                throw new IllegalArgumentException("duplicate period-close event identity: " + event.eventId());
            }
        }
    }

    public record Data(List<RangeEntry> ranges, List<EventEntry> events)
    {
        public Data
        {
            ranges = List.copyOf(Objects.requireNonNull(ranges, "ranges"));
            events = List.copyOf(Objects.requireNonNull(events, "events"));
        }
    }

    public record RangeEntry(String rangeId, LocalDate startDate, LocalDate endDate, String rangeKind,
            String status, Instant closedAt, String closedBy, String closeReason, Instant reopenedAt,
            String reopenedBy, String reopenReason)
    {
    }

    public record EventEntry(String eventId, String rangeId, String eventType, String actor, String reason,
            Instant eventAt)
    {
    }
}
