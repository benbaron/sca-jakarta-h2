package org.nonprofitbookkeeping.interchange.sclx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Typed contract helpers for selected-company counterparties, merchants, and merchant line links. */
final class SclxPartyExtension
{
    static final String KEY = "counterparties";

    private static final Set<String> ROOT_KEYS = Set.of(
            "counterparties", "merchants", "transactionLineMerchants");
    private static final Set<String> COUNTERPARTY_KEYS = Set.of(
            "counterpartyId", "displayName", "kind", "email", "phone", "notes", "active");
    private static final Set<String> MERCHANT_KEYS = Set.of(
            "merchantId", "name", "notes", "active");
    private static final Set<String> MERCHANT_LINK_KEYS = Set.of("lineId", "merchantId");

    private SclxPartyExtension()
    {
    }

    static Map<String, Object> value(
            List<Map<String, Object>> counterparties,
            List<Map<String, Object>> merchants,
            List<Map<String, Object>> transactionLineMerchants)
    {
        return Map.of(
                "counterparties", List.copyOf(counterparties),
                "merchants", List.copyOf(merchants),
                "transactionLineMerchants", List.copyOf(transactionLineMerchants));
    }

    static Map<String, Object> counterpartyEntry(
            String counterpartyId,
            String displayName,
            String kind,
            String email,
            String phone,
            String notes,
            boolean active)
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("counterpartyId", requireText(counterpartyId, "counterpartyId"));
        entry.put("displayName", requireText(displayName, "displayName"));
        entry.put("kind", requireText(kind, "kind"));
        entry.put("email", optionalText(email));
        entry.put("phone", optionalText(phone));
        entry.put("notes", optionalText(notes));
        entry.put("active", active);
        return java.util.Collections.unmodifiableMap(entry);
    }

    static Map<String, Object> merchantEntry(
            String merchantId,
            String name,
            String notes,
            boolean active)
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("merchantId", requireText(merchantId, "merchantId"));
        entry.put("name", requireText(name, "name"));
        entry.put("notes", optionalText(notes));
        entry.put("active", active);
        return java.util.Collections.unmodifiableMap(entry);
    }

    static Map<String, Object> transactionLineMerchantEntry(String lineId, String merchantId)
    {
        return Map.of(
                "lineId", requireText(lineId, "lineId"),
                "merchantId", requireText(merchantId, "merchantId"));
    }

    static Data data(SclxExportDocument.Extensions extensions)
    {
        Objects.requireNonNull(extensions, "extensions");
        Object raw = extensions.scaJakartaH2().get(KEY);
        if (raw == null)
        {
            return new Data(List.of(), List.of(), List.of());
        }
        if (!(raw instanceof Map<?, ?> root))
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.counterparties must be an object");
        }
        if (!root.keySet().equals(ROOT_KEYS))
        {
            throw new IllegalArgumentException(
                    "extensions.scaJakartaH2.counterparties has unsupported fields");
        }

        return new Data(
                counterparties(array(root, "counterparties")),
                merchants(array(root, "merchants")),
                merchantLinks(array(root, "transactionLineMerchants")));
    }

    static Set<String> uniqueCounterpartyIds(Data data)
    {
        Set<String> identities = new HashSet<>();
        for (CounterpartyEntry entry : data.counterparties())
        {
            requireUnique(identities, entry.counterpartyId(), "counterparty");
        }
        return identities;
    }

    static Set<String> uniqueMerchantIds(Data data)
    {
        Set<String> identities = new HashSet<>();
        for (MerchantEntry entry : data.merchants())
        {
            requireUnique(identities, entry.merchantId(), "merchant");
        }
        return identities;
    }

    private static List<?> array(Map<?, ?> root, String field)
    {
        Object value = root.get(field);
        if (!(value instanceof List<?> list))
        {
            throw new IllegalArgumentException(
                    "extensions.scaJakartaH2.counterparties." + field + " must be an array");
        }
        return list;
    }

    private static List<CounterpartyEntry> counterparties(List<?> values)
    {
        List<CounterpartyEntry> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++)
        {
            Map<?, ?> map = object(values.get(index), "counterparties", index, COUNTERPARTY_KEYS);
            result.add(new CounterpartyEntry(
                    text(map, "counterpartyId", "counterparties", index),
                    text(map, "displayName", "counterparties", index),
                    text(map, "kind", "counterparties", index),
                    optional(map, "email", "counterparties", index),
                    optional(map, "phone", "counterparties", index),
                    optional(map, "notes", "counterparties", index),
                    flag(map, "active", "counterparties", index)));
        }
        return List.copyOf(result);
    }

    private static List<MerchantEntry> merchants(List<?> values)
    {
        List<MerchantEntry> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++)
        {
            Map<?, ?> map = object(values.get(index), "merchants", index, MERCHANT_KEYS);
            result.add(new MerchantEntry(
                    text(map, "merchantId", "merchants", index),
                    text(map, "name", "merchants", index),
                    optional(map, "notes", "merchants", index),
                    flag(map, "active", "merchants", index)));
        }
        return List.copyOf(result);
    }

    private static List<TransactionLineMerchant> merchantLinks(List<?> values)
    {
        List<TransactionLineMerchant> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++)
        {
            Map<?, ?> map = object(values.get(index), "transactionLineMerchants", index, MERCHANT_LINK_KEYS);
            result.add(new TransactionLineMerchant(
                    text(map, "lineId", "transactionLineMerchants", index),
                    text(map, "merchantId", "transactionLineMerchants", index)));
        }
        return List.copyOf(result);
    }

    private static Map<?, ?> object(Object value, String array, int index, Set<String> keys)
    {
        if (!(value instanceof Map<?, ?> map))
        {
            throw new IllegalArgumentException(
                    "extensions.scaJakartaH2.counterparties." + array + "[" + index + "] must be an object");
        }
        if (!map.keySet().equals(keys))
        {
            throw new IllegalArgumentException(
                    "extensions.scaJakartaH2.counterparties." + array + "[" + index
                            + "] has unsupported fields");
        }
        return map;
    }

    private static String text(Map<?, ?> map, String field, String array, int index)
    {
        Object value = map.get(field);
        if (!(value instanceof String text) || text.isBlank())
        {
            throw new IllegalArgumentException(
                    "extensions.scaJakartaH2.counterparties." + array + "[" + index + "]." + field
                            + " must be nonblank text");
        }
        return text;
    }

    private static String optional(Map<?, ?> map, String field, String array, int index)
    {
        Object value = map.get(field);
        if (value == null)
        {
            return null;
        }
        if (!(value instanceof String text))
        {
            throw new IllegalArgumentException(
                    "extensions.scaJakartaH2.counterparties." + array + "[" + index + "]." + field
                            + " must be text or null");
        }
        return optionalText(text);
    }

    private static boolean flag(Map<?, ?> map, String field, String array, int index)
    {
        Object value = map.get(field);
        if (!(value instanceof Boolean flag))
        {
            throw new IllegalArgumentException(
                    "extensions.scaJakartaH2.counterparties." + array + "[" + index + "]." + field
                            + " must be boolean");
        }
        return flag;
    }

    private static void requireUnique(Set<String> identities, String identity, String type)
    {
        if (!identities.add(identity))
        {
            throw new IllegalArgumentException("duplicate " + type + " portable identity: " + identity);
        }
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

    record Data(
            List<CounterpartyEntry> counterparties,
            List<MerchantEntry> merchants,
            List<TransactionLineMerchant> transactionLineMerchants)
    {
        Data
        {
            counterparties = List.copyOf(counterparties);
            merchants = List.copyOf(merchants);
            transactionLineMerchants = List.copyOf(transactionLineMerchants);
        }
    }

    record CounterpartyEntry(
            String counterpartyId,
            String displayName,
            String kind,
            String email,
            String phone,
            String notes,
            boolean active)
    {
    }

    record MerchantEntry(String merchantId, String name, String notes, boolean active)
    {
    }

    record TransactionLineMerchant(String lineId, String merchantId)
    {
    }
}
