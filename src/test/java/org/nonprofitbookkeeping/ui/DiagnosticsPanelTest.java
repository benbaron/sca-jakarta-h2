package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DiagnosticsPanelTest component.
 */
public class DiagnosticsPanelTest
{
    @Test
    public void panelDelegatesFactualQueriesToTypedDiagnosticsService() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/DiagnosticsPanel.java"));

        assertTrue(source.contains("DiagnosticsQueryService.Report report = diagnostics.query()"));
        assertFalse(source.contains("UiDataSources.forCurrentSessionDatabase"));
        assertFalse(source.contains("UiServiceRegistry.accountLookup"));
        assertFalse(source.contains("UiServiceRegistry.fundLookup"));
        assertFalse(source.contains("MainWindow.sharedSessionState"));
    }
}
