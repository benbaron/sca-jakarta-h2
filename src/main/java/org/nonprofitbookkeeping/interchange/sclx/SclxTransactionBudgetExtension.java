package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Versioned category-code relationships for canonical transaction splits. */
final class SclxTransactionBudgetExtension
{
    static final String KEY = "transactionBudgets";
    private static final String IDENTITY_FIELD = "_scaBudgetCategoryCode";

    private SclxTransactionBudgetExtension()
    {
    }

    static Map<String, String> parse(JsonNode root)
    {
        JsonNode extension = root.path("extensions").path("scaJakartaH2").path(KEY);
        if (extension.isMissingNode() || extension.isNull())
        {
            return Map.of();
        }
        requireFields(extension, Set.of("version", "lines"));
        if (!extension.path("version").isIntegralNumber() || !extension.path("version").canConvertToInt()
                || extension.path("version").intValue() != 1
                || !extension.path("lines").isArray())
        {
            throw new IllegalStateException("transactionBudgets requires version 1 and a lines array.");
        }
        Map<String, JsonNode> postingLines = new LinkedHashMap<>();
        for (JsonNode transaction : root.path("transactions"))
        {
            for (JsonNode line : transaction.path("lines"))
            {
                postingLines.put(line.path("lineId").asText(), line);
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode link : extension.path("lines"))
        {
            requireFields(link, Set.of("lineId", "categoryCode"));
            String lineId = text(link, "lineId");
            String code = text(link, "categoryCode");
            if (code.length() > 64 || result.putIfAbsent(lineId, code) != null)
            {
                throw new IllegalStateException("Invalid or duplicate transaction Budget relationship: " + lineId);
            }
            JsonNode line = postingLines.get(lineId);
            if (line == null)
            {
                throw new IllegalStateException("Transaction Budget line does not resolve: " + lineId);
            }
            try
            {
                if (new BigDecimal(line.path("debit").asText()).signum() == 0
                        && new BigDecimal(line.path("credit").asText()).signum() == 0)
                {
                    throw new IllegalStateException("A zero-value line cannot carry a Budget category: " + lineId);
                }
            }
            catch (NumberFormatException ex)
            {
                throw new IllegalStateException("Budget relationship requires a valid posting line: " + lineId, ex);
            }
        }
        return Map.copyOf(result);
    }

    /** Internal preview copy: include extension facts in existing transaction/line conflict hashes. */
    static JsonNode identityDocument(JsonNode root)
    {
        Map<String, String> codes = parse(root);
        JsonNode copy = root.deepCopy();
        for (JsonNode transaction : copy.path("transactions"))
        {
            for (JsonNode line : transaction.path("lines"))
            {
                if (line.has(IDENTITY_FIELD))
                {
                    throw new IllegalStateException("Reserved transaction-line identity field.");
                }
                String code = codes.get(line.path("lineId").asText());
                if (code != null)
                {
                    ((ObjectNode) line).put(IDENTITY_FIELD, code);
                }
            }
        }
        return copy;
    }

    private static void requireFields(JsonNode node, Set<String> expected)
    {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!node.isObject() || !actual.equals(expected))
        {
            throw new IllegalStateException("transactionBudgets object must contain exactly " + expected);
        }
    }

    private static String text(JsonNode node, String field)
    {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank())
        {
            throw new IllegalStateException("transactionBudgets " + field + " must be nonblank text.");
        }
        return value.textValue().trim();
    }
}
