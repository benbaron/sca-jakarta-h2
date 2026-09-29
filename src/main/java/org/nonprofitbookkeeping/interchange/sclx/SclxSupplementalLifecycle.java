package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Versioned item allocation references inside the existing supplemental-detail extension. */
final class SclxSupplementalLifecycle
{
    record Link(UUID itemId, String effect, String lineId)
    {
        Map<String, Object> value()
        {
            return Map.of("version", 1, "itemId", itemId.toString(), "itemEffect", effect, "transactionLineId", lineId);
        }
    }

    static Link link(JsonNode value)
    {
        if (value == null || value.isNull())
        {
            return null;
        }
        Set<String> fields = new HashSet<>();
        value.fieldNames().forEachRemaining(fields::add);
        if (!value.isObject() || !fields.equals(Set.of("version", "itemId", "itemEffect", "transactionLineId"))
                || !value.path("version").isIntegralNumber() || !value.path("version").canConvertToInt() || value.path("version").intValue() != 1)
        {
            throw new IllegalStateException("Supplemental lifecycle must use version 1 and exactly itemId, itemEffect and transactionLineId.");
        }
        UUID item;
        try
        {
            String text = value.path("itemId").asText();
            item = UUID.fromString(text);
            if (!item.toString().equalsIgnoreCase(text))
            {
                throw new IllegalArgumentException("Noncanonical UUID");
            }
        }
        catch (IllegalArgumentException ex)
        {
            throw new IllegalStateException("Supplemental lifecycle itemId must be a UUID.", ex);
        }
        String effect = value.path("itemEffect").asText();
        String line = value.path("transactionLineId").asText();
        if (!Set.of("INCREASE", "DECREASE").contains(effect) || line.isBlank())
        {
            throw new IllegalStateException("Supplemental lifecycle requires a supported effect and transaction line reference.");
        }
        return new Link(item, effect, line);
    }

    static Map<String, Link> parse(JsonNode root)
    {
        Map<String, JsonNode> accounts = new HashMap<>();
        root.path("chartOfAccounts").forEach(a -> accounts.put(a.path("accountId").asText(), a));
        Map<String, String> owners = new HashMap<>();
        Map<String, JsonNode> lines = new HashMap<>();
        root.path("transactions").forEach(t -> t.path("lines").forEach(l ->
        {
            String id = l.path("lineId").asText();
            owners.put(id, t.path("transactionId").asText());
            lines.put(id, l);
        }));
        Map<String, Link> result = new LinkedHashMap<>();
        Map<UUID, String> kinds = new HashMap<>();
        Map<String, BigDecimal> allocations = new HashMap<>();
        for (JsonNode detail : root.path("extensions").path("scaJakartaH2").path("supplementalDetails"))
        {
            Link link = link(detail.get("lifecycle"));
            if (link == null)
            {
                continue;
            }
            String id = detail.path("supplementalDetailId").asText();
            if (result.put(id, link) != null)
            {
                throw new IllegalStateException("Duplicate lifecycle detail: " + id);
            }
            JsonNode line = lines.get(link.lineId());
            if (line == null || !detail.path("transactionId").asText().equals(owners.get(link.lineId())))
            {
                throw new IllegalStateException("Supplemental lifecycle line is dangling or belongs to another transaction: " + id);
            }
            String kind = detail.path("kind").asText();
            String previous = kinds.putIfAbsent(link.itemId(), kind);
            JsonNode account = accounts.get(line.path("accountId").asText());
            String subtype = java.util.Arrays.stream(org.nonprofitbookkeeping.service.SupplementalOpenItemQueryService.Kind.values())
                    .filter(k -> k.name().equals(kind)).map(k -> k.accountSubtype().name()).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unsupported lifecycle kind: " + kind));
            if (account == null || !subtype.equals(account.path("subtype").asText()) || (previous != null && !previous.equals(kind)))
            {
                throw new IllegalStateException("Supplemental lifecycle account/item kind conflicts: " + id);
            }
            BigDecimal signed = amount(line, "debit").subtract(amount(line, "credit"));
            if ("CREDIT".equals(account.path("increaseSide").asText()))
            {
                signed = signed.negate();
            }
            BigDecimal allocated = amount(detail, "amount");
            if (allocated.signum() <= 0 || signed.signum() != (link.effect().equals("INCREASE") ? 1 : -1))
            {
                throw new IllegalStateException("Supplemental lifecycle amount/effect conflicts with its ledger line: " + id);
            }
            BigDecimal total = allocations.merge(link.lineId(), allocated, BigDecimal::add);
            if (total.compareTo(signed.abs()) > 0)
            {
                throw new IllegalStateException("Supplemental lifecycle allocations exceed ledger line: " + link.lineId());
            }
        }
        return Map.copyOf(result);
    }

    static Map<String, BigDecimal> explained(JsonNode root)
    {
        Map<String, Link> links = parse(root);
        Map<String, BigDecimal> amounts = new HashMap<>();
        for (JsonNode detail : root.path("extensions").path("scaJakartaH2").path("supplementalDetails"))
        {
            Link link = links.get(detail.path("supplementalDetailId").asText());
            if (link != null)
            {
                amounts.merge(link.lineId(), amount(detail, "amount"), BigDecimal::add);
            }
        }
        if (links.isEmpty())
        {
            return amounts;
        }
        Map<String, JsonNode> transactions = new HashMap<>();
        root.path("transactions").forEach(t -> transactions.put(t.path("transactionId").asText(), t));
        Set<String> done = new HashSet<>();
        for (String id : transactions.keySet()) inherit(id, transactions, amounts, done, new HashSet<>());
        return amounts;
    }

    private static void inherit(String id, Map<String, JsonNode> transactions, Map<String, BigDecimal> amounts,
            Set<String> done, Set<String> path)
    {
        if (done.contains(id))
        {
            return;
        }
        if (!path.add(id))
        {
            throw new IllegalStateException("Cyclic SCLX correction history.");
        }
        JsonNode txn = transactions.get(id);
        if (txn != null && "REVERSAL".equals(txn.path("correctionType").asText()))
        {
            String sourceId = txn.path("correctionOfTransactionId").asText();
            JsonNode source = transactions.get(sourceId);
            if (source == null)
            {
                throw new IllegalStateException("Dangling SCLX reversal source.");
            }
            inherit(sourceId, transactions, amounts, done, path);
            JsonNode originals = source.path("lines"), inverses = txn.path("lines");
            if (originals.size() != inverses.size())
            {
                throw new IllegalStateException("SCLX inverse line count differs.");
            }
            for (int i = 0; i < originals.size(); i++)
            {
                JsonNode a = originals.get(i), b = inverses.get(i);
                String inverseId = b.path("lineId").asText();
                BigDecimal covered = amounts.get(a.path("lineId").asText());
                if (covered == null)
                {
                    continue;
                }
                if (amounts.containsKey(inverseId))
                {
                    throw new IllegalStateException("SCLX inverse duplicates lifecycle allocations.");
                }
                if (!a.path("accountId").equals(b.path("accountId")) || !a.path("fundId").equals(b.path("fundId"))
                        || amount(a, "debit").compareTo(amount(b, "credit")) != 0
                        || amount(a, "credit").compareTo(amount(b, "debit")) != 0)
                {
                    throw new IllegalStateException("SCLX lifecycle inverse must preserve canonical line correspondence.");
                }
                amounts.put(inverseId, covered);
            }
        }
        done.add(id);
        path.remove(id);
    }

    static BigDecimal amount(JsonNode value, String field)
    {
        JsonNode node = value.get(field);
        if (node == null || node.isNull())
        {
            return BigDecimal.ZERO;
        }
        try
        {
            return new BigDecimal(node.asText());
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalStateException("Invalid exact decimal: " + field, ex);
        }
    }
}
