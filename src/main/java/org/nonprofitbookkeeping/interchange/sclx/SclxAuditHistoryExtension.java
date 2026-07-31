package org.nonprofitbookkeeping.interchange.sclx;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Governed selected-company factual audit history for SCLX 1.3. */
public final class SclxAuditHistoryExtension
{
    public static final String KEY = "auditHistory";

    private SclxAuditHistoryExtension()
    {
    }

    public static Map<String, Object> value(List<Map<String, Object>> events)
    {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("version", 1);
        value.put("events", List.copyOf(events));
        return Map.copyOf(value);
    }

    public static Map<String, Object> eventEntry(
            String auditEventId,
            Instant occurredAt,
            String actor,
            String actionType,
            String entityType,
            String entityId,
            String summary,
            String beforeValue,
            String afterValue,
            String reason)
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("auditEventId", auditEventId);
        entry.put("occurredAt", occurredAt);
        entry.put("actor", actor);
        entry.put("actionType", actionType);
        entry.put("entityType", entityType);
        putOptional(entry, "entityId", entityId);
        entry.put("summary", summary);
        putOptional(entry, "beforeValue", beforeValue);
        putOptional(entry, "afterValue", afterValue);
        putOptional(entry, "reason", reason);
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
            return new Data(List.of());
        }
        if (!(raw instanceof Map<?, ?> root))
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.auditHistory must be an object");
        }
        if (!root.keySet().equals(Set.of("version", "events")))
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.auditHistory has unsupported fields");
        }
        if (SclxExtensionValueReader.integer(root, "version", "extensions.scaJakartaH2.auditHistory") != 1)
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.auditHistory.version must be 1");
        }

        List<EventEntry> events = new ArrayList<>();
        List<Map<?, ?>> eventObjects = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "events", "extensions.scaJakartaH2.auditHistory"),
                "extensions.scaJakartaH2.auditHistory.events",
                Set.of("auditEventId", "occurredAt", "actor", "actionType", "entityType", "entityId",
                        "summary", "beforeValue", "afterValue", "reason"));
        for (int index = 0; index < eventObjects.size(); index++)
        {
            Map<?, ?> value = eventObjects.get(index);
            String path = "extensions.scaJakartaH2.auditHistory.events[" + index + ']';
            events.add(new EventEntry(
                    SclxExtensionValueReader.text(value, "auditEventId", path),
                    SclxExtensionValueReader.instant(value, "occurredAt", path, false),
                    SclxExtensionValueReader.text(value, "actor", path),
                    SclxExtensionValueReader.text(value, "actionType", path),
                    SclxExtensionValueReader.text(value, "entityType", path),
                    SclxExtensionValueReader.optionalText(value, "entityId", path),
                    SclxExtensionValueReader.text(value, "summary", path),
                    SclxExtensionValueReader.optionalText(value, "beforeValue", path),
                    SclxExtensionValueReader.optionalText(value, "afterValue", path),
                    SclxExtensionValueReader.optionalText(value, "reason", path)));
        }
        return new Data(events);
    }

    public static void requireUniqueIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        for (EventEntry event : data.events())
        {
            if (!ids.add(event.auditEventId()))
            {
                throw new IllegalArgumentException("duplicate audit-event identity: " + event.auditEventId());
            }
        }
    }

    public record Data(List<EventEntry> events)
    {
        public Data
        {
            events = List.copyOf(Objects.requireNonNull(events, "events"));
        }
    }

    public record EventEntry(
            String auditEventId,
            Instant occurredAt,
            String actor,
            String actionType,
            String entityType,
            String entityId,
            String summary,
            String beforeValue,
            String afterValue,
            String reason)
    {
    }
}
