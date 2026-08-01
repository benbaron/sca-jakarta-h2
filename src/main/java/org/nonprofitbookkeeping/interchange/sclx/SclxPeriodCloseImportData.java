package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict, non-mutating projection of the governed SCLX period-close extension. */
final class SclxPeriodCloseImportData
{
    private final List<RangeValue> ranges;
    private final List<EventValue> events;

    private SclxPeriodCloseImportData(List<RangeValue> ranges, List<EventValue> events)
    {
        this.ranges = List.copyOf(ranges);
        this.events = List.copyOf(events);
    }

    static SclxPeriodCloseImportData parse(JsonNode root)
    {
        JsonNode value = root.path("extensions").path("scaJakartaH2").path("periodClose");
        if (value.isMissingNode() || value.isNull())
        {
            return new SclxPeriodCloseImportData(List.of(), List.of());
        }
        String rootPath = "$.extensions.scaJakartaH2.periodClose";
        requireObject(value, rootPath);
        requireFields(value, Set.of("version", "ranges", "events"),
                Set.of("version", "ranges", "events"), rootPath);
        if (integer(value, "version", rootPath) != 1)
        {
            throw new IllegalStateException(rootPath + ".version must be 1.");
        }

        List<RangeValue> ranges = new ArrayList<>();
        Set<String> rangeIds = new HashSet<>();
        JsonNode rangeNodes = requiredArray(value, "ranges", rootPath);
        for (int index = 0; index < rangeNodes.size(); index++)
        {
            JsonNode range = rangeNodes.get(index);
            String path = rootPath + ".ranges[" + index + "]";
            requireObject(range, path);
            requireFields(range,
                    Set.of("rangeId", "startDate", "endDate", "rangeKind", "status",
                            "closedAt", "closedBy", "closeReason", "reopenedAt", "reopenedBy",
                            "reopenReason"),
                    Set.of("rangeId", "startDate", "endDate", "rangeKind", "status",
                            "closedAt", "closedBy"), path);
            String rangeId = uniqueId(range, "rangeId", path, rangeIds, "period-close range");
            LocalDate startDate = date(range, "startDate", path);
            LocalDate endDate = date(range, "endDate", path);
            if (endDate.isBefore(startDate))
            {
                throw new IllegalStateException(path + ".endDate must be on or after startDate.");
            }
            String rangeKind = enumText(range, "rangeKind", path, Set.of("CALCULATED", "CUSTOM"));
            String status = enumText(range, "status", path, Set.of("CLOSED", "REOPENED"));
            Instant closedAt = instant(range, "closedAt", path, false);
            String closedBy = boundedText(range, "closedBy", path, 200);
            String closeReason = optionalBoundedText(range, "closeReason", path, 1000);
            Instant reopenedAt = instant(range, "reopenedAt", path, true);
            String reopenedBy = optionalBoundedText(range, "reopenedBy", path, 200);
            String reopenReason = optionalBoundedText(range, "reopenReason", path, 1000);
            if ("CLOSED".equals(status)
                    && (reopenedAt != null || reopenedBy != null || reopenReason != null))
            {
                throw new IllegalStateException(path + " cannot contain reopen facts while status is CLOSED.");
            }
            if ("REOPENED".equals(status) && (reopenedAt == null || reopenedBy == null))
            {
                throw new IllegalStateException(path + " must contain reopenedAt and reopenedBy.");
            }
            if (reopenedAt != null && reopenedAt.isBefore(closedAt))
            {
                throw new IllegalStateException(path + ".reopenedAt must not precede closedAt.");
            }
            ranges.add(new RangeValue(rangeId, startDate, endDate, rangeKind, status, closedAt,
                    closedBy, closeReason, reopenedAt, reopenedBy, reopenReason));
        }

        List<EventValue> events = new ArrayList<>();
        Set<String> eventIds = new HashSet<>();
        JsonNode eventNodes = requiredArray(value, "events", rootPath);
        for (int index = 0; index < eventNodes.size(); index++)
        {
            JsonNode event = eventNodes.get(index);
            String path = rootPath + ".events[" + index + "]";
            requireObject(event, path);
            requireFields(event,
                    Set.of("eventId", "rangeId", "eventType", "actor", "reason", "eventAt"),
                    Set.of("eventId", "rangeId", "eventType", "actor", "eventAt"), path);
            String eventId = uniqueId(event, "eventId", path, eventIds, "period-close event");
            String rangeId = text(event, "rangeId", path);
            if (!rangeIds.contains(rangeId))
            {
                throw new IllegalStateException(path + ".rangeId does not resolve to an imported close range.");
            }
            events.add(new EventValue(
                    eventId,
                    rangeId,
                    enumText(event, "eventType", path, Set.of("CLOSED", "REOPENED")),
                    boundedText(event, "actor", path, 200),
                    optionalBoundedText(event, "reason", path, 1000),
                    instant(event, "eventAt", path, false)));
        }
        requireMatchingHistory(ranges, events);
        requireNoOverlappingClosedRanges(ranges);
        ranges.sort(Comparator.comparing(RangeValue::startDate)
                .thenComparing(RangeValue::endDate)
                .thenComparing(RangeValue::externalId));
        events.sort(Comparator.comparing(EventValue::eventAt)
                .thenComparing(EventValue::externalId));
        return new SclxPeriodCloseImportData(ranges, events);
    }

    List<RangeValue> ranges()
    {
        return ranges;
    }

    List<EventValue> events()
    {
        return events;
    }

    private static void requireMatchingHistory(List<RangeValue> ranges, List<EventValue> events)
    {
        Map<String, List<EventValue>> byRange = new HashMap<>();
        for (EventValue event : events)
        {
            byRange.computeIfAbsent(event.rangeId(), ignored -> new ArrayList<>()).add(event);
        }
        for (RangeValue range : ranges)
        {
            List<EventValue> history = byRange.getOrDefault(range.externalId(), List.of());
            EventValue closed = singleEvent(history, "CLOSED", range.externalId());
            requireSameFacts(range.closedAt(), range.closedBy(), range.closeReason(), closed,
                    "close", range.externalId());
            if ("REOPENED".equals(range.status()))
            {
                EventValue reopened = singleEvent(history, "REOPENED", range.externalId());
                requireSameFacts(range.reopenedAt(), range.reopenedBy(), range.reopenReason(), reopened,
                        "reopen", range.externalId());
                if (history.size() != 2)
                {
                    throw new IllegalStateException(
                            "Reopened period-close range must have exactly two factual events: "
                                    + range.externalId() + ".");
                }
            }
            else if (history.size() != 1)
            {
                throw new IllegalStateException(
                        "Closed period-close range must have exactly one factual event: "
                                + range.externalId() + ".");
            }
        }
    }

    private static EventValue singleEvent(List<EventValue> events, String type, String rangeId)
    {
        List<EventValue> matching = events.stream()
                .filter(event -> type.equals(event.eventType()))
                .toList();
        if (matching.size() != 1)
        {
            throw new IllegalStateException(
                    "Period-close range must have exactly one " + type + " event: " + rangeId + ".");
        }
        return matching.get(0);
    }

    private static void requireSameFacts(
            Instant expectedAt,
            String expectedActor,
            String expectedReason,
            EventValue event,
            String label,
            String rangeId)
    {
        if (!expectedAt.equals(event.eventAt())
                || !expectedActor.equals(event.actor())
                || !Objects.equals(expectedReason, event.reason()))
        {
            throw new IllegalStateException(
                    "Period-close " + label + " event does not match range facts: " + rangeId + ".");
        }
    }

    private static void requireNoOverlappingClosedRanges(List<RangeValue> ranges)
    {
        List<RangeValue> closed = ranges.stream()
                .filter(range -> "CLOSED".equals(range.status()))
                .sorted(Comparator.comparing(RangeValue::startDate)
                        .thenComparing(RangeValue::endDate))
                .toList();
        for (int index = 1; index < closed.size(); index++)
        {
            RangeValue previous = closed.get(index - 1);
            RangeValue current = closed.get(index);
            if (!current.startDate().isAfter(previous.endDate()))
            {
                throw new IllegalStateException(
                        "Imported closed period ranges overlap: " + previous.externalId()
                                + " and " + current.externalId() + ".");
            }
        }
    }

    private static String uniqueId(
            JsonNode value, String field, String path, Set<String> identities, String label)
    {
        String identity = text(value, field, path);
        if (!identities.add(identity))
        {
            throw new IllegalStateException("SCLX contains duplicate " + label + " identity " + identity + ".");
        }
        return identity;
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

    private static String enumText(
            JsonNode value, String field, String path, Set<String> allowed)
    {
        String result = text(value, field, path);
        if (!allowed.contains(result))
        {
            throw new IllegalStateException(path + "." + field + " has unsupported value " + result + ".");
        }
        return result;
    }

    private static String boundedText(JsonNode value, String field, String path, int limit)
    {
        String result = text(value, field, path);
        if (result.length() > limit)
        {
            throw new IllegalStateException(path + "." + field + " exceeds " + limit + " characters.");
        }
        return result;
    }

    private static String optionalBoundedText(JsonNode value, String field, String path, int limit)
    {
        String result = optionalText(value, field, path);
        if (result != null && result.length() > limit)
        {
            throw new IllegalStateException(path + "." + field + " exceeds " + limit + " characters.");
        }
        return result;
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

    private static int integer(JsonNode value, String field, String path)
    {
        JsonNode node = value.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt())
        {
            throw new IllegalStateException(path + "." + field + " must be an integer.");
        }
        return node.intValue();
    }

    private static LocalDate date(JsonNode value, String field, String path)
    {
        try
        {
            return LocalDate.parse(text(value, field, path));
        }
        catch (DateTimeParseException ex)
        {
            throw new IllegalStateException(path + "." + field + " must use ISO date format.", ex);
        }
    }

    private static Instant instant(JsonNode value, String field, String path, boolean optional)
    {
        String source = optionalText(value, field, path);
        if (source == null)
        {
            if (optional)
            {
                return null;
            }
            throw new IllegalStateException(path + "." + field + " must be a nonblank ISO instant.");
        }
        try
        {
            return Instant.parse(source);
        }
        catch (DateTimeParseException ex)
        {
            throw new IllegalStateException(path + "." + field + " must use ISO instant format.", ex);
        }
    }

    record RangeValue(
            String externalId,
            LocalDate startDate,
            LocalDate endDate,
            String rangeKind,
            String status,
            Instant closedAt,
            String closedBy,
            String closeReason,
            Instant reopenedAt,
            String reopenedBy,
            String reopenReason)
    {
    }

    record EventValue(
            String externalId,
            String rangeId,
            String eventType,
            String actor,
            String reason,
            Instant eventAt)
    {
    }
}
