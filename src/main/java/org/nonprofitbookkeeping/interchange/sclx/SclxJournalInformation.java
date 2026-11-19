package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.nonprofitbookkeeping.model.TransactionInformation;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Versioned informational journal-header facts, never bank-cleared authority. */
final class SclxJournalInformation
{
    static final String KEY = "journalInformation";
    private static final String IDENTITY = "_scaJournalInformation";

    private SclxJournalInformation()
    {
    }

    static Map<String, TransactionInformation> parse(JsonNode root)
    {
        JsonNode extension = root.path("extensions").path("scaJakartaH2").path(KEY);
        if (extension.isMissingNode() || extension.isNull())
        {
            return Map.of();
        }
        fields(extension, Set.of("version", "transactions"));
        if (!extension.path("version").isIntegralNumber() || !extension.path("version").canConvertToInt()
                || extension.path("version").intValue() != 1 || !extension.path("transactions").isArray())
        {
            throw new IllegalStateException("journalInformation requires version 1 and transactions.");
        }
        Set<String> transactionIds = new HashSet<>();
        root.path("transactions").forEach(transaction -> transactionIds.add(transaction.path("transactionId").asText()));
        Map<String, TransactionInformation> result = new LinkedHashMap<>();
        for (JsonNode value : extension.path("transactions"))
        {
            fields(value, Set.of("transactionId", "transferOrCheckReference", "statementDate", "incomingDate"));
            String id = text(value, "transactionId");
            if (id == null || id.isBlank() || !transactionIds.contains(id))
            {
                throw new IllegalStateException("journalInformation transaction does not resolve: " + id);
            }
            String reference = text(value, "transferOrCheckReference");
            if (reference != null && reference.length() > 200)
            {
                throw new IllegalStateException("journalInformation reference exceeds 200 characters.");
            }
            TransactionInformation information;
            try
            {
                information = new TransactionInformation(reference, date(value, "statementDate"), date(value, "incomingDate"));
            }
            catch (RuntimeException ex)
            {
                throw new IllegalStateException("Invalid journalInformation transaction " + id + ": " + ex.getMessage(), ex);
            }
            if (information.isEmpty() || result.putIfAbsent(id, information) != null)
            {
                throw new IllegalStateException("Empty or duplicate journalInformation transaction: " + id);
            }
        }
        return Map.copyOf(result);
    }

    static JsonNode identityDocument(JsonNode root)
    {
        Map<String, TransactionInformation> information = parse(root);
        JsonNode copy = root.deepCopy();
        for (JsonNode transaction : copy.path("transactions"))
        {
            if (transaction.has(IDENTITY))
            {
                throw new IllegalStateException("Reserved journal-information identity field.");
            }
            var facts = information.get(transaction.path("transactionId").asText());
            if (facts != null)
            {
                ObjectNode value = ((ObjectNode) transaction).putObject(IDENTITY);
                value.put("transferOrCheckReference", facts.transferOrCheckReference());
                value.put("statementDate", facts.statementDate() == null ? null : facts.statementDate().toString());
                value.put("incomingDate", facts.incomingDate() == null ? null : facts.incomingDate().toString());
            }
        }
        return copy;
    }

    static Map<String, Object> export(String transactionId, TransactionInformation information)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transactionId", transactionId);
        if (information.transferOrCheckReference() != null)
        {
            result.put("transferOrCheckReference", information.transferOrCheckReference());
        }
        if (information.statementDate() != null)
        {
            result.put("statementDate", information.statementDate().toString());
        }
        if (information.incomingDate() != null)
        {
            result.put("incomingDate", information.incomingDate().toString());
        }
        return Map.copyOf(result);
    }

    private static String text(JsonNode object, String key)
    {
        JsonNode value = object.get(key);
        if (value == null || value.isNull())
        {
            return null;
        }
        if (!value.isTextual())
        {
            throw new IllegalStateException("journalInformation " + key + " must be text or null.");
        }
        return value.textValue();
    }

    private static LocalDate date(JsonNode object, String key)
    {
        String value = text(object, key);
        return value == null ? null : LocalDate.parse(value);
    }

    private static void fields(JsonNode value, Set<String> allowed)
    {
        if (!value.isObject())
        {
            throw new IllegalStateException("journalInformation requires objects.");
        }
        value.fieldNames().forEachRemaining(key ->
        {
            if (!allowed.contains(key))
            {
                throw new IllegalStateException("Unknown journalInformation field: " + key);
            }
        });
    }
}
