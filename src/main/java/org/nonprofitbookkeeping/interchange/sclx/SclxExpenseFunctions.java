package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.nonprofitbookkeeping.model.AccountClassification;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ExpenseFunction;
import java.math.BigDecimal;
import java.util.*;

/** Sparse canonical expense purpose; absence retains legacy unclassified identities. */
final class SclxExpenseFunctions
{
    static final String KEY = "expenseFunctions";
    private static final String IDENTITY_FIELD = "_scaExpenseFunction";

    private SclxExpenseFunctions()
    {
    }

    static Map<String, ExpenseFunction> parse(JsonNode root)
    {
        JsonNode extension = root.path("extensions").path("scaJakartaH2").path(KEY);
        if (extension.isMissingNode())
        {
            return Map.of();
        }
        requireFields(extension, Set.of("version", "lines"));
        if (!extension.path("version").isIntegralNumber() || !extension.path("version").canConvertToInt()
                || extension.path("version").intValue() != 1 || !extension.path("lines").isArray())
        {
            throw new IllegalStateException("expenseFunctions requires version 1 and a lines array.");
        }
        Map<String, JsonNode> accounts = new HashMap<>(), lines = new HashMap<>();
        for (JsonNode account : root.path("chartOfAccounts"))
        {
            accounts.put(account.path("accountId").asText(), account);
        }
        for (JsonNode transaction : root.path("transactions"))
        {
            for (JsonNode line : transaction.path("lines"))
            {
                if (lines.putIfAbsent(line.path("lineId").asText(), line) != null)
                {
                    throw new IllegalStateException("Ambiguous expense-function line identity.");
                }
            }
        }
        Map<String, ExpenseFunction> result = new LinkedHashMap<>();
        for (JsonNode fact : extension.path("lines"))
        {
            requireFields(fact, Set.of("lineId", "code"));
            String id = text(fact, "lineId"), code = text(fact, "code");
            ExpenseFunction function;
            try
            {
                function = ExpenseFunction.valueOf(code);
            }
            catch (IllegalArgumentException ex)
            {
                throw new IllegalStateException("Expense function must be AR, OA or FR: " + code, ex);
            }
            JsonNode line = lines.get(id);
            JsonNode account = line == null ? null : accounts.get(line.path("accountId").asText());
            if (result.putIfAbsent(id, function) != null || account == null
                    || AccountClassification.parsePortableType(account.path("type").asText()).type() != AccountType.EXPENSE)
            {
                throw new IllegalStateException("Expense function requires a unique resolved EXPENSE line: " + id);
            }
            try
            {
                BigDecimal debit = new BigDecimal(line.path("debit").asText()), credit = new BigDecimal(line.path("credit").asText());
                if (debit.signum() < 0 || credit.signum() < 0 || (debit.signum() == 0) == (credit.signum() == 0))
                {
                    throw new IllegalStateException("Expense function requires a nonzero one-sided line: " + id);
                }
            }
            catch (NumberFormatException ex)
            {
                throw new IllegalStateException("Expense function requires valid posting amounts: " + id, ex);
            }
        }
        Map<String, JsonNode> transactions = new HashMap<>();
        root.path("transactions").forEach(t -> transactions.put(t.path("transactionId").asText(), t));
        for (JsonNode inverse : root.path("transactions"))
        {
            if (!"REVERSAL".equals(inverse.path("correctionType").asText()))
            {
                continue;
            }
            JsonNode source = transactions.get(inverse.path("correctionOfTransactionId").asText());
            if (source == null)
            {
                throw new IllegalStateException("Expense-function reversal source does not resolve.");
            }
            List<ExpenseFunction> before = new ArrayList<>(), after = new ArrayList<>();
            source.path("lines").forEach(l -> before.add(result.get(l.path("lineId").asText())));
            inverse.path("lines").forEach(l -> after.add(result.get(l.path("lineId").asText())));
            if ((before.stream().anyMatch(Objects::nonNull) || after.stream().anyMatch(Objects::nonNull)) && !before.equals(after))
            {
                throw new IllegalStateException("Expense-function reversal must retain exact source line purposes.");
            }
        }
        return Map.copyOf(result);
    }

    static JsonNode identityDocument(JsonNode root)
    {
        Map<String, ExpenseFunction> functions = parse(root);
        JsonNode copy = root.deepCopy();
        for (JsonNode transaction : copy.path("transactions"))
        {
            for (JsonNode line : transaction.path("lines"))
            {
                if (line.has(IDENTITY_FIELD))
                {
                    throw new IllegalStateException("Reserved expense-function identity field.");
                }
                ExpenseFunction function = functions.get(line.path("lineId").asText());
                if (function != null)
                {
                    ((ObjectNode)line).put(IDENTITY_FIELD, function.name());
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
            throw new IllegalStateException("expenseFunctions object requires exactly " + expected);
        }
    }

    private static String text(JsonNode node, String key)
    {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.textValue().isBlank())
        {
            throw new IllegalStateException("expenseFunctions " + key + " requires nonblank text.");
        }
        return value.textValue();
    }
}
