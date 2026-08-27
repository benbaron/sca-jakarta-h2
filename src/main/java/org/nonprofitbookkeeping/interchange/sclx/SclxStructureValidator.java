package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validates bounded SCLX identities, counts, and core references without mutating H2. */
public final class SclxStructureValidator
{
    public static final long MAX_TOTAL_ENTITIES = 1_000_000L;
    public static final long MAX_TRANSACTIONS = 250_000L;
    public static final long MAX_TRANSACTION_LINES = 1_000_000L;
    public static final long MAX_ACCOUNTS = 100_000L;
    public static final long MAX_FUNDS = 100_000L;
    public static final int MAX_ID_CODE_POINTS = 160;

    public SclxStructureValidation validate(SclxParsedDocument document)
    {
        Objects.requireNonNull(document, "document");
        JsonNode root = document.root();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        JsonNode accounts = section(root, "chartOfAccounts", errors);
        JsonNode funds = section(root, "funds", errors);
        JsonNode budgets = section(root, "budgets", errors);
        JsonNode transactions = section(root, "transactions", errors);

        Set<String> accountIds = identities(accounts, "accountId", "$.chartOfAccounts", errors);
        Set<String> fundIds = identities(funds, "fundId", "$.funds", errors);
        Set<String> budgetIds = identities(budgets, "budgetId", "$.budgets", errors);
        identities(transactions, "transactionId", "$.transactions", errors);

        validateFunds(funds, fundIds, errors);
        long lineCount = validateTransactions(transactions, accountIds, fundIds, budgetIds, errors);
        validateBudgets(budgets, accountIds, fundIds, errors);
        long total = accounts.size() + funds.size() + budgets.size() + transactions.size() + lineCount;
        SclxSectionCounts counts = new SclxSectionCounts(
                accounts.size(), funds.size(), budgets.size(), transactions.size(), lineCount, total);
        limits(counts, errors);
        unknownRootFields(root, warnings);
        return new SclxStructureValidation(counts, errors, warnings);
    }

    private static JsonNode section(JsonNode root, String name, List<String> errors)
    {
        JsonNode value = root.get(name);
        if (value == null)
        {
            return JsonNodeFactory.instance.arrayNode();
        }
        if (!value.isArray())
        {
            errors.add("$." + name + " must be an array");
            return JsonNodeFactory.instance.arrayNode();
        }
        return value;
    }

    private static Set<String> identities(JsonNode values, String field, String path, List<String> errors)
    {
        Set<String> result = new HashSet<>();
        for (int index = 0; index < values.size(); index++)
        {
            JsonNode value = values.get(index);
            String itemPath = path + "[" + index + "]";
            if (!value.isObject())
            {
                errors.add(itemPath + " must be an object");
            }
            else
            {
                identity(value, field, itemPath, result, errors);
            }
        }
        return result;
    }

    private static void validateFunds(JsonNode funds, Set<String> fundIds, List<String> errors)
    {
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        for (JsonNode fund : funds)
        {
            JsonNode id = fund == null ? null : fund.get("fundId");
            if (fund != null && fund.isObject() && id != null && id.isTextual() && !id.textValue().isBlank())
            {
                byId.putIfAbsent(id.textValue(), fund);
            }
        }

        for (int index = 0; index < funds.size(); index++)
        {
            JsonNode fund = funds.get(index);
            if (!fund.isObject())
            {
                continue;
            }
            String path = "$.funds[" + index + "]";
            reference(fund, "parentFundId", path, fundIds, false, errors);
            JsonNode parentNode = fund.get("parentFundId");
            if (parentNode == null || parentNode.isNull() || !parentNode.isTextual() || parentNode.textValue().isBlank())
            {
                continue;
            }

            boolean active = fund.has("active") && fund.get("active").isBoolean() && fund.get("active").asBoolean();
            Set<String> visited = new HashSet<>();
            String parentId = parentNode.textValue();
            while (parentId != null)
            {
                if (!visited.add(parentId))
                {
                    errors.add(path + ".parentFundId creates a circular fund hierarchy");
                    break;
                }
                JsonNode parent = byId.get(parentId);
                if (parent == null)
                {
                    break;
                }
                if (active && parent.has("active") && parent.get("active").isBoolean()
                        && !parent.get("active").asBoolean())
                {
                    errors.add(path + ".parentFundId places an active fund beneath inactive parent fund " + parentId);
                    break;
                }
                JsonNode next = parent.get("parentFundId");
                parentId = next != null && next.isTextual() && !next.textValue().isBlank()
                        ? next.textValue() : null;
            }
        }
    }

    private static long validateTransactions(JsonNode transactions, Set<String> accounts,
            Set<String> funds, Set<String> budgets, List<String> errors)
    {
        long count = 0L;
        for (int index = 0; index < transactions.size(); index++)
        {
            JsonNode transaction = transactions.get(index);
            if (!transaction.isObject())
            {
                continue;
            }
            String path = "$.transactions[" + index + "]";
            JsonNode lines = transaction.get("lines");
            if (lines == null || !lines.isArray())
            {
                errors.add(path + ".lines must be an array");
                continue;
            }
            count += lines.size();
            Set<String> lineIds = new HashSet<>();
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++)
            {
                JsonNode line = lines.get(lineIndex);
                String linePath = path + ".lines[" + lineIndex + "]";
                if (!line.isObject())
                {
                    errors.add(linePath + " must be an object");
                    continue;
                }
                identity(line, "lineId", linePath, lineIds, errors);
                reference(line, "accountId", linePath, accounts, true, errors);
                reference(line, "fundId", linePath, funds, false, errors);
                reference(line, "budgetId", linePath, budgets, false, errors);
            }
        }
        return count;
    }

    private static void validateBudgets(JsonNode budgets, Set<String> accounts,
            Set<String> funds, List<String> errors)
    {
        for (int index = 0; index < budgets.size(); index++)
        {
            JsonNode budget = budgets.get(index);
            if (!budget.isObject())
            {
                continue;
            }
            String path = "$.budgets[" + index + "]";
            reference(budget, "fundId", path, funds, false, errors);
            JsonNode lines = budget.get("lines");
            if (lines != null && !lines.isArray())
            {
                errors.add(path + ".lines must be an array when present");
            }
            else if (lines != null)
            {
                for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++)
                {
                    JsonNode line = lines.get(lineIndex);
                    String linePath = path + ".lines[" + lineIndex + "]";
                    if (!line.isObject())
                    {
                        errors.add(linePath + " must be an object");
                    }
                    else
                    {
                        reference(line, "accountId", linePath, accounts, false, errors);
                    }
                }
            }
        }
    }

    private static void identity(JsonNode item, String field, String path,
            Set<String> values, List<String> errors)
    {
        JsonNode node = item.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank())
        {
            errors.add(path + "." + field + " is required and must be a nonblank string");
            return;
        }
        String value = node.textValue();
        if (value.codePointCount(0, value.length()) > MAX_ID_CODE_POINTS)
        {
            errors.add(path + "." + field + " exceeds " + MAX_ID_CODE_POINTS + " Unicode code points");
        }
        if (!values.add(value))
        {
            errors.add(path + "." + field + " duplicates portable identity " + value);
        }
    }

    private static void reference(JsonNode item, String field, String path,
            Set<String> targets, boolean required, List<String> errors)
    {
        JsonNode node = item.get(field);
        if (node == null || node.isNull())
        {
            if (required)
            {
                errors.add(path + "." + field + " is required");
            }
            return;
        }
        if (!node.isTextual() || node.textValue().isBlank())
        {
            errors.add(path + "." + field + " must be a nonblank string");
        }
        else if (!targets.contains(node.textValue()))
        {
            errors.add(path + "." + field + " does not resolve: " + node.textValue());
        }
    }

    private static void limits(SclxSectionCounts counts, List<String> errors)
    {
        if (counts.accounts() > MAX_ACCOUNTS) errors.add("SCLX account count exceeds " + MAX_ACCOUNTS);
        if (counts.funds() > MAX_FUNDS) errors.add("SCLX fund count exceeds " + MAX_FUNDS);
        if (counts.transactions() > MAX_TRANSACTIONS) errors.add("SCLX transaction count exceeds " + MAX_TRANSACTIONS);
        if (counts.transactionLines() > MAX_TRANSACTION_LINES) errors.add("SCLX transaction-line count exceeds " + MAX_TRANSACTION_LINES);
        if (counts.totalEntities() > MAX_TOTAL_ENTITIES) errors.add("SCLX total entity count exceeds " + MAX_TOTAL_ENTITIES);
    }

    private static void unknownRootFields(JsonNode root, List<String> warnings)
    {
        Set<String> known = Set.of("format", "version", "exportedAt", "compatibility", "organization",
                "reportingPeriod", "chartOfAccounts", "funds", "budgets", "people", "bankAccounts",
                "officeAssignments", "committeeMemberships", "events", "documents", "transactions",
                "bankingItems", "outstandingItems", "otherAssetItems", "supplementalItems", "assets",
                "supplies", "bankStatementImports", "extensions");
        root.fieldNames().forEachRemaining(field ->
        {
            if (!known.contains(field)) warnings.add("Unknown bounded SCLX root field: " + field);
        });
    }
}
