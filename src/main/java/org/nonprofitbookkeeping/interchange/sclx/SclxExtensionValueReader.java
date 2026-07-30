package org.nonprofitbookkeeping.interchange.sclx;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared strict value readers for governed SCLX extension objects. */
final class SclxExtensionValueReader
{
    private SclxExtensionValueReader()
    {
    }

    static List<?> array(Map<?, ?> root, String field, String path)
    {
        Object value = root.get(field);
        if (!(value instanceof List<?> list))
        {
            throw new IllegalArgumentException(path + '.' + field + " must be an array");
        }
        return list;
    }

    static List<Map<?, ?>> objects(List<?> values, String path, Set<String> keys)
    {
        List<Map<?, ?>> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++)
        {
            Object value = values.get(index);
            if (!(value instanceof Map<?, ?> map))
            {
                throw new IllegalArgumentException(path + '[' + index + "] must be an object");
            }
            if (!keys.containsAll(map.keySet()))
            {
                throw new IllegalArgumentException(path + '[' + index + "] has unsupported fields");
            }
            result.add(map);
        }
        return List.copyOf(result);
    }

    static String text(Map<?, ?> map, String field, String path)
    {
        Object value = map.get(field);
        if (!(value instanceof String text) || text.isBlank())
        {
            throw new IllegalArgumentException(path + '.' + field + " must be nonblank text");
        }
        return text;
    }

    static String optionalText(Map<?, ?> map, String field, String path)
    {
        Object value = map.get(field);
        if (value == null)
        {
            return null;
        }
        if (!(value instanceof String text))
        {
            throw new IllegalArgumentException(path + '.' + field + " must be text or null");
        }
        return text.isBlank() ? null : text.strip();
    }

    static boolean flag(Map<?, ?> map, String field, String path)
    {
        Object value = map.get(field);
        if (!(value instanceof Boolean flag))
        {
            throw new IllegalArgumentException(path + '.' + field + " must be boolean");
        }
        return flag;
    }

    static int integer(Map<?, ?> map, String field, String path)
    {
        Object value = map.get(field);
        if (!(value instanceof Number number))
        {
            throw new IllegalArgumentException(path + '.' + field + " must be an integer");
        }
        long integer = number.longValue();
        if (integer < Integer.MIN_VALUE || integer > Integer.MAX_VALUE
                || number.doubleValue() != (double) integer)
        {
            throw new IllegalArgumentException(path + '.' + field + " must be an integer");
        }
        return (int) integer;
    }

    static BigDecimal decimal(Map<?, ?> map, String field, String path, boolean nullable)
    {
        Object value = map.get(field);
        if (value == null && nullable)
        {
            return null;
        }
        if (!(value instanceof BigDecimal decimal))
        {
            throw new IllegalArgumentException(path + '.' + field + " must be a decimal"
                    + (nullable ? " or null" : ""));
        }
        return decimal;
    }

    static LocalDate date(Map<?, ?> map, String field, String path, boolean nullable)
    {
        Object value = map.get(field);
        if (value == null && nullable)
        {
            return null;
        }
        if (!(value instanceof LocalDate date))
        {
            throw new IllegalArgumentException(path + '.' + field + " must be a date"
                    + (nullable ? " or null" : ""));
        }
        return date;
    }

    static Instant instant(Map<?, ?> map, String field, String path, boolean nullable)
    {
        Object value = map.get(field);
        if (value == null && nullable)
        {
            return null;
        }
        if (!(value instanceof Instant instant))
        {
            throw new IllegalArgumentException(path + '.' + field + " must be an instant"
                    + (nullable ? " or null" : ""));
        }
        return instant;
    }
}
