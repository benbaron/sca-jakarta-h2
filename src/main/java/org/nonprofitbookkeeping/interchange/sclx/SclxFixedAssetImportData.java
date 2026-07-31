package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import org.nonprofitbookkeeping.model.FixedAsset;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Set;

/** Strict, non-mutating projection of the governed SCLX fixed-assets extension. */
final class SclxFixedAssetImportData
{
    private final List<AssetValue> assets;
    private final List<RunValue> runs;

    private SclxFixedAssetImportData(List<AssetValue> assets, List<RunValue> runs)
    {
        this.assets = List.copyOf(assets);
        this.runs = List.copyOf(runs);
    }

    static SclxFixedAssetImportData parse(JsonNode root)
    {
        JsonNode value = root.path("extensions").path("scaJakartaH2").path("fixedAssets");
        if (value.isMissingNode() || value.isNull())
        {
            return new SclxFixedAssetImportData(List.of(), List.of());
        }
        requireObject(value, "$.extensions.scaJakartaH2.fixedAssets");
        requireFields(value, Set.of("version", "assets", "depreciationRuns"),
                Set.of("version", "assets", "depreciationRuns"),
                "$.extensions.scaJakartaH2.fixedAssets");
        if (integer(value, "version", "$.extensions.scaJakartaH2.fixedAssets") != 1)
        {
            throw new IllegalStateException("$.extensions.scaJakartaH2.fixedAssets.version must be 1.");
        }
        JsonNode assetNodes = requiredArray(value, "assets", "$.extensions.scaJakartaH2.fixedAssets");
        JsonNode runNodes = requiredArray(value, "depreciationRuns", "$.extensions.scaJakartaH2.fixedAssets");

        List<AssetValue> assets = new ArrayList<>();
        Set<String> assetIds = new HashSet<>();
        for (int index = 0; index < assetNodes.size(); index++)
        {
            JsonNode asset = assetNodes.get(index);
            String path = "$.extensions.scaJakartaH2.fixedAssets.assets[" + index + "]";
            requireObject(asset, path);
            requireFields(asset,
                    Set.of("assetId", "name", "acquisitionDate", "acquisitionCost", "salvageValue",
                            "usefulLifeMonths", "depreciationMethod", "openingAccumulatedDepreciation",
                            "status", "notes", "assetAccountId", "accumulatedDepreciationAccountId",
                            "depreciationExpenseAccountId", "fundId", "createdAt", "updatedAt"),
                    Set.of("assetId", "name", "acquisitionDate", "acquisitionCost", "salvageValue",
                            "usefulLifeMonths", "depreciationMethod", "openingAccumulatedDepreciation",
                            "status", "assetAccountId", "accumulatedDepreciationAccountId",
                            "depreciationExpenseAccountId", "fundId", "createdAt", "updatedAt"),
                    path);
            String externalId = uniqueId(asset, "assetId", path, assetIds, "fixed asset");
            BigDecimal acquisitionCost = money(asset, "acquisitionCost", path, false);
            BigDecimal salvageValue = money(asset, "salvageValue", path, false);
            if (salvageValue.compareTo(acquisitionCost) > 0)
            {
                throw new IllegalStateException(path + ".salvageValue cannot exceed acquisitionCost.");
            }
            BigDecimal opening = money(asset, "openingAccumulatedDepreciation", path, false);
            int usefulLife = integer(asset, "usefulLifeMonths", path);
            if (usefulLife != 36 && usefulLife != 60 && usefulLife != 84)
            {
                throw new IllegalStateException(path + ".usefulLifeMonths must be 36, 60, or 84.");
            }
            assets.add(new AssetValue(
                    externalId,
                    boundedText(asset, "name", path, 200),
                    date(asset, "acquisitionDate", path),
                    acquisitionCost,
                    salvageValue,
                    usefulLife,
                    enumValue(FixedAsset.DepreciationMethod.class,
                            text(asset, "depreciationMethod", path), path + ".depreciationMethod"),
                    opening,
                    enumValue(FixedAsset.Status.class, text(asset, "status", path), path + ".status"),
                    optionalText(asset, "notes", path),
                    text(asset, "assetAccountId", path),
                    text(asset, "accumulatedDepreciationAccountId", path),
                    text(asset, "depreciationExpenseAccountId", path),
                    text(asset, "fundId", path),
                    instant(asset, "createdAt", path),
                    instant(asset, "updatedAt", path)));
        }

        List<RunValue> runs = new ArrayList<>();
        Set<String> runIds = new HashSet<>();
        Set<String> assetPeriods = new HashSet<>();
        for (int index = 0; index < runNodes.size(); index++)
        {
            JsonNode run = runNodes.get(index);
            String path = "$.extensions.scaJakartaH2.fixedAssets.depreciationRuns[" + index + "]";
            requireObject(run, path);
            requireFields(run,
                    Set.of("depreciationRunId", "assetId", "runDate", "depreciationAmount",
                            "transactionId", "notes", "createdAt"),
                    Set.of("depreciationRunId", "assetId", "runDate", "depreciationAmount",
                            "transactionId", "createdAt"),
                    path);
            String externalId = uniqueId(run, "depreciationRunId", path, runIds, "depreciation run");
            String assetId = text(run, "assetId", path);
            if (!assetIds.contains(assetId))
            {
                throw new IllegalStateException(path + ".assetId does not resolve to an imported fixed asset.");
            }
            LocalDate runDate = date(run, "runDate", path);
            if (!assetPeriods.add(assetId + '\u0000' + runDate))
            {
                throw new IllegalStateException(path + " duplicates a fixed-asset run date.");
            }
            runs.add(new RunValue(
                    externalId,
                    assetId,
                    runDate,
                    money(run, "depreciationAmount", path, true),
                    text(run, "transactionId", path),
                    optionalText(run, "notes", path),
                    instant(run, "createdAt", path)));
        }
        assets.sort(Comparator.comparing(AssetValue::externalId));
        runs.sort(Comparator.comparing(RunValue::externalId));
        return new SclxFixedAssetImportData(assets, runs);
    }

    List<AssetValue> assets()
    {
        return assets;
    }

    List<RunValue> runs()
    {
        return runs;
    }

    private static String uniqueId(
            JsonNode value,
            String field,
            String path,
            Set<String> identities,
            String label)
    {
        String identity = text(value, field, path);
        if (!identities.add(identity))
        {
            throw new IllegalStateException("SCLX contains duplicate " + label + " identity " + identity + ".");
        }
        return identity;
    }

    private static JsonNode requiredArray(JsonNode value, String field, String path)
    {
        JsonNode node = value.get(field);
        if (node == null || !node.isArray())
        {
            throw new IllegalStateException(path + "." + field + " must be an array.");
        }
        return node;
    }

    private static void requireObject(JsonNode value, String path)
    {
        if (value == null || !value.isObject())
        {
            throw new IllegalStateException(path + " must be an object.");
        }
    }

    private static void requireFields(
            JsonNode value,
            Set<String> allowed,
            Set<String> required,
            String path)
    {
        Set<String> present = new HashSet<>();
        Iterator<String> names = value.fieldNames();
        while (names.hasNext())
        {
            String name = names.next();
            if (!allowed.contains(name))
            {
                throw new IllegalStateException(path + " has unsupported field " + name + ".");
            }
            present.add(name);
        }
        if (!present.containsAll(required))
        {
            Set<String> missing = new HashSet<>(required);
            missing.removeAll(present);
            throw new IllegalStateException(path + " is missing required fields " + missing + ".");
        }
    }

    private static String boundedText(JsonNode value, String field, String path, int limit)
    {
        String result = text(value, field, path);
        if (result.length() > limit)
        {
            throw new IllegalStateException(path + "." + field + " exceeds " + limit + " characters.");
        }
        return result;
    }

    private static String text(JsonNode value, String field, String path)
    {
        String result = optionalText(value, field, path);
        if (result == null)
        {
            throw new IllegalStateException(path + "." + field + " must be a nonblank string.");
        }
        return result;
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
        String result = node.textValue().trim();
        return result.isEmpty() ? null : result;
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

    private static BigDecimal money(JsonNode value, String field, String path, boolean positive)
    {
        JsonNode node = value.get(field);
        if (node == null || (!node.isTextual() && !node.isNumber()))
        {
            throw new IllegalStateException(path + "." + field + " must be a decimal value.");
        }
        try
        {
            BigDecimal amount = new BigDecimal(node.asText());
            if (amount.scale() > 4 || amount.setScale(4, RoundingMode.UNNECESSARY).precision() > 19)
            {
                throw new IllegalStateException(path + "." + field + " exceeds DECIMAL(19,4).");
            }
            if (positive ? amount.signum() <= 0 : amount.signum() < 0)
            {
                throw new IllegalStateException(path + "." + field
                        + (positive ? " must be positive." : " must be nonnegative."));
            }
            return amount.setScale(4, RoundingMode.UNNECESSARY);
        }
        catch (ArithmeticException | NumberFormatException ex)
        {
            throw new IllegalStateException(path + "." + field + " must be a DECIMAL(19,4) value.", ex);
        }
    }

    private static LocalDate date(JsonNode value, String field, String path)
    {
        try
        {
            return LocalDate.parse(text(value, field, path));
        }
        catch (DateTimeParseException ex)
        {
            throw new IllegalStateException(path + "." + field + " must use ISO date format.", ex);
        }
    }

    private static Instant instant(JsonNode value, String field, String path)
    {
        try
        {
            return Instant.parse(text(value, field, path));
        }
        catch (DateTimeParseException ex)
        {
            throw new IllegalStateException(path + "." + field + " must use ISO instant format.", ex);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String path)
    {
        try
        {
            return Enum.valueOf(type, value);
        }
        catch (IllegalArgumentException ex)
        {
            throw new IllegalStateException(path + " has unsupported value " + value + ".", ex);
        }
    }

    record AssetValue(
            String externalId,
            String name,
            LocalDate acquisitionDate,
            BigDecimal acquisitionCost,
            BigDecimal salvageValue,
            int usefulLifeMonths,
            FixedAsset.DepreciationMethod depreciationMethod,
            BigDecimal openingAccumulatedDepreciation,
            FixedAsset.Status status,
            String notes,
            String assetAccountId,
            String accumulatedDepreciationAccountId,
            String depreciationExpenseAccountId,
            String fundId,
            Instant createdAt,
            Instant updatedAt)
    {
    }

    record RunValue(
            String externalId,
            String assetId,
            LocalDate runDate,
            BigDecimal amount,
            String transactionId,
            String notes,
            Instant createdAt)
    {
    }
}
