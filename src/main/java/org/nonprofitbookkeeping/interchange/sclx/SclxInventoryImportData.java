package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.InventoryMovement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Strict, non-mutating projection of the governed SCLX inventory extension. */
final class SclxInventoryImportData
{
    private final List<ItemValue> items;
    private final List<MovementValue> movements;

    private SclxInventoryImportData(List<ItemValue> items, List<MovementValue> movements)
    {
        this.items = List.copyOf(items);
        this.movements = List.copyOf(movements);
    }

    static SclxInventoryImportData parse(JsonNode root)
    {
        JsonNode value = root.path("extensions").path("scaJakartaH2").path("inventory");
        if (value.isMissingNode() || value.isNull())
        {
            return new SclxInventoryImportData(List.of(), List.of());
        }
        requireObject(value, "$.extensions.scaJakartaH2.inventory");
        requireFields(value, Set.of("version", "items", "movements"),
                Set.of("version", "items", "movements"), "$.extensions.scaJakartaH2.inventory");
        if (integer(value, "version", "$.extensions.scaJakartaH2.inventory") != 1)
        {
            throw new IllegalStateException("$.extensions.scaJakartaH2.inventory.version must be 1.");
        }

        List<ItemValue> items = new ArrayList<>();
        Set<String> itemIds = new HashSet<>();
        JsonNode itemNodes = requiredArray(value, "items", "$.extensions.scaJakartaH2.inventory");
        for (int index = 0; index < itemNodes.size(); index++)
        {
            JsonNode item = itemNodes.get(index);
            String path = "$.extensions.scaJakartaH2.inventory.items[" + index + "]";
            requireObject(item, path);
            requireFields(item,
                    Set.of("itemId", "name", "itemType", "quantity", "unit", "unitValue",
                            "acquisitionDate", "custodian", "storageLocation", "condition", "status",
                            "notes", "inventoryAccountId", "fundId", "createdAt", "updatedAt"),
                    Set.of("itemId", "name", "itemType", "quantity", "unit", "unitValue",
                            "acquisitionDate", "condition", "status", "inventoryAccountId", "fundId",
                            "createdAt", "updatedAt"), path);
            String externalId = uniqueId(item, "itemId", path, itemIds, "inventory item");
            items.add(new ItemValue(
                    externalId,
                    boundedText(item, "name", path, 200),
                    boundedText(item, "itemType", path, 120),
                    decimal(item, "quantity", path, false),
                    boundedText(item, "unit", path, 40),
                    decimal(item, "unitValue", path, false),
                    date(item, "acquisitionDate", path),
                    optionalBoundedText(item, "custodian", path, 200),
                    optionalBoundedText(item, "storageLocation", path, 200),
                    enumValue(InventoryItem.Condition.class, text(item, "condition", path),
                            path + ".condition"),
                    enumValue(InventoryItem.Status.class, text(item, "status", path), path + ".status"),
                    optionalText(item, "notes", path),
                    text(item, "inventoryAccountId", path),
                    text(item, "fundId", path),
                    instant(item, "createdAt", path),
                    instant(item, "updatedAt", path)));
        }

        List<MovementValue> movements = new ArrayList<>();
        Set<String> movementIds = new HashSet<>();
        JsonNode movementNodes = requiredArray(value, "movements", "$.extensions.scaJakartaH2.inventory");
        for (int index = 0; index < movementNodes.size(); index++)
        {
            JsonNode movement = movementNodes.get(index);
            String path = "$.extensions.scaJakartaH2.inventory.movements[" + index + "]";
            requireObject(movement, path);
            requireFields(movement,
                    Set.of("movementId", "itemId", "movementDate", "movementType", "quantityChange",
                            "resultingQuantity", "unitValue", "transactionId", "notes", "createdAt"),
                    Set.of("movementId", "itemId", "movementDate", "movementType", "quantityChange",
                            "resultingQuantity", "unitValue", "createdAt"), path);
            String externalId = uniqueId(
                    movement, "movementId", path, movementIds, "inventory movement");
            String itemId = text(movement, "itemId", path);
            if (!itemIds.contains(itemId))
            {
                throw new IllegalStateException(path + ".itemId does not resolve to an imported inventory item.");
            }
            BigDecimal quantityChange = decimal(movement, "quantityChange", path, true);
            if (quantityChange.signum() == 0)
            {
                throw new IllegalStateException(path + ".quantityChange must be nonzero.");
            }
            movements.add(new MovementValue(
                    externalId,
                    itemId,
                    date(movement, "movementDate", path),
                    enumValue(InventoryMovement.MovementType.class,
                            text(movement, "movementType", path), path + ".movementType"),
                    quantityChange,
                    decimal(movement, "resultingQuantity", path, false),
                    decimal(movement, "unitValue", path, false),
                    optionalText(movement, "transactionId", path),
                    optionalText(movement, "notes", path),
                    instant(movement, "createdAt", path)));
        }
        items.sort(Comparator.comparing(ItemValue::externalId));
        movements.sort(Comparator.comparing(MovementValue::externalId));
        return new SclxInventoryImportData(items, movements);
    }

    List<ItemValue> items()
    {
        return items;
    }

    List<MovementValue> movements()
    {
        return movements;
    }

    private static String uniqueId(
            JsonNode value, String field, String path, Set<String> identities, String label)
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

    private static void requireFields(JsonNode value, Set<String> allowed, Set<String> required, String path)
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

    private static String optionalBoundedText(JsonNode value, String field, String path, int limit)
    {
        String result = optionalText(value, field, path);
        if (result != null && result.length() > limit)
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

    private static BigDecimal decimal(JsonNode value, String field, String path, boolean signed)
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
            if (!signed && amount.signum() < 0)
            {
                throw new IllegalStateException(path + "." + field + " must be nonnegative.");
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

    record ItemValue(
            String externalId,
            String name,
            String itemType,
            BigDecimal quantity,
            String unit,
            BigDecimal unitValue,
            LocalDate acquisitionDate,
            String custodian,
            String storageLocation,
            InventoryItem.Condition condition,
            InventoryItem.Status status,
            String notes,
            String inventoryAccountId,
            String fundId,
            Instant createdAt,
            Instant updatedAt)
    {
    }

    record MovementValue(
            String externalId,
            String itemId,
            LocalDate movementDate,
            InventoryMovement.MovementType movementType,
            BigDecimal quantityChange,
            BigDecimal resultingQuantity,
            BigDecimal unitValue,
            String transactionId,
            String notes,
            Instant createdAt)
    {
    }
}
