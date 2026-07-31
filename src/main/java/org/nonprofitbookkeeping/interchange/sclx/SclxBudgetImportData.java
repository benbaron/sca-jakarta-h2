package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict, non-mutating projection of the governed standard SCLX budget section. */
final class SclxBudgetImportData
{
    private final List<BudgetValue> budgets;

    private SclxBudgetImportData(List<BudgetValue> budgets)
    {
        this.budgets = List.copyOf(budgets);
    }

    static SclxBudgetImportData parse(JsonNode value)
    {
        if (value == null || value.isMissingNode() || value.isNull())
        {
            return new SclxBudgetImportData(List.of());
        }
        if (!value.isArray())
        {
            throw new IllegalStateException("SCLX budgets must be an array.");
        }
        List<BudgetValue> budgets = new ArrayList<>();
        Set<Integer> activeYears = new HashSet<>();
        for (int budgetIndex = 0; budgetIndex < value.size(); budgetIndex++)
        {
            JsonNode budget = value.get(budgetIndex);
            String path = "$.budgets[" + budgetIndex + "]";
            requireObject(budget, path);
            String externalId = text(budget, "budgetId", path);
            String name = boundedText(budget, "name", path, 200);
            int fiscalYear = integer(budget, "fiscalYear", path);
            if (fiscalYear < 1900 || fiscalYear > 9999)
            {
                throw new IllegalStateException(path + ".fiscalYear must be a four-digit year.");
            }
            String version = boundedText(budget, "version", path, 64);
            boolean active = requiredBoolean(budget, "active", path);
            if (active && !activeYears.add(fiscalYear))
            {
                throw new IllegalStateException(
                        "SCLX contains more than one active budget for fiscal year " + fiscalYear + ".");
            }

            JsonNode linesNode = budget.get("lines");
            if (linesNode == null || !linesNode.isArray())
            {
                throw new IllegalStateException(path + ".lines must be an array.");
            }
            List<LineValue> lines = new ArrayList<>();
            Set<String> scopes = new HashSet<>();
            for (int lineIndex = 0; lineIndex < linesNode.size(); lineIndex++)
            {
                JsonNode line = linesNode.get(lineIndex);
                String linePath = path + ".lines[" + lineIndex + "]";
                requireObject(line, linePath);
                String lineId = text(line, "lineId", linePath);
                String accountId = optionalText(line, "accountId");
                if (accountId != null)
                {
                    throw new IllegalStateException(
                            linePath + ".accountId cannot be preserved by the normalized budget model.");
                }
                String fundId = optionalText(line, "fundId");
                String categoryCode = boundedText(line, "categoryCode", linePath, 64);
                String monthText = optionalText(line, "periodMonth");
                YearMonth periodMonth = monthText == null ? null : parseMonth(monthText, linePath);
                if (periodMonth != null && periodMonth.getYear() != fiscalYear)
                {
                    throw new IllegalStateException(
                            linePath + ".periodMonth must be within fiscal year " + fiscalYear + ".");
                }
                BigDecimal amount = decimal(line, "amount", linePath);
                if (amount.scale() > 4)
                {
                    throw new IllegalStateException(linePath + ".amount supports at most four decimal places.");
                }
                if (amount.setScale(4, RoundingMode.UNNECESSARY).precision() > 19)
                {
                    throw new IllegalStateException(linePath + ".amount exceeds DECIMAL(19,4).");
                }
                String scope = categoryCode + "\u0000" + Objects.toString(fundId, "")
                        + "\u0000" + Objects.toString(periodMonth, "");
                if (!scopes.add(scope))
                {
                    throw new IllegalStateException(
                            linePath + " duplicates a category, fund, and period scope in " + externalId + ".");
                }
                lines.add(new LineValue(lineId, fundId, categoryCode, periodMonth, amount));
            }
            lines.sort(Comparator.comparing(LineValue::externalId));
            budgets.add(new BudgetValue(
                    externalId, name, fiscalYear, version, active, List.copyOf(lines)));
        }
        budgets.sort(Comparator.comparing(BudgetValue::externalId));
        return new SclxBudgetImportData(budgets);
    }

    List<BudgetValue> budgets()
    {
        return budgets;
    }

    private static void requireObject(JsonNode value, String path)
    {
        if (value == null || !value.isObject())
        {
            throw new IllegalStateException(path + " must be an object.");
        }
    }

    private static String boundedText(JsonNode value, String field, String path, int maxLength)
    {
        String text = text(value, field, path);
        if (text.length() > maxLength)
        {
            throw new IllegalStateException(path + "." + field + " exceeds " + maxLength + " characters.");
        }
        return text;
    }

    private static String text(JsonNode value, String field, String path)
    {
        String text = optionalText(value, field);
        if (text == null)
        {
            throw new IllegalStateException(path + "." + field + " must be a nonblank string.");
        }
        return text;
    }

    private static String optionalText(JsonNode value, String field)
    {
        JsonNode node = value.get(field);
        return node == null || node.isNull() || !node.isTextual() || node.textValue().isBlank()
                ? null
                : node.textValue().trim();
    }

    private static int integer(JsonNode value, String field, String path)
    {
        JsonNode node = value.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt())
        {
            throw new IllegalStateException(path + "." + field + " must be an integer.");
        }
        return node.intValue();
    }

    private static boolean requiredBoolean(JsonNode value, String field, String path)
    {
        JsonNode node = value.get(field);
        if (node == null || !node.isBoolean())
        {
            throw new IllegalStateException(path + "." + field + " must be a boolean.");
        }
        return node.booleanValue();
    }

    private static BigDecimal decimal(JsonNode value, String field, String path)
    {
        JsonNode node = value.get(field);
        if (node == null || (!node.isTextual() && !node.isNumber()))
        {
            throw new IllegalStateException(path + "." + field + " must be a decimal value.");
        }
        try
        {
            return new BigDecimal(node.asText());
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalStateException(path + "." + field + " must be a decimal value.", ex);
        }
    }

    private static YearMonth parseMonth(String value, String path)
    {
        try
        {
            return YearMonth.parse(value);
        }
        catch (RuntimeException ex)
        {
            throw new IllegalStateException(path + ".periodMonth must use YYYY-MM.", ex);
        }
    }

    record BudgetValue(
            String externalId,
            String name,
            int fiscalYear,
            String version,
            boolean active,
            List<LineValue> lines)
    {
        BudgetValue
        {
            lines = List.copyOf(lines);
        }
    }

    record LineValue(
            String externalId,
            String fundId,
            String categoryCode,
            YearMonth periodMonth,
            BigDecimal amount)
    {
    }
}
