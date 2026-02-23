package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Represents the DepreciationRunsPanel component in the nonprofit bookkeeping application.
 */
public class DepreciationRunsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();

    public DepreciationRunsPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Depreciation Runs");
        title.getStyleClass().add("panel-title");

        Button run = new Button("Run Depreciation");
        Button preview = new Button("Preview Journal");
        HBox actions = new HBox(8, run, preview);

        root.setTop(new VBox(6, title, actions, new Separator()));
        root.setCenter(new Label("TODO: Depreciation run wizard + posting preview (outputs + automation)."));
    }

    @Override public String title() { return "Depreciation Runs"; }
    @Override public Node root() { return root; }
}
