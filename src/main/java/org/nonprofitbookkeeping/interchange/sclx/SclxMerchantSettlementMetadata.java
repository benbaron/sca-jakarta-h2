package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.nonprofitbookkeeping.service.MerchantSettlementCommand;
import org.nonprofitbookkeeping.service.MerchantSettlementFacts;
import java.math.BigDecimal;
import java.util.*;

/** Strict optional allocation facts; money stays on canonical referenced lines. */
final class SclxMerchantSettlementMetadata
{
    record Component(String kind, String lineId)
    {
    }

    record Facts(String role, BigDecimal withheld, String evidence, List<Component> components)
    {
        MerchantSettlementCommand command(List<String> postingLineIds)
        {
            return new MerchantSettlementCommand(role, withheld, evidence, components.stream()
                    .map(c -> new MerchantSettlementCommand.Component(c.kind(), postingLineIds.indexOf(c.lineId()))).toList());
        }
    }

    private SclxMerchantSettlementMetadata()
    {
    }

    static Facts facts(JsonNode value)
    {
        requireFields(value, Set.of("version", "role", "withheldUnconfirmed", "evidenceReference", "components"));
        if (!value.path("version").isIntegralNumber() || !value.path("version").canConvertToInt() || value.path("version").intValue() != 1
                || !value.path("role").isTextual() || !value.path("components").isArray())
        {
            throw new IllegalStateException("Merchant settlement requires version 1, explicit role and component array.");
        }
        BigDecimal withheld = null;
        JsonNode amount = value.get("withheldUnconfirmed");
        if (!amount.isNull())
        {
            if (!amount.isTextual() || !amount.textValue().matches("[0-9]+(?:\\.[0-9]{1,4})?"))
            {
                throw new IllegalStateException("Observed withholding must be exact nonnegative decimal text with at most four places, or null.");
            }
            withheld = new BigDecimal(amount.textValue());
        }
        JsonNode evidence = value.get("evidenceReference");
        if (!evidence.isNull() && !evidence.isTextual())
        {
            throw new IllegalStateException("Merchant evidence must be text or null.");
        }
        List<Component> components = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode component : value.path("components"))
        {
            requireFields(component, Set.of("kind", "lineId"));
            if (!component.path("kind").isTextual() || !component.path("lineId").isTextual()
                    || component.path("lineId").textValue().isBlank() || !seen.add(component.path("lineId").textValue()))
            {
                throw new IllegalStateException("Merchant components require supported kind and distinct lineId.");
            }
            if (!Set.of("PAYOUT", "FEE", "NMR_DISCHARGE").contains(component.path("kind").textValue()))
            {
                throw new IllegalStateException("Unsupported merchant component kind.");
            }
            components.add(new Component(component.path("kind").textValue(), component.path("lineId").textValue()));
        }
        Facts facts = new Facts(value.path("role").textValue(), withheld, evidence.isNull() ? null : evidence.textValue().trim(),
                components.stream().sorted(Comparator.comparing(Component::kind).thenComparing(Component::lineId)).toList());
        validateValue(facts.command(components.stream().map(Component::lineId).toList()), "RECEIVABLE",
                "GROSS_RECEIPT".equals(facts.role()) ? "INCREASE" : "DECREASE");
        return facts;
    }

    private static BigDecimal amount(JsonNode node, String key)
    {
        try
        {
            return new BigDecimal(node.path(key).asText());
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalStateException("Merchant component amount is not an exact decimal.", ex);
        }
    }

    private static void validateValue(MerchantSettlementCommand command, String kind, String effect)
    {
        try
        {
            MerchantSettlementFacts.validate(command, kind, effect);
        }
        catch (RuntimeException ex)
        {
            throw new IllegalStateException("Invalid merchant settlement facts: " + ex.getMessage(), ex);
        }
    }

    private static void requireFields(JsonNode node, Set<String> expected)
    {
        Set<String> actual = new HashSet<>();
        if (node != null)
        {
            node.fieldNames().forEachRemaining(actual::add);
        }
        if (node == null || !node.isObject() || !actual.equals(expected))
        {
            throw new IllegalStateException("Merchant settlement object has missing or unsupported fields.");
        }
    }

    static void validate(JsonNode root)
    {
        boolean present = false;
        for (JsonNode detail : root.path("extensions").path("scaJakartaH2").path("supplementalDetails"))
        {
            present |= detail.has("merchantSettlement");
        }
        if (present)
        {
            SclxTransactionDetailImportData.parse(root);
        }
    }

    static void validateLinks(JsonNode root, Map<String, JsonNode> transactions, Map<String, JsonNode> lines, Map<String, String> merchants)
    {
        Map<String, JsonNode> accounts = new HashMap<>();
        root.path("chartOfAccounts").forEach(a -> accounts.put(a.path("accountId").asText(), a));
        Set<String> claimed = new HashSet<>();
        Map<String, String> itemDimensions = new HashMap<>();
        var details = root.path("extensions").path("scaJakartaH2").path("supplementalDetails");
        for (JsonNode detail : details)
        {
            if (!detail.has("merchantSettlement"))
            {
                continue;
            }
            Facts facts = facts(detail.get("merchantSettlement"));
            var lifecycle = SclxSupplementalLifecycle.link(detail.get("lifecycle"));
            if (lifecycle == null)
            {
                throw new IllegalStateException("Merchant settlement requires complete receivable lifecycle.");
            }
            validateValue(facts.command(new ArrayList<>(lines.keySet())), detail.path("kind").asText(), lifecycle.effect());
            JsonNode line = lines.get(lifecycle.lineId());
            JsonNode account = line == null ? null : accounts.get(line.path("accountId").asText());
            String merchant = merchants.get(lifecycle.lineId());
            if (line == null || account == null || !"ASSET".equals(account.path("type").asText()) || !"DEBIT".equals(account.path("increaseSide").asText())
                    || merchant == null || !line.path("activityId").isTextual() || line.path("activityId").asText().isBlank())
            {
                throw new IllegalStateException("Merchant receivable requires debit-normal ASSET account, Merchant and Event.");
            }
            String dims = line.path("accountId").asText() + "/" + line.path("fundId").asText() + "/" + line.path("activityId").asText() + "/" + merchant;
            String previous = itemDimensions.putIfAbsent(lifecycle.itemId().toString(), dims);
            if (previous != null && !previous.equals(dims))
            {
                throw new IllegalStateException("Merchant receivable item dimensions conflict.");
            }
            Set<String> owningLines = new HashSet<>();
            transactions.get(detail.path("transactionId").asText()).path("lines").forEach(l -> owningLines.add(l.path("lineId").asText()));
            BigDecimal total = BigDecimal.ZERO;
            for (Component component : facts.components())
            {
                JsonNode linked = lines.get(component.lineId());
                if (linked == null || !owningLines.contains(component.lineId()) || !claimed.add(component.lineId())
                        || component.lineId().equals(lifecycle.lineId()) || !merchant.equals(merchants.get(component.lineId()))
                        || !line.path("fundId").equals(linked.path("fundId")) || !line.path("activityId").equals(linked.path("activityId")))
                {
                    throw new IllegalStateException("Merchant component is duplicate, foreign or has different Merchant/Fund/Event.");
                }
                BigDecimal debit = amount(linked, "debit");
                if (debit.signum() <= 0 || amount(linked, "credit").signum() != 0)
                {
                    throw new IllegalStateException("Merchant component requires a debit line.");
                }
                JsonNode componentAccount = accounts.get(linked.path("accountId").asText());
                String type = componentAccount == null ? "" : componentAccount.path("type").asText();
                if ((component.kind().equals("PAYOUT") && (!type.equals("BANK") || !"DEBIT".equals(componentAccount.path("increaseSide").asText())))
                        || (component.kind().equals("FEE") && (!type.equals("EXPENSE") || !"DEBIT".equals(componentAccount.path("increaseSide").asText()))))
                {
                    throw new IllegalStateException("Merchant payout/fee account classification conflicts.");
                }
                if (component.kind().equals("NMR_DISCHARGE"))
                {
                    BigDecimal explained = BigDecimal.ZERO;
                    for (JsonNode allocation : details)
                    {
                        var allocationLink = SclxSupplementalLifecycle.link(allocation.get("lifecycle"));
                        if (allocationLink != null && component.lineId().equals(allocationLink.lineId()) && allocation.has("nmr")
                                && "DECREASE".equals(allocationLink.effect()))
                        {
                            var nmr = SclxNmrItemMetadata.facts(allocation.get("nmr"));
                            if ("MERCHANT_DISCHARGE".equals(nmr.movement()) && nmr.evidenceReference() != null)
                            {
                                explained = explained.add(amount(allocation, "amount"));
                            }
                        }
                    }
                    if (!type.equals("LIABILITY") || !"CREDIT".equals(componentAccount.path("increaseSide").asText())
                            || explained.compareTo(debit) != 0)
                    {
                        throw new IllegalStateException("Merchant NMR component is not fully evidenced MERCHANT_DISCHARGE.");
                    }
                }
                total = total.add(debit);
            }
            if (total.compareTo(amount(detail, "amount")) > 0)
            {
                throw new IllegalStateException("Merchant component total exceeds receivable allocation.");
            }
        }
        for (JsonNode detail : details)
        {
            var link = SclxSupplementalLifecycle.link(detail.get("lifecycle"));
            if (link != null && itemDimensions.containsKey(link.itemId().toString()))
            {
                JsonNode line = lines.get(link.lineId());
                String dims = line.path("accountId").asText() + "/" + line.path("fundId").asText() + "/" + line.path("activityId").asText() + "/" + merchants.get(link.lineId());
                if (!"RECEIVABLE".equals(detail.path("kind").asText()) || !itemDimensions.get(link.itemId().toString()).equals(dims))
                {
                    throw new IllegalStateException("All allocations of a designated merchant item must retain its dimensions.");
                }
            }
        }
        // Propagate by fixed point so backdated and reverse-of-reversal source order is irrelevant.
        int previousSize;
        do
        {
            previousSize = claimed.size();
            for (JsonNode transaction : transactions.values())
            {
                if (!"REVERSAL".equals(transaction.path("correctionType").asText()))
                {
                    continue;
                }
                JsonNode source = transactions.get(transaction.path("correctionOfTransactionId").asText());
                if (source == null)
                {
                    throw new IllegalStateException("Merchant correction source does not resolve.");
                }
                for (int index = 0; index < source.path("lines").size(); index++)
                {
                    JsonNode inverse = transaction.path("lines").get(index);
                    if (inverse != null && claimed.contains(source.path("lines").get(index).path("lineId").asText()))
                    {
                        claimed.add(inverse.path("lineId").asText());
                    }
                }
            }
        }
        while (claimed.size() != previousSize);
        // Inherited component facts use the original links; verify each inverse counterpart too.
        for (JsonNode transaction : transactions.values())
        {
            if (!"REVERSAL".equals(transaction.path("correctionType").asText()))
            {
                continue;
            }
            JsonNode source = transactions.get(transaction.path("correctionOfTransactionId").asText());
            if (source == null)
            {
                throw new IllegalStateException("Merchant correction source does not resolve.");
            }
            for (int index = 0; index < source.path("lines").size(); index++)
            {
                JsonNode original = source.path("lines").get(index);
                if (!claimed.contains(original.path("lineId").asText()))
                {
                    continue;
                }
                JsonNode inverse = transaction.path("lines").get(index);
                if (inverse == null || !original.path("accountId").equals(inverse.path("accountId"))
                        || !original.path("fundId").equals(inverse.path("fundId")) || !original.path("activityId").equals(inverse.path("activityId"))
                        || !Objects.equals(merchants.get(original.path("lineId").asText()), merchants.get(inverse.path("lineId").asText()))
                        || amount(original, "debit").compareTo(amount(inverse, "credit")) != 0
                        || amount(original, "credit").compareTo(amount(inverse, "debit")) != 0)
                {
                    throw new IllegalStateException("Merchant component inverse must preserve source dimensions and opposite amounts.");
                }
                claimed.add(inverse.path("lineId").asText());
            }
        }
    }

    static JsonNode identityDocument(JsonNode root)
    {
        validate(root);
        JsonNode copy = root.deepCopy();
        Map<String, ObjectNode> transactions = new HashMap<>();
        for (JsonNode transaction : copy.path("transactions"))
        {
            if (transaction.has("_scaMerchantSettlements"))
            {
                throw new IllegalStateException("Reserved merchant settlement transaction identity field in source.");
            }
            transactions.put(transaction.path("transactionId").asText(), (ObjectNode)transaction);
        }
        for (JsonNode detail : copy.path("extensions").path("scaJakartaH2").path("supplementalDetails"))
        {
            if (detail.has("merchantSettlement"))
            {
                Facts facts = facts(detail.get("merchantSettlement"));
                ObjectNode normalized = ((ObjectNode)detail.get("merchantSettlement")).deepCopy();
                if (facts.withheld() != null)
                {
                    normalized.put("withheldUnconfirmed", facts.withheld().setScale(4).toPlainString());
                }
                normalized.put("evidenceReference", facts.evidence());
                var components = normalized.putArray("components");
                facts.components().forEach(c -> components.addObject().put("kind", c.kind()).put("lineId", c.lineId()));
                ((ObjectNode)detail).set("merchantSettlement", normalized);
                ObjectNode transaction = transactions.get(detail.path("transactionId").asText());
                ObjectNode metadata = transaction.has("_scaMerchantSettlements") ? (ObjectNode)transaction.get("_scaMerchantSettlements") : transaction.putObject("_scaMerchantSettlements");
                metadata.set(detail.path("supplementalDetailId").asText(), normalized);
            }
        }
        return copy;
    }
}
