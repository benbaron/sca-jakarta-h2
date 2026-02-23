package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Represents the BudgetEditorPanel component in the nonprofit bookkeeping application.
 */
public class BudgetEditorPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();

    public BudgetEditorPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Budget Editor");
        title.getStyleClass().add("panel-title");

        Button add = new Button("+ Add Budget Line");
        Button save = new Button("Save");
        HBox actions = new HBox(8, add, save);

        root.setTop(new VBox(6, title, actions, new Separator()));
        root.setCenter(new Label("TODO: Budget entry grid (inputs)."));
    }

    @Override public String title() { return "Budget Editor"; }
    @Override public Node root() { return root; }
}
