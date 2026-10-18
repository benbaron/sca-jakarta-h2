package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Optional explicit designation; absence never infers an NMR item from a line flag. */
final class SclxNmrItemMetadata
{
    private static final String IDENTITY_FIELD = "_scaNmrItems";

    private SclxNmrItemMetadata()
    {
    }

    record Facts(String recipientId, String movement, String evidenceReference)
    {
    }

    static Facts facts(JsonNode value)
    {
        Set<String> fields = new HashSet<>();
        if (value != null)
        {
            value.fieldNames().forEachRemaining(fields::add);
        }
        if (value == null || !value.isObject() || !value.path("version").isIntegralNumber()
                || !value.path("version").canConvertToInt() || !Set.of(1, 2).contains(value.path("version").intValue())
                || !fields.equals(value.path("version").intValue() == 1 ? Set.of("version", "recipientId")
                    : Set.of("version", "recipientId", "movement", "evidenceReference"))
                || !value.path("recipientId").isTextual() || value.path("recipientId").textValue().isBlank())
        {
            throw new IllegalStateException("Supplemental NMR metadata requires supported version 1/2 and exactly its documented fields and recipientId.");
        }
        String movement = optional(value, "movement", 30);
        String evidence = optional(value, "evidenceReference", 500);
        if (movement != null)
        {
            try
            {
                org.nonprofitbookkeeping.model.NmrMovementKind.valueOf(movement);
            }
            catch (IllegalArgumentException ex)
            {
                throw new IllegalStateException("Unsupported NMR movement: " + movement, ex);
            }
        }
        return new Facts(value.path("recipientId").textValue(), movement, evidence);
    }

    static String recipient(JsonNode value)
    {
        return facts(value).recipientId();
    }

    private static String optional(JsonNode value, String name, int limit)
    {
        JsonNode field = value.get(name);
        if (field == null || field.isNull())
        {
            return null;
        }
        if (!field.isTextual() || field.textValue().isBlank() || field.textValue().trim().length() > limit)
        {
            throw new IllegalStateException("NMR " + name + " must be nonblank text within " + limit + " characters or null.");
        }
        return field.textValue().trim();
    }

    static JsonNode normalized(JsonNode value)
    {
        Facts facts = facts(value);
        ObjectNode result = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        boolean detailed = facts.movement() != null || facts.evidenceReference() != null;
        result.put("version", detailed ? 2 : 1); result.put("recipientId", facts.recipientId());
        if (detailed)
        {
            result.put("movement", facts.movement()); result.put("evidenceReference", facts.evidenceReference());
        }
        return result;
    }

    static void validate(JsonNode root)
    {
        for (JsonNode detail : root.path("extensions").path("scaJakartaH2").path("supplementalDetails"))
        {
            if (detail.has("nmr"))
            {
                SclxTransactionDetailImportData.parse(root);
                return;
            }
        }
    }

    /** New designation changes the complete transaction hash; legacy hashes stay unchanged. */
    static JsonNode identityDocument(JsonNode root)
    {
        validate(root);
        JsonNode copy = root.deepCopy();
        Map<String, ObjectNode> transactions = new LinkedHashMap<>();
        for (JsonNode transaction : copy.path("transactions"))
        {
            if (transaction.has(IDENTITY_FIELD))
            {
                throw new IllegalStateException("Reserved NMR transaction identity field in source.");
            }
            transactions.put(transaction.path("transactionId").asText(), (ObjectNode) transaction);
        }
        for (JsonNode detail : copy.path("extensions").path("scaJakartaH2").path("supplementalDetails"))
        {
            if (detail.has("nmr"))
            {
                JsonNode facts = normalized(detail.get("nmr"));
                ((ObjectNode)detail).set("nmr", facts);
                ObjectNode transaction = transactions.get(detail.path("transactionId").asText());
                if (transaction == null)
                {
                    throw new IllegalStateException("NMR supplemental transaction does not resolve.");
                }
                ObjectNode metadata = transaction.has(IDENTITY_FIELD)
                        ? (ObjectNode) transaction.get(IDENTITY_FIELD) : transaction.putObject(IDENTITY_FIELD);
                metadata.set(detail.path("supplementalDetailId").asText(), facts);
            }
        }
        return copy;
    }
}
