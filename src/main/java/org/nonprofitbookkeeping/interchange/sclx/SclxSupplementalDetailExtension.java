package org.nonprofitbookkeeping.interchange.sclx;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Typed contract helper for canonical transaction supplemental-detail export. */
final class SclxSupplementalDetailExtension
{
    static final String KEY = "supplementalDetails";

    private static final Set<String> ENTRY_KEYS = Set.of(
            "supplementalDetailId",
            "transactionId",
            "lineOrder",
            "kind",
            "entryRef",
            "counterparty",
            "description",
            "reference",
            "amount",
            "dueDate",
            "startDate",
            "endDate",
            "notes");

    private static final Set<String> SUPPORTED_KINDS = Set.of(
            "RECEIVABLE",
            "PAYABLE",
            "PREPAID_EXPENSE",
            "DEFERRED_REVENUE",
            "OTHER_ASSET",
            "OTHER_LIABILITY");

    private SclxSupplementalDetailExtension()
    {
    }

    static Map<String, Object> entry(
            String supplementalDetailId,
            String transactionId,
            int lineOrder,
            String kind,
            String entryRef,
            String counterparty,
            String description,
            String reference,
            BigDecimal amount,
            LocalDate dueDate,
            LocalDate startDate,
            LocalDate endDate,
            String notes)
    {
        validateSemantics(lineOrder, kind, description, amount, startDate, endDate);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("supplementalDetailId", requireText(supplementalDetailId, "supplementalDetailId"));
        entry.put("transactionId", requireText(transactionId, "transactionId"));
        entry.put("lineOrder", lineOrder);
        entry.put("kind", kind);
        entry.put("entryRef", optionalText(entryRef));
        entry.put("counterparty", optionalText(counterparty));
        entry.put("description", description.strip());
        entry.put("reference", optionalText(reference));
        entry.put("amount", amount);
        entry.put("dueDate", dueDate);
        entry.put("startDate", startDate);
        entry.put("endDate", endDate);
        entry.put("notes", optionalText(notes));
        return java.util.Collections.unmodifiableMap(entry);
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
            throw new IllegalArgumentException(
                    "extensions.scaJakartaH2.supplementalDetails must be an array");
        }

        List<Entry> entries = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++)
        {
            if (!(values.get(index) instanceof Map<?, ?> map))
            {
                throw new IllegalArgumentException(
                        "extensions.scaJakartaH2.supplementalDetails[" + index + "] must be an object");
            }
            if (!map.keySet().equals(ENTRY_KEYS))
            {
                throw new IllegalArgumentException(
                        "extensions.scaJakartaH2.supplementalDetails[" + index
                                + "] has unsupported fields");
            }

            Entry entry = new Entry(
                    text(map, "supplementalDetailId", index),
                    text(map, "transactionId", index),
                    integer(map, "lineOrder", index),
                    text(map, "kind", index),
                    optional(map, "entryRef", index),
                    optional(map, "counterparty", index),
                    text(map, "description", index),
                    optional(map, "reference", index),
                    decimal(map, "amount", index),
                    date(map, "dueDate", index),
                    date(map, "startDate", index),
                    date(map, "endDate", index),
                    optional(map, "notes", index));
            validateSemantics(
                    entry.lineOrder(),
                    entry.kind(),
                    entry.description(),
                    entry.amount(),
                    entry.startDate(),
                    entry.endDate());
            entries.add(entry);
        }
        return List.copyOf(entries);
    }

    static Set<String> uniqueIds(List<Entry> entries)
    {
        Set<String> identities = new HashSet<>();
        for (Entry entry : entries)
        {
            if (!identities.add(entry.supplementalDetailId()))
            {
                throw new IllegalArgumentException(
                        "duplicate supplemental-detail portable identity: " + entry.supplementalDetailId());
            }
        }
        return identities;
    }

    private static void validateSemantics(
            int lineOrder,
            String kind,
            String description,
            BigDecimal amount,
            LocalDate startDate,
            LocalDate endDate)
    {
        if (lineOrder < 0)
        {
            throw new IllegalArgumentException("supplemental detail lineOrder must not be negative");
        }
        if (!SUPPORTED_KINDS.contains(requireText(kind, "kind")))
        {
            throw new IllegalArgumentException("unsupported supplemental detail kind: " + kind);
        }
        requireText(description, "description");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0)
        {
            throw new IllegalArgumentException("supplemental detail amount must not be negative");
        }
        if ((startDate == null) != (endDate == null))
        {
            throw new IllegalArgumentException(
                    "supplemental detail startDate and endDate must both be present or absent");
        }
        if (startDate != null && startDate.isAfter(endDate))
        {
            throw new IllegalArgumentException(
                    "supplemental detail startDate must be on or before endDate");
        }
    }

    private static String text(Map<?, ?> map, String field, int index)
    {
        Object value = map.get(field);
        if (!(value instanceof String text) || text.isBlank())
        {
            throw fieldError(index, field, "must be nonblank text");
        }
        return text;
    }

    private static String optional(Map<?, ?> map, String field, int index)
    {
        Object value = map.get(field);
        if (value == null)
        {
            return null;
        }
        if (!(value instanceof String text))
        {
            throw fieldError(index, field, "must be text or null");
        }
        return optionalText(text);
    }

    private static int integer(Map<?, ?> map, String field, int index)
    {
        Object value = map.get(field);
        if (!(value instanceof Number number))
        {
            throw fieldError(index, field, "must be an integer");
        }
        long longValue = number.longValue();
        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE
                || number.doubleValue() != (double) longValue)
        {
            throw fieldError(index, field, "must be an integer");
        }
        return (int) longValue;
    }

    private static BigDecimal decimal(Map<?, ?> map, String field, int index)
    {
        Object value = map.get(field);
        if (!(value instanceof BigDecimal decimal))
        {
            throw fieldError(index, field, "must be a decimal");
        }
        return decimal;
    }

    private static LocalDate date(Map<?, ?> map, String field, int index)
    {
        Object value = map.get(field);
        if (value == null)
        {
            return null;
        }
        if (!(value instanceof LocalDate date))
        {
            throw fieldError(index, field, "must be a date or null");
        }
        return date;
    }

    private static IllegalArgumentException fieldError(int index, String field, String message)
    {
        return new IllegalArgumentException(
                "extensions.scaJakartaH2.supplementalDetails[" + index + "]." + field + " " + message);
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String optionalText(String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }

    record Entry(
            String supplementalDetailId,
            String transactionId,
            int lineOrder,
            String kind,
            String entryRef,
            String counterparty,
            String description,
            String reference,
            BigDecimal amount,
            LocalDate dueDate,
            LocalDate startDate,
            LocalDate endDate,
            String notes)
    {
    }
}
