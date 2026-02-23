package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Represents the AssetsRegisterPanel component in the nonprofit bookkeeping application.
 */
public class AssetsRegisterPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();

    public AssetsRegisterPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Asset Register");
        title.getStyleClass().add("panel-title");

        Button add = new Button("+ Add Asset");
        Button save = new Button("Save");
        HBox actions = new HBox(8, add, save);

        root.setTop(new VBox(6, title, actions, new Separator()));
        root.setCenter(new Label("TODO: Asset register (inputs + asset details)."));
    }

    @Override public String title() { return "Asset Register"; }
    @Override public Node root() { return root; }
}
