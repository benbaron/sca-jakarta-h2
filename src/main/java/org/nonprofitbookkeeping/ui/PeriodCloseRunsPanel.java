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
import org.nonprofitbookkeeping.repository.PeriodCloseRunRecord;

import java.time.LocalDate;

/**
 * PeriodCloseRunsPanel component.
 */
public class PeriodCloseRunsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<PeriodCloseRunRecord> table = new TableView<>();
    private final Label status = new Label();
    private final Label auditSummary = new Label();

    public PeriodCloseRunsPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Period Close Runs");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        Button record = new Button("Record Completed Close");
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


    private void recordRunWithStatus(org.nonprofitbookkeeping.repository.WorkflowRunStatus statusValue, String notes)
    {
        UiAsync.run("period-close-record-run-" + statusValue.name().toLowerCase(java.util.Locale.ROOT), () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            PeriodCloseRunRecord run = new PeriodCloseRunRecord(
                    java.util.UUID.randomUUID(),
                    group,
                    LocalDate.now(),
                    statusValue,
                    null,
                    notes);
            UiServiceRegistry.periodCloseRunRepository().append(run);
            return run;
        }, run -> {
            status.setText("Recorded " + run.status().name() + " close run for " + run.groupCode() + " on " + run.closeDate() + ".");
            reload();
        }, ex -> status.setText("Could not record " + statusValue.name() + " close run: " + UiErrors.safeMessage(ex)));
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
            reloadAuditSummary();
            status.setText("Loaded " + rows.size() +" period-close run(s) for active company.");
        }, ex -> status.setText("Could not load period-close runs: " + UiErrors.safeMessage(ex)));
    }

    private void openApprovalAudit()
    {
        Object selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            DrillThroughCoordinator.openPanelWithContext(AppPanelId.APPROVAL_AUDIT, "PERIOD_CLOSE");
            return;
        }
        java.util.UUID runId = ((PeriodCloseRunRecord) selected).id();
        DrillThroughCoordinator.openPanelWithContext(AppPanelId.APPROVAL_AUDIT, "PERIOD_CLOSE::" + runId);
    }

    private void reloadAuditSummary()
    {
        UiAsync.run("period_close-audit-summary", () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            java.util.List<org.nonprofitbookkeeping.repository.ApprovalAuditRecord> rows = UiServiceRegistry.approvalAuditService().listRecent(group, 500);
            long approvals = rows.stream().filter(r -> "PERIOD_CLOSE".equals(r.workflowType()) && r.decision() == ApprovalDecision.APPROVED).count();
            long rejections = rows.stream().filter(r -> "PERIOD_CLOSE".equals(r.workflowType()) && r.decision() == ApprovalDecision.REJECTED).count();
            return "Approval history: approvals=" + approvals + ", rejections=" + rejections;
        },
                auditSummary::setText,
                ex -> auditSummary.setText("Approval history unavailable: " + UiErrors.safeMessage(ex)));
    }

    private void recordApproval(ApprovalDecision decision)
    {
        PeriodCloseRunRecord selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a period-close run before recording an approval decision.");
            return;
        }

        UiAsync.run("period-close-approval-record", () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            return UiServiceRegistry.approvalAuditService().recordDecision(
                    group,
                    "PERIOD_CLOSE",
                    selected.id(),
                    decision,
                    "ui-operator",
                    "Recorded from Period Close Runs workspace");
        }, auditId -> status.setText("Recorded " + decision.name() + " decision under audit id " + auditId + "."),
                ex -> status.setText("Could not record approval decision: " + UiErrors.safeMessage(ex)));
    }
}
