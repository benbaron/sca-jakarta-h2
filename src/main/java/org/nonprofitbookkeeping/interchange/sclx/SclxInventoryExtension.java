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

/** Governed inventory-item and movement extension for SCLX 1.3. */
public final class SclxInventoryExtension
{
    public static final String KEY = "inventory";

    private SclxInventoryExtension()
    {
    }

    public static Map<String, Object> value(List<Map<String, Object>> items, List<Map<String, Object>> movements)
    {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("version", 2);
        value.put("items", List.copyOf(items));
        value.put("movements", List.copyOf(movements));
        return Map.copyOf(value);
    }

    public static Map<String, Object> itemEntry(
            String itemId, String name, String itemType, BigDecimal quantity, String unit,
            BigDecimal unitValue, BigDecimal carryingValue, String valuationMethod,
            String financialTreatment, LocalDate acquisitionDate, String custodian, String storageLocation,
            String condition, String status, String notes, String inventoryAccountId, String fundId,
            Instant createdAt, Instant updatedAt)
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("itemId", itemId);
        entry.put("name", name);
        entry.put("itemType", itemType);
        entry.put("quantity", quantity);
        entry.put("unit", unit);
        entry.put("unitValue", unitValue);
        entry.put("carryingValue", carryingValue);
        entry.put("valuationMethod", valuationMethod);
        entry.put("financialTreatment", financialTreatment);
        entry.put("acquisitionDate", acquisitionDate);
        putOptional(entry, "custodian", custodian);
        putOptional(entry, "storageLocation", storageLocation);
        entry.put("condition", condition);
        entry.put("status", status);
        putOptional(entry, "notes", notes);
        entry.put("inventoryAccountId", inventoryAccountId);
        entry.put("fundId", fundId);
        entry.put("createdAt", createdAt);
        entry.put("updatedAt", updatedAt);
        return Map.copyOf(entry);
    }

    /** Compatibility overload for version-1 capitalized inventory writers. */
    public static Map<String, Object> itemEntry(
            String itemId, String name, String itemType, BigDecimal quantity, String unit,
            BigDecimal unitValue, String financialTreatment, LocalDate acquisitionDate,
            String custodian, String storageLocation, String condition, String status, String notes,
            String inventoryAccountId, String fundId, Instant createdAt, Instant updatedAt)
    {
        return itemEntry(itemId, name, itemType, quantity, unit, unitValue,
                quantity.multiply(unitValue).setScale(4, java.math.RoundingMode.HALF_UP),
                "MOVING_WEIGHTED_AVERAGE", financialTreatment, acquisitionDate, custodian,
                storageLocation, condition, status, notes, inventoryAccountId, fundId,
                createdAt, updatedAt);
    }

    /** Compatibility overload for version-1 capitalized inventory writers. */
    public static Map<String, Object> itemEntry(
            String itemId, String name, String itemType, BigDecimal quantity, String unit,
            BigDecimal unitValue, LocalDate acquisitionDate, String custodian, String storageLocation,
            String condition, String status, String notes, String inventoryAccountId, String fundId,
            Instant createdAt, Instant updatedAt)
    {
        return itemEntry(itemId, name, itemType, quantity, unit, unitValue,
                quantity.multiply(unitValue).setScale(4, java.math.RoundingMode.HALF_UP),
                "MOVING_WEIGHTED_AVERAGE", "CAPITALIZED_INVENTORY",
                acquisitionDate, custodian, storageLocation, condition, status, notes,
                inventoryAccountId, fundId, createdAt, updatedAt);
    }

    public static Map<String, Object> movementEntry(
            String movementId, String itemId, LocalDate movementDate, String movementType,
            BigDecimal quantityChange, BigDecimal resultingQuantity, BigDecimal unitValue,
            BigDecimal valueChange, BigDecimal resultingCarryingValue,
            String transactionId, String notes, Instant createdAt)
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("movementId", movementId);
        entry.put("itemId", itemId);
        entry.put("movementDate", movementDate);
        entry.put("movementType", movementType);
        entry.put("quantityChange", quantityChange);
        entry.put("resultingQuantity", resultingQuantity);
        entry.put("unitValue", unitValue);
        entry.put("valueChange", valueChange);
        entry.put("resultingCarryingValue", resultingCarryingValue);
        putOptional(entry, "transactionId", transactionId);
        putOptional(entry, "notes", notes);
        entry.put("createdAt", createdAt);
        return Map.copyOf(entry);
    }

    /** Compatibility overload deriving legacy fixed-unit valuation facts. */
    public static Map<String, Object> movementEntry(
            String movementId, String itemId, LocalDate movementDate, String movementType,
            BigDecimal quantityChange, BigDecimal resultingQuantity, BigDecimal unitValue,
            String transactionId, String notes, Instant createdAt)
    {
        return movementEntry(movementId, itemId, movementDate, movementType, quantityChange,
                resultingQuantity, unitValue,
                quantityChange.multiply(unitValue).setScale(4, java.math.RoundingMode.HALF_UP),
                resultingQuantity.multiply(unitValue).setScale(4, java.math.RoundingMode.HALF_UP),
                transactionId, notes, createdAt);
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
            throw new IllegalArgumentException("extensions.scaJakartaH2.inventory must be an object");
        }
        if (!root.keySet().equals(Set.of("version", "items", "movements")))
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.inventory has unsupported fields");
        }
        int version = SclxExtensionValueReader.integer(root, "version", "extensions.scaJakartaH2.inventory");
        if (version != 1 && version != 2)
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.inventory.version must be 1 or 2");
        }
        List<ItemEntry> items = new ArrayList<>();
        List<Map<?, ?>> itemObjects = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "items", "extensions.scaJakartaH2.inventory"),
                "extensions.scaJakartaH2.inventory.items",
                Set.of("itemId", "name", "itemType", "quantity", "unit", "unitValue", "financialTreatment",
                        "acquisitionDate", "custodian", "storageLocation", "condition", "status",
                        "notes", "inventoryAccountId", "fundId", "createdAt", "updatedAt",
                        "carryingValue", "valuationMethod"));
        for (int index = 0; index < itemObjects.size(); index++)
        {
            Map<?, ?> value = itemObjects.get(index);
            String path = "extensions.scaJakartaH2.inventory.items[" + index + ']';
            BigDecimal quantity = SclxExtensionValueReader.decimal(value, "quantity", path, false);
            BigDecimal unitValue = SclxExtensionValueReader.decimal(value, "unitValue", path, false);
            BigDecimal carryingValue = version == 2
                    ? SclxExtensionValueReader.decimal(value, "carryingValue", path, false)
                    : quantity.multiply(unitValue).setScale(4, java.math.RoundingMode.HALF_UP);
            String valuationMethod = version == 2
                    ? SclxExtensionValueReader.text(value, "valuationMethod", path)
                    : "MOVING_WEIGHTED_AVERAGE";
            items.add(new ItemEntry(
                    SclxExtensionValueReader.text(value, "itemId", path),
                    SclxExtensionValueReader.text(value, "name", path),
                    SclxExtensionValueReader.text(value, "itemType", path),
                    quantity,
                    SclxExtensionValueReader.text(value, "unit", path),
                    unitValue,
                    carryingValue,
                    valuationMethod,
                    SclxExtensionValueReader.optionalText(value, "financialTreatment", path),
                    SclxExtensionValueReader.date(value, "acquisitionDate", path, false),
                    SclxExtensionValueReader.optionalText(value, "custodian", path),
                    SclxExtensionValueReader.optionalText(value, "storageLocation", path),
                    SclxExtensionValueReader.text(value, "condition", path),
                    SclxExtensionValueReader.text(value, "status", path),
                    SclxExtensionValueReader.optionalText(value, "notes", path),
                    SclxExtensionValueReader.text(value, "inventoryAccountId", path),
                    SclxExtensionValueReader.text(value, "fundId", path),
                    SclxExtensionValueReader.instant(value, "createdAt", path, false),
                    SclxExtensionValueReader.instant(value, "updatedAt", path, false)));
        }
        List<MovementEntry> movements = new ArrayList<>();
        List<Map<?, ?>> movementObjects = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "movements", "extensions.scaJakartaH2.inventory"),
                "extensions.scaJakartaH2.inventory.movements",
                Set.of("movementId", "itemId", "movementDate", "movementType", "quantityChange",
                        "resultingQuantity", "unitValue", "transactionId", "notes", "createdAt",
                        "valueChange", "resultingCarryingValue"));
        for (int index = 0; index < movementObjects.size(); index++)
        {
            Map<?, ?> value = movementObjects.get(index);
            String path = "extensions.scaJakartaH2.inventory.movements[" + index + ']';
            BigDecimal quantityChange = SclxExtensionValueReader.decimal(value, "quantityChange", path, false);
            BigDecimal resultingQuantity = SclxExtensionValueReader.decimal(value, "resultingQuantity", path, false);
            BigDecimal unitValue = SclxExtensionValueReader.decimal(value, "unitValue", path, false);
            BigDecimal valueChange = version == 2
                    ? SclxExtensionValueReader.decimal(value, "valueChange", path, false)
                    : quantityChange.multiply(unitValue).setScale(4, java.math.RoundingMode.HALF_UP);
            BigDecimal resultingCarryingValue = version == 2
                    ? SclxExtensionValueReader.decimal(value, "resultingCarryingValue", path, false)
                    : resultingQuantity.multiply(unitValue).setScale(4, java.math.RoundingMode.HALF_UP);
            movements.add(new MovementEntry(
                    SclxExtensionValueReader.text(value, "movementId", path),
                    SclxExtensionValueReader.text(value, "itemId", path),
                    SclxExtensionValueReader.date(value, "movementDate", path, false),
                    SclxExtensionValueReader.text(value, "movementType", path),
                    quantityChange,
                    resultingQuantity,
                    unitValue,
                    valueChange,
                    resultingCarryingValue,
                    SclxExtensionValueReader.optionalText(value, "transactionId", path),
                    SclxExtensionValueReader.optionalText(value, "notes", path),
                    SclxExtensionValueReader.instant(value, "createdAt", path, false)));
        }
        return new Data(items, movements);
    }

    public static Set<String> uniqueItemIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        for (ItemEntry item : data.items())
        {
            if (!ids.add(item.itemId()))
            {
                throw new IllegalArgumentException("duplicate inventory item identity: " + item.itemId());
            }
        }
        return Set.copyOf(ids);
    }

    public static void requireUniqueMovementIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        for (MovementEntry movement : data.movements())
        {
            if (!ids.add(movement.movementId()))
            {
                throw new IllegalArgumentException("duplicate inventory movement identity: " + movement.movementId());
            }
        }
    }

    public record Data(List<ItemEntry> items, List<MovementEntry> movements)
    {
        public Data
        {
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            movements = List.copyOf(Objects.requireNonNull(movements, "movements"));
        }
    }

    public record ItemEntry(String itemId, String name, String itemType, BigDecimal quantity, String unit,
            BigDecimal unitValue, BigDecimal carryingValue, String valuationMethod,
            String financialTreatment, LocalDate acquisitionDate, String custodian, String storageLocation,
            String condition, String status, String notes, String inventoryAccountId, String fundId,
            Instant createdAt, Instant updatedAt)
    {
    }

    public record MovementEntry(String movementId, String itemId, LocalDate movementDate, String movementType,
            BigDecimal quantityChange, BigDecimal resultingQuantity, BigDecimal unitValue,
            BigDecimal valueChange, BigDecimal resultingCarryingValue,
            String transactionId, String notes, Instant createdAt)
    {
    }
}
