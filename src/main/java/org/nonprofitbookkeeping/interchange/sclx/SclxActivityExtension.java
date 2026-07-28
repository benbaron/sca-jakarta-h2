package org.nonprofitbookkeeping.interchange.sclx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Typed contract helpers for the governed activities extension. */
final class SclxActivityExtension
{
    static final String KEY = "activities";
    private static final Set<String> ENTRY_KEYS = Set.of("activityId", "code", "name", "active");

    private SclxActivityExtension()
    {
    }

    static Map<String, Object> entry(String activityId, String code, String name, boolean active)
    {
        return Map.of(
                "activityId", requireText(activityId, "activityId"),
                "code", requireText(code, "code"),
                "name", requireText(name, "name"),
                "active", active);
    }

    static List<Entry> entries(SclxExportDocument.Extensions extensions)
    {
        Objects.requireNonNull(extensions, "extensions");
        Object raw = extensions.scaJakartaH2().get(KEY);
        if (raw == null)
        {
            return List.of();
        }
        if (!(raw instanceof List<?> values))
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.activities must be an array");
        }

        List<Entry> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++)
        {
            Object value = values.get(index);
            if (!(value instanceof Map<?, ?> map))
            {
                throw new IllegalArgumentException(
                        "extensions.scaJakartaH2.activities[" + index + "] must be an object");
            }
            if (!map.keySet().equals(ENTRY_KEYS))
            {
                throw new IllegalArgumentException(
                        "extensions.scaJakartaH2.activities[" + index + "] has unsupported fields");
            }
            result.add(new Entry(
                    text(map, "activityId", index),
                    text(map, "code", index),
                    text(map, "name", index),
                    flag(map, "active", index)));
        }
        return List.copyOf(result);
    }

    static Set<String> uniqueIds(SclxExportDocument.Extensions extensions)
    {
        Set<String> ids = new HashSet<>();
        for (Entry entry : entries(extensions))
        {
            if (!ids.add(entry.activityId()))
            {
                throw new IllegalArgumentException(
                        "duplicate activity portable identity: " + entry.activityId());
            }
        }
        return ids;
    }

    private static String text(Map<?, ?> map, String key, int index)
    {
        Object value = map.get(key);
        if (!(value instanceof String text))
        {
            throw new IllegalArgumentException(
                    "extensions.scaJakartaH2.activities[" + index + "]." + key + " must be text");
        }
        return requireText(text, key);
    }

    private static boolean flag(Map<?, ?> map, String key, int index)
    {
        Object value = map.get(key);
        if (!(value instanceof Boolean flag))
        {
            throw new IllegalArgumentException(
                    "extensions.scaJakartaH2.activities[" + index + "]." + key + " must be boolean");
        }
        return flag;
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    record Entry(String activityId, String code, String name, boolean active)
    {
        Entry
        {
            requireText(activityId, "activityId");
            requireText(code, "code");
            requireText(name, "name");
        }
    }
}
