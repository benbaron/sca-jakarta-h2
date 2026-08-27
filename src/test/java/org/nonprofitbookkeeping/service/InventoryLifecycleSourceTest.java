package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source guardrails for P17-C5 inventory lifecycle serialization. */
class InventoryLifecycleSourceTest
{
    @Test
    void metadataAndLifecycleWritesUseTheSameInventoryItemLock() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/service/InventoryService.java"));

        assertTrue(source.contains(
                "InventoryItem.class, itemId, LockModeType.PESSIMISTIC_WRITE"));
        assertTrue(source.contains(
                "InventoryItem.class, preview.inventoryItemId(), LockModeType.PESSIMISTIC_WRITE"));
        assertTrue(source.contains("Inventory status changes use the explicit lifecycle action"));
    }
}
