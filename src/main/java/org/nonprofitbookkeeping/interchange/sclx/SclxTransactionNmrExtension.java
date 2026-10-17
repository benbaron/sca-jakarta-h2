package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Sparse, versioned preservation of the user's canonical NMR line flags. */
final class SclxTransactionNmrExtension
{
    static final String KEY = "transactionNmr";
    private static final String IDENTITY_FIELD = "_scaNmr";

    private SclxTransactionNmrExtension()
    {
    }

    static Set<String> parse(JsonNode root)
    {
        JsonNode extension = root.path("extensions").path("scaJakartaH2").path(KEY);
        if (extension.isMissingNode())
        {
            return Set.of();
        }
        Set<String> fields = new HashSet<>();
        extension.fieldNames().forEachRemaining(fields::add);
        if (!extension.isObject() || !fields.equals(Set.of("version", "lineIds"))
                || !extension.path("version").isIntegralNumber() || !extension.path("version").canConvertToInt()
                || extension.path("version").intValue() != 1 || !extension.path("lineIds").isArray())
        {
            throw new IllegalStateException("transactionNmr requires exactly version 1 and a lineIds array.");
        }
        Map<String, JsonNode> postingLines = new LinkedHashMap<>();
        for (JsonNode transaction : root.path("transactions"))
        {
            for (JsonNode line : transaction.path("lines"))
            {
                if (postingLines.putIfAbsent(line.path("lineId").asText(), line) != null)
                {
                    throw new IllegalStateException("Ambiguous transaction NMR line identity.");
                }
            }
        }
        Set<String> result = new HashSet<>();
        for (JsonNode id : extension.path("lineIds"))
        {
            if (!id.isTextual() || id.textValue().isBlank() || !result.add(id.textValue()))
            {
                throw new IllegalStateException("NMR line IDs must be unique nonblank text.");
            }
            JsonNode line = postingLines.get(id.textValue());
            if (line == null)
            {
                throw new IllegalStateException("Transaction NMR line does not resolve: " + id.textValue());
            }
            try
            {
                BigDecimal debit = new BigDecimal(line.path("debit").asText());
                BigDecimal credit = new BigDecimal(line.path("credit").asText());
                if (debit.signum() < 0 || credit.signum() < 0 || (debit.signum() == 0) == (credit.signum() == 0))
                {
                    throw new IllegalStateException("NMR requires a nonzero, one-sided posting line: " + id.textValue());
                }
            }
            catch (NumberFormatException ex)
            {
                throw new IllegalStateException("NMR requires valid debit/credit amounts: " + id.textValue(), ex);
            }
        }
        return Set.copyOf(result);
    }

    /** Preserve legacy unmarked hashes while including marked flags in existing conflict identity. */
    static JsonNode identityDocument(JsonNode root)
    {
        Set<String> marked = parse(root);
        JsonNode copy = root.deepCopy();
        for (JsonNode transaction : copy.path("transactions"))
        {
            for (JsonNode line : transaction.path("lines"))
            {
                if (line.has(IDENTITY_FIELD))
                {
                    throw new IllegalStateException("Reserved NMR transaction-line identity field.");
                }
                if (marked.contains(line.path("lineId").asText()))
                {
                    ((ObjectNode) line).put(IDENTITY_FIELD, true);
                }
            }
        }
        return copy;
    }
}
