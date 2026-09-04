package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartOfAccountsInterchangePanelTest
{
    @Test
    void mappingTextRequiresOneUniqueSourceToTargetPairPerLine()
    {
        assertEquals(
                Map.of("1000", "1100", "2000", "2100"),
                ChartOfAccountsInterchangePanel.parseMappingsForTests("1000=1100\n2000 = 2100\n"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ChartOfAccountsInterchangePanel.parseMappingsForTests("1000"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ChartOfAccountsInterchangePanel.parseMappingsForTests("1000=1100\n1000=1200"));
    }

    @Test
    void productionRouteUsesInterchangeWrapperAndRetainsExplicitControls() throws Exception
    {
        String factory = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/PanelFactory.java"));
        String panel = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ChartOfAccountsInterchangePanel.java"));

        assertTrue(factory.contains(
                "factories.put(AppPanelId.CHART_OF_ACCOUNTS, ChartOfAccountsInterchangePanel::new)"));
        assertTrue(panel.contains("coaImportJsonButton"));
        assertTrue(panel.contains("coaExportJsonButton"));
        assertTrue(panel.contains("coaJsonPreviewTable"));
        assertTrue(panel.contains("UiServiceRegistry.coaJsonImport().commit(confirmed)"));
        assertTrue(panel.contains("delegate.onPanelShown()"));
        assertTrue(panel.contains("UiAsync.run"));
    }
}
