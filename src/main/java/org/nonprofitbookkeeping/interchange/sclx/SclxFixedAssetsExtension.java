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

/** Typed contract for selected-company fixed assets and completed depreciation runs. */
final class SclxFixedAssetsExtension
{
    static final String KEY = "fixedAssets";
    private static final String PATH = "extensions.scaJakartaH2.fixedAssets";
    private static final Set<String> ROOT_KEYS = Set.of("assets", "depreciationRuns");
    private static final Set<String> ASSET_KEYS = Set.of(
            "fixedAssetId", "assetAccountId", "accumulatedDepreciationAccountId",
            "depreciationExpenseAccountId", "fundId", "name", "acquisitionDate",
            "acquisitionCost", "salvageValue", "usefulLifeMonths", "depreciationMethod",
            "openingAccumulatedDepreciation", "status", "notes", "createdAt", "updatedAt");
    private static final Set<String> RUN_KEYS = Set.of(
            "depreciationRunId", "fixedAssetId", "runDate", "depreciationAmount",
            "transactionId", "notes", "createdAt");
    private static final Set<Integer> USEFUL_LIVES = Set.of(36, 60, 84);
    private static final Set<String> METHODS = Set.of("STRAIGHT_LINE");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISPOSED", "INACTIVE");

    private SclxFixedAssetsExtension()
    {
    }

    static Map<String, Object> value(
            List<Map<String, Object>> assets,
            List<Map<String, Object>> depreciationRuns)
    {
        return Map.of(
                "assets", List.copyOf(assets),
                "depreciationRuns", List.copyOf(depreciationRuns));
    }

    static Map<String, Object> assetEntry(
            String fixedAssetId,
            String assetAccountId,
            String accumulatedDepreciationAccountId,
            String depreciationExpenseAccountId,
            String fundId,
            String name,
            LocalDate acquisitionDate,
            BigDecimal acquisitionCost,
            BigDecimal salvageValue,
            int usefulLifeMonths,
            String depreciationMethod,
            BigDecimal openingAccumulatedDepreciation,
            String status,
            String notes,
            Instant createdAt,
            Instant updatedAt)
    {
        AssetEntry validated = new AssetEntry(
                fixedAssetId,
                assetAccountId,
                accumulatedDepreciationAccountId,
                depreciationExpenseAccountId,
                fundId,
                name,
                acquisitionDate,
                acquisitionCost,
                salvageValue,
                usefulLifeMonths,
                depreciationMethod,
                openingAccumulatedDepreciation,
                status,
                optionalText(notes),
                createdAt,
                updatedAt);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("fixedAssetId", validated.fixedAssetId());
        entry.put("assetAccountId", validated.assetAccountId());
        entry.put("accumulatedDepreciationAccountId", validated.accumulatedDepreciationAccountId());
        entry.put("depreciationExpenseAccountId", validated.depreciationExpenseAccountId());
        entry.put("fundId", validated.fundId());
        entry.put("name", validated.name());
        entry.put("acquisitionDate", validated.acquisitionDate());
        entry.put("acquisitionCost", validated.acquisitionCost());
        entry.put("salvageValue", validated.salvageValue());
        entry.put("usefulLifeMonths", validated.usefulLifeMonths());
        entry.put("depreciationMethod", validated.depreciationMethod());
        entry.put("openingAccumulatedDepreciation", validated.openingAccumulatedDepreciation());
        entry.put("status", validated.status());
        entry.put("notes", validated.notes());
        entry.put("createdAt", validated.createdAt());
        entry.put("updatedAt", validated.updatedAt());
        return java.util.Collections.unmodifiableMap(entry);
    }

    static Map<String, Object> depreciationRunEntry(
            String depreciationRunId,
            String fixedAssetId,
            LocalDate runDate,
            BigDecimal depreciationAmount,
            String transactionId,
            String notes,
            Instant createdAt)
    {
        DepreciationRunEntry validated = new DepreciationRunEntry(
                depreciationRunId,
                fixedAssetId,
                runDate,
                depreciationAmount,
                transactionId,
                optionalText(notes),
                createdAt);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("depreciationRunId", validated.depreciationRunId());
        entry.put("fixedAssetId", validated.fixedAssetId());
        entry.put("runDate", validated.runDate());
        entry.put("depreciationAmount", validated.depreciationAmount());
        entry.put("transactionId", validated.transactionId());
        entry.put("notes", validated.notes());
        entry.put("createdAt", validated.createdAt());
        return java.util.Collections.unmodifiableMap(entry);
    }

    static Data data(SclxExportDocument.Extensions extensions)
    {
        Objects.requireNonNull(extensions, "extensions");
        Object raw = extensions.scaJakartaH2().get(KEY);
        if (raw == null)
        {
            return new Data(List.of(), List.of());
        }
        if (!(raw instanceof Map<?, ?> root))
        {
            throw new IllegalArgumentException(PATH + " must be an object");
        }
        if (!root.keySet().equals(ROOT_KEYS))
        {
            throw new IllegalArgumentException(PATH + " has unsupported fields");
        }

        List<AssetEntry> assets = new ArrayList<>();
        List<Map<?, ?>> assetMaps = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "assets", PATH),
                PATH + ".assets",
                ASSET_KEYS);
        for (int index = 0; index < assetMaps.size(); index++)
        {
            Map<?, ?> map = assetMaps.get(index);
            String path = PATH + ".assets[" + index + ']';
            assets.add(new AssetEntry(
                    SclxExtensionValueReader.text(map, "fixedAssetId", path),
                    SclxExtensionValueReader.text(map, "assetAccountId", path),
                    SclxExtensionValueReader.text(map, "accumulatedDepreciationAccountId", path),
                    SclxExtensionValueReader.text(map, "depreciationExpenseAccountId", path),
                    SclxExtensionValueReader.text(map, "fundId", path),
                    SclxExtensionValueReader.text(map, "name", path),
                    SclxExtensionValueReader.date(map, "acquisitionDate", path, false),
                    SclxExtensionValueReader.decimal(map, "acquisitionCost", path, false),
                    SclxExtensionValueReader.decimal(map, "salvageValue", path, false),
                    SclxExtensionValueReader.integer(map, "usefulLifeMonths", path),
                    SclxExtensionValueReader.text(map, "depreciationMethod", path),
                    SclxExtensionValueReader.decimal(map, "openingAccumulatedDepreciation", path, false),
                    SclxExtensionValueReader.text(map, "status", path),
                    SclxExtensionValueReader.optionalText(map, "notes", path),
                    SclxExtensionValueReader.instant(map, "createdAt", path, false),
                    SclxExtensionValueReader.instant(map, "updatedAt", path, false)));
        }

        List<DepreciationRunEntry> runs = new ArrayList<>();
        List<Map<?, ?>> runMaps = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "depreciationRuns", PATH),
                PATH + ".depreciationRuns",
                RUN_KEYS);
        for (int index = 0; index < runMaps.size(); index++)
        {
            Map<?, ?> map = runMaps.get(index);
            String path = PATH + ".depreciationRuns[" + index + ']';
            runs.add(new DepreciationRunEntry(
                    SclxExtensionValueReader.text(map, "depreciationRunId", path),
                    SclxExtensionValueReader.text(map, "fixedAssetId", path),
                    SclxExtensionValueReader.date(map, "runDate", path, false),
                    SclxExtensionValueReader.decimal(map, "depreciationAmount", path, false),
                    SclxExtensionValueReader.text(map, "transactionId", path),
                    SclxExtensionValueReader.optionalText(map, "notes", path),
                    SclxExtensionValueReader.instant(map, "createdAt", path, false)));
        }
        return new Data(List.copyOf(assets), List.copyOf(runs));
    }

    static Set<String> uniqueAssetIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        data.assets().forEach(entry -> requireUnique(ids, entry.fixedAssetId(), "fixed asset"));
        return ids;
    }

    static Set<String> uniqueDepreciationRunIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        data.depreciationRuns().forEach(entry ->
                requireUnique(ids, entry.depreciationRunId(), "depreciation run"));
        return ids;
    }

    private static void requireUnique(Set<String> ids, String identity, String type)
    {
        if (!ids.add(identity))
        {
            throw new IllegalArgumentException("duplicate " + type + " portable identity: " + identity);
        }
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String optionalText(String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }

    record Data(List<AssetEntry> assets, List<DepreciationRunEntry> depreciationRuns)
    {
        Data
        {
            assets = List.copyOf(assets);
            depreciationRuns = List.copyOf(depreciationRuns);
        }
    }

    record AssetEntry(
            String fixedAssetId,
            String assetAccountId,
            String accumulatedDepreciationAccountId,
            String depreciationExpenseAccountId,
            String fundId,
            String name,
            LocalDate acquisitionDate,
            BigDecimal acquisitionCost,
            BigDecimal salvageValue,
            int usefulLifeMonths,
            String depreciationMethod,
            BigDecimal openingAccumulatedDepreciation,
            String status,
            String notes,
            Instant createdAt,
            Instant updatedAt)
    {
        AssetEntry
        {
            requireText(fixedAssetId, "fixedAssetId");
            requireText(assetAccountId, "assetAccountId");
            requireText(accumulatedDepreciationAccountId, "accumulatedDepreciationAccountId");
            requireText(depreciationExpenseAccountId, "depreciationExpenseAccountId");
            requireText(fundId, "fundId");
            requireText(name, "name");
            Objects.requireNonNull(acquisitionDate, "acquisitionDate");
            Objects.requireNonNull(acquisitionCost, "acquisitionCost");
            Objects.requireNonNull(salvageValue, "salvageValue");
            Objects.requireNonNull(openingAccumulatedDepreciation, "openingAccumulatedDepreciation");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (acquisitionCost.signum() < 0)
            {
                throw new IllegalArgumentException("acquisitionCost must be nonnegative");
            }
            if (salvageValue.signum() < 0 || salvageValue.compareTo(acquisitionCost) > 0)
            {
                throw new IllegalArgumentException("salvageValue must be nonnegative and not exceed acquisitionCost");
            }
            if (openingAccumulatedDepreciation.signum() < 0)
            {
                throw new IllegalArgumentException("openingAccumulatedDepreciation must be nonnegative");
            }
            if (!USEFUL_LIVES.contains(usefulLifeMonths))
            {
                throw new IllegalArgumentException("usefulLifeMonths must be 36, 60, or 84");
            }
            if (!METHODS.contains(requireText(depreciationMethod, "depreciationMethod")))
            {
                throw new IllegalArgumentException("unsupported depreciationMethod: " + depreciationMethod);
            }
            if (!STATUSES.contains(requireText(status, "status")))
            {
                throw new IllegalArgumentException("unsupported fixed asset status: " + status);
            }
            notes = optionalText(notes);
        }
    }

    record DepreciationRunEntry(
            String depreciationRunId,
            String fixedAssetId,
            LocalDate runDate,
            BigDecimal depreciationAmount,
            String transactionId,
            String notes,
            Instant createdAt)
    {
        DepreciationRunEntry
        {
            requireText(depreciationRunId, "depreciationRunId");
            requireText(fixedAssetId, "fixedAssetId");
            Objects.requireNonNull(runDate, "runDate");
            Objects.requireNonNull(depreciationAmount, "depreciationAmount");
            requireText(transactionId, "transactionId");
            Objects.requireNonNull(createdAt, "createdAt");
            if (depreciationAmount.signum() <= 0)
            {
                throw new IllegalArgumentException("depreciationAmount must be positive");
            }
            notes = optionalText(notes);
        }
    }
}
