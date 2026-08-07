package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-composition guard for the production P16-S2 COA CSV path. */
public class ImportPreviewPanelCoaAtomicSourceTest
{
    @Test
    public void productionCoaCsvRouteUsesFrozenAtomicServiceAndCommittedBatchCounts() throws Exception
    {
        String panel = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ImportPreviewPanel.java"));
        String registry = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/UiServiceRegistry.java"));

        assertTrue(panel.contains("UiServiceRegistry.coaCsvImport().preview(file)"));
        assertTrue(panel.contains("UiServiceRegistry.coaCsvImport().commit(confirmed, actor)"));
        assertTrue(panel.contains("Confirm Atomic COA CSV Commit"));
        assertTrue(panel.contains("created=\" + result.createdCount()"));
        assertTrue(panel.contains("updated=\" + result.updatedCount()"));
        assertTrue(panel.contains("skipped=\" + result.skippedCount()"));
        assertFalse(panel.contains("previewService.commitAcceptedCoaRows("));
        assertFalse(panel.contains("row -> UiServiceRegistry.accountAdmin().upsert("));
        assertTrue(registry.contains("public static CoaCsvImportService coaCsvImport()"));
    }
}
