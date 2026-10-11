package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level policy test for reconciliation approval workflow removal.
 */
public class ReconciliationRunsPanelSourceTest
{
    @Test
    public void sourceDoesNotExposeApprovalWorkflowActions() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/ReconciliationRunsPanel.java"));

        assertTrue(source.contains("comparison workflow"));
        assertFalse(source.contains("Approve Selected"));
        assertFalse(source.contains("Reject Selected"));
        assertFalse(source.contains("View Approval Audit"));
        assertFalse(source.contains("recordApproval"));
        assertFalse(source.contains("ApprovalDecision"));
    }
}
