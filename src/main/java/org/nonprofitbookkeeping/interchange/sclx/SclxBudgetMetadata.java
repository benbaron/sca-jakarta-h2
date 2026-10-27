package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.nonprofitbookkeeping.model.BudgetLine;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.service.BudgetPlanService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Complete, versioned plan/line history layered on the standard SCLX budget graph. */
final class SclxBudgetMetadata
{
    static final String KEY = "budgetMetadata";
    private static final String IDENTITY = "_scaBudgetMetadata";
    private static final int MAX_NOTES = 1_048_576;

    record Plan(LocalDate start, LocalDate end, BudgetPlan.Status status, BudgetPlanService.ImportHistory history)
    {
    }

    record Data(boolean present, Map<String, Plan> plans, Map<String, BudgetPlanService.ImportLineHistory> lines)
    {
        Data
        {
            plans = Map.copyOf(plans);
            lines = Map.copyOf(lines);
        }
    }

    static Map<String, Object> export(String company, List<BudgetPlan> plans, List<BudgetLine> lines)
    {
        List<Map<String, Object>> headers = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();
        for (BudgetPlan plan : plans)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("budgetId", budgetId(company, plan));
            row.put("periodStart", plan.getPeriodStart()); row.put("periodEnd", plan.getPeriodEnd());
            row.put("status", plan.getStatus().name()); row.put("activatedAt", plan.getActivatedAt()); row.put("archivedAt", plan.getArchivedAt());
            row.put("notes", plan.getNotes()); row.put("createdAt", plan.getCreatedAt()); row.put("updatedAt", plan.getUpdatedAt());
            headers.add(Collections.unmodifiableMap(row));
        }
        for (BudgetLine line : lines)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("lineId", SclxPortableIdentity.budgetLine(budgetId(company, line.getBudgetPlan()),
                    line.getBudgetCategory().getCode(), null,
                    line.getFund() == null ? null : SclxPortableIdentity.fund(company, line.getFund().getCode()),
                    line.getActivity() == null ? null : SclxPortableIdentity.activity(company, line.getActivity().getCode()),
                    line.getPeriodMonth() == null ? null : line.getPeriodMonth().toString()));
            row.put("notes", line.getNotes()); row.put("createdAt", line.getCreatedAt()); row.put("updatedAt", line.getUpdatedAt());
            details.add(Collections.unmodifiableMap(row));
        }
        headers.sort(Comparator.comparing(row -> (String) row.get("budgetId")));
        details.sort(Comparator.comparing(row -> (String) row.get("lineId")));
        return Map.of("version", 1, "plans", List.copyOf(headers), "lines", List.copyOf(details));
    }

    static Data parse(JsonNode root)
    {
        JsonNode ext = root.path("extensions").path("scaJakartaH2").path(KEY);
        if (ext.isMissingNode())
        {
            return new Data(false, Map.of(), Map.of());
        }
        fields(ext, Set.of("version", "plans", "lines"));
        if (!ext.path("version").isIntegralNumber() || !ext.path("version").canConvertToInt()
                || ext.path("version").intValue() != 1 || !ext.path("plans").isArray() || !ext.path("lines").isArray())
        {
            throw invalid("requires version 1 and plans/lines arrays");
        }
        Map<String, JsonNode> corePlans = new LinkedHashMap<>(), coreLines = new LinkedHashMap<>();
        for (JsonNode budget : root.path("budgets"))
        {
            unique(corePlans, text(budget, "budgetId"), budget);
            for (JsonNode line : budget.path("lines")) unique(coreLines, text(line, "lineId"), line);
        }
        Map<String, Plan> plans = new LinkedHashMap<>();
        for (JsonNode fact : ext.path("plans"))
        {
            fields(fact, Set.of("budgetId", "periodStart", "periodEnd", "status", "activatedAt", "archivedAt", "notes", "createdAt", "updatedAt"));
            String id = text(fact, "budgetId");
            BudgetPlan.Status status;
            try
            {
                status = BudgetPlan.Status.valueOf(text(fact, "status"));
            }
            catch (IllegalArgumentException ex)
            {
                throw invalid("unknown plan status");
            }
            LocalDate start = date(fact, "periodStart"), end = date(fact, "periodEnd");
            JsonNode core = corePlans.get(id);
            if (core == null || !core.path("active").isBoolean() || core.path("active").booleanValue() != (status == BudgetPlan.Status.ACTIVE)
                    || end.isBefore(start))
            {
                throw invalid("unresolved plan, contradictory active state or inverted dates: " + id);
            }
            Plan plan = new Plan(start, end, status, new BudgetPlanService.ImportHistory(
                    instant(fact, "activatedAt", true), instant(fact, "archivedAt", true),
                    instant(fact, "createdAt", false), instant(fact, "updatedAt", false), notes(fact)));
            if ((status == BudgetPlan.Status.ACTIVE && (plan.history().activatedAt() == null || plan.history().archivedAt() != null))
                    || (status == BudgetPlan.Status.ARCHIVED && plan.history().archivedAt() == null)
                    || (status == BudgetPlan.Status.DRAFT && plan.history().archivedAt() != null))
            {
                throw invalid("plan activation/archive timestamps contradict status");
            }
            unique(plans, id, plan);
            for (JsonNode line : core.path("lines"))
            {
                JsonNode month = line.path("periodMonth");
                if (!month.isMissingNode() && !month.isNull())
                {
                    YearMonth value;
                    try
                    {
                        value = YearMonth.parse(text(line, "periodMonth"));
                    }
                    catch (RuntimeException ex)
                    {
                        throw invalid("invalid line month");
                    }
                    if (value.atEndOfMonth().isBefore(start) || value.atDay(1).isAfter(end))
                    {
                        throw invalid("line month outside exact plan dates");
                    }
                }
            }
        }
        Map<String, BudgetPlanService.ImportLineHistory> lines = new LinkedHashMap<>();
        for (JsonNode fact : ext.path("lines"))
        {
            fields(fact, Set.of("lineId", "notes", "createdAt", "updatedAt"));
            String id = text(fact, "lineId");
            if (!coreLines.containsKey(id)) throw invalid("unresolved line: " + id);
            unique(lines, id, new BudgetPlanService.ImportLineHistory(instant(fact, "createdAt", false), instant(fact, "updatedAt", false), notes(fact)));
        }
        if (!plans.keySet().equals(corePlans.keySet()) || !lines.keySet().equals(coreLines.keySet()))
        {
            throw invalid("metadata must cover every core budget and line exactly once");
        }
        return new Data(true, Map.copyOf(plans), Map.copyOf(lines));
    }

    static JsonNode identityDocument(JsonNode root)
    {
        Data data = parse(root);
        if (data.present()) SclxBudgetImportData.parseDocument(root);
        JsonNode copy = root.deepCopy();
        Map<String, JsonNode> planFacts = new HashMap<>(), lineFacts = new HashMap<>();
        root.path("extensions").path("scaJakartaH2").path(KEY).path("plans").forEach(row -> planFacts.put(text(row, "budgetId"), row));
        root.path("extensions").path("scaJakartaH2").path(KEY).path("lines").forEach(row -> lineFacts.put(text(row, "lineId"), row));
        for (JsonNode plan : copy.path("budgets"))
        {
            if (plan.has(IDENTITY)) throw invalid("reserved identity field");
            JsonNode header = planFacts.get(text(plan, "budgetId"));
            if (data.present()) ((ObjectNode) plan).set(IDENTITY, header.deepCopy());
            for (JsonNode line : plan.path("lines"))
            {
                if (line.has(IDENTITY)) throw invalid("reserved line identity field");
                if (data.present())
                {
                    ObjectNode fact = ((ObjectNode) line).putObject(IDENTITY);
                    fact.set("plan", header.deepCopy());
                    fact.set("line", lineFacts.get(text(line, "lineId")).deepCopy());
                }
            }
        }
        return copy;
    }

    private static String budgetId(String company, BudgetPlan plan)
    {
        return SclxPortableIdentity.budget(company, plan.getFiscalYear(), plan.getVersionCode());
    }

    private static void fields(JsonNode node, Set<String> expected)
    {
        Set<String> actual = new HashSet<>(); node.fieldNames().forEachRemaining(actual::add);
        if (!node.isObject() || !actual.equals(expected)) throw invalid("requires exactly " + expected);
    }

    private static <T> void unique(Map<String, T> map, String id, T value)
    {
        if (map.putIfAbsent(id, value) != null) throw invalid("duplicate identity: " + id);
    }

    private static String text(JsonNode node, String key)
    {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.textValue().isBlank()) throw invalid("requires nonblank " + key);
        return value.textValue();
    }

    private static String notes(JsonNode node)
    {
        JsonNode value = node.path("notes");
        if (value.isNull()) return null;
        if (!value.isTextual() || value.textValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_NOTES)
        {
            throw invalid("invalid/oversized notes");
        }
        return value.textValue();
    }

    private static LocalDate date(JsonNode node, String key)
    {
        try
        {
            LocalDate value = LocalDate.parse(text(node, key));
            if (value.getYear() < 1900 || value.getYear() > 9999) throw invalid("date outside supported range");
            return value;
        }
        catch (RuntimeException ex)
        {
            throw invalid("invalid " + key);
        }
    }

    private static Instant instant(JsonNode node, String key, boolean nullable)
    {
        if (nullable && node.path(key).isNull()) return null;
        try
        {
            Instant value = Instant.parse(text(node, key));
            if (value.isBefore(Instant.parse("1900-01-01T00:00:00Z")) || value.isAfter(Instant.parse("9999-12-31T23:59:59.999999999Z")))
                throw invalid("timestamp outside supported range");
            if (value.getNano() % 1000 != 0) throw invalid("timestamp exceeds persisted microsecond precision");
            return value;
        }
        catch (RuntimeException ex)
        {
            throw invalid("invalid " + key);
        }
    }

    private static IllegalStateException invalid(String detail)
    {
        return new IllegalStateException("budgetMetadata: " + detail);
    }
}
