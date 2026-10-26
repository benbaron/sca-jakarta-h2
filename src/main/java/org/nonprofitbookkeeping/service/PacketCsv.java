package org.nonprofitbookkeeping.service;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Lossless literal CSV presentation of typed report records; collections are exported in separate tables. */
final class PacketCsv
{
    private PacketCsv()
    {
    }

    static String records(Class<?> type, List<?> records)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> schema = new LinkedHashMap<>();
        flatten(schema, "", type, null);
        for (Object record : records)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            flatten(row, "", type, record);
            rows.add(row);
        }
        return rows(new ArrayList<>(schema.keySet()), rows);
    }

    static String metadata(Object record)
    {
        return records(record.getClass(), List.of(record));
    }

    static Map<String, Object> row(Object... entries)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2)
        {
            row.put(entries[i].toString(), entries[i + 1]);
        }
        return row;
    }

    static String rows(List<String> schema, List<Map<String, Object>> rows)
    {
        var columns = new LinkedHashSet<>(schema);
        rows.forEach(row -> columns.addAll(row.keySet()));
        StringBuilder out = new StringBuilder();
        out.append(columns.stream().map(PacketCsv::quote).collect(Collectors.joining(","))).append('\n');
        for (var row : rows)
        {
            out.append(columns.stream().map(key -> quote(text(row.get(key))))
                    .collect(Collectors.joining(","))).append('\n');
        }
        return out.toString();
    }

    private static void flatten(Map<String, Object> row, String prefix, Class<?> type, Object value)
    {
        if (!type.isRecord())
        {
            throw new IllegalArgumentException("Packet projection must be a record: " + type.getName());
        }
        for (RecordComponent component : type.getRecordComponents())
        {
            if (Collection.class.isAssignableFrom(component.getType()))
            {
                continue;
            }
            Object field;
            try
            {
                field = value == null ? null : component.getAccessor().invoke(value);
            }
            catch (ReflectiveOperationException ex)
            {
                throw new IllegalStateException("Could not render packet projection", ex);
            }
            String name = prefix + component.getName();
            if (component.getType().isRecord())
            {
                flatten(row, name + ".", component.getType(), field);
            }
            else
            {
                row.put(name, field);
            }
        }
    }

    private static String text(Object value)
    {
        if (value == null)
        {
            return "";
        }
        if (value instanceof Optional<?> optional)
        {
            return text(optional.orElse(null));
        }
        if (value instanceof BigDecimal decimal)
        {
            return decimal.toPlainString();
        }
        if (value instanceof Enum<?> code)
        {
            return code.name();
        }
        if (value instanceof Map<?, ?> map)
        {
            return map.entrySet().stream().sorted(java.util.Comparator.comparing(e -> e.getKey().toString()))
                    .map(e -> text(e.getKey()) + "=" + text(e.getValue())).collect(Collectors.joining("; "));
        }
        return value.toString();
    }

    private static String quote(String value)
    {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
