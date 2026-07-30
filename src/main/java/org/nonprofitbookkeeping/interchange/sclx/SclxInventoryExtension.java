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
        value.put("version", 1);
        value.put("items", List.copyOf(items));
        value.put("movements", List.copyOf(movements));
        return Map.copyOf(value);
    }

    public static Map<String, Object> itemEntry(
            String itemId, String name, String itemType, BigDecimal quantity, String unit,
            BigDecimal unitValue, LocalDate acquisitionDate, String custodian, String storageLocation,
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

    public static Map<String, Object> movementEntry(
            String movementId, String itemId, LocalDate movementDate, String movementType,
            BigDecimal quantityChange, BigDecimal resultingQuantity, BigDecimal unitValue,
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
        putOptional(entry, "transactionId", transactionId);
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
            throw new IllegalArgumentException("extensions.scaJakartaH2.inventory must be an object");
        }
        if (!root.keySet().equals(Set.of("version", "items", "movements")))
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.inventory has unsupported fields");
        }
        if (SclxExtensionValueReader.integer(root, "version", "extensions.scaJakartaH2.inventory") != 1)
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.inventory.version must be 1");
        }
        List<ItemEntry> items = new ArrayList<>();
        List<Map<?, ?>> itemObjects = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "items", "extensions.scaJakartaH2.inventory"),
                "extensions.scaJakartaH2.inventory.items",
                Set.of("itemId", "name", "itemType", "quantity", "unit", "unitValue",
                        "acquisitionDate", "custodian", "storageLocation", "condition", "status",
                        "notes", "inventoryAccountId", "fundId", "createdAt", "updatedAt"));
        for (int index = 0; index < itemObjects.size(); index++)
        {
            Map<?, ?> value = itemObjects.get(index);
            String path = "extensions.scaJakartaH2.inventory.items[" + index + ']';
            items.add(new ItemEntry(
                    SclxExtensionValueReader.text(value, "itemId", path),
                    SclxExtensionValueReader.text(value, "name", path),
                    SclxExtensionValueReader.text(value, "itemType", path),
                    SclxExtensionValueReader.decimal(value, "quantity", path, false),
                    SclxExtensionValueReader.text(value, "unit", path),
                    SclxExtensionValueReader.decimal(value, "unitValue", path, false),
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
                        "resultingQuantity", "unitValue", "transactionId", "notes", "createdAt"));
        for (int index = 0; index < movementObjects.size(); index++)
        {
            Map<?, ?> value = movementObjects.get(index);
            String path = "extensions.scaJakartaH2.inventory.movements[" + index + ']';
            movements.add(new MovementEntry(
                    SclxExtensionValueReader.text(value, "movementId", path),
                    SclxExtensionValueReader.text(value, "itemId", path),
                    SclxExtensionValueReader.date(value, "movementDate", path, false),
                    SclxExtensionValueReader.text(value, "movementType", path),
                    SclxExtensionValueReader.decimal(value, "quantityChange", path, false),
                    SclxExtensionValueReader.decimal(value, "resultingQuantity", path, false),
                    SclxExtensionValueReader.decimal(value, "unitValue", path, false),
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
            BigDecimal unitValue, LocalDate acquisitionDate, String custodian, String storageLocation,
            String condition, String status, String notes, String inventoryAccountId, String fundId,
            Instant createdAt, Instant updatedAt)
    {
    }

    public record MovementEntry(String movementId, String itemId, LocalDate movementDate, String movementType,
            BigDecimal quantityChange, BigDecimal resultingQuantity, BigDecimal unitValue,
            String transactionId, String notes, Instant createdAt)
    {
    }
}
