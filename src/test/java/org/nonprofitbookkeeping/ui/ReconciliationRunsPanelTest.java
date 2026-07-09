package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ReconciliationRunsPanel visible-control guardrail.
 */
public class ReconciliationRunsPanelTest
{
    @Test
    public void panelSourceExposesFullWorkspaceActionsWithoutApprovalWorkflow() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/ReconciliationRunsPanel.java"));

        assertTrue(source.contains("Load"));
        assertTrue(source.contains("New Reconciliation"));
        assertTrue(source.contains("Edit Existing"));
        assertTrue(source.contains("Save Unresolved"));
        assertTrue(source.contains("Finalize"));
        assertTrue(source.contains("Warn only"));
        assertTrue(source.contains("Overwrite ledger cleared state"));
        assertTrue(source.contains("Never overwrite; require manual resolution"));
        assertTrue(source.contains("Decide per imported line"));
        assertFalse(source.contains("Approve Selected"));
        assertFalse(source.contains("Reject Selected"));
        assertFalse(source.contains("View Approval Audit"));
        assertFalse(source.contains("Record Started"));
        assertFalse(source.contains("Record Completed Run"));
        assertFalse(source.contains("Record Failed"));
    }
}
