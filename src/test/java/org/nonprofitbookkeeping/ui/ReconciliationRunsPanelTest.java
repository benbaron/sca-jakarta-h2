package org.nonprofitbookkeeping.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ReconciliationRunsPanelTest component.
 */
public class ReconciliationRunsPanelTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    public void panelExposesComparisonActionsWithoutApprovalWorkflow()
    {
        ReconciliationRunsPanel panel = FxTestSupport.onFx(ReconciliationRunsPanel::new);

        FxTestSupport.onFx(() -> {
            VBox top = (VBox) ((BorderPane) panel.root()).getTop();
            HBox actions = (HBox) top.getChildren().get(1);
            List<String> actionLabels = actions.getChildren().stream()
                    .map(Button.class::cast)
                    .map(Button::getText)
                    .toList();
            Label workflowNote = (Label) top.getChildren().get(2);

            assertEquals(List.of("Refresh", "Record Started", "Record Completed Run", "Record Failed"), actionLabels);
            assertFalse(actionLabels.contains("Approve Selected"));
            assertFalse(actionLabels.contains("Reject Selected"));
            assertFalse(actionLabels.contains("View Approval Audit"));
            assertTrue(workflowNote.getText().contains("comparison workflow"));
            assertTrue(workflowNote.getText().contains("approve/reject decisions are not part of this panel"));
            return null;
        });
    }
}
