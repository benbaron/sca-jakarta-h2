package org.nonprofitbookkeeping.interchange.sclx;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Governed fixed-asset and completed-depreciation extension for SCLX 1.3. */
public final class SclxFixedAssetsExtension
{
    public static final String KEY = "fixedAssets";

    private SclxFixedAssetsExtension()
    {
    }

    public static Map<String, Object> value(
            List<Map<String, Object>> assets,
            List<Map<String, Object>> depreciationRuns)
    {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("version", 1);
        value.put("assets", List.copyOf(assets));
        value.put("depreciationRuns", List.copyOf(depreciationRuns));
        return Map.copyOf(value);
    }

    public static Map<String, Object> assetEntry(
            String assetId,
            String name,
            LocalDate acquisitionDate,
            BigDecimal acquisitionCost,
            BigDecimal salvageValue,
            int usefulLifeMonths,
            String depreciationMethod,
            BigDecimal openingAccumulatedDepreciation,
            String status,
            String notes,
            String assetAccountId,
            String accumulatedDepreciationAccountId,
            String depreciationExpenseAccountId,
            String fundId,
            Instant createdAt,
            Instant updatedAt)
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("assetId", assetId);
        entry.put("name", name);
        entry.put("acquisitionDate", acquisitionDate);
        entry.put("acquisitionCost", acquisitionCost);
        entry.put("salvageValue", salvageValue);
        entry.put("usefulLifeMonths", usefulLifeMonths);
        entry.put("depreciationMethod", depreciationMethod);
        entry.put("openingAccumulatedDepreciation", openingAccumulatedDepreciation);
        entry.put("status", status);
        putOptional(entry, "notes", notes);
        entry.put("assetAccountId", assetAccountId);
        entry.put("accumulatedDepreciationAccountId", accumulatedDepreciationAccountId);
        entry.put("depreciationExpenseAccountId", depreciationExpenseAccountId);
        entry.put("fundId", fundId);
        entry.put("createdAt", createdAt);
        entry.put("updatedAt", updatedAt);
        return Map.copyOf(entry);
    }

    public static Map<String, Object> depreciationRunEntry(
            String depreciationRunId,
            String assetId,
            LocalDate runDate,
            BigDecimal depreciationAmount,
            String transactionId,
            String notes,
            Instant createdAt)
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("depreciationRunId", depreciationRunId);
        entry.put("assetId", assetId);
        entry.put("runDate", runDate);
        entry.put("depreciationAmount", depreciationAmount);
        entry.put("transactionId", transactionId);
        putOptional(entry, "notes", notes);
        entry.put("createdAt", createdAt);
        return Map.copyOf(entry);
    }

    private static void putOptional(Map<String, Object> entry, String key, Object value)
    {
        if (value != null)
        {
            entry.put(key, value);
        }
    }

    public static Data data(SclxExportDocument.Extensions extensions)
    {
        Object raw = extensions.scaJakartaH2().get(KEY);
        if (raw == null)
        {
            return new Data(List.of(), List.of());
        }
        if (!(raw instanceof Map<?, ?> root))
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.fixedAssets must be an object");
        }
        if (!root.keySet().equals(Set.of("version", "assets", "depreciationRuns")))
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.fixedAssets has unsupported fields");
        }
        if (SclxExtensionValueReader.integer(root, "version", "extensions.scaJakartaH2.fixedAssets") != 1)
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.fixedAssets.version must be 1");
        }

        List<AssetEntry> assets = new ArrayList<>();
        List<Map<?, ?>> assetObjects = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "assets", "extensions.scaJakartaH2.fixedAssets"),
                "extensions.scaJakartaH2.fixedAssets.assets",
                Set.of("assetId", "name", "acquisitionDate", "acquisitionCost", "salvageValue",
                        "usefulLifeMonths", "depreciationMethod", "openingAccumulatedDepreciation",
                        "status", "notes", "assetAccountId", "accumulatedDepreciationAccountId",
                        "depreciationExpenseAccountId", "fundId", "createdAt", "updatedAt"));
        for (int index = 0; index < assetObjects.size(); index++)
        {
            Map<?, ?> value = assetObjects.get(index);
            String path = "extensions.scaJakartaH2.fixedAssets.assets[" + index + ']';
            assets.add(new AssetEntry(
                    SclxExtensionValueReader.text(value, "assetId", path),
                    SclxExtensionValueReader.text(value, "name", path),
                    SclxExtensionValueReader.date(value, "acquisitionDate", path, false),
                    SclxExtensionValueReader.decimal(value, "acquisitionCost", path, false),
                    SclxExtensionValueReader.decimal(value, "salvageValue", path, false),
                    SclxExtensionValueReader.integer(value, "usefulLifeMonths", path),
                    SclxExtensionValueReader.text(value, "depreciationMethod", path),
                    SclxExtensionValueReader.decimal(value, "openingAccumulatedDepreciation", path, false),
                    SclxExtensionValueReader.text(value, "status", path),
                    SclxExtensionValueReader.optionalText(value, "notes", path),
                    SclxExtensionValueReader.text(value, "assetAccountId", path),
                    SclxExtensionValueReader.text(value, "accumulatedDepreciationAccountId", path),
                    SclxExtensionValueReader.text(value, "depreciationExpenseAccountId", path),
                    SclxExtensionValueReader.text(value, "fundId", path),
                    SclxExtensionValueReader.instant(value, "createdAt", path, false),
                    SclxExtensionValueReader.instant(value, "updatedAt", path, false)));
        }

        List<DepreciationRunEntry> depreciationRuns = new ArrayList<>();
        List<Map<?, ?>> runObjects = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "depreciationRuns", "extensions.scaJakartaH2.fixedAssets"),
                "extensions.scaJakartaH2.fixedAssets.depreciationRuns",
                Set.of("depreciationRunId", "assetId", "runDate", "depreciationAmount",
                        "transactionId", "notes", "createdAt"));
        for (int index = 0; index < runObjects.size(); index++)
        {
            Map<?, ?> value = runObjects.get(index);
            String path = "extensions.scaJakartaH2.fixedAssets.depreciationRuns[" + index + ']';
            depreciationRuns.add(new DepreciationRunEntry(
                    SclxExtensionValueReader.text(value, "depreciationRunId", path),
                    SclxExtensionValueReader.text(value, "assetId", path),
                    SclxExtensionValueReader.date(value, "runDate", path, false),
                    SclxExtensionValueReader.decimal(value, "depreciationAmount", path, false),
                    SclxExtensionValueReader.text(value, "transactionId", path),
                    SclxExtensionValueReader.optionalText(value, "notes", path),
                    SclxExtensionValueReader.instant(value, "createdAt", path, false)));
        }
        return new Data(assets, depreciationRuns);
    }

    public static Set<String> uniqueAssetIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        for (AssetEntry asset : data.assets())
        {
            if (!ids.add(asset.assetId()))
            {
                throw new IllegalArgumentException("duplicate fixed asset identity: " + asset.assetId());
            }
        }
        return Set.copyOf(ids);
    }

    public static void requireUniqueRunIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        for (DepreciationRunEntry run : data.depreciationRuns())
        {
            if (!ids.add(run.depreciationRunId()))
            {
                throw new IllegalArgumentException("duplicate depreciation-run identity: " + run.depreciationRunId());
            }
        }
    }

    public record Data(List<AssetEntry> assets, List<DepreciationRunEntry> depreciationRuns)
    {
        public Data
        {
            assets = List.copyOf(Objects.requireNonNull(assets, "assets"));
            depreciationRuns = List.copyOf(Objects.requireNonNull(depreciationRuns, "depreciationRuns"));
        }
    }

    public record AssetEntry(
            String assetId,
            String name,
            LocalDate acquisitionDate,
            BigDecimal acquisitionCost,
            BigDecimal salvageValue,
            int usefulLifeMonths,
            String depreciationMethod,
            BigDecimal openingAccumulatedDepreciation,
            String status,
            String notes,
            String assetAccountId,
            String accumulatedDepreciationAccountId,
            String depreciationExpenseAccountId,
            String fundId,
            Instant createdAt,
            Instant updatedAt)
    {
    }

    public record DepreciationRunEntry(
            String depreciationRunId,
            String assetId,
            LocalDate runDate,
            BigDecimal depreciationAmount,
            String transactionId,
            String notes,
            Instant createdAt)
    {
    }
}
