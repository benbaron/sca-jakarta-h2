package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Represents the InventoryPanel component in the nonprofit bookkeeping application.
 */
public class InventoryPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();

    public InventoryPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Inventory");
        title.getStyleClass().add("panel-title");

        Button add = new Button("+ Add");
        add.setOnAction(e -> onNew());

        HBox actions = new HBox(8, add);
        VBox header = new VBox(6, title, actions, new Separator());

        root.setTop(header);
        root.setCenter(new Label("TODO: Implement Inventory content."));
    }

    @Override public String title() { return "Inventory"; }
    @Override public Node root() { return root; }

    @Override public void onNew() {
        // placeholder
    }
}
