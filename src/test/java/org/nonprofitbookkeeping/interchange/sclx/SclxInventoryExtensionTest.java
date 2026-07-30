package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SclxInventoryExtensionTest
{
    @Test
    void parsesCountsAndValidatesReferences()
    {
        SclxExportDocument base = SclxJsonSerializerTest.document();
        Map<String, Object> values = new LinkedHashMap<>(base.extensions().scaJakartaH2());
        String itemId = SclxPortableIdentity.inventoryItem("TEST", "item-1");
        values.put(SclxInventoryExtension.KEY, SclxInventoryExtension.value(
                List.of(SclxInventoryExtension.itemEntry(
                        itemId, "Tablecloth", "SUPPLY", new BigDecimal("4.0000"), "each",
                        new BigDecimal("12.5000"), LocalDate.of(2026, 1, 2), null, "Locker",
                        "GOOD", "ACTIVE", null, "account:TEST:1010", "fund:TEST:GENERAL",
                        Instant.EPOCH, Instant.EPOCH)),
                List.of(SclxInventoryExtension.movementEntry(
                        SclxPortableIdentity.inventoryMovement("TEST", "movement-1"), itemId,
                        LocalDate.of(2026, 1, 2), "RECEIPT", new BigDecimal("4.0000"),
                        new BigDecimal("4.0000"), new BigDecimal("12.5000"), null,
                        "Initial quantity", Instant.EPOCH))));
        SclxExportDocument document = copy(base, values);

        new SclxExportDocumentValidator().validate(document);
        SclxInventoryExtension.Data data = SclxInventoryExtension.data(document.extensions());
        assertEquals(1, data.items().size());
        assertEquals(1, data.movements().size());
        SclxExportCounts counts = SclxExportCounts.from(document, 0L, 0L);
        assertEquals(1, counts.inventoryItems());
        assertEquals(1, counts.inventoryMovements());

        values.put(SclxInventoryExtension.KEY, SclxInventoryExtension.value(
                List.of(),
                List.of(SclxInventoryExtension.movementEntry(
                        "inventory-movement:TEST:missing", "inventory-item:TEST:missing",
                        LocalDate.of(2026, 1, 3), "ISSUE", BigDecimal.ONE.negate(),
                        BigDecimal.ZERO, BigDecimal.ONE, null, null, Instant.EPOCH))));
        assertThrows(IllegalArgumentException.class,
                () -> new SclxExportDocumentValidator().validate(copy(base, values)));
    }

    private static SclxExportDocument copy(
            SclxExportDocument base, Map<String, Object> extensionValues)
    {
        return new SclxExportDocument(
                base.format(), base.version(), base.exportedAt(), base.organization(),
                base.chartOfAccounts(), base.funds(), base.budgets(), base.transactions(),
                new SclxExportDocument.Extensions(1, extensionValues));
    }
}
