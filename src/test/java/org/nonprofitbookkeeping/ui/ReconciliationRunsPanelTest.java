package org.nonprofitbookkeeping.ui;

import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    public void approveWithoutSelection_setsHelpfulStatus()
    {
        ReconciliationRunsPanel panel = FxTestSupport.onFx(ReconciliationRunsPanel::new);

        String status = FxTestSupport.onFx(() -> {
            VBox top = (VBox) ((javafx.scene.layout.BorderPane) panel.root()).getTop();
            HBox actions = (HBox) top.getChildren().get(1);
            Button approve = (Button) actions.getChildren().get(2);
            approve.fire();
            return ((javafx.scene.control.Label) top.getChildren().get(2)).getText();
        });

        assertEquals("Select a reconciliation run before recording an approval decision.", status);
    }
}
