package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * ImportExportJobsPanel component.
 */
public class ImportExportJobsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<UiWorkspaceDataStore.ImportExportJob> table = new TableView<>();
    private final Label status = new Label("Import/export operations are tracked here for this session.");

    public ImportExportJobsPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Import / Export Jobs");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        Button clear = new Button("Clear Session History");
        clear.setOnAction(e -> {
            UiWorkspaceDataStore.clearJobsForTests();
            reload();
        });

        root.setTop(new VBox(6, title, new HBox(8, refresh, clear), status, new Separator()));
        buildTable();
        root.setCenter(table);

        reload();
    }

    @Override
    public String title()
    {
        return "Import / Export Jobs";
    }

    @Override
    public Node root()
    {
        return root;
    }

    private void buildTable()
    {
        TableColumn<UiWorkspaceDataStore.ImportExportJob, String> when = new TableColumn<>("Recorded At");
        when.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().recordedAt())));
        TableColumn<UiWorkspaceDataStore.ImportExportJob, String> op = new TableColumn<>("Operation");
        op.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().operation()));
        TableColumn<UiWorkspaceDataStore.ImportExportJob, String> source = new TableColumn<>("Source");
        source.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().sourcePath()));
        TableColumn<UiWorkspaceDataStore.ImportExportJob, String> target = new TableColumn<>("Target");
        target.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().targetPath()));
        TableColumn<UiWorkspaceDataStore.ImportExportJob, String> format = new TableColumn<>("Format");
        format.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().format() == null ? "" : v.getValue().format().name()));
        TableColumn<UiWorkspaceDataStore.ImportExportJob, String> rows = new TableColumn<>("Rows");
        rows.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().rowCount())));
        TableColumn<UiWorkspaceDataStore.ImportExportJob, String> txns = new TableColumn<>("Transactions");
        txns.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().transactionCount())));
        TableColumn<UiWorkspaceDataStore.ImportExportJob, String> outcome = new TableColumn<>("Outcome");
        outcome.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().outcome()));
        TableColumn<UiWorkspaceDataStore.ImportExportJob, String> err = new TableColumn<>("Error");
        err.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().error()));

        table.getColumns().addAll(when, op, source, target, format, rows, txns, outcome, err);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No import/export jobs have been recorded in this session."));
    }

    private void reload()
    {
        table.getItems().setAll(UiWorkspaceDataStore.jobs());
        status.setText("Loaded " + table.getItems().size() + " import/export job record(s).");
    }
}
