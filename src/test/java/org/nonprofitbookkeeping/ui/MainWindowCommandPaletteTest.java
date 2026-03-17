package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MainWindowCommandPaletteTest component.
 */
public class MainWindowCommandPaletteTest
{
    @Test
    public void commandPaletteEntries_includeDiagnosticsAndImportPreview()
    {
        List<MainWindow.PaletteEntry> entries = MainWindow.commandPaletteEntriesForTests();
        assertTrue(entries.stream().anyMatch(e -> e.panelId() == AppPanelId.DIAGNOSTICS));
        assertTrue(entries.stream().anyMatch(e -> e.panelId() == AppPanelId.IMPORT_PREVIEW));
    }

    @Test
    public void commandPaletteEntries_includePhase2Panels()
    {
        List<MainWindow.PaletteEntry> entries = MainWindow.commandPaletteEntriesForTests();
        assertTrue(entries.stream().anyMatch(e -> e.panelId() == AppPanelId.APPROVAL_AUDIT));
        assertTrue(entries.stream().anyMatch(e -> e.panelId() == AppPanelId.IMPORT_EXPORT_JOBS));
        assertTrue(entries.stream().anyMatch(e -> e.panelId() == AppPanelId.BANK_TRANSACTIONS));
    }

    @Test
    public void commandPaletteEntries_useFriendlyLabelsAndSortedOrder()
    {
        List<MainWindow.PaletteEntry> entries = MainWindow.commandPaletteEntriesForTests();

        MainWindow.PaletteEntry budgetVsActual = entries.stream()
                .filter(e -> e.panelId() == AppPanelId.BUDGET_VS_ACTUAL)
                .findFirst()
                .orElseThrow();
        assertEquals("Budget vs Actual", budgetVsActual.label());

        List<String> labels = entries.stream().map(MainWindow.PaletteEntry::label).toList();
        List<String> sorted = labels.stream().sorted().toList();
        assertEquals(sorted, labels);
    }
}
