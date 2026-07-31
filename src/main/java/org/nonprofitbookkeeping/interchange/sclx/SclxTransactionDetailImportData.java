package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import org.nonprofitbookkeeping.model.CounterpartyKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict import projection for transaction-linked SCLX application extensions. */
final class SclxTransactionDetailImportData
{
    private static final Set<String> ACTIVITY_FIELDS = Set.of(
            "activityId", "code", "name", "active");
    private static final Set<String> PARTY_ROOT_FIELDS = Set.of(
            "counterparties", "merchants", "transactionLineMerchants");
    private static final Set<String> COUNTERPARTY_FIELDS = Set.of(
            "counterpartyId", "displayName", "kind", "email", "phone", "notes", "active");
    private static final Set<String> MERCHANT_FIELDS = Set.of(
            "merchantId", "name", "notes", "active");
    private static final Set<String> MERCHANT_LINK_FIELDS = Set.of("lineId", "merchantId");
    private static final Set<String> SUPPLEMENTAL_FIELDS = Set.of(
            "supplementalDetailId", "transactionId", "lineOrder", "kind", "entryRef",
            "counterparty", "description", "reference", "amount", "dueDate",
            "startDate", "endDate", "notes");
    private static final Set<String> SUPPLEMENTAL_KINDS = Set.of(
            "RECEIVABLE", "PAYABLE", "PREPAID_EXPENSE", "DEFERRED_REVENUE",
            "OTHER_ASSET", "OTHER_LIABILITY");

    private final List<ActivityValue> activities;
    private final List<CounterpartyValue> counterparties;
    private final List<MerchantValue> merchants;
    private final Map<String, String> merchantByLineId;
    private final Map<String, List<SupplementalValue>> supplementalByTransactionId;

    private SclxTransactionDetailImportData(
            List<ActivityValue> activities,
            List<CounterpartyValue> counterparties,
            List<MerchantValue> merchants,
            Map<String, String> merchantByLineId,
            Map<String, List<SupplementalValue>> supplementalByTransactionId)
    {
        this.activities = List.copyOf(activities);
        this.counterparties = List.copyOf(counterparties);
        this.merchants = List.copyOf(merchants);
        this.merchantByLineId = Map.copyOf(merchantByLineId);
        Map<String, List<SupplementalValue>> details = new LinkedHashMap<>();
        supplementalByTransactionId.forEach((key, value) -> details.put(key, List.copyOf(value)));
        this.supplementalByTransactionId = Map.copyOf(details);
    }

    static SclxTransactionDetailImportData parse(JsonNode root)
    {
        Objects.requireNonNull(root, "root");
        JsonNode app = root.path("extensions").path("scaJakartaH2");
        List<ActivityValue> activities = activities(app.get("activities"));
        PartyValues parties = parties(app.get("counterparties"));
        List<SupplementalValue> supplemental = supplemental(app.get("supplementalDetails"));

        Map<String, JsonNode> transactions = new LinkedHashMap<>();
        Map<String, JsonNode> lines = new LinkedHashMap<>();
        for (JsonNode transaction : root.path("transactions"))
        {
            String transactionId = text(transaction, "transactionId", "transaction");
            transactions.put(transactionId, transaction);
            Set<String> counterpartiesForTransaction = new HashSet<>();
            for (JsonNode line : transaction.path("lines"))
            {
                String lineId = text(line, "lineId", "transaction line");
                lines.put(lineId, line);
                String activityId = optionalText(line, "activityId", "transaction line " + lineId);
                if (activityId != null && activities.stream().noneMatch(value -> value.externalId().equals(activityId)))
                {
                    throw new IllegalStateException("Transaction line activity does not resolve: " + activityId + ".");
                }
                String counterpartyId = optionalText(line, "counterpartyId", "transaction line " + lineId);
                if (counterpartyId != null)
                {
                    if (parties.counterparties().stream().noneMatch(value -> value.externalId().equals(counterpartyId)))
                    {
                        throw new IllegalStateException(
                                "Transaction line counterparty does not resolve: " + counterpartyId + ".");
                    }
                    counterpartiesForTransaction.add(counterpartyId);
                }
            }
            if (counterpartiesForTransaction.size() > 1)
            {
                throw new IllegalStateException(
                        "One canonical transaction cannot import more than one header counterparty: "
                                + transactionId + ".");
            }
        }

        Map<String, String> merchantByLine = new LinkedHashMap<>();
        for (MerchantLink link : parties.merchantLinks())
        {
            JsonNode line = lines.get(link.lineId());
            if (line == null)
            {
                throw new IllegalStateException("Merchant relationship line does not resolve: " + link.lineId() + ".");
            }
            if (parties.merchants().stream().noneMatch(value -> value.externalId().equals(link.merchantId())))
            {
                throw new IllegalStateException(
                        "Merchant relationship merchant does not resolve: " + link.merchantId() + ".");
            }
            if (merchantByLine.put(link.lineId(), link.merchantId()) != null)
            {
                throw new IllegalStateException(
                        "A transaction line cannot have more than one merchant relationship: " + link.lineId() + ".");
            }
        }

        Map<String, List<SupplementalValue>> supplementalByTransaction = new LinkedHashMap<>();
        for (SupplementalValue value : supplemental)
        {
            if (!transactions.containsKey(value.transactionId()))
            {
                throw new IllegalStateException(
                        "Supplemental detail transaction does not resolve: " + value.transactionId() + ".");
            }
            supplementalByTransaction.computeIfAbsent(value.transactionId(), ignored -> new ArrayList<>()).add(value);
        }
        supplementalByTransaction.values().forEach(values -> values.sort(
                java.util.Comparator.comparingInt(SupplementalValue::lineOrder)
                        .thenComparing(SupplementalValue::externalId)));

        return new SclxTransactionDetailImportData(
                activities,
                parties.counterparties(),
                parties.merchants(),
                merchantByLine,
                supplementalByTransaction);
    }

    List<ActivityValue> activities()
    {
        return activities;
    }

    List<CounterpartyValue> counterparties()
    {
        return counterparties;
    }

    List<MerchantValue> merchants()
    {
        return merchants;
    }

    String merchantForLine(String lineId)
    {
        return merchantByLineId.get(lineId);
    }

    List<SupplementalValue> supplementalForTransaction(String transactionId)
    {
        return supplementalByTransactionId.getOrDefault(transactionId, List.of());
    }

    private static List<ActivityValue> activities(JsonNode values)
    {
        if (absent(values))
        {
            return List.of();
        }
        requireArray(values, "activities");
        List<ActivityValue> result = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < values.size(); index++)
        {
            JsonNode value = object(values.get(index), ACTIVITY_FIELDS, "activities[" + index + "]");
            ActivityValue activity = new ActivityValue(
                    text(value, "activityId", "activity"),
                    text(value, "code", "activity"),
                    text(value, "name", "activity"),
                    flag(value, "active", "activity"));
            unique(identities, activity.externalId(), "activity identity");
            unique(codes, activity.code(), "activity code");
            result.add(activity);
        }
        return List.copyOf(result);
    }

    private static PartyValues parties(JsonNode value)
    {
        if (absent(value))
        {
            return new PartyValues(List.of(), List.of(), List.of());
        }
        JsonNode root = object(value, PARTY_ROOT_FIELDS, "counterparties");
        JsonNode counterpartyValues = requiredArray(root, "counterparties", "counterparties");
        JsonNode merchantValues = requiredArray(root, "merchants", "counterparties");
        JsonNode linkValues = requiredArray(root, "transactionLineMerchants", "counterparties");

        List<CounterpartyValue> counterparties = new ArrayList<>();
        Set<String> counterpartyIds = new HashSet<>();
        for (int index = 0; index < counterpartyValues.size(); index++)
        {
            JsonNode item = object(counterpartyValues.get(index), COUNTERPARTY_FIELDS,
                    "counterparties.counterparties[" + index + "]");
            CounterpartyValue counterparty = new CounterpartyValue(
                    text(item, "counterpartyId", "counterparty"),
                    text(item, "displayName", "counterparty"),
                    enumValue(CounterpartyKind.class, text(item, "kind", "counterparty"), "counterparty kind"),
                    optionalText(item, "email", "counterparty"),
                    optionalText(item, "phone", "counterparty"),
                    optionalText(item, "notes", "counterparty"),
                    flag(item, "active", "counterparty"));
            unique(counterpartyIds, counterparty.externalId(), "counterparty identity");
            counterparties.add(counterparty);
        }

        List<MerchantValue> merchants = new ArrayList<>();
        Set<String> merchantIds = new HashSet<>();
        Set<String> merchantNames = new HashSet<>();
        for (int index = 0; index < merchantValues.size(); index++)
        {
            JsonNode item = object(merchantValues.get(index), MERCHANT_FIELDS,
                    "counterparties.merchants[" + index + "]");
            MerchantValue merchant = new MerchantValue(
                    text(item, "merchantId", "merchant"),
                    text(item, "name", "merchant"),
                    optionalText(item, "notes", "merchant"),
                    flag(item, "active", "merchant"));
            unique(merchantIds, merchant.externalId(), "merchant identity");
            unique(merchantNames, merchant.name(), "merchant name");
            merchants.add(merchant);
        }

        List<MerchantLink> links = new ArrayList<>();
        for (int index = 0; index < linkValues.size(); index++)
        {
            JsonNode item = object(linkValues.get(index), MERCHANT_LINK_FIELDS,
                    "counterparties.transactionLineMerchants[" + index + "]");
            links.add(new MerchantLink(
                    text(item, "lineId", "merchant relationship"),
                    text(item, "merchantId", "merchant relationship")));
        }
        return new PartyValues(counterparties, merchants, links);
    }

    private static List<SupplementalValue> supplemental(JsonNode values)
    {
        if (absent(values))
        {
            return List.of();
        }
        requireArray(values, "supplementalDetails");
        List<SupplementalValue> result = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (int index = 0; index < values.size(); index++)
        {
            JsonNode item = object(values.get(index), SUPPLEMENTAL_FIELDS,
                    "supplementalDetails[" + index + "]");
            SupplementalValue detail = new SupplementalValue(
                    text(item, "supplementalDetailId", "supplemental detail"),
                    text(item, "transactionId", "supplemental detail"),
                    integer(item, "lineOrder", "supplemental detail"),
                    text(item, "kind", "supplemental detail"),
                    optionalText(item, "entryRef", "supplemental detail"),
                    optionalText(item, "counterparty", "supplemental detail"),
                    text(item, "description", "supplemental detail"),
                    optionalText(item, "reference", "supplemental detail"),
                    decimal(item, "amount", "supplemental detail"),
                    optionalDate(item, "dueDate", "supplemental detail"),
                    optionalDate(item, "startDate", "supplemental detail"),
                    optionalDate(item, "endDate", "supplemental detail"),
                    optionalText(item, "notes", "supplemental detail"));
            validateSupplemental(detail);
            unique(identities, detail.externalId(), "supplemental-detail identity");
            result.add(detail);
        }
        return List.copyOf(result);
    }

    private static void validateSupplemental(SupplementalValue value)
    {
        if (value.lineOrder() < 0)
        {
            throw new IllegalStateException("Supplemental detail lineOrder must not be negative.");
        }
        if (!SUPPLEMENTAL_KINDS.contains(value.kind()))
        {
            throw new IllegalStateException("Unsupported supplemental detail kind: " + value.kind() + ".");
        }
        if (value.amount().signum() < 0)
        {
            throw new IllegalStateException("Supplemental detail amount must not be negative.");
        }
        if ((value.startDate() == null) != (value.endDate() == null))
        {
            throw new IllegalStateException(
                    "Supplemental detail startDate and endDate must both be present or absent.");
        }
        if (value.startDate() != null && value.startDate().isAfter(value.endDate()))
        {
            throw new IllegalStateException("Supplemental detail startDate must not follow endDate.");
        }
    }

    private static JsonNode object(JsonNode value, Set<String> fields, String path)
    {
        if (value == null || !value.isObject())
        {
            throw new IllegalStateException(path + " must be an object.");
        }
        Set<String> actual = new HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(fields))
        {
            throw new IllegalStateException(path + " must contain exactly " + fields + ".");
        }
        return value;
    }

    private static JsonNode requiredArray(JsonNode root, String field, String path)
    {
        JsonNode value = root.get(field);
        requireArray(value, path + "." + field);
        return value;
    }

    private static void requireArray(JsonNode value, String path)
    {
        if (value == null || !value.isArray())
        {
            throw new IllegalStateException(path + " must be an array.");
        }
    }

    private static boolean absent(JsonNode value)
    {
        return value == null || value.isMissingNode() || value.isNull();
    }

    private static String text(JsonNode value, String field, String path)
    {
        JsonNode node = value.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank())
        {
            throw new IllegalStateException(path + "." + field + " must be nonblank text.");
        }
        return node.textValue().trim();
    }

    private static String optionalText(JsonNode value, String field, String path)
    {
        JsonNode node = value.get(field);
        if (node == null || node.isNull())
        {
            return null;
        }
        if (!node.isTextual())
        {
            throw new IllegalStateException(path + "." + field + " must be text or null.");
        }
        return node.textValue().isBlank() ? null : node.textValue().trim();
    }

    private static boolean flag(JsonNode value, String field, String path)
    {
        JsonNode node = value.get(field);
        if (node == null || !node.isBoolean())
        {
            throw new IllegalStateException(path + "." + field + " must be boolean.");
        }
        return node.booleanValue();
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

    private static LocalDate optionalDate(JsonNode value, String field, String path)
    {
        String text = optionalText(value, field, path);
        if (text == null)
        {
            return null;
        }
        try
        {
            return LocalDate.parse(text);
        }
        catch (RuntimeException ex)
        {
            throw new IllegalStateException(path + "." + field + " must be an ISO date.", ex);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label)
    {
        try
        {
            return Enum.valueOf(type, value);
        }
        catch (RuntimeException ex)
        {
            throw new IllegalStateException("Unsupported " + label + ": " + value + ".", ex);
        }
    }

    private static void unique(Set<String> values, String value, String label)
    {
        if (!values.add(value))
        {
            throw new IllegalStateException("Duplicate " + label + ": " + value + ".");
        }
    }

    record ActivityValue(String externalId, String code, String name, boolean active)
    {
    }

    record CounterpartyValue(
            String externalId,
            String displayName,
            CounterpartyKind kind,
            String email,
            String phone,
            String notes,
            boolean active)
    {
    }

    record MerchantValue(String externalId, String name, String notes, boolean active)
    {
    }

    record SupplementalValue(
            String externalId,
            String transactionId,
            int lineOrder,
            String kind,
            String entryRef,
            String counterparty,
            String description,
            String reference,
            BigDecimal amount,
            LocalDate dueDate,
            LocalDate startDate,
            LocalDate endDate,
            String notes)
    {
    }

    private record MerchantLink(String lineId, String merchantId)
    {
    }

    private record PartyValues(
            List<CounterpartyValue> counterparties,
            List<MerchantValue> merchants,
            List<MerchantLink> merchantLinks)
    {
        private PartyValues
        {
            counterparties = List.copyOf(counterparties);
            merchants = List.copyOf(merchants);
            merchantLinks = List.copyOf(merchantLinks);
        }
    }
}
