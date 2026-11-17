package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Optional SCLX 1.3 application extension retaining the company chart graph. */
final class SclxCharts
{
    static final String KEY = "charts";
    static final String TYPE = "CHART";

    private SclxCharts()
    {
    }

    static String identity(ChartOfAccounts chart)
    {
        return SclxPortableIdentity.identity("chart", chart.getPortableId().toString());
    }

    static Map<String, Object> export(Company company, List<ChartOfAccounts> charts, List<Account> accounts)
    {
        List<Map<String, Object>> values = new ArrayList<>();
        Set<String> included = new HashSet<>();
        for (ChartOfAccounts chart : charts.stream().sorted(Comparator.comparing(ChartOfAccounts::getPortableId)).toList())
        {
            if (!SclxChartScope.sameCompany(chart.getCompany(), company))
            {
                throw new IllegalArgumentException("Chart is outside the selected company");
            }
            String id = identity(chart);
            if (!included.add(id))
            {
                throw new IllegalArgumentException("Duplicate chart identity");
            }
            values.add(Map.of("chartId", id, "name", chart.getName(), "chartVersion", chart.getVersion(),
                    "status", chart.getStatus().name(), "selected", SclxChartScope.sameChart(chart, company.getActiveChartOfAccounts())));
        }
        if (company.getActiveChartOfAccounts() != null
                && !included.contains(identity(company.getActiveChartOfAccounts())))
        {
            throw new IllegalArgumentException("Selected chart is outside the selected company");
        }
        List<Map<String, String>> memberships = new ArrayList<>();
        for (Account account : accounts.stream().sorted(Comparator.comparing(a -> SclxPortableIdentity.account(company.getCode(), a))).toList())
        {
            String chartId = identity(account.getChart());
            if (!included.contains(chartId))
            {
                throw new IllegalArgumentException("Account references an omitted chart");
            }
            if (account.getParent() != null && !SclxChartScope.sameChart(account.getChart(), account.getParent().getChart()))
            {
                throw new IllegalArgumentException("Account parent belongs to a different chart");
            }
            memberships.add(Map.of("accountId", SclxPortableIdentity.account(company.getCode(), account), "chartId", chartId));
        }
        return Map.of("version", 1, "charts", List.copyOf(values), "accounts", List.copyOf(memberships));
    }

    static boolean qualifiedAccount(String id)
    {
        return id.startsWith("chart-account:") && id.split(":", -1).length == 3;
    }

    static String accountHash(JsonNode content, String sourceId)
    {
        var copy = (com.fasterxml.jackson.databind.node.ObjectNode) content.deepCopy();
        copy.put("accountId", sourceId);
        return SclxImportPreviewService.hash(copy);
    }

    static Data parse(JsonNode root)
    {
        JsonNode app = root.path("extensions").path("scaJakartaH2");
        if (!app.has(KEY))
        {
            for (JsonNode account : root.path("chartOfAccounts"))
            {
                if (qualifiedAccount(account.path("accountId").asText()))
                {
                    fail("Chart-aware accounts require the charts extension");
                }
            }
            return new Data(false, List.of(), Map.of());
        }
        JsonNode value = app.get(KEY);
        fields(value, Set.of("version", "charts", "accounts"));
        if (!value.path("version").isIntegralNumber() || !value.path("version").canConvertToInt()
                || value.path("version").intValue() != 1) fail("Unsupported charts version");
        JsonNode chartRows = value.path("charts"), accountRows = value.path("accounts");
        if (!chartRows.isArray() || !accountRows.isArray())
        {
            fail("Charts and memberships must be arrays");
        }
        if (chartRows.size() > SclxStructureValidator.MAX_ACCOUNTS
                || accountRows.size() > SclxStructureValidator.MAX_ACCOUNTS) fail("Chart graph exceeds import limits");
        List<Chart> charts = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<UUID> uuids = new HashSet<>();
        int selected = 0;
        for (JsonNode row : chartRows)
        {
            fields(row, Set.of("chartId", "name", "chartVersion", "status", "selected"));
            String id = text(row, "chartId", 160);
            int colon = id.lastIndexOf(':');
            UUID uuid;
            try
            {
                if (!id.startsWith("chart:") || colon < 0)
                {
                    throw new IllegalArgumentException();
                }
                uuid = UUID.fromString(id.substring(colon + 1));
            }
            catch (IllegalArgumentException ex)
            {
                throw new IllegalStateException("Chart identity must contain an intrinsic UUID", ex);
            }
            if (!id.equals(SclxPortableIdentity.identity("chart", uuid.toString())))
            {
                fail("Noncanonical chart identity");
            }
            if (!ids.add(id) || !uuids.add(uuid))
            {
                fail("Duplicate chart identity");
            }
            ChartStatus status;
            try { status = ChartStatus.valueOf(text(row, "status", 20)); }
            catch (IllegalArgumentException ex) { throw new IllegalStateException("Unsupported chart status", ex); }
            if (!row.path("selected").isBoolean())
            {
                fail("Chart selected must be boolean");
            }
            boolean active = row.path("selected").booleanValue();
            if (active && ++selected > 1)
            {
                fail("More than one selected chart");
            }
            if (active && status != ChartStatus.ACTIVE)
            {
                fail("Selected chart must have ACTIVE status");
            }
            charts.add(new Chart(id, uuid, text(row, "name", 200), text(row, "chartVersion", 50), status, active));
        }
        Map<String, String> memberships = new LinkedHashMap<>();
        for (JsonNode row : accountRows)
        {
            fields(row, Set.of("accountId", "chartId"));
            String accountId = text(row, "accountId", 160), chartId = text(row, "chartId", 160);
            if (!ids.contains(chartId))
            {
                fail("Account references an unknown chart");
            }
            if (memberships.putIfAbsent(accountId, chartId) != null)
            {
                fail("Duplicate account chart membership");
            }
        }
        Set<String> includedAccounts = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (JsonNode account : root.path("chartOfAccounts"))
        {
            String id = text(account, "accountId", 160);
            if (!includedAccounts.add(id))
            {
                fail("Duplicate account identity");
            }
            String chart = memberships.get(id);
            if (chart == null)
            {
                fail("Missing account chart membership");
            }
            String[] parts = id.split(":", -1);
            String chartUuid = chart.substring(chart.lastIndexOf(':') + 1);
            if (!qualifiedAccount(id) || !parts[1].equals(chartUuid)
                    || !id.equals(SclxPortableIdentity.identity("chart-account", chartUuid, text(account, "code", 64))))
            {
                fail("Account identity does not match its chart and code");
            }
            if (!codes.add(chart + "\u0000" + text(account, "code", 64)))
            {
                fail("Duplicate account code within chart");
            }
            JsonNode parent = account.get("parentAccountId");
            if (parent != null && !parent.isNull()
                    && (!parent.isTextual() || !chart.equals(memberships.get(parent.textValue()))))
            {
                fail("Account parent belongs to a different or missing chart");
            }
        }
        if (!includedAccounts.equals(memberships.keySet()))
        {
            fail("Chart membership references an omitted account");
        }
        return new Data(true, List.copyOf(charts), Map.copyOf(memberships));
    }

    record Data(boolean present, List<Chart> charts, Map<String, String> memberships)
    {
    }
    record Chart(String id, UUID uuid, String name, String version, ChartStatus status, boolean selected)
    {
    }

    private static void fields(JsonNode value, Set<String> expected)
    {
        if (value == null || !value.isObject())
        {
            fail("Chart graph entry must be an object");
        }
        Set<String> actual = new HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected))
        {
            fail("Unsupported or missing chart graph fields");
        }
    }

    private static String text(JsonNode value, String field, int limit)
    {
        JsonNode text = value.get(field);
        if (text == null || !text.isTextual() || text.textValue().isBlank()
                || text.textValue().codePointCount(0, text.textValue().length()) > limit)
        {
            throw new IllegalStateException("Invalid chart graph " + field);
        }
        return text.textValue();
    }

    private static void fail(String message)
    {
        throw new IllegalStateException(message);
    }

}
