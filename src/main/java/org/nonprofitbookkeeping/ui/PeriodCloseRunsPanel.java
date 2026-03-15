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
import org.nonprofitbookkeeping.repository.PeriodCloseRunRecord;

import java.time.LocalDate;

public class PeriodCloseRunsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<PeriodCloseRunRecord> table = new TableView<>();
    private final Label status = new Label();

    public PeriodCloseRunsPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Period Close Runs");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        Button record = new Button("Record Completed Close");
        record.setOnAction(e -> recordRun());

        root.setTop(new VBox(6, title, new HBox(8, refresh, record), status, new Separator()));

        TableColumn<PeriodCloseRunRecord, String> closeDate = new TableColumn<>("Close Date");
        closeDate.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().closeDate())));
        TableColumn<PeriodCloseRunRecord, String> workflowStatus = new TableColumn<>("Status");
        workflowStatus.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().status().name()));
        TableColumn<PeriodCloseRunRecord, String> producedTxn = new TableColumn<>("Produced Txn");
        producedTxn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().producedTransactionId() == null ? "" : v.getValue().producedTransactionId().toString()));
        TableColumn<PeriodCloseRunRecord, String> notes = new TableColumn<>("Notes");
        notes.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().notes() == null ? "" : v.getValue().notes()));

        table.getColumns().addAll(closeDate, workflowStatus, producedTxn, notes);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No period-close runs found for active company."));
        root.setCenter(table);

        reload();
    }

    @Override
    public String title()
    {
        return "Period Close Runs";
    }

    @Override
    public Node root()
    {
        return root;
    }

    private void recordRun()
    {
        UiAsync.run("period-close-record-run", () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            return UiServiceRegistry.periodCloseService()
                    .recordCompletedClose(group, LocalDate.now(), null, "Recorded from UI workspace");
        }, run -> {
            status.setText("Recorded close run for " + run.groupCode() + " on " + run.closeDate() + ".");
            reload();
        }, ex -> status.setText("Could not record close run: " + UiErrors.safeMessage(ex)));
    }

    private void reload()
    {
        status.setText("Loading period-close runs...");
        UiAsync.run("period-close-runs-load", () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            return UiServiceRegistry.periodCloseRunRepository()
                    .findByGroupAndDateRange(group, LocalDate.now().minusYears(1), LocalDate.now().plusDays(1));
        }, rows -> {
            table.getItems().setAll(rows);
            status.setText("Loaded " + rows.size() + " period-close run(s) for active company.");
        }, ex -> status.setText("Could not load period-close runs: " + UiErrors.safeMessage(ex)));
    }
}
