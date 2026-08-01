package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Strict, non-mutating projection of governed selected-company factual audit history. */
final class SclxAuditHistoryImportData
{
    private static final int MAX_LOB_TEXT = 1_048_576;
    private static final Instant MIN_INSTANT = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant MAX_INSTANT = Instant.parse("9999-12-31T23:59:59.999999999Z");

    private final List<EventValue> events;

    private SclxAuditHistoryImportData(List<EventValue> events)
    {
        this.events = List.copyOf(events);
    }

    static SclxAuditHistoryImportData parse(JsonNode root)
    {
        JsonNode value = root.path("extensions").path("scaJakartaH2").path("auditHistory");
        if (value.isMissingNode() || value.isNull())
        {
            return new SclxAuditHistoryImportData(List.of());
        }
        String rootPath = "$.extensions.scaJakartaH2.auditHistory";
        requireObject(value, rootPath);
        requireFields(value, Set.of("version", "events"), Set.of("version", "events"), rootPath);
        if (integer(value, "version", rootPath) != 1)
        {
            throw new IllegalStateException(rootPath + ".version must be 1.");
        }

        List<EventValue> events = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        JsonNode eventNodes = requiredArray(value, "events", rootPath);
        for (int index = 0; index < eventNodes.size(); index++)
        {
            JsonNode event = eventNodes.get(index);
            String path = rootPath + ".events[" + index + "]";
            requireObject(event, path);
            requireFields(event,
                    Set.of("auditEventId", "occurredAt", "actor", "actionType", "entityType",
                            "entityId", "summary", "beforeValue", "afterValue", "reason"),
                    Set.of("auditEventId", "occurredAt", "actor", "actionType", "entityType", "summary"),
                    path);
            String externalId = boundedText(event, "auditEventId", path, 160);
            if (!identities.add(externalId))
            {
                throw new IllegalStateException(
                        "SCLX contains duplicate audit-event identity " + externalId + ".");
            }
            events.add(new EventValue(
                    externalId,
                    boundedInstant(event, "occurredAt", path),
                    boundedText(event, "actor", path, 200),
                    boundedText(event, "actionType", path, 80),
                    boundedText(event, "entityType", path, 120),
                    optionalBoundedText(event, "entityId", path, 120),
                    boundedText(event, "summary", path, 500),
                    optionalBoundedText(event, "beforeValue", path, MAX_LOB_TEXT),
                    optionalBoundedText(event, "afterValue", path, MAX_LOB_TEXT),
                    optionalBoundedText(event, "reason", path, 1000)));
        }
        events.sort(Comparator.comparing(EventValue::occurredAt).thenComparing(EventValue::externalId));
        return new SclxAuditHistoryImportData(events);
    }

    List<EventValue> events()
    {
        return events;
    }

    private static void requireObject(JsonNode value, String path)
    {
        if (value == null || !value.isObject())
        {
            throw new IllegalStateException(path + " must be an object.");
        }
    }

    private static void requireFields(JsonNode value, Set<String> allowed, Set<String> required, String path)
    {
        Set<String> present = new HashSet<>();
        Iterator<String> names = value.fieldNames();
        while (names.hasNext())
        {
            String name = names.next();
            if (!allowed.contains(name))
            {
                throw new IllegalStateException(path + " has unsupported field " + name + ".");
            }
            present.add(name);
        }
        if (!present.containsAll(required))
        {
            Set<String> missing = new HashSet<>(required);
            missing.removeAll(present);
            throw new IllegalStateException(path + " is missing required fields " + missing + ".");
        }
    }

    private static JsonNode requiredArray(JsonNode value, String field, String path)
    {
        JsonNode node = value.get(field);
        if (node == null || !node.isArray())
        {
            throw new IllegalStateException(path + "." + field + " must be an array.");
        }
        return node;
    }

    private static int integer(JsonNode value, String field, String path)
    {
        JsonNode node = value.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt())
        {
            throw new IllegalStateException(path + "." + field + " must be an integer.");
        }
        return node.intValue();
    }

    private static String boundedText(JsonNode value, String field, String path, int limit)
    {
        String result = text(value, field, path);
        requireLength(result, field, path, limit);
        return result;
    }

    private static String optionalBoundedText(JsonNode value, String field, String path, int limit)
    {
        String result = optionalText(value, field, path);
        if (result != null)
        {
            requireLength(result, field, path, limit);
        }
        return result;
    }

    private static void requireLength(String value, String field, String path, int limit)
    {
        if (value.codePointCount(0, value.length()) > limit)
        {
            throw new IllegalStateException(path + "." + field + " exceeds " + limit + " characters.");
        }
    }

    private static String text(JsonNode value, String field, String path)
    {
        String result = optionalText(value, field, path);
        if (result == null)
        {
            throw new IllegalStateException(path + "." + field + " must be a nonblank string.");
        }
        return result;
    }

    private static String optionalText(JsonNode value, String field, String path)
    {
        JsonNode node = value.get(field);
        if (node == null || node.isNull())
        {
            return null;
        }
        if (!node.isTextual())
        {
            throw new IllegalStateException(path + "." + field + " must be text or null.");
        }
        String result = node.textValue().trim();
        return result.isEmpty() ? null : result;
    }

    private static Instant boundedInstant(JsonNode value, String field, String path)
    {
        try
        {
            Instant result = Instant.parse(text(value, field, path));
            if (result.isBefore(MIN_INSTANT) || result.isAfter(MAX_INSTANT))
            {
                throw new IllegalStateException(
                        path + "." + field + " must be between 1900-01-01 and 9999-12-31.");
            }
            return result;
        }
        catch (DateTimeParseException ex)
        {
            throw new IllegalStateException(path + "." + field + " must use ISO instant format.", ex);
        }
    }

    record EventValue(
            String externalId,
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
