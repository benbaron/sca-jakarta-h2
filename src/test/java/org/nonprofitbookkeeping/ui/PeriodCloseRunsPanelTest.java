package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source guardrails for the authoritative Period Close workspace. */
class PeriodCloseRunsPanelTest
{
    @Test
    void periodCloseWorkspaceUsesRealCloseReopenAndHistoryOperations() throws Exception
    {
        String source = Files.readString(
                Path.of("src/main/java/org/nonprofitbookkeeping/ui/PeriodCloseRunsPanel.java"));

        assertTrue(source.contains("new Button(\"Use Active Month\")"));
        assertTrue(source.contains("new Button(\"Close Range\")"));
        assertTrue(source.contains("new Button(\"Reopen Selected\")"));
        assertTrue(source.contains("service().closeRange("));
        assertTrue(source.contains("service().reopenRange("));
        assertTrue(source.contains("service().listRanges(company)"));
        assertTrue(source.contains("service().listEvents(company)"));
        assertTrue(source.contains("new SplitPane(rangePane, historyPane)"));
        assertTrue(source.contains("TableView.UNCONSTRAINED_RESIZE_POLICY"));
        assertTrue(source.contains("column.setSortable(true)"));
        assertTrue(source.contains("column.setResizable(true)"));
        assertTrue(source.contains("column.setReorderable(true)"));

        assertFalse(source.contains("Approve Selected"));
        assertFalse(source.contains("Reject Selected"));
        assertFalse(source.contains("ApprovalDecision"));
        assertFalse(source.contains("approvalAuditService"));
        assertFalse(source.contains("periodCloseRunRepository"));
        assertFalse(source.contains("Record Started"));
        assertFalse(source.contains("Record Failed"));
        assertFalse(source.contains("Record Completed Close"));
    }
}
