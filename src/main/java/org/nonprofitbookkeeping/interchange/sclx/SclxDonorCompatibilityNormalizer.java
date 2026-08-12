package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Converts the bounded legacy aliases emitted by the donor workbook exporter into canonical import fields. */
final class SclxDonorCompatibilityNormalizer
{
    private static final Set<String> UNSUPPORTED_ROOT_SECTIONS = Set.of(
            "bankAccounts", "officeAssignments", "committeeMemberships", "events", "documents",
            "bankingItems", "outstandingItems", "otherAssetItems", "supplementalItems", "assets",
            "supplies", "bankStatementImports");

    private SclxDonorCompatibilityNormalizer()
    {
    }

    static Normalization normalize(ObjectNode source, Instant exportedAt, boolean numericExportedAt)
    {
        ObjectNode root = source.deepCopy();
        List<SclxCompatibilityNotice> notices = new ArrayList<>();
        if (numericExportedAt)
        {
            root.put("exportedAt", exportedAt.toString());
            warning(notices, "SCLX_DONOR_NUMERIC_EXPORTED_AT_NORMALIZED", "$.exportedAt",
                    "Converted the donor numeric epoch-seconds exportedAt value to RFC 3339 UTC.");
        }
        if (!donorDialect(root, numericExportedAt))
        {
            return new Normalization(root, notices);
        }

        normalizeOrganization(root, notices);
        normalizeAccounts(root, notices);
        String generalFundId = normalizeFunds(root, notices);
        normalizePeople(root, notices);
        normalizeBudgets(root, notices);
        normalizeTransactions(root, generalFundId, notices);
        reportUnsupportedSections(root, notices);
        return new Normalization(root, notices);
    }

    private static boolean donorDialect(ObjectNode root, boolean numericExportedAt)
    {
        if (numericExportedAt || nonEmpty(root.get("people")))
        {
            return true;
        }
        for (JsonNode account : root.path("chartOfAccounts"))
        {
            if (account.has("Number") || account.has("Name") || account.has("Type"))
            {
                return true;
            }
        }
        for (JsonNode transaction : root.path("transactions"))
        {
            if (transaction.path("transactionDate").isArray()
                    || "POSTED".equalsIgnoreCase(text(transaction, "status")))
            {
                return true;
            }
        }
        return false;
    }

    private static void normalizeOrganization(ObjectNode root, List<SclxCompatibilityNotice> notices)
    {
        if (!(root.get("organization") instanceof ObjectNode organization))
        {
            return;
        }
        if (blank(text(organization, "code")))
        {
            String fallback = firstText(organization, "name", "organizationId");
            organization.put("code", canonicalCode(fallback, "DONOR"));
            warning(notices, "SCLX_DONOR_ORGANIZATION_CODE_DERIVED", "$.organization.code",
                    "Derived the missing organization code from donor organization identity fields.");
        }
        List<String> preserved = new ArrayList<>();
        if (blank(text(organization, "baseCurrency"))) preserved.add("currency");
        if (blank(text(organization, "fiscalYearStart"))) preserved.add("fiscal-year start");
        if (!preserved.isEmpty())
        {
            warning(notices, "SCLX_DONOR_TARGET_SETTINGS_PRESERVED", "$.organization",
                    "The donor omitted " + String.join(" and ", preserved)
                            + "; the target company's existing setting(s) will be preserved.");
        }
    }

    private static void normalizeAccounts(ObjectNode root, List<SclxCompatibilityNotice> notices)
    {
        JsonNode values = root.get("chartOfAccounts");
        if (!(values instanceof ArrayNode accounts))
        {
            return;
        }
        Map<String, String> idsByNumber = new HashMap<>();
        for (JsonNode value : accounts)
        {
            if (value instanceof ObjectNode account)
            {
                String id = text(account, "accountId");
                String number = text(account, "Number");
                if (!blank(id)) idsByNumber.put(id, id);
                if (!blank(number) && !blank(id)) idsByNumber.put(number, id);
            }
        }

        int normalized = 0;
        Set<String> codes = new HashSet<>();
        for (JsonNode value : accounts)
        {
            if (!(value instanceof ObjectNode account)) continue;
            boolean aliases = account.has("Number") || account.has("Name") || account.has("Type")
                    || account.has("IncreaseSide") || account.has("OpeningBalance");
            String externalId = text(account, "accountId");
            String code = firstText(account, "code", "Number", "accountId");
            account.put("code", uniqueCode(canonicalCode(code, "ACCOUNT"), codes));
            account.put("name", clipped(firstText(account, "name", "Name", "code", "accountId"), 200));
            String type = upper(firstText(account, "type", "Type"));
            String normalizedType = normalizeAccountType(type);
            account.put("type", normalizedType);
            if (!Set.of("ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE", "BANK")
                    .contains(normalizedType))
            {
                error(notices, "SCLX_DONOR_ACCOUNT_TYPE_UNSUPPORTED", "$.chartOfAccounts",
                        "Donor account " + externalId + " has unsupported type " + type + ".");
            }
            String side = upper(firstText(account, "increaseSide", "IncreaseSide"));
            String normalizedSide = blank(side) ? normalSide(normalizedType) : side;
            account.put("increaseSide", normalizedSide);
            if (!Set.of("DEBIT", "CREDIT").contains(normalizedSide))
            {
                error(notices, "SCLX_DONOR_INCREASE_SIDE_UNSUPPORTED", "$.chartOfAccounts",
                        "Donor account " + externalId + " has unsupported increase side " + side + ".");
            }
            if (!account.hasNonNull("openingBalance"))
            {
                JsonNode opening = account.get("OpeningBalance");
                if (opening == null || opening.isNull())
                {
                    account.put("openingBalance", BigDecimal.ZERO);
                }
                else
                {
                    account.set("openingBalance", opening.deepCopy());
                }
            }
            if (!account.path("posting").isBoolean()) account.put("posting", true);
            if (!account.path("active").isBoolean()) account.put("active", true);
            String parent = firstText(account, "parentAccountId", "Parent");
            if (!blank(parent))
            {
                String resolved = idsByNumber.get(parent);
                if (resolved == null)
                {
                    error(notices, "SCLX_DONOR_ACCOUNT_PARENT_UNRESOLVED", "$.chartOfAccounts",
                            "Donor account " + externalId + " names an unresolved parent " + parent + ".");
                }
                else
                {
                    account.put("parentAccountId", resolved);
                }
            }
            if (aliases) normalized++;
        }
        if (normalized > 0)
        {
            warning(notices, "SCLX_DONOR_ACCOUNT_ALIASES_NORMALIZED", "$.chartOfAccounts",
                    "Normalized donor aliases and defaults for " + normalized + " account(s).");
        }
    }

    private static String normalizeFunds(ObjectNode root, List<SclxCompatibilityNotice> notices)
    {
        JsonNode values = root.get("funds");
        if (!(values instanceof ArrayNode funds))
        {
            return null;
        }
        int normalized = 0;
        String generalFundId = null;
        Set<String> codes = new HashSet<>();
        for (JsonNode value : funds)
        {
            if (!(value instanceof ObjectNode fund)) continue;
            String id = text(fund, "fundId");
            String name = firstText(fund, "name", "fundId");
            String code = firstText(fund, "code", "fundId", "name");
            fund.put("code", uniqueCode(canonicalCode(code, "FUND"), codes));
            fund.put("name", clipped(name, 200));
            if (blank(text(fund, "type")))
            {
                fund.put("type", fund.path("restricted").asBoolean(false)
                        ? "TEMP_RESTRICTED" : "UNRESTRICTED");
                normalized++;
            }
            if (!fund.path("active").isBoolean()) fund.put("active", true);
            if (blank(text(fund, "restrictionText")) && !blank(text(fund, "description")))
            {
                fund.put("restrictionText", text(fund, "description"));
            }
            if ("GENERAL FUND".equalsIgnoreCase(name)
                    || "GENERAL".equals(canonicalCode(id, ""))
                    || "GENERAL_FUND".equals(canonicalCode(id, "")))
            {
                generalFundId = id;
            }
        }
        if (normalized > 0)
        {
            warning(notices, "SCLX_DONOR_FUND_DEFAULTS_NORMALIZED", "$.funds",
                    "Normalized donor defaults for " + normalized + " fund(s).");
        }
        return generalFundId;
    }

    private static void normalizePeople(ObjectNode root, List<SclxCompatibilityNotice> notices)
    {
        JsonNode value = root.get("people");
        if (!(value instanceof ArrayNode people) || people.isEmpty())
        {
            return;
        }
        ObjectNode extensions = object(root, "extensions");
        ObjectNode app = object(extensions, "scaJakartaH2");
        ObjectNode parties = object(app, "counterparties");
        ArrayNode counterparties = array(parties, "counterparties");
        array(parties, "merchants");
        array(parties, "transactionLineMerchants");
        Set<String> existing = new HashSet<>();
        counterparties.forEach(item -> existing.add(text(item, "counterpartyId")));

        int mapped = 0;
        for (JsonNode person : people)
        {
            String id = text(person, "personId");
            String name = text(person, "displayName");
            if (blank(id) || blank(name))
            {
                error(notices, "SCLX_DONOR_PERSON_INVALID", "$.people",
                        "Every donor person requires personId and displayName before it can be mapped.");
                continue;
            }
            if (!existing.add(id))
            {
                error(notices, "SCLX_DONOR_PERSON_ID_CONFLICT", "$.people",
                        "Donor person identity " + id + " conflicts with an existing counterparty identity.");
                continue;
            }
            ObjectNode counterparty = counterparties.addObject();
            counterparty.put("counterpartyId", id);
            counterparty.put("displayName", clipped(name, 200));
            String kind = upper(text(person, "kind"));
            counterparty.put("kind", Set.of("PERSON", "ORG", "OTHER").contains(kind) ? kind : "OTHER");
            copyNullableText(person, counterparty, "email");
            copyNullableText(person, counterparty, "phone");
            counterparty.putNull("notes");
            counterparty.put("active", true);
            mapped++;
        }
        root.set("people", root.arrayNode());
        warning(notices, "SCLX_DONOR_PEOPLE_MAPPED", "$.people",
                "Mapped " + mapped + " donor people to canonical counterparties; transaction links are preserved.");
    }

    private static void normalizeBudgets(ObjectNode root, List<SclxCompatibilityNotice> notices)
    {
        JsonNode value = root.get("budgets");
        if (!(value instanceof ArrayNode budgets) || budgets.isEmpty())
        {
            return;
        }
        boolean donorShape = false;
        boolean nonZero = false;
        for (JsonNode budget : budgets)
        {
            donorShape |= blank(text(budget, "version"));
            for (JsonNode line : budget.path("lines"))
            {
                donorShape |= line.has("budgetedAmount");
                nonZero |= nonZero(line.get("budgetedAmount")) || nonZero(line.get("amount"));
            }
        }
        if (!donorShape)
        {
            return;
        }
        if (nonZero)
        {
            error(notices, "SCLX_DONOR_BUDGET_DATA_UNSUPPORTED", "$.budgets",
                    "Donor budgets contain non-zero values and cannot be skipped safely.");
            return;
        }
        int count = budgets.size();
        root.set("budgets", root.arrayNode());
        warning(notices, "SCLX_DONOR_EMPTY_BUDGETS_SKIPPED", "$.budgets",
                "Skipped " + count + " donor budget shell(s) because they have no non-zero canonical budget data.");
    }

    private static void normalizeTransactions(
            ObjectNode root,
            String generalFundId,
            List<SclxCompatibilityNotice> notices)
    {
        JsonNode value = root.get("transactions");
        if (!(value instanceof ArrayNode transactions))
        {
            return;
        }
        int dates = 0;
        int statuses = 0;
        int assignedFunds = 0;
        int linkedPeople = 0;
        int references = 0;
        int budgetReferences = 0;
        int fallbackDescriptions = 0;
        boolean missingGeneralFund = false;
        for (JsonNode item : transactions)
        {
            if (!(item instanceof ObjectNode transaction)) continue;
            dates += normalizeDate(transaction, "transactionDate");
            dates += normalizeDate(transaction, "postingDate");
            if ("POSTED".equalsIgnoreCase(text(transaction, "status")))
            {
                transaction.put("status", "ENTERED");
                statuses++;
            }
            String transactionId = text(transaction, "transactionId");
            String description = text(transaction, "description");
            String reference = text(transaction, "reference");
            if (blank(description))
            {
                description = blank(reference)
                        ? "Imported donor transaction " + transactionId
                        : "Reference: " + reference;
                fallbackDescriptions++;
            }
            else if (!blank(reference))
            {
                description = description + " [Reference: " + reference + "]";
            }
            if (!blank(reference))
            {
                references++;
                transaction.remove("reference");
            }
            transaction.put("description", clipped(description, 500));
            if (!blank(text(transaction, "budgetId"))) budgetReferences++;
            transaction.remove("budgetId");
            String headerPerson = text(transaction, "personId");
            for (JsonNode lineValue : transaction.path("lines"))
            {
                if (!(lineValue instanceof ObjectNode line)) continue;
                if (blank(text(line, "fundId")))
                {
                    if (blank(generalFundId))
                    {
                        missingGeneralFund = true;
                    }
                    else
                    {
                        line.put("fundId", generalFundId);
                        assignedFunds++;
                    }
                }
                String personId = firstText(line, "counterpartyId", "personId");
                if (blank(personId)) personId = headerPerson;
                if (!blank(personId) && blank(text(line, "counterpartyId")))
                {
                    line.put("counterpartyId", personId);
                    linkedPeople++;
                }
                if (!blank(text(line, "budgetId"))) budgetReferences++;
                line.remove("budgetId");
            }
        }
        if (missingGeneralFund)
        {
            error(notices, "SCLX_DONOR_GENERAL_FUND_REQUIRED", "$.transactions[*].lines[*].fundId",
                    "Donor transactions omit fund assignments, but no unique General Fund is available.");
        }
        if (dates > 0) warning(notices, "SCLX_DONOR_DATE_ARRAYS_NORMALIZED", "$.transactions",
                "Converted " + dates + " donor date array(s) to ISO calendar dates.");
        if (statuses > 0) warning(notices, "SCLX_DONOR_POSTED_STATUS_NORMALIZED", "$.transactions[*].status",
                "Converted " + statuses + " donor POSTED status value(s) to canonical ENTERED.");
        if (assignedFunds > 0) warning(notices, "SCLX_DONOR_GENERAL_FUND_ASSIGNED",
                "$.transactions[*].lines[*].fundId", "Assigned " + assignedFunds
                        + " fundless transaction line(s) to General Fund (" + generalFundId + ").");
        if (linkedPeople > 0) warning(notices, "SCLX_DONOR_COUNTERPARTY_LINKS_NORMALIZED",
                "$.transactions[*].lines[*].counterpartyId", "Mapped " + linkedPeople
                        + " donor person link(s) to canonical transaction counterparties.");
        if (references > 0) warning(notices, "SCLX_DONOR_REFERENCES_PRESERVED", "$.transactions[*].reference",
                "Preserved " + references + " donor transaction reference(s) in canonical transaction memos.");
        if (budgetReferences > 0) warning(notices, "SCLX_DONOR_BUDGET_REFERENCES_SKIPPED",
                "$.transactions[*].budgetId", "Removed " + budgetReferences
                        + " donor workbook budget annotation(s) that do not identify canonical budgets.");
        if (fallbackDescriptions > 0) warning(notices, "SCLX_DONOR_DESCRIPTIONS_DERIVED",
                "$.transactions[*].description", "Derived a non-blank memo for " + fallbackDescriptions
                        + " donor transaction(s) that omitted a description.");
    }

    private static int normalizeDate(ObjectNode value, String field)
    {
        JsonNode node = value.get(field);
        if (!(node instanceof ArrayNode parts)) return 0;
        if (parts.size() != 3 || !parts.get(0).canConvertToInt()
                || !parts.get(1).canConvertToInt() || !parts.get(2).canConvertToInt())
        {
            throw new IllegalArgumentException("Donor " + field + " must contain [year, month, day]");
        }
        try
        {
            value.put(field, LocalDate.of(
                    parts.get(0).intValue(), parts.get(1).intValue(), parts.get(2).intValue()).toString());
            return 1;
        }
        catch (DateTimeException ex)
        {
            throw new IllegalArgumentException("Donor " + field + " is not a valid calendar date", ex);
        }
    }

    private static void reportUnsupportedSections(ObjectNode root, List<SclxCompatibilityNotice> notices)
    {
        for (String section : UNSUPPORTED_ROOT_SECTIONS)
        {
            JsonNode value = root.get(section);
            if (nonEmpty(value))
            {
                error(notices, "SCLX_DONOR_UNSUPPORTED_SECTION", "$." + section,
                        "Donor section " + section + " contains data that has no safe canonical mapping.");
            }
        }
    }

    private static ObjectNode object(ObjectNode parent, String field)
    {
        JsonNode value = parent.get(field);
        if (value instanceof ObjectNode object) return object;
        ObjectNode object = parent.objectNode();
        parent.set(field, object);
        return object;
    }

    private static ArrayNode array(ObjectNode parent, String field)
    {
        JsonNode value = parent.get(field);
        if (value instanceof ArrayNode array) return array;
        ArrayNode array = parent.arrayNode();
        parent.set(field, array);
        return array;
    }

    private static void copyNullableText(JsonNode source, ObjectNode target, String field)
    {
        String value = text(source, field);
        if (blank(value)) target.putNull(field); else target.put(field, value);
    }

    private static boolean nonZero(JsonNode value)
    {
        if (value == null || value.isNull()) return false;
        try
        {
            return new BigDecimal(value.asText()).signum() != 0;
        }
        catch (NumberFormatException ex)
        {
            return true;
        }
    }

    private static boolean nonEmpty(JsonNode value)
    {
        if (value == null || value.isNull() || value.isMissingNode()) return false;
        if (value.isContainerNode()) return !value.isEmpty();
        return !blank(value.asText());
    }

    private static String normalizeAccountType(String value)
    {
        if (Set.of("ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE", "BANK").contains(value))
        {
            return value;
        }
        if (Set.of("CASH", "CHECKING", "SAVINGS").contains(value)) return "BANK";
        return value;
    }

    private static String normalSide(String accountType)
    {
        return Set.of("LIABILITY", "EQUITY", "INCOME").contains(normalizeAccountType(accountType))
                ? "CREDIT" : "DEBIT";
    }

    private static String uniqueCode(String requested, Set<String> used)
    {
        String result = requested;
        int suffix = 2;
        while (!used.add(result.toUpperCase(Locale.ROOT)))
        {
            String marker = "_" + suffix++;
            result = clipped(requested, 64 - marker.length()) + marker;
        }
        return result;
    }

    private static String canonicalCode(String value, String fallback)
    {
        String source = blank(value) ? fallback : value;
        String normalized = upper(source).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) normalized = upper(fallback);
        return clipped(normalized, 64);
    }

    private static String firstText(JsonNode value, String... fields)
    {
        for (String field : fields)
        {
            String text = text(value, field);
            if (!blank(text)) return text;
        }
        return "";
    }

    private static String text(JsonNode value, String field)
    {
        JsonNode node = value == null ? null : value.get(field);
        return node != null && node.isTextual() ? node.textValue().trim() : "";
    }

    private static String upper(String value)
    {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private static String clipped(String value, int length)
    {
        String text = value == null ? "" : value.trim();
        return text.length() <= length ? text : text.substring(0, length);
    }

    private static void warning(
            List<SclxCompatibilityNotice> notices, String code, String path, String message)
    {
        notices.add(new SclxCompatibilityNotice(code, path, message, false));
    }

    private static void error(
            List<SclxCompatibilityNotice> notices, String code, String path, String message)
    {
        notices.add(new SclxCompatibilityNotice(code, path, message, true));
    }

    record Normalization(ObjectNode root, List<SclxCompatibilityNotice> notices)
    {
        Normalization
        {
            notices = List.copyOf(notices);
        }
    }
}
