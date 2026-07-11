package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source and routing guardrails for the unified Journal and its P03-C7 compliance layer. */
class JournalWorkspacePortSourceTest
{
    @Test
    void retiredP03DestinationsNormalizeToJournalWithoutJavaFxInitialization()
    {
        assertEquals(AppPanelId.JOURNAL_PANE, AppPanelId.canonical(AppPanelId.LEDGER_REGISTER));
        assertEquals(AppPanelId.JOURNAL_PANE, AppPanelId.canonical(AppPanelId.TXN_EDITOR));
        assertEquals(AppPanelId.JOURNAL_PANE, AppPanelId.canonical(AppPanelId.JOURNAL_PANE));
        assertEquals(AppPanelId.BANKING, AppPanelId.canonical(AppPanelId.BANKING));
    }

    @Test
    void navigationExposesOneJournalDestination() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/NavigationPane.java"));

        assertTrue(source.contains("addItem(content, AppPanelId.JOURNAL_PANE, \"Journal\""));
        assertFalse(source.contains("addItem(content, AppPanelId.LEDGER_REGISTER"));
        assertFalse(source.contains("addItem(content, AppPanelId.TXN_EDITOR"));
        assertFalse(source.contains("\"Inspect Journal\""));
    }

    @Test
    void factoryRoutesCanonicalJournalToCompliancePanel() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/PanelFactory.java"));

        assertTrue(source.contains("factories.put(AppPanelId.JOURNAL_PANE, JournalWorkspaceCompliancePanel::new)"));
        assertFalse(source.contains("LedgerRegisterPanel::new"));
        assertFalse(source.contains("TransactionEditorPanel::new"));
        assertFalse(source.contains("JournalPane::new"));
    }

    @Test
    void complianceLayerProvidesOverallEditorScrollAndTableContractWithoutVisibleCommentary() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/JournalWorkspaceCompliancePanel.java"));

        assertTrue(source.contains("installOverallEditorScroll"));
        assertTrue(source.contains("journalWorkspaceEditorScroll"));
        assertTrue(source.contains("setFitToWidth(true)"));
        assertTrue(source.contains("setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED)"));
        assertTrue(source.contains("installTableCompliance"));
        assertTrue(source.contains("TableView.UNCONSTRAINED_RESIZE_POLICY"));
        assertTrue(source.contains("column.setSortable(true)"));
        assertTrue(source.contains("column.setResizable(true)"));
        assertTrue(source.contains("column.setReorderable(true)"));
        assertTrue(source.contains("restoreTableState"));
        assertTrue(source.contains("installTableStatePersistence"));
        assertTrue(source.contains("wrapTableInSplitRegion"));
        assertTrue(source.contains("new SplitPane(table)"));
        assertTrue(source.contains("installDividerState"));
        assertTrue(source.contains("CompanyUiFormat"));
        assertTrue(source.contains("CompanyUiPreferencesService"));
        assertFalse(source.contains("Columns are sortable, resizable, and rearrangeable"));
        assertFalse(source.contains("The table scrolls independently in both directions"));
        assertFalse(source.contains("java.util.prefs.Preferences"));
        assertFalse(source.contains("CurrentCompany"));
    }

    @Test
    void journalDelegateRemainsServiceBacked() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/JournalWorkspacePanel.java"));

        assertTrue(source.contains("TransactionEntryService"));
        assertTrue(source.contains("service.enter(command)"));
        assertTrue(source.contains("service.update(targetId, command)"));
        assertTrue(source.contains("transactionCorrection().delete"));
        assertTrue(source.contains("transactionCorrection().reverse"));
        assertTrue(source.contains("TransactionSupplementalLineCommand"));
        assertFalse(source.contains("CurrentCompany"));
        assertFalse(source.contains("nonprofitbookkeeping.persistence"));
    }
}
