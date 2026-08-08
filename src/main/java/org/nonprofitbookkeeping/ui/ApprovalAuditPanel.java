package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.AuditHistoryService.AuditEventView;
import org.nonprofitbookkeeping.service.AuditHistoryService.AuditHistoryFilter;

/**
 * Stable compatibility panel class for the production factual Audit History destination.
 * The {@link AppPanelId#APPROVAL_AUDIT} identifier is retained only for saved navigation compatibility.
 */
public class ApprovalAuditPanel implements AppPanel
{
    private static final int MAX_ROWS = 500;

    private final CompanyUiFormat companyFormat = CompanyUiFormat.activeCompany();
    private final BorderPane root = new BorderPane();
    private final TableView<AuditEventView> table = new TableView<>();
    private final Label status = new Label("Load factual audit history for the active company.");
    private final TextField actionFilter = new TextField();
    private final TextField entityFilter = new TextField();
    private final TextField actorFilter = new TextField();
    private final DatePicker fromDate = new DatePicker();
    private final DatePicker toDate = new DatePicker();
    private final TextArea beforeValue = detailArea();
    private final TextArea afterValue = detailArea();
    private final TextArea reason = detailArea();
    private final Label selectedIdentity = new Label("Select an audit event to inspect its factual details.");

    public ApprovalAuditPanel()
    {
        root.setPadding(new Insets(8));
        root.setMinSize(0, 0);

        Label title = new Label("Audit History");
        title.getStyleClass().add("panel-title");

        actionFilter.setPromptText("Action contains");
        entityFilter.setPromptText("Entity type or ID contains");
        actorFilter.setPromptText("Actor contains");
        companyFormat.install(fromDate);
        companyFormat.install(toDate);

        Button apply = new Button("Apply Filters");
        apply.setOnAction(e -> reload());
        Button reset = new Button("Reset");
        reset.setOnAction(e -> {
            actionFilter.clear();
            entityFilter.clear();
            actorFilter.clear();
            fromDate.setValue(null);
            toDate.setValue(null);
            reload();
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());

        GridPane filters = new GridPane();
        filters.setHgap(8);
        filters.setVgap(6);
        filters.add(new Label("Action"), 0, 0);
        filters.add(actionFilter, 1, 0);
        filters.add(new Label("Entity"), 2, 0);
        filters.add(entityFilter, 3, 0);
        filters.add(new Label("Actor"), 0, 1);
        filters.add(actorFilter, 1, 1);
        filters.add(new Label("From"), 2, 1);
        filters.add(fromDate, 3, 1);
        filters.add(new Label("To"), 4, 1);
        filters.add(toDate, 5, 1);
        GridPane.setHgrow(actionFilter, Priority.ALWAYS);
        GridPane.setHgrow(entityFilter, Priority.ALWAYS);
        GridPane.setHgrow(actorFilter, Priority.ALWAYS);

        HBox actions = new HBox(8, apply, reset, refresh);
        actions.getStyleClass().add("panel-action-row");
        status.setWrapText(true);
        root.setTop(new VBox(6, title, filters, actions, status, new Separator()));

        buildTable();
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> showDetails(selected));

        VBox tablePane = new VBox(6, new Label("Factual events"), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        tablePane.setMinSize(0, 0);

        GridPane details = new GridPane();
        details.setHgap(8);
        details.setVgap(6);
        details.add(new Label("Before"), 0, 0);
        details.add(beforeValue, 1, 0);
        details.add(new Label("After"), 0, 1);
        details.add(afterValue, 1, 1);
        details.add(new Label("Reason"), 0, 2);
        details.add(reason, 1, 2);
        GridPane.setHgrow(beforeValue, Priority.ALWAYS);
        GridPane.setHgrow(afterValue, Priority.ALWAYS);
        GridPane.setHgrow(reason, Priority.ALWAYS);
        GridPane.setVgrow(beforeValue, Priority.ALWAYS);
        GridPane.setVgrow(afterValue, Priority.ALWAYS);
        GridPane.setVgrow(reason, Priority.ALWAYS);

        VBox detailPane = new VBox(6, new Label("Selected factual event"), selectedIdentity, details);
        VBox.setVgrow(details, Priority.ALWAYS);
        detailPane.setMinSize(0, 0);

        SplitPane split = new SplitPane(tablePane, detailPane);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.62);
        split.setMinSize(0, 0);
        split.setId("auditHistoryTableDetailsSplit");
        CompanySplitPaneStateBinder.bind(split, "audit-history-table-details", 0.62);
        root.setCenter(split);

        reload();
    }

    @Override
    public String title()
    {
        return "Audit History";
    }

    @Override
    public Node root()
    {
        return root;
    }

    private void buildTable()
    {
        table.setId("auditHistoryTable");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No factual audit events found for the active company and filters."));

        table.getColumns().add(column("Occurred", "occurred", 190,
                row -> companyFormat.formatDateTime(row.occurredAt())));
        table.getColumns().add(column("Actor", "actor", 150, AuditEventView::actor));
        table.getColumns().add(column("Action", "action", 190, AuditEventView::actionType));
        table.getColumns().add(column("Entity Type", "entityType", 160, AuditEventView::entityType));
        table.getColumns().add(column("Entity ID", "entityId", 180, row -> safe(row.entityId())));
        table.getColumns().add(column("Summary", "summary", 360, AuditEventView::summary));
    }

    private void reload()
    {
        AuditEventView selected = table.getSelectionModel().getSelectedItem();
        var selectedPortableId = selected == null ? null : selected.portableId();
        AuditHistoryFilter filter;
        try
        {
            filter = new AuditHistoryFilter(
                    actionFilter.getText(),
                    entityFilter.getText(),
                    actorFilter.getText(),
                    fromDate.getValue(),
                    toDate.getValue());
        }
        catch (IllegalArgumentException ex)
        {
            status.setText(UiErrors.safeMessage(ex));
            return;
        }

        status.setText("Loading factual audit history...");
        UiAsync.run("audit-history-load", () -> UiServiceRegistry.auditHistory().listRecent(filter, MAX_ROWS), rows -> {
            table.getItems().setAll(rows);
            if (selectedPortableId != null)
            {
                rows.stream()
                        .filter(row -> selectedPortableId.equals(row.portableId()))
                        .findFirst()
                        .ifPresent(row -> table.getSelectionModel().select(row));
            }
            if (table.getSelectionModel().getSelectedItem() == null && !rows.isEmpty())
            {
                table.getSelectionModel().selectFirst();
            }
            if (rows.isEmpty())
            {
                showDetails(null);
            }
            status.setText("Loaded " + rows.size() + " factual audit event(s) for the active company.");
        }, ex -> status.setText("Could not load factual audit history: " + UiErrors.safeMessage(ex)));
    }

    private void showDetails(AuditEventView selected)
    {
        if (selected == null)
        {
            selectedIdentity.setText("Select an audit event to inspect its factual details.");
            beforeValue.clear();
            afterValue.clear();
            reason.clear();
            return;
        }
        selectedIdentity.setText(selected.actionType() + " — " + selected.entityType()
                + (safe(selected.entityId()).isBlank() ? "" : " " + selected.entityId())
                + " — " + companyFormat.formatDateTime(selected.occurredAt()));
        beforeValue.setText(safe(selected.beforeValue()));
        afterValue.setText(safe(selected.afterValue()));
        reason.setText(safe(selected.reason()));
    }

    private static TextArea detailArea()
    {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefRowCount(4);
        area.setMinHeight(72);
        area.setMaxHeight(180);
        return area;
    }

    private static TableColumn<AuditEventView, String> column(
            String title,
            String id,
            double width,
            java.util.function.Function<AuditEventView, String> value)
    {
        TableColumn<AuditEventView, String> column = new TableColumn<>(title);
        column.setId(id);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(safe(value.apply(cell.getValue()))));
        return column;
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }
}
