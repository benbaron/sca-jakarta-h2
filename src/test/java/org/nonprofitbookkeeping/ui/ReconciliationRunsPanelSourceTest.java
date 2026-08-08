package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level policy tests for reconciliation workflow and UI design-rule guardrails.
 */
public class ReconciliationRunsPanelSourceTest
{
    @Test
    public void sourceDoesNotExposeApprovalWorkflowActions() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/ReconciliationRunsPanel.java"));

        assertTrue(source.contains("Bank Reconciliation"));
        assertFalse(source.contains("Approve Selected"));
        assertFalse(source.contains("Reject Selected"));
        assertFalse(source.contains("View Approval Audit"));
        assertFalse(source.contains("recordApproval"));
        assertFalse(source.contains("ApprovalDecision"));
        assertFalse(source.contains("Record Started"));
        assertFalse(source.contains("Record Completed Run"));
        assertFalse(source.contains("Record Failed"));
    }

    @Test
    public void sourceExposesFullReconciliationWorkspaceControls() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/ReconciliationRunsPanel.java"));

        assertTrue(source.contains("New Reconciliation"));
        assertTrue(source.contains("Edit Existing"));
        assertTrue(source.contains("Save Unresolved"));
        assertTrue(source.contains("Finalize"));
        assertTrue(source.contains("Add Manual Line"));
        assertTrue(source.contains("Import Bank Statement…"));
        assertTrue(source.contains("OFX/QFX, mapped CSV, and normalized CSV"));
        assertFalse(source.contains("CSV Import"));
        assertFalse(source.contains("OFX Import"));
        assertFalse(source.contains("QIF Import"));
        assertTrue(source.contains("Warn only"));
        assertTrue(source.contains("Overwrite ledger cleared state"));
        assertTrue(source.contains("Never overwrite; require manual resolution"));
        assertTrue(source.contains("Decide per imported line"));
        assertTrue(source.contains("Auto Match"));
        assertTrue(source.contains("Match Selected"));
        assertTrue(source.contains("Unmatch"));
        assertTrue(source.contains("Mark Cleared"));
        assertTrue(source.contains("Record Difference Explanation"));
        assertTrue(source.contains("TableView.UNCONSTRAINED_RESIZE_POLICY"));
        assertFalse(source.contains("TableView.CONSTRAINED_RESIZE_POLICY"));
        assertFalse(source.contains("Delete unavailable"));
    }
}
