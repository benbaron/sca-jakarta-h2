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
        String compactPanel = panel.replaceAll("\\s+", "");

        assertTrue(compactPanel.contains("UiServiceRegistry.coaCsvImport().preview(file)"));
        assertTrue(compactPanel.contains("UiServiceRegistry.coaCsvImport().commit(confirmed,actor)"));
        assertTrue(panel.contains("Confirm Atomic COA CSV Commit"));
        assertTrue(compactPanel.contains("created=\"+result.createdCount()"));
        assertTrue(compactPanel.contains("updated=\"+result.updatedCount()"));
        assertTrue(compactPanel.contains("skipped=\"+result.skippedCount()"));
        assertFalse(compactPanel.contains("previewService.commitAcceptedCoaRows("));
        assertFalse(compactPanel.contains("row->UiServiceRegistry.accountAdmin().upsert("));
        assertTrue(registry.contains("public static CoaCsvImportService coaCsvImport()"));
    }
}
