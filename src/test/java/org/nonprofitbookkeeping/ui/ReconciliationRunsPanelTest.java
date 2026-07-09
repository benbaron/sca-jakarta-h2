package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    public void panelExposesFullWorkspaceActionsWithoutApprovalWorkflow()
    {
        ReconciliationRunsPanel panel = FxTestSupport.onFx(ReconciliationRunsPanel::new);

        FxTestSupport.onFx(() -> {
            VBox top = (VBox) ((BorderPane) panel.root()).getTop();
            HBox sessionControls = (HBox) top.getChildren().get(2);
            HBox policyControls = (HBox) top.getChildren().get(3);
            List<String> actionLabels = sessionControls.getChildren().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .map(Button::getText)
                    .toList();
            List<String> policyLabels = policyControls.getChildren().stream()
                    .filter(RadioButton.class::isInstance)
                    .map(RadioButton.class::cast)
                    .map(RadioButton::getText)
                    .toList();
            String fullVisibleText = top.getChildren().stream()
                    .map(Node::toString)
                    .reduce("", (left, right) -> left + "\n" + right);

            assertTrue(actionLabels.contains("Load"));
            assertTrue(actionLabels.contains("New Reconciliation"));
            assertTrue(actionLabels.contains("Edit Existing"));
            assertTrue(actionLabels.contains("Save Unresolved"));
            assertTrue(actionLabels.contains("Finalize"));
            assertTrue(policyLabels.contains("Warn only"));
            assertTrue(policyLabels.contains("Overwrite ledger cleared state"));
            assertTrue(policyLabels.contains("Never overwrite; require manual resolution"));
            assertTrue(policyLabels.contains("Decide per imported line"));
            assertFalse(actionLabels.contains("Approve Selected"));
            assertFalse(actionLabels.contains("Reject Selected"));
            assertFalse(actionLabels.contains("View Approval Audit"));
            assertFalse(fullVisibleText.contains("Record Started"));
            assertFalse(fullVisibleText.contains("Record Completed Run"));
            assertFalse(fullVisibleText.contains("Record Failed"));
            return null;
        });
    }
}
