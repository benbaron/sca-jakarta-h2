package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.nonprofitbookkeeping.model.AssetAcquisitionSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Sparse acquisition provenance; intrinsic asset and line identities remain authoritative. */
final class SclxAssetAcquisitions
{
    static final String KEY = "assetAcquisitions";
    private static final String IDENTITY_FIELD = "_scaAssetAcquisition";

    record Fact(AssetAcquisitionSource source, String lineId) {}

    private SclxAssetAcquisitions()
    {
    }

    static Map<String, Fact> parse(JsonNode root)
    {
        JsonNode extension = root.path("extensions").path("scaJakartaH2").path(KEY);
        if (extension.isMissingNode())
        {
            return Map.of();
        }
        fields(extension, Set.of("version", "assets"));
        if (!extension.path("version").isIntegralNumber() || !extension.path("version").canConvertToInt()
                || extension.path("version").intValue() != 1 || !extension.path("assets").isArray())
        {
            throw new IllegalStateException("assetAcquisitions requires version 1 and an assets array.");
        }
        Map<String, JsonNode> assets = new HashMap<>(), accounts = new HashMap<>(), lines = new HashMap<>(), parents = new HashMap<>();
        root.path("extensions").path("scaJakartaH2").path("fixedAssets").path("assets")
                .forEach(a -> unique(assets, a.path("assetId").asText(), a));
        root.path("chartOfAccounts").forEach(a -> unique(accounts, a.path("accountId").asText(), a));
        for (JsonNode transaction : root.path("transactions"))
        {
            for (JsonNode line : transaction.path("lines"))
            {
                String id = text(line, "lineId");
                unique(lines, id, line); parents.put(id, transaction);
            }
        }
        Map<String, Fact> result = new LinkedHashMap<>();
        Map<String, BigDecimal> claims = new HashMap<>();
        for (JsonNode node : extension.path("assets"))
        {
            AssetAcquisitionSource source;
            try
            {
                source = AssetAcquisitionSource.valueOf(text(node, "source"));
            }
            catch (IllegalArgumentException ex)
            {
                throw new IllegalStateException("Unknown asset acquisition source.", ex);
            }
            boolean linked = source != AssetAcquisitionSource.REGISTER_ONLY;
            fields(node, linked ? Set.of("assetId", "source", "lineId") : Set.of("assetId", "source"));
            String assetId = text(node, "assetId"), lineId = linked ? text(node, "lineId") : null;
            JsonNode asset = assets.get(assetId);
            if (asset == null || result.putIfAbsent(assetId, new Fact(source, lineId)) != null)
            {
                throw new IllegalStateException("Acquisition requires a unique resolved fixed asset: " + assetId);
            }
            if (!linked)
            {
                continue;
            }
            JsonNode line = lines.get(lineId), transaction = parents.get(lineId);
            JsonNode account = line == null ? null : accounts.get(line.path("accountId").asText());
            if (account == null || !"ASSET".equals(account.path("type").asText())
                    || !"FIXED_ASSET".equals(account.path("subtype").asText())
                    || !"DEBIT".equals(account.path("increaseSide").asText())
                    || !asset.path("assetAccountId").equals(line.path("accountId"))
                    || !asset.path("fundId").equals(line.path("fundId"))
                    || !asset.path("acquisitionDate").equals(transaction.path("transactionDate"))
                    || !"ENTERED".equals(transaction.path("status").asText())
                    || "REVERSAL".equals(transaction.path("correctionType").asText())
                    || amount(line, "debit").signum() <= 0 || amount(line, "credit").signum() != 0
                    || amount(asset, "acquisitionCost").signum() <= 0)
            {
                throw new IllegalStateException("Acquisition requires a matching ordinary fixed-asset debit: " + assetId);
            }
            BigDecimal claimed = claims.merge(lineId, amount(asset, "acquisitionCost"), BigDecimal::add);
            if (claimed.compareTo(amount(line, "debit")) > 0)
            {
                throw new IllegalStateException("Acquisition claims exceed source debit: " + lineId);
            }
            if (source == AssetAcquisitionSource.NEW_PURCHASE)
            {
                if (transaction.path("lines").size() != 2
                        || amount(line, "debit").compareTo(amount(asset, "acquisitionCost")) != 0)
                {
                    throw new IllegalStateException("NEW_PURCHASE requires an exact two-line asset/BANK entry: " + assetId);
                }
                JsonNode bankLine = null;
                for (JsonNode candidate : transaction.path("lines"))
                {
                    if (!lineId.equals(candidate.path("lineId").asText()))
                    {
                        bankLine = candidate;
                    }
                }
                JsonNode bankAccount = bankLine == null ? null : accounts.get(bankLine.path("accountId").asText());
                if (bankAccount == null || !"BANK".equals(bankAccount.path("type").asText())
                        || !asset.path("fundId").equals(bankLine.path("fundId"))
                        || amount(bankLine, "debit").signum() != 0
                        || amount(bankLine, "credit").compareTo(amount(asset, "acquisitionCost")) != 0)
                {
                    throw new IllegalStateException("NEW_PURCHASE requires an exact same-Fund BANK credit: " + assetId);
                }
            }
        }
        return Map.copyOf(result);
    }

    static JsonNode identityDocument(JsonNode root)
    {
        Map<String, Fact> facts = parse(root);
        JsonNode copy = root.deepCopy();
        for (JsonNode asset : copy.path("extensions").path("scaJakartaH2").path("fixedAssets").path("assets"))
        {
            if (asset.has(IDENTITY_FIELD))
            {
                throw new IllegalStateException("Reserved acquisition identity field.");
            }
            Fact fact = facts.get(asset.path("assetId").asText());
            if (fact != null)
            {
                ObjectNode identity = ((ObjectNode)asset).putObject(IDENTITY_FIELD);
                identity.put("source", fact.source().name());
                if (fact.lineId() != null)
                {
                    identity.put("lineId", fact.lineId());
                }
            }
        }
        return copy;
    }

    private static void unique(Map<String, JsonNode> values, String id, JsonNode node)
    {
        if (values.putIfAbsent(id, node) != null)
        {
            throw new IllegalStateException("Ambiguous acquisition reference: " + id);
        }
    }

    private static BigDecimal amount(JsonNode node, String key)
    {
        try
        {
            BigDecimal value = new BigDecimal(node.path(key).asText()).setScale(4, RoundingMode.UNNECESSARY);
            if (value.precision() > 19)
            {
                throw new ArithmeticException("Amount exceeds DECIMAL(19,4).");
            }
            return value;
        }
        catch (ArithmeticException | NumberFormatException ex)
        {
            throw new IllegalStateException("Invalid acquisition amount: " + key, ex);
        }
    }

    private static void fields(JsonNode node, Set<String> expected)
    {
        Set<String> actual = new HashSet<>(); node.fieldNames().forEachRemaining(actual::add);
        if (!node.isObject() || !actual.equals(expected))
        {
            throw new IllegalStateException("assetAcquisitions object requires exactly " + expected);
        }
    }

    private static String text(JsonNode node, String key)
    {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.textValue().isBlank())
        {
            throw new IllegalStateException("assetAcquisitions " + key + " requires nonblank text.");
        }
        return value.textValue();
    }
}
