package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainWindowCommandPaletteTest
{
    @Test
    public void commandPaletteEntries_includeDiagnosticsAndImportPreview()
    {
        List<MainWindow.PaletteEntry> entries = MainWindow.commandPaletteEntriesForTests();
        assertTrue(entries.stream().anyMatch(e -> e.panelId() == AppPanelId.DIAGNOSTICS));
        assertTrue(entries.stream().anyMatch(e -> e.panelId() == AppPanelId.IMPORT_PREVIEW));
    }
}
