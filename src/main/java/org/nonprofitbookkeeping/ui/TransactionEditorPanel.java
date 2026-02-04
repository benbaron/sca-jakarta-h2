package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class TransactionEditorPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<SplitRow> splitTable = new TableView<>();

    public TransactionEditorPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Transaction Editor");
        title.getStyleClass().add("panel-title");

        Button save = new Button("Save");
        Button post = new Button("Post / Validate");
        Button journal = new Button("Journal View");
        HBox actions = new HBox(8, save, post, journal);

        VBox top = new VBox(6, title, actions, new Separator(), buildHeaderForm());
        root.setTop(top);

        buildSplitTable();
        root.setCenter(buildSplitEditor());

        save.setOnAction(e -> onSave());
        post.setOnAction(e -> validateOrPost());
        journal.setOnAction(e -> showJournal());
    }

    private Node buildHeaderForm()
    {
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.setPadding(new Insets(8, 0, 8, 0));

        TextField date = new TextField();
        TextField payee = new TextField();
        TextField memo = new TextField();
        TextField bank = new TextField();

        int r = 0;
        g.add(new Label("Date"), 0, r); g.add(date, 1, r);
        g.add(new Label("Payee"), 2, r); g.add(payee, 3, r);
        r++;
        g.add(new Label("Memo"), 0, r); g.add(memo, 1, r, 3, 1);
        r++;
        g.add(new Label("Bank"), 0, r); g.add(bank, 1, r);

        g.getColumnConstraints().addAll(
            new ColumnConstraints(70),
            new ColumnConstraints(220),
            new ColumnConstraints(70),
            new ColumnConstraints(220)
        );
        g.getColumnConstraints().get(1).setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().get(3).setHgrow(Priority.ALWAYS);

        return g;
    }

    private void buildSplitTable()
    {
        splitTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        splitTable.getColumns().add(col("Account", SplitRow::account));
        splitTable.getColumns().add(col("Fund", SplitRow::fund));
        splitTable.getColumns().add(col("Amount", SplitRow::amount));
        splitTable.getColumns().add(col("Activity", SplitRow::activity));
        splitTable.getColumns().add(col("Merchant", SplitRow::merchant));
        splitTable.getColumns().add(col("NMR", SplitRow::nmr));
        splitTable.getColumns().add(col("Notes", SplitRow::notes));

        splitTable.getItems().addAll(
            new SplitRow("", "", "", "", "", "", ""),
            new SplitRow("", "", "", "", "", "", "")
        );
    }

    private Node buildSplitEditor()
    {
        Label lbl = new Label("Splits");
        lbl.getStyleClass().add("subheader");

        Button addLine = new Button("+ Add Line");
        Button removeLine = new Button("– Remove");
        ToolBar tb = new ToolBar(addLine, removeLine);

        addLine.setOnAction(e -> splitTable.getItems().add(new SplitRow("", "", "", "", "", "", "")));
        removeLine.setOnAction(e -> {
            SplitRow sel = splitTable.getSelectionModel().getSelectedItem();
            if (sel != null) splitTable.getItems().remove(sel);
        });

        VBox box = new VBox(6, lbl, tb, splitTable);
        VBox.setVgrow(splitTable, Priority.ALWAYS);
        return box;
    }

    private TableColumn<SplitRow, String> col(String name, java.util.function.Function<SplitRow, String> getter)
    {
        TableColumn<SplitRow, String> c = new TableColumn<>(name);
        c.setCellValueFactory(v -> new SimpleStringProperty(getter.apply(v.getValue())));
        return c;
    }

    private void validateOrPost()
    {
        Alert a = new Alert(Alert.AlertType.INFORMATION, "Validate/Post (placeholder).");
        a.setHeaderText("Post / Validate");
        a.showAndWait();
    }

    private void showJournal()
    {
        Alert a = new Alert(Alert.AlertType.INFORMATION, "Journal View (placeholder).\n\nDetails-first inspector; journal is a drill-down.");
        a.setHeaderText("Journal View");
        a.showAndWait();
    }

    @Override public String title() { return "Transaction Editor"; }
    @Override public Node root() { return root; }

    @Override public void onSave()
    {
        Alert a = new Alert(Alert.AlertType.INFORMATION, "Save (placeholder).");
        a.setHeaderText("Save");
        a.showAndWait();
    }

    public record SplitRow(String account, String fund, String amount, String activity, String merchant, String nmr, String notes) {}
}
