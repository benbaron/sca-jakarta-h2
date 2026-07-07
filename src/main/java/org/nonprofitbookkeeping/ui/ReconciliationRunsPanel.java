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
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.repository.ReconciliationRunRecord;

import java.time.LocalDate;

/**
 * ReconciliationRunsPanel component.
 */
public class ReconciliationRunsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<ReconciliationRunRecord> table = new TableView<>();
    private final Label status = new Label();

    public ReconciliationRunsPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Reconciliation Runs");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        Button record = new Button("Record Completed Run");
        record.setOnAction(e -> recordRun());
        Button start = new Button("Record Started");
        start.setOnAction(e -> recordRunWithStatus(org.nonprofitbookkeeping.repository.WorkflowRunStatus.STARTED, "Started from UI workspace"));
        Button fail = new Button("Record Failed");
        fail.setOnAction(e -> recordRunWithStatus(org.nonprofitbookkeeping.repository.WorkflowRunStatus.FAILED, "Failed from UI workspace"));
        Label workflowNote = new Label("Reconciliation is a comparison workflow; approve/reject decisions are not part of this panel.");
        workflowNote.getStyleClass().add("help-text");

        root.setTop(new VBox(6, title, new HBox(8, refresh, start, record, fail), workflowNote, status, new Separator()));

        TableColumn<ReconciliationRunRecord, String> when = new TableColumn<>("Statement End");
        when.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().statementEndingOn())));
        TableColumn<ReconciliationRunRecord, String> format = new TableColumn<>("Format");
        format.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().bankFormat().name()));
        TableColumn<ReconciliationRunRecord, String> txns = new TableColumn<>("Imported Txns");
        txns.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().importedTransactionCount())));
        TableColumn<ReconciliationRunRecord, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().status().name()));
        TableColumn<ReconciliationRunRecord, String> notes = new TableColumn<>("Notes");
        notes.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().notes() == null ? "" : v.getValue().notes()));

        table.getColumns().addAll(when, format, txns, statusCol, notes);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No reconciliation runs found for active company."));
        root.setCenter(table);

        reload();
    }

    @Override
    public String title()
    {
        return "Reconciliation Runs";
    }

    @Override
    public Node root()
    {
        return root;
    }

    private void recordRun()
    {
        UiAsync.run("recon-record-run", () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            return UiServiceRegistry.reconciliationService()
                    .recordCompletedRun(group, LocalDate.now(), BankingDataFormat.OFX, 0, "Recorded from UI workspace");
        }, run -> {
            status.setText("Recorded run for " + run.groupCode() + " ending " + run.statementEndingOn() + ".");
            reload();
        }, ex -> status.setText("Could not record run: " + UiErrors.safeMessage(ex)));
    }


    private void recordRunWithStatus(org.nonprofitbookkeeping.repository.WorkflowRunStatus statusValue, String notes)
    {
        UiAsync.run("recon-record-run-" + statusValue.name().toLowerCase(java.util.Locale.ROOT), () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            ReconciliationRunRecord run = new ReconciliationRunRecord(
                    java.util.UUID.randomUUID(),
                    group,
                    LocalDate.now(),
                    BankingDataFormat.OFX,
                    0,
                    statusValue,
                    notes);
            UiServiceRegistry.reconciliationRunRepository().append(run);
            return run;
        }, run -> {
            status.setText("Recorded " + run.status().name() + " run for " + run.groupCode() + " ending " + run.statementEndingOn() + ".");
            reload();
        }, ex -> status.setText("Could not record " + statusValue.name() + " run: " + UiErrors.safeMessage(ex)));
    }

    private void reload()
    {
        status.setText("Loading reconciliation runs...");
        UiAsync.run("recon-runs-load", () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            return UiServiceRegistry.reconciliationRunRepository()
                    .findByGroupAndDateRange(group, LocalDate.now().minusYears(1), LocalDate.now().plusDays(1));
        }, rows -> {
            table.getItems().setAll(rows);
            status.setText("Loaded " + rows.size() +" reconciliation comparison run(s) for active company.");
        }, ex -> status.setText("Could not load reconciliation runs: " + UiErrors.safeMessage(ex)));
    }

}
