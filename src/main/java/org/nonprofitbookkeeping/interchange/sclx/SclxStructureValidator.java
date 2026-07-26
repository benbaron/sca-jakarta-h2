package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Validates bounded SCLX section counts, portable identities, and core references without mutating H2. */
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

        JsonNode accounts = array(root, "chartOfAccounts", errors);
        JsonNode funds = array(root, "funds", errors);
        JsonNode budgets = array(root, "budgets", errors);
        JsonNode transactions = array(root, "transactions", errors);

        Set<String> accountIds = identities(accounts, "accountId", "$.chartOfAccounts", errors);
        Set<String> fundIds = identities(funds, "fundId", "$.funds", errors);
        Set<String> budgetIds = identities(budgets, "budgetId", "$.budgets", errors);
        identities(transactions, "transactionId", "$.transactions", errors);

        long transactionLines = 0L;
        for (int index = 0; index < transactions.size(); index++)
        {
            JsonNode transaction = transactions.get(index);
            String path = "$.transactions[" + index + "]";
            if (!transaction.isObject())
            {
                errors.add(path + " must be an object");
                continue;
            }
            JsonNode lines = transaction.get("lines");
            if (lines == null || !lines.isArray())
            {
                errors.add(path + ".lines must be an array");
                continue;
            }
            transactionLines += lines.size();
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
                requireUniqueIdentity(line, "lineId", linePath, lineIds, errors);
                requireReference(line, "accountId", linePath, accountIds, errors);
                optionalReference(line, "fundId", linePath, fundIds, errors);
                optionalReference(line, "budgetId", linePath, budgetIds, errors);
            }
        }

        for (int index = 0; index < budgets.size(); index++)
        {
            JsonNode budget = budgets.get(index);
            String path = "$.budgets[" + index + "]";
            if (!budget.isObject())
            {
                errors.add(path + " must be an object");
                continue;
            }
            optionalReference(budget, "fundId", path, fundIds, errors);
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
                        continue;
                    }
                    optionalReference(line, "accountId", linePath, accountIds, errors);
                }
            }
        }

        long total = accounts.size() + funds.size() + budgets.size() + transactions.size() + transactionLines;
        SclxSectionCounts counts = new SclxSectionCounts(
                accounts.size(), funds.size(), budgets.size(), transactions.size(), transactionLines, total);
        enforceLimits(counts, errors);
        warnUnknownRootSections(root, warnings);
        return new SclxStructureValidation(counts, errors, warnings);
    }

    private static JsonNode array(JsonNode root, String field, List<String> errors)
    {
        JsonNode value = root.get(field);
        if (value == null)
        {
            return root.arrayNode();
        }
        if (!value.isArray())
        {
            errors.add("$." + field + " must be an array");
            return root.arrayNode();
        }
        return value;
    }

    private static Set<String> identities(JsonNode array, String field, String path, List<String> errors)
    {
        Set<String> identities = new HashSet<>();
        for (int index = 0; index < array.size(); index++)
        {
            JsonNode item = array.get(index);
            String itemPath = path + "[" + index + "]";
            if (!item.isObject())
            {
                errors.add(itemPath + " must be an object");
                continue;
            }
            requireUniqueIdentity(item, field, itemPath, identities, errors);
        }
        return identities;
    }

    private static void requireUniqueIdentity(JsonNode item, String field, String path,
            Set<String> identities, List<String> errors)
    {
        JsonNode value = item.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank())
        {
            errors.add(path + "." + field + " is required and must be a nonblank string");
            return;
        }
        String identity = value.textValue();
        if (identity.codePointCount(0, identity.length()) > MAX_ID_CODE_POINTS)
        {
            errors.add(path + "." + field + " exceeds " + MAX_ID_CODE_POINTS + " Unicode code points");
        }
        if (!identities.add(identity))
        {
            errors.add(path + "." + field + " duplicates portable identity " + identity);
        }
    }

    private static void requireReference(JsonNode item, String field, String path,
            Set<String> targets, List<String> errors)
    {
        JsonNode value = item.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank())
        {
            errors.add(path + "." + field + " is required and must be a nonblank string");
            return;
        }
        if (!targets.contains(value.textValue()))
        {
            errors.add(path + "." + field + " does not resolve: " + value.textValue());
        }
    }

    private static void optionalReference(JsonNode item, String field, String path,
            Set<String> targets, List<String> errors)
    {
        JsonNode value = item.get(field);
        if (value == null || value.isNull())
        {
            return;
        }
        if (!value.isTextual() || value.textValue().isBlank())
        {
            errors.add(path + "." + field + " must be a nonblank string when present");
        }
        else if (!targets.contains(value.textValue()))
        {
            errors.add(path + "." + field + " does not resolve: " + value.textValue());
        }
    }

    private static void enforceLimits(SclxSectionCounts counts, List<String> errors)
    {
        if (counts.accounts() > MAX_ACCOUNTS)
        {
            errors.add("SCLX account count exceeds " + MAX_ACCOUNTS);
        }
        if (counts.funds() > MAX_FUNDS)
        {
            errors.add("SCLX fund count exceeds " + MAX_FUNDS);
        }
        if (counts.transactions() > MAX_TRANSACTIONS)
        {
            errors.add("SCLX transaction count exceeds " + MAX_TRANSACTIONS);
        }
        if (counts.transactionLines() > MAX_TRANSACTION_LINES)
        {
            errors.add("SCLX transaction-line count exceeds " + MAX_TRANSACTION_LINES);
        }
        if (counts.totalEntities() > MAX_TOTAL_ENTITIES)
        {
            errors.add("SCLX total entity count exceeds " + MAX_TOTAL_ENTITIES);
        }
    }

    private static void warnUnknownRootSections(JsonNode root, List<String> warnings)
    {
        Set<String> known = Set.of(
                "format", "version", "exportedAt", "compatibility", "organization", "reportingPeriod",
                "chartOfAccounts", "funds", "budgets", "people", "bankAccounts", "officeAssignments",
                "committeeMemberships", "events", "documents", "transactions", "bankingItems",
                "outstandingItems", "otherAssetItems", "supplementalItems", "assets", "supplies",
                "bankStatementImports", "extensions");
        root.fieldNames().forEachRemaining(field ->
        {
            if (!known.contains(field))
            {
                warnings.add("Unknown bounded SCLX root field: " + field);
            }
        });
    }
}
