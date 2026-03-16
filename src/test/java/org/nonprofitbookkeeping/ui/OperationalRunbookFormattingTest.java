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
    void scheduleRunbookFormatting_includesActionAndScheduleKind()
    {
        String line = SchedulesPanel.formatRunbookEntry("OPEN", "RECEIVABLE", "1100", "Accounts Receivable", LocalDateTime.of(2026, 3, 15, 12, 30));
        assertTrue(line.contains("OPEN"));
        assertTrue(line.contains("RECEIVABLE"));
        assertTrue(line.contains("1100"));
    }

    @Test
    void assetsLifecycleFormatting_includesAccountAndAction()
    {
        String line = AssetsRegisterPanel.formatLifecycleEntry("ACQUIRED", "1500", "Equipment", LocalDateTime.of(2026, 3, 15, 12, 30));
        assertTrue(line.contains("ACQUIRED"));
        assertTrue(line.contains("1500"));
    }

    @Test
    void depreciationRunFormatting_includesStateAndAccount()
    {
        String line = DepreciationRunsPanel.formatRunEntry("COMPLETED", "1500", "Equipment", LocalDateTime.of(2026, 3, 15, 12, 30));
        assertTrue(line.contains("COMPLETED"));
        assertTrue(line.contains("1500"));
    }

    @Test
    void inventoryMovementFormatting_includesQuantity()
    {
        String line = InventoryPanel.formatMovementEntry("RECEIPT", 5, "1300", "Inventory", LocalDateTime.of(2026, 3, 15, 12, 30));
        assertTrue(line.contains("RECEIPT"));
        assertTrue(line.contains("qty=5"));
        assertTrue(line.contains("1300"));
    }
}
