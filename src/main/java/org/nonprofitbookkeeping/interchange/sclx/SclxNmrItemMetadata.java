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

    static String recipient(JsonNode value)
    {
        Set<String> fields = new HashSet<>();
        if (value != null)
        {
            value.fieldNames().forEachRemaining(fields::add);
        }
        if (value == null || !value.isObject() || !fields.equals(Set.of("version", "recipientId"))
                || !value.path("version").isIntegralNumber() || !value.path("version").canConvertToInt()
                || value.path("version").intValue() != 1 || !value.path("recipientId").isTextual()
                || value.path("recipientId").textValue().isBlank())
        {
            throw new IllegalStateException("Supplemental NMR metadata requires version 1 and a nonblank recipientId, with no other fields.");
        }
        return value.path("recipientId").textValue();
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
                recipient(detail.get("nmr"));
                ObjectNode transaction = transactions.get(detail.path("transactionId").asText());
                if (transaction == null)
                {
                    throw new IllegalStateException("NMR supplemental transaction does not resolve.");
                }
                ObjectNode metadata = transaction.has(IDENTITY_FIELD)
                        ? (ObjectNode) transaction.get(IDENTITY_FIELD) : transaction.putObject(IDENTITY_FIELD);
                metadata.set(detail.path("supplementalDetailId").asText(), detail.get("nmr"));
            }
        }
        return copy;
    }
}
