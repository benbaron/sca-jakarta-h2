package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

public class InspectorPane extends VBox
{
    private final Label title = new Label("Inspector");
    private final TextArea body = new TextArea();

    public InspectorPane()
    {
        getStyleClass().add("inspector");
        setPadding(new Insets(8));
        setSpacing(8);

        title.getStyleClass().add("inspector-title");
        body.setEditable(false);
        body.setWrapText(true);

        getChildren().addAll(title, new Separator(), body);
        clear();
    }

    public void show(String t, String b)
    {
        title.setText(t);
        body.setText(b == null ? "" : b);
    }

    public void clear()
    {
        show("Inspector", "Right-click an item to see details here.\n\n(Placeholder)");
    }
}
