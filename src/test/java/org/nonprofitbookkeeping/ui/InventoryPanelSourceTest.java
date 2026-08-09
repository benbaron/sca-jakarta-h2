package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-level guardrails for Inventory panel navigation. */
class InventoryPanelSourceTest
{
    @Test
    void inventoryItemEditorIsReachedThroughNewOrEditSelected() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/InventoryPanel.java"));

        assertTrue(source.contains("New Item"));
        assertTrue(source.contains("Edit Selected"));
        assertTrue(source.contains("openNewItemEditor"));
        assertTrue(source.contains("openEditItemEditor"));
        assertTrue(source.contains("root.setCenter(itemEditorPanel)"));
        assertTrue(source.contains("root.setCenter(listPanel)"));
    }

    @Test
    void inventoryMovementsUseFrozenPreviewExplicitConfirmationAndCanonicalLinks() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/InventoryPanel.java"));

        assertTrue(source.contains("previewMovement("));
        assertTrue(source.contains("confirmMovement(preview)"));
        assertTrue(source.contains("recordMovement(preview,"));
        assertTrue(source.contains("Zero-value nonfinancial movement (no ledger transaction)"));
        assertTrue(source.contains("Reverse Selected Movement"));
        assertTrue(source.contains("previewMovementReversal("));
        assertTrue(source.contains("reverseMovement(preview,"));
        assertTrue(source.contains("Drill Selected Txn to Ledger"));
    }
}
