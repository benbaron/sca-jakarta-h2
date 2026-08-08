package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-composition guard for P16-S3 finalized-session integrity. */
public class ReconciliationRunsPanelIntegritySourceTest
{
    @Test
    void finalizedSessionsAreReadOnlyAndDifferenceExplanationsStayFactual() throws Exception
    {
        String panel = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ReconciliationRunsPanel.java"));

        assertTrue(panel.contains("Record Difference Explanation"));
        assertTrue(panel.contains("recordDifferenceExplanation(requireSession()"));
        assertTrue(panel.contains("no accounting transaction was created"));
        assertTrue(panel.contains("applyFinalizedReadOnly("));
        assertTrue(panel.contains("Finalized reconciliation is read-only. Start a successor reconciliation to continue."));
        assertTrue(panel.contains("Start Successor Reconciliation"));
        assertTrue(panel.contains("service().startSuccessor(new SuccessorCommand("));
        assertFalse(panel.contains("new Button(\"Resolve Difference\")"));
        assertFalse(panel.contains("service().resolveDifference("));
    }
}
