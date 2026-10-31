package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.service.BudgetCategoryAdminService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Complete selected-company category masters; core code references remain legacy compatible. */
final class SclxBudgetCategories
{
    static final String KEY = "budgetCategories", TYPE = "BUDGET_CATEGORY";
    private static final ObjectMapper JSON = new ObjectMapper();

    record Fact(String id, BudgetCategoryAdminService.ImportFact value) { }
    record Data(boolean present, Map<String, Fact> byCode)
    {
        Data { byCode = Map.copyOf(byCode); }
    }

    static String id(String company, UUID uuid)
    {
        return SclxPortableIdentity.identity("budget-category", company, uuid.toString());
    }

    static Map<String, Object> export(String company, List<BudgetCategory> categories)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var category : categories)
        {
            if (category.getCompany() == null || !company.equals(category.getCompany().getCode()))
                throw invalid("category outside selected company");
            var fact = BudgetCategoryAdminService.importFact(category);
            ObjectNode node = node(fact); node.remove("portableId"); node.put("categoryId", id(company, fact.portableId()));
            @SuppressWarnings("unchecked") Map<String, Object> row = JSON.convertValue(node, LinkedHashMap.class);
            rows.add(Collections.unmodifiableMap(row));
        }
        rows.sort(Comparator.comparing(row -> (String)row.get("categoryId")));
        return Map.of("version", 1, "categories", List.copyOf(rows));
    }

    static Data parse(JsonNode root)
    {
        JsonNode ext = root.path("extensions").path("scaJakartaH2").path(KEY);
        if (ext.isMissingNode()) return new Data(false, Map.of());
        fields(ext, Set.of("version", "categories"));
        if (!ext.path("version").isIntegralNumber() || !ext.path("version").canConvertToInt()
                || ext.path("version").intValue() != 1 || !ext.path("categories").isArray())
            throw invalid("requires integer version 1 and categories array");
        Map<String, Fact> facts = new LinkedHashMap<>(); Set<UUID> uuids = new HashSet<>(); Set<String> codes = new HashSet<>();
        for (JsonNode row : ext.path("categories"))
        {
            Fact fact = parseEntry(row);
            if (!uuids.add(fact.value().portableId()) || !codes.add(fact.value().code().toUpperCase(Locale.ROOT)))
                throw invalid("duplicate category UUID or company code");
            facts.put(fact.value().code(), fact);
        }
        Set<String> required = new HashSet<>();
        root.path("budgets").forEach(plan -> plan.path("lines").forEach(line -> required.add(text(line, "categoryCode"))));
        required.addAll(SclxTransactionBudgetExtension.parse(root).values());
        if (!facts.keySet().containsAll(required)) throw invalid("masters must cover every referenced category code");
        return new Data(true, facts);
    }

    static Fact parseEntry(JsonNode row)
    {
        fields(row, Set.of("categoryId", "code", "name", "active", "effectiveFrom", "effectiveTo", "description", "createdAt", "updatedAt"));
        try
        {
            String identity = text(row, "categoryId");
            int colon = identity.lastIndexOf(':');
            if (!identity.startsWith("budget-category:") || colon <= "budget-category:".length() || identity.length() > 160)
                throw invalid("invalid category identity");
            String uuidText = identity.substring(colon + 1); UUID uuid = UUID.fromString(uuidText);
            if (!uuid.toString().equals(uuidText) || !row.path("active").isBoolean()) throw invalid("invalid UUID or active type");
            return new Fact(identity, new BudgetCategoryAdminService.ImportFact(uuid, text(row, "code"), text(row, "name"),
                    row.path("active").booleanValue(), date(row, "effectiveFrom"), date(row, "effectiveTo"), nullableText(row, "description"),
                    Instant.parse(text(row, "createdAt")), Instant.parse(text(row, "updatedAt"))));
        }
        catch (RuntimeException ex)
        {
            throw invalid("invalid category fields, dates, bounds or timestamp precision");
        }
    }

    static long count(SclxExportDocument.Extensions extensions)
    {
        Object raw = extensions.scaJakartaH2().get(KEY);
        if (raw == null && !extensions.scaJakartaH2().containsKey(KEY)) return 0;
        ObjectNode root = JSON.createObjectNode(); root.putObject("extensions").putObject("scaJakartaH2").set(KEY, JSON.valueToTree(raw));
        return parse(root).byCode().size();
    }

    static String fingerprint(BudgetCategoryAdminService.ImportFact fact)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(node(fact).toString().getBytes(StandardCharsets.UTF_8)));
        }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static ObjectNode node(BudgetCategoryAdminService.ImportFact fact)
    {
        ObjectNode node = JSON.createObjectNode(); node.put("portableId", fact.portableId().toString());
        node.put("code", fact.code()); node.put("name", fact.name()); node.put("active", fact.active());
        node.put("effectiveFrom", fact.effectiveFrom() == null ? null : fact.effectiveFrom().toString());
        node.put("effectiveTo", fact.effectiveTo() == null ? null : fact.effectiveTo().toString());
        node.put("description", fact.description()); node.put("createdAt", fact.createdAt().toString()); node.put("updatedAt", fact.updatedAt().toString());
        return node;
    }

    private static LocalDate date(JsonNode row, String field)
    {
        String value = nullableText(row, field);
        return value == null ? null : LocalDate.parse(value);
    }

    private static String nullableText(JsonNode row, String field)
    {
        if (row.path(field).isNull()) return null;
        return text(row, field);
    }

    private static String text(JsonNode row, String field)
    {
        if (!row.path(field).isTextual()) throw invalid("requires text for " + field);
        return row.path(field).textValue();
    }

    private static void fields(JsonNode node, Set<String> expected)
    {
        Set<String> actual = new HashSet<>(); node.fieldNames().forEachRemaining(actual::add);
        if (!node.isObject() || !actual.equals(expected)) throw invalid("unsupported or missing fields");
    }

    private static IllegalStateException invalid(String detail)
    {
        return new IllegalStateException("budgetCategories: " + detail);
    }
}
