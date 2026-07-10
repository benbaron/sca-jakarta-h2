package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source and routing guardrails for P03-C6. */
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
    void factoryRoutesOnlyCanonicalJournalWorkspace() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/PanelFactory.java"));

        assertTrue(source.contains("factories.put(AppPanelId.JOURNAL_PANE, JournalWorkspacePanel::new)"));
        assertFalse(source.contains("LedgerRegisterPanel::new"));
        assertFalse(source.contains("TransactionEditorPanel::new"));
        assertFalse(source.contains("JournalPane::new"));
    }

    @Test
    void journalWorkspaceUsesResizableServiceBackedSections() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/JournalWorkspacePanel.java"));

        assertTrue(source.contains("journalWorkspaceOuterSplit"));
        assertTrue(source.contains("journalWorkspaceEditorSplit"));
        assertTrue(source.contains("journalWorkspaceDetailSplit"));
        assertTrue(source.contains("setOrientation(Orientation.VERTICAL)"));
        assertTrue(source.contains("setOrientation(Orientation.HORIZONTAL)"));
        assertTrue(source.contains("installDividerState"));
        assertTrue(source.contains("TransactionEntryService"));
        assertTrue(source.contains("service.enter(command)"));
        assertTrue(source.contains("service.update(targetId, command)"));
        assertTrue(source.contains("transactionCorrection().delete"));
        assertTrue(source.contains("transactionCorrection().reverse"));
        assertTrue(source.contains("TransactionSupplementalLineCommand"));
        assertTrue(source.contains("TableView.UNCONSTRAINED_RESIZE_POLICY"));
        assertTrue(source.contains("setReorderable(true)"));
        assertFalse(source.contains("CurrentCompany"));
        assertFalse(source.contains("nonprofitbookkeeping.persistence"));
    }
}
