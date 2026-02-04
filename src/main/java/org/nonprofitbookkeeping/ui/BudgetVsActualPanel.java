package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class BudgetVsActualPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();

    public BudgetVsActualPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Budget vs Actual");
        title.getStyleClass().add("panel-title");

        Button run = new Button("Run");
        Button export = new Button("Export");
        HBox actions = new HBox(8, run, export);

        root.setTop(new VBox(6, title, actions, new Separator()));
        root.setCenter(new Label("TODO: Budget vs Actual report (outputs)."));
    }

    @Override public String title() { return "Budget vs Actual"; }
    @Override public Node root() { return root; }
}
