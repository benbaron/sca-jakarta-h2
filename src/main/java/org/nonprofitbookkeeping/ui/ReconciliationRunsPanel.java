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
import org.nonprofitbookkeeping.repository.ApprovalDecision;
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
    private final Label auditSummary = new Label();

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
        Button approve = new Button("Approve Selected");
        approve.setOnAction(e -> recordApproval(ApprovalDecision.APPROVED));
        Button reject = new Button("Reject Selected");
        reject.setOnAction(e -> recordApproval(ApprovalDecision.REJECTED));
        Button viewAudit = new Button("View Approval Audit");
        viewAudit.setOnAction(e -> openApprovalAudit());

        root.setTop(new VBox(6, title, new HBox(8, refresh, start, record, fail, approve, reject, viewAudit), status, auditSummary, new Separator()));

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
            reloadAuditSummary();
            status.setText("Loaded " + rows.size() +" reconciliation run(s) for active company.");
        }, ex -> status.setText("Could not load reconciliation runs: " + UiErrors.safeMessage(ex)));
    }

    private void openApprovalAudit()
    {
        Object selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            DrillThroughCoordinator.openPanelWithContext(AppPanelId.APPROVAL_AUDIT, "RECONCILIATION");
            return;
        }
        java.util.UUID runId = ((ReconciliationRunRecord) selected).id();
        DrillThroughCoordinator.openPanelWithContext(AppPanelId.APPROVAL_AUDIT, "RECONCILIATION::" + runId);
    }

    private void reloadAuditSummary()
    {
        UiAsync.run("reconciliation-audit-summary", () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            java.util.List<org.nonprofitbookkeeping.repository.ApprovalAuditRecord> rows = UiServiceRegistry.approvalAuditService().listRecent(group, 500);
            long approvals = rows.stream().filter(r -> "RECONCILIATION".equals(r.workflowType()) && r.decision() == ApprovalDecision.APPROVED).count();
            long rejections = rows.stream().filter(r -> "RECONCILIATION".equals(r.workflowType()) && r.decision() == ApprovalDecision.REJECTED).count();
            return "Approval history: approvals=" + approvals + ", rejections=" + rejections;
        },
                auditSummary::setText,
                ex -> auditSummary.setText("Approval history unavailable: " + UiErrors.safeMessage(ex)));
    }

    private void recordApproval(ApprovalDecision decision)
    {
        ReconciliationRunRecord selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a reconciliation run before recording an approval decision.");
            return;
        }

        UiAsync.run("recon-approval-record", () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            return UiServiceRegistry.approvalAuditService().recordDecision(
                    group,
                    "RECONCILIATION",
                    selected.id(),
                    decision,
                    "ui-operator",
                    "Recorded from Reconciliation Runs workspace");
        }, auditId -> status.setText("Recorded " + decision.name() + " decision under audit id " + auditId + "."),
                ex -> status.setText("Could not record approval decision: " + UiErrors.safeMessage(ex)));
    }
}
