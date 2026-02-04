package org.nonprofitbookkeeping.ui;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class ReportLibraryPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();

    public ReportLibraryPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Reports Library");
        Label range = new Label();
        range.textProperty().bind(Bindings.createStringBinding(() -> "Date Range: " + DateRangeContext.get(), DateRangeContext.selectedProperty()));
        title.getStyleClass().add("panel-title");

        Button run = new Button("Run");
        Button export = new Button("Export");
        HBox actions = new HBox(8, run, export);

        root.setTop(new VBox(6, title, range, actions, new Separator()));

        ListView<String> list = new ListView<>();
        list.getItems().addAll(
            "Balance Sheet",
            "Income Statement",
            "Fund Summary",
            "Budget vs Actual",
            "Transaction Detail",
            "Schedule Detail"
        );

        VBox right = new VBox(8);
        right.setPadding(new Insets(8));
        right.getChildren().addAll(
            new Label("Parameters (placeholder)"),
            new TextArea("TODO: parameter controls (date range, fund/account filters, etc.)"),
            new Separator(),
            new Label("Preview (placeholder)"),
            new TextArea("TODO: embedded preview or open a new window.")
        );

        SplitPane sp = new SplitPane(list, right);
        sp.setDividerPositions(0.30);
        root.setCenter(sp);
    }

    @Override public String title() { return "Reports Library"; }
    @Override public Node root() { return root; }
}
