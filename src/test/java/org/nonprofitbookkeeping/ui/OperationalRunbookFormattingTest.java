package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OperationalRunbookFormattingTest component.
 */
class OperationalRunbookFormattingTest
{
    @Test
    void inventoryMovementFormatting_includesQuantity()
    {
        String line = InventoryPanel.formatMovementEntry("RECEIPT", 5, "1300", "Inventory", LocalDateTime.of(2026, 3, 15, 12, 30));
        assertTrue(line.contains("RECEIPT"));
        assertTrue(line.contains("qty=5"));
        assertTrue(line.contains("1300"));
    }
}
