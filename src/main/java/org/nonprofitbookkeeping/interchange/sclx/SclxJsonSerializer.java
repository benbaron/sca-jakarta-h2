package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Serializes the governed SCLX 1.3 DTO graph to deterministic UTF-8 JSON bytes. */
public final class SclxJsonSerializer
{
    private static final Comparator<SclxExportDocument.Transaction> TRANSACTION_ORDER = Comparator
            .comparing(SclxExportDocument.Transaction::transactionDate)
            .thenComparing(SclxExportDocument.Transaction::transactionId);

    private final ObjectMapper mapper;
    private final SclxExportDocumentValidator validator;

    public SclxJsonSerializer()
    {
        this(new ObjectMapper(), new SclxExportDocumentValidator());
    }

    SclxJsonSerializer(ObjectMapper mapper, SclxExportDocumentValidator validator)
    {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public byte[] serialize(SclxExportDocument document)
    {
        validator.validate(Objects.requireNonNull(document, "document"));
        ObjectNode root = mapper.createObjectNode();
        root.put("format", document.format());
        root.put("version", document.version());
        root.put("exportedAt", DateTimeFormatter.ISO_INSTANT.format(document.exportedAt()));
        root.set("organization", organization(document.organization()));
        root.set("chartOfAccounts", accounts(document.chartOfAccounts()));
        root.set("funds", funds(document.funds()));
        root.set("budgets", budgets(document.budgets()));
        root.set("transactions", transactions(document.transactions()));
        root.set("extensions", extensions(document.extensions()));

        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        try
        {
            String json = mapper.writer(printer).writeValueAsString(root);
            if (!json.endsWith("\n"))
            {
                json += "\n";
            }
            return json.getBytes(StandardCharsets.UTF_8);
        }
        catch (JsonProcessingException ex)
        {
            throw new IllegalStateException("Could not serialize SCLX 1.3 JSON.", ex);
        }
    }

    private ObjectNode organization(SclxExportDocument.Organization organization)
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("organizationId", organization.organizationId());
        node.put("code", organization.code());
        node.put("name", organization.name());
        node.put("baseCurrency", organization.baseCurrency());
        node.put("fiscalYearStart", organization.fiscalYearStart().toString());
        return node;
    }

    private ArrayNode accounts(List<SclxExportDocument.Account> accounts)
    {
        ArrayNode array = mapper.createArrayNode();
        accounts.stream()
                .sorted(Comparator.comparing(SclxExportDocument.Account::accountId))
                .forEach(account -> {
                    ObjectNode node = array.addObject();
                    node.put("accountId", account.accountId());
                    node.put("code", account.code());
                    node.put("name", account.name());
                    node.put("type", account.type());
                    optionalText(node, "subtype", account.subtype());
                    node.put("increaseSide", account.increaseSide());
                    optionalText(node, "parentAccountId", account.parentAccountId());
                    node.put("currency", account.currency());
                    node.put("openingBalance", decimal(account.openingBalance()));
                    node.put("posting", account.posting());
                    node.put("active", account.active());
                });
        return array;
    }

    private ArrayNode funds(List<SclxExportDocument.Fund> funds)
    {
        ArrayNode array = mapper.createArrayNode();
        funds.stream()
                .sorted(Comparator.comparing(SclxExportDocument.Fund::fundId))
                .forEach(fund -> {
                    ObjectNode node = array.addObject();
                    node.put("fundId", fund.fundId());
                    node.put("code", fund.code());
                    node.put("name", fund.name());
                    node.put("type", fund.type());
                    optionalText(node, "parentFundId", fund.parentFundId());
                    node.put("active", fund.active());
                    optionalDate(node, "effectiveFrom", fund.effectiveFrom());
                    optionalDate(node, "effectiveTo", fund.effectiveTo());
                    optionalText(node, "restrictionText", fund.restrictionText());
                });
        return array;
    }

    private ArrayNode budgets(List<SclxExportDocument.Budget> budgets)
    {
        ArrayNode array = mapper.createArrayNode();
        budgets.stream()
                .sorted(Comparator.comparing(SclxExportDocument.Budget::budgetId))
                .forEach(budget -> {
                    ObjectNode node = array.addObject();
                    node.put("budgetId", budget.budgetId());
                    node.put("name", budget.name());
                    node.put("fiscalYear", budget.fiscalYear());
                    node.put("version", budget.version());
                    node.put("active", budget.active());
                    ArrayNode lines = node.putArray("lines");
                    budget.lines().stream()
                            .sorted(Comparator.comparing(SclxExportDocument.BudgetLine::lineId))
                            .forEach(line -> {
                                ObjectNode lineNode = lines.addObject();
                                lineNode.put("lineId", line.lineId());
                                optionalText(lineNode, "accountId", line.accountId());
                                optionalText(lineNode, "fundId", line.fundId());
                                lineNode.put("categoryCode", line.categoryCode());
                                optionalText(lineNode, "periodMonth", line.periodMonth());
                                lineNode.put("amount", decimal(line.amount()));
                            });
                });
        return array;
    }

    private ArrayNode transactions(List<SclxExportDocument.Transaction> transactions)
    {
        ArrayNode array = mapper.createArrayNode();
        transactions.stream()
                .sorted(TRANSACTION_ORDER)
                .forEach(transaction -> {
                    ObjectNode node = array.addObject();
                    node.put("transactionId", transaction.transactionId());
                    node.put("transactionDate", transaction.transactionDate().toString());
                    node.put("description", transaction.description());
                    optionalText(node, "reference", transaction.reference());
                    node.put("status", transaction.status());
                    optionalText(node, "correctionType", transaction.correctionType());
                    optionalText(node, "correctionOfTransactionId", transaction.correctionOfTransactionId());
                    ArrayNode lines = node.putArray("lines");
                    transaction.lines().stream()
                            .sorted(Comparator.comparing(SclxExportDocument.TransactionLine::lineId))
                            .forEach(line -> {
                                ObjectNode lineNode = lines.addObject();
                                lineNode.put("lineId", line.lineId());
                                lineNode.put("accountId", line.accountId());
                                optionalText(lineNode, "fundId", line.fundId());
                                optionalText(lineNode, "activityId", line.activityId());
                                optionalText(lineNode, "counterpartyId", line.counterpartyId());
                                lineNode.put("debit", decimal(line.debit()));
                                lineNode.put("credit", decimal(line.credit()));
                                optionalText(lineNode, "memo", line.memo());
                            });
                });
        return array;
    }

    private ObjectNode extensions(SclxExportDocument.Extensions extensions)
    {
        ObjectNode node = mapper.createObjectNode();
        node.put("version", extensions.version());
        node.set("scaJakartaH2", extensionMap(extensions.scaJakartaH2()));
        return node;
    }

    private ObjectNode extensionMap(Map<String, Object> values)
    {
        ObjectNode node = mapper.createObjectNode();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> node.set(entry.getKey(), extensionValue(entry.getValue())));
        return node;
    }

    private JsonNode extensionValue(Object value)
    {
        if (value == null)
        {
            return mapper.getNodeFactory().nullNode();
        }
        if (value instanceof String text)
        {
            return mapper.getNodeFactory().textNode(text);
        }
        if (value instanceof Boolean flag)
        {
            return mapper.getNodeFactory().booleanNode(flag);
        }
        if (value instanceof BigDecimal decimal)
        {
            return mapper.getNodeFactory().textNode(decimal(decimal));
        }
        if (value instanceof BigInteger integer)
        {
            return mapper.getNodeFactory().numberNode(integer);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
        {
            return mapper.getNodeFactory().numberNode(((Number) value).longValue());
        }
        if (value instanceof LocalDate date)
        {
            return mapper.getNodeFactory().textNode(date.toString());
        }
        if (value instanceof YearMonth month)
        {
            return mapper.getNodeFactory().textNode(month.toString());
        }
        if (value instanceof Instant instant)
        {
            return mapper.getNodeFactory().textNode(DateTimeFormatter.ISO_INSTANT.format(instant));
        }
        if (value instanceof Enum<?> enumeration)
        {
            return mapper.getNodeFactory().textNode(enumeration.name());
        }
        if (value instanceof Map<?, ?> map)
        {
            List<Map.Entry<String, Object>> entries = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                if (!(entry.getKey() instanceof String key))
                {
                    throw new IllegalArgumentException("SCLX extension map keys must be strings");
                }
                entries.add(new AbstractMap.SimpleImmutableEntry<>(key, entry.getValue()));
            }
            ObjectNode node = mapper.createObjectNode();
            entries.stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> node.set(entry.getKey(), extensionValue(entry.getValue())));
            return node;
        }
        if (value instanceof Collection<?> collection)
        {
            ArrayNode array = mapper.createArrayNode();
            collection.forEach(element -> array.add(extensionValue(element)));
            return array;
        }
        if (value instanceof Object[] arrayValues)
        {
            ArrayNode array = mapper.createArrayNode();
            for (Object element : arrayValues)
            {
                array.add(extensionValue(element));
            }
            return array;
        }
        throw new IllegalArgumentException(
                "Unsupported deterministic SCLX extension value type: " + value.getClass().getName());
    }

    private static String decimal(BigDecimal value)
    {
        BigDecimal normalized = Objects.requireNonNull(value, "decimal value");
        if (normalized.signum() == 0)
        {
            return "0";
        }
        normalized = normalized.stripTrailingZeros();
        if (normalized.scale() < 0)
        {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
    }

    private static void optionalText(ObjectNode node, String field, String value)
    {
        if (value == null)
        {
            node.putNull(field);
        }
        else
        {
            node.put(field, value);
        }
    }

    private static void optionalDate(ObjectNode node, String field, LocalDate value)
    {
        if (value == null)
        {
            node.putNull(field);
        }
        else
        {
            node.put(field, value.toString());
        }
    }
}
