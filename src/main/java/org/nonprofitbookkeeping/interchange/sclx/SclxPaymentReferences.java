package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.nonprofitbookkeeping.service.PaymentReference;
import org.nonprofitbookkeeping.service.PaymentReferences;
import org.nonprofitbookkeeping.model.TxnSplit;
import java.time.LocalDate;
import java.util.*;

/** Versioned bank-split payment facts; line IDs carry account/transaction ownership. */
final class SclxPaymentReferences
{
    static final String KEY = "paymentReferences";
    private static final String IDENTITY = "_scaPaymentReference";

    static Map<String, PaymentReference> parse(JsonNode root)
    {
        JsonNode extension = root.path("extensions").path("scaJakartaH2").path(KEY);
        if (extension.isMissingNode() || extension.isNull())
        {
            return Map.of();
        }
        fields(extension, Set.of("version", "lines"));
        if (!extension.path("version").isIntegralNumber() || !extension.path("version").canConvertToInt()
                || extension.path("version").intValue() != 1
                || !extension.path("lines").isArray())
        {
            throw new IllegalStateException("paymentReferences requires version 1 and lines.");
        }
        Map<String, String> accountTypes = new LinkedHashMap<>();
        for (JsonNode account : root.path("chartOfAccounts"))
        {
            accountTypes.put(account.path("accountId").asText(), account.path("type").asText());
        }
        Map<String, JsonNode> lines = new LinkedHashMap<>();
        for (JsonNode txn : root.path("transactions"))
        {
            for (JsonNode line : txn.path("lines"))
            {
                lines.put(line.path("lineId").asText(), line);
            }
        }
        Map<String, PaymentReference> result = new LinkedHashMap<>();
        for (JsonNode fact : extension.path("lines"))
        {
            fields(fact, Set.of("lineId", "method", "reference", "issuedOn", "deliveredOn"));
            String id = text(fact, "lineId");
            try
            {
                PaymentReference value = new PaymentReference(PaymentReference.Method.valueOf(text(fact, "method")),
                        nullableText(fact, "reference"), date(fact, "issuedOn"), date(fact, "deliveredOn"));
                JsonNode line = lines.get(id);
                if (line == null || !"BANK".equals(accountTypes.get(line.path("accountId").asText()))
                        || (new java.math.BigDecimal(line.path("debit").asText()).signum() == 0
                        && new java.math.BigDecimal(line.path("credit").asText()).signum() == 0)
                        || result.putIfAbsent(id, value) != null)
                {
                    throw new IllegalArgumentException("Unresolved, non-bank, zero-value or duplicate payment line: " + id);
                }
            }
            catch (RuntimeException ex)
            {
                throw new IllegalStateException("Invalid paymentReferences line " + id + ": " + ex.getMessage(), ex);
            }
        }
        return Map.copyOf(result);
    }

    static JsonNode identityDocument(JsonNode root)
    {
        Map<String, PaymentReference> values = parse(root);
        JsonNode copy = root.deepCopy();
        for (JsonNode txn : copy.path("transactions"))
        {
            for (JsonNode line : txn.path("lines"))
            {
                if (line.has(IDENTITY))
                {
                    throw new IllegalStateException("Reserved payment identity field.");
                }
                PaymentReference value = values.get(line.path("lineId").asText());
                if (value != null)
                {
                    ObjectNode identity = ((ObjectNode) line).putObject(IDENTITY);
                    identity.put("method", value.method().name());
                    identity.put("reference", value.reference());
                    identity.put("issuedOn", value.issuedOn() == null ? null : value.issuedOn().toString());
                    identity.put("deliveredOn", value.deliveredOn() == null ? null : value.deliveredOn().toString());
                }
            }
        }
        return copy;
    }

    static Map<String, Object> export(TxnSplit split, String id)
    {
        PaymentReference value = PaymentReferences.read(split);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lineId", Objects.requireNonNull(id));
        result.put("method", value.method().name());
        result.put("reference", value.reference());
        result.put("issuedOn", value.issuedOn() == null ? null : value.issuedOn().toString());
        result.put("deliveredOn", value.deliveredOn() == null ? null : value.deliveredOn().toString());
        return Collections.unmodifiableMap(result);
    }

    private static LocalDate date(JsonNode value, String key)
    {
        String text = nullableText(value, key);
        return text == null ? null : LocalDate.parse(text);
    }
    private static String nullableText(JsonNode value, String key)
    {
        if (value.path(key).isNull())
        {
            return null;
        }
        return text(value, key);
    }
    private static String text(JsonNode value, String key)
    {
        if (!value.path(key).isTextual() || value.path(key).asText().isBlank())
        {
            throw new IllegalStateException("paymentReferences " + key + " requires text.");
        }
        return value.path(key).asText();
    }
    private static void fields(JsonNode value, Set<String> expected)
    {
        Set<String> actual = new HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        if (!value.isObject() || !actual.equals(expected))
        {
            throw new IllegalStateException("paymentReferences requires fields " + expected);
        }
    }
}
