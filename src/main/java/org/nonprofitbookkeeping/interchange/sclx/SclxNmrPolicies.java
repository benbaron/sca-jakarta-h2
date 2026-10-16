package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nonprofitbookkeeping.model.NmrPolicyVersion;
import org.nonprofitbookkeeping.service.NmrPolicyImport;
import org.nonprofitbookkeeping.service.NmrPolicyService;
import org.nonprofitbookkeeping.service.NmrPolicyTerms;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict shared codec for optional retained policy configuration; no accounting behavior. */
final class SclxNmrPolicies
{
    static final String KEY = "nmrPolicies";
    static final String TYPE = "NMR_POLICY_VERSION";
    private static final Set<String> FIELDS = Set.of("policyVersionId", "requestHash", "savedAt", "savedBy",
            "policyCode", "category", "validFrom", "validTo", "rate", "currency", "dateBasis", "roundingRule",
            "eligibility", "exemptions", "recipient", "reporting", "merchantPath", "sourceReference", "authority", "sourceDate");

    private SclxNmrPolicies()
    {
    }

    static Map<String, Object> export(String companyCode, List<NmrPolicyVersion> policies)
    {
        List<Map<String, Object>> entries = policies.stream()
                .sorted(Comparator.comparing(p -> p.getPortableId().toString()))
                .map(p ->
                {
                    if (!p.getCompany().getCode().equalsIgnoreCase(companyCode))
                    {
                        throw new IllegalArgumentException("Cannot export another company's NMR policy.");
                    }
                    return entry(SclxPortableIdentity.identity("nmr-policy-version", companyCode,
                            p.getPortableId().toString()), NmrPolicyService.importValue(p));
                }).toList();
        return Map.of("version", 1, "versions", entries);
    }

    static Map<String, Object> entry(String id, NmrPolicyImport p)
    {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("policyVersionId", id);
        value.put("requestHash", p.requestHash());
        value.put("savedAt", p.savedAt().toString());
        value.put("savedBy", p.savedBy());
        NmrPolicyTerms t = p.terms();
        value.put("policyCode", t.policyCode());
        value.put("category", t.category());
        value.put("validFrom", t.validFrom().toString());
        if (t.validTo() != null)
        {
            value.put("validTo", t.validTo().toString());
        }
        value.put("rate", t.rate());
        value.put("currency", t.currency());
        value.put("dateBasis", t.dateBasis());
        value.put("roundingRule", t.roundingRule());
        value.put("eligibility", t.eligibility());
        value.put("exemptions", t.exemptions());
        value.put("recipient", t.recipient());
        value.put("reporting", t.reporting());
        value.put("merchantPath", t.merchantPath());
        value.put("sourceReference", t.sourceReference());
        value.put("authority", t.authority());
        value.put("sourceDate", t.sourceDate().toString());
        return Map.copyOf(value);
    }

    static List<Entry> parseExtensions(SclxExportDocument.Extensions extensions)
    {
        Object raw = extensions.scaJakartaH2().get(KEY);
        return raw == null ? List.of() : parseValue(new ObjectMapper().valueToTree(raw));
    }

    static List<Entry> parse(JsonNode root)
    {
        JsonNode value = root.path("extensions").path("scaJakartaH2").get(KEY);
        return value == null ? List.of() : parseValue(value);
    }

    private static List<Entry> parseValue(JsonNode value)
    {
        fields(value, Set.of("version", "versions"));
        if (!value.path("version").isIntegralNumber() || !value.path("version").canConvertToInt()
                || value.path("version").intValue() != 1
                || !value.path("versions").isArray())
        {
            throw new IllegalArgumentException("nmrPolicies requires integer version 1 and a versions array.");
        }
        List<Entry> result = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (JsonNode node : value.path("versions"))
        {
            Entry entry = parseEntry(node);
            if (!ids.add(entry.value().portableId()))
            {
                throw new IllegalArgumentException("Duplicate NMR policy UUID: " + entry.externalId());
            }
            result.add(entry);
        }
        var ordered = result.stream().map(entry -> entry.value().terms())
                .sorted(Comparator.comparing(NmrPolicyTerms::policyCode).thenComparing(NmrPolicyTerms::category)
                        .thenComparing(NmrPolicyTerms::validFrom)).toList();
        for (int index = 1; index < ordered.size(); index++)
        {
            if (NmrPolicyService.overlaps(ordered.get(index - 1), ordered.get(index)))
            {
                throw new IllegalArgumentException("NMR policy source effective intervals overlap.");
            }
        }
        return List.copyOf(result);
    }

    static Entry parseEntry(JsonNode node)
    {
        fields(node, FIELDS);
        String id = text(node, "policyVersionId");
        if (id.length() > 160 || id.lastIndexOf(':') < 0)
        {
            throw new IllegalArgumentException("NMR policyVersionId must be a qualified native UUID identity.");
        }
        String uuidText = id.substring(id.lastIndexOf(':') + 1);
        UUID uuid = UUID.fromString(uuidText);
        if (!uuid.toString().equals(uuidText))
        {
            throw new IllegalArgumentException("NMR policy UUID must use canonical UUID format.");
        }
        JsonNode rateNode = node.path("rate");
        if ((!rateNode.isNumber() && !rateNode.isTextual()) || rateNode.asText().length() > 40)
        {
            throw new IllegalArgumentException("NMR policy rate must be an explicit exact decimal.");
        }
        java.math.BigDecimal rate = new java.math.BigDecimal(rateNode.asText());
        if (rate.signum() < 0 || rate.stripTrailingZeros().scale() > 4
                || (long) rate.precision() - rate.scale() > 15)
        {
            throw new IllegalArgumentException("NMR policy rate must fit a nonnegative DECIMAL(19,4).");
        }
        NmrPolicyTerms terms = new NmrPolicyTerms(text(node, "policyCode"), text(node, "category"),
                LocalDate.parse(text(node, "validFrom")), node.has("validTo") ? LocalDate.parse(text(node, "validTo")) : null,
                rate, text(node, "currency"), text(node, "dateBasis"), text(node, "roundingRule"),
                text(node, "eligibility"), text(node, "exemptions"), text(node, "recipient"), text(node, "reporting"),
                text(node, "merchantPath"), text(node, "sourceReference"), text(node, "authority"),
                LocalDate.parse(text(node, "sourceDate")));
        NmrPolicyImport imported = new NmrPolicyImport(uuid, text(node, "requestHash"),
                Instant.parse(text(node, "savedAt")), text(node, "savedBy"), terms);
        return new Entry(id, imported);
    }

    private static void fields(JsonNode node, Set<String> allowed)
    {
        if (!node.isObject())
        {
            throw new IllegalArgumentException("NMR policy extension/entry must be an object.");
        }
        node.fieldNames().forEachRemaining(name ->
        {
            if (!allowed.contains(name))
            {
                throw new IllegalArgumentException("Unsupported NMR policy field: " + name);
            }
        });
    }

    private static String text(JsonNode node, String field)
    {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank())
        {
            throw new IllegalArgumentException("NMR policy " + field + " must be nonblank text.");
        }
        return value.textValue();
    }

    record Entry(String externalId, NmrPolicyImport value)
    {
    }
}
