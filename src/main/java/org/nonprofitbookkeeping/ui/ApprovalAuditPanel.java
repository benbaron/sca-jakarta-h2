package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.repository.ApprovalAuditRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * ApprovalAuditPanel component.
 */
public class ApprovalAuditPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<ApprovalAuditRecord> table = new TableView<>();
    private final Label status = new Label("Load recent approval and audit records for the active company.");
    private final TextField workflowTypeFilter = new TextField();
    private final TextField decisionFilter = new TextField();
    private final TextField actorFilter = new TextField();
    private final TextField runIdFilter = new TextField();
    private final DatePicker fromDate = new DatePicker();
    private final DatePicker toDate = new DatePicker();

    public ApprovalAuditPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Approval Audit");
        title.getStyleClass().add("panel-title");

        workflowTypeFilter.setPromptText("Workflow type");
        decisionFilter.setPromptText("Decision");
        actorFilter.setPromptText("Actor");
        runIdFilter.setPromptText("Run ID");

        Button apply = new Button("Apply Filters");
        apply.setOnAction(e -> reload());
        Button reset = new Button("Reset");
        reset.setOnAction(e -> {
            workflowTypeFilter.clear();
            decisionFilter.clear();
            actorFilter.clear();
            runIdFilter.clear();
            fromDate.setValue(null);
            toDate.setValue(null);
            reload();
        });

        HBox filters = new HBox(8,
                new Label("Workflow"), workflowTypeFilter,
                new Label("Decision"), decisionFilter,
                new Label("Actor"), actorFilter,
                new Label("Run ID"), runIdFilter,
                new Label("From"), fromDate,
                new Label("To"), toDate,
                apply,
                reset);
        HBox.setHgrow(workflowTypeFilter, Priority.ALWAYS);
        HBox.setHgrow(actorFilter, Priority.ALWAYS);

        root.setTop(new VBox(6, title, filters, status, new Separator()));
        buildTable();
        root.setCenter(table);

        String incomingContext = DrillThroughCoordinator.consumeContext(AppPanelId.APPROVAL_AUDIT);
        if (!incomingContext.isBlank())
        {
            String[] parts = incomingContext.split("::", 2);
            workflowTypeFilter.setText(parts[0]);
            if (parts.length == 2)
            {
                runIdFilter.setText(parts[1]);
            }
        }
        reload();
    }

    @Override
    public String title()
    {
        return "Approval Audit";
    }

    @Override
    public Node root()
    {
        return root;
    }

    private void buildTable()
    {
        TableColumn<ApprovalAuditRecord, String> created = new TableColumn<>("Created");
        created.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().createdAt())));
        TableColumn<ApprovalAuditRecord, String> workflow = new TableColumn<>("Workflow");
        workflow.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().workflowType()));
        TableColumn<ApprovalAuditRecord, String> runId = new TableColumn<>("Run ID");
        runId.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().workflowRunId() == null ? "" : v.getValue().workflowRunId().toString()));
        TableColumn<ApprovalAuditRecord, String> decision = new TableColumn<>("Decision");
        decision.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().decision().name()));
        TableColumn<ApprovalAuditRecord, String> actor = new TableColumn<>("Actor");
        actor.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().actor()));
        TableColumn<ApprovalAuditRecord, String> rationale = new TableColumn<>("Rationale");
        rationale.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().rationale()));
        table.getColumns().addAll(created, workflow, runId, decision, actor, rationale);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No approval/audit records found for current filters."));
    }

    private void reload()
    {
        status.setText("Loading approval/audit records...");
        UiAsync.run("approval-audit-load", () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            List<ApprovalAuditRecord> rows = UiServiceRegistry.approvalAuditService().listRecent(group, 500);
            return filter(rows,
                    workflowTypeFilter.getText(),
                    decisionFilter.getText(),
                    actorFilter.getText(),
                    runIdFilter.getText(),
                    fromDate.getValue(),
                    toDate.getValue());
        }, rows -> {
            table.getItems().setAll(rows);
            status.setText("Loaded " + rows.size() + " approval/audit record(s) for active filters.");
        }, ex -> status.setText("Could not load approval/audit records: " + UiErrors.safeMessage(ex)));
    }

    static List<ApprovalAuditRecord> filter(List<ApprovalAuditRecord> rows,
                                            String workflowType,
                                            String decision,
                                            String actor,
                                            String runId,
                                            LocalDate from,
                                            LocalDate to)
    {
        String workflowQuery = normalize(workflowType);
        String decisionQuery = normalize(decision);
        String actorQuery = normalize(actor);
        String runIdQuery = normalize(runId);

        return rows.stream()
                .filter(r -> contains(normalize(r.workflowType()), workflowQuery))
                .filter(r -> contains(normalize(r.decision().name()), decisionQuery))
                .filter(r -> contains(normalize(r.actor()), actorQuery))
                .filter(r -> contains(normalize(r.workflowRunId() == null ? "" : r.workflowRunId().toString()), runIdQuery))
                .filter(r -> from == null || !r.createdAt().toLocalDate().isBefore(from))
                .filter(r -> to == null || !r.createdAt().toLocalDate().isAfter(to))
                .toList();
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String value, String query)
    {
        if (query == null || query.isBlank())
        {
            return true;
        }
        return value.contains(query);
    }
}
