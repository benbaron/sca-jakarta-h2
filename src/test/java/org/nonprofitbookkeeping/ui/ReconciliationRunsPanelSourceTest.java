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

        assertTrue(source.contains("comparison workflow"));
        assertFalse(source.contains("Approve Selected"));
        assertFalse(source.contains("Reject Selected"));
        assertFalse(source.contains("View Approval Audit"));
        assertFalse(source.contains("recordApproval"));
        assertFalse(source.contains("ApprovalDecision"));
    }

    @Test
    public void sourceKeepsComparisonTablesDesignRuleAware() throws Exception
    {
        String source = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/ReconciliationRunsPanel.java"));

        assertTrue(source.contains("SplitPane split = new SplitPane"));
        assertTrue(source.contains("TableView.UNCONSTRAINED_RESIZE_POLICY"));
        assertFalse(source.contains("TableView.CONSTRAINED_RESIZE_POLICY"));
        assertTrue(source.contains("installTableStatePersistence(table, \"runs\")"));
        assertTrue(source.contains("installTableStatePersistence(comparisonTable, \"comparison\")"));
        assertTrue(source.contains("Delete unavailable"));
        assertTrue(source.contains("reconciliation records are factual history"));
    }
}
