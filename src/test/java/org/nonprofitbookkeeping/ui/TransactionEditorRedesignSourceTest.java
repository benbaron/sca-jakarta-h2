package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-level guardrails for the P03-C4 Transaction Editor and Journal redesign. */
class TransactionEditorRedesignSourceTest
{
    @Test
    void transactionEditorUsesTaskSizedPagesAndSupplementalDetailPanels() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/TransactionEditorPanel.java"));

        assertTrue(source.contains("new TabPane()"));
        assertTrue(source.contains("1. Header"));
        assertTrue(source.contains("2. Entry Lines"));
        assertTrue(source.contains("3. Additional Details"));
        assertTrue(source.contains("4. Donation Subschedule"));
        assertTrue(source.contains("5. Supplemental Details"));
        assertTrue(source.contains("Party / Document"));
        assertTrue(source.contains("Bank / Reconciliation"));
        assertTrue(source.contains("Budget / Fund"));
        assertTrue(source.contains("Receivable"));
        assertTrue(source.contains("Deferred Revenue"));
        assertTrue(source.contains("Other Liability"));
        assertTrue(source.contains("TABLE_STATE"));
        assertTrue(source.contains("SUPPLEMENTAL_TABLE_STATE"));
        assertTrue(source.contains("setReorderable(true)"));
        assertTrue(source.contains("setEditable(true)"));
        assertTrue(source.contains("add.setOnAction(event -> addSupplementalRow(kind))"));
        assertTrue(source.contains("remove.setOnAction(event -> removeSupplementalRow(kind))"));
        assertTrue(source.contains("validateSupplementalRows"));
        assertFalse(source.contains("Supplemental detail persistence for"));
        assertFalse(source.contains("open_subwindow_schedules"));
        assertFalse(source.contains("SchedulesPanel"));
    }

    @Test
    void journalPaneUsesGroupedTransactionDisplay() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/JournalPane.java"));

        assertTrue(source.contains("Account Title and Description"));
        assertTrue(source.contains("Transaction ID"));
        assertTrue(source.contains("Supplemental"));
        assertTrue(source.contains("Schedules (0)"));
        assertTrue(source.contains("transaction-local details"));
        assertTrue(source.contains("not the eliminated generic Schedules module"));
        assertTrue(source.contains("Delete"));
        assertTrue(source.contains("Refresh"));
        assertFalse(source.contains("open_subwindow_schedules"));
        assertFalse(source.contains("SchedulesPanel"));
    }
}
