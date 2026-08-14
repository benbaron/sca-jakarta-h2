package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.CompanyAdminService;
import org.nonprofitbookkeeping.service.CompanyOwnershipIssueView;
import org.nonprofitbookkeeping.service.CompanyOwnershipRepairResult;
import org.nonprofitbookkeeping.service.CompanyOwnershipService;
import org.nonprofitbookkeeping.service.CompanyView;

import java.util.Objects;
import java.util.function.Supplier;

/** Explicit, audited repair surface for unresolved legacy company ownership. */
public final class CompanyOwnershipDiagnosticsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<CompanyOwnershipIssueView> issues = new TableView<>();
    private final ComboBox<CompanyView> targetCompany = new ComboBox<>();
    private final TextField actor = new TextField("ui-operator");
    private final TextArea reason = new TextArea();
    private final TextArea details = new TextArea();
    private final Button assignOwner = new Button("Assign Owner…");
    private final Label status = new Label("Loading ownership diagnostics…");
    private final Supplier<CompanyOwnershipService> ownershipService;
    private final Supplier<CompanyAdminService> companyService;

    public CompanyOwnershipDiagnosticsPanel()
    {
        this(UiServiceRegistry::companyOwnership, UiServiceRegistry::companyAdmin);
    }

    CompanyOwnershipDiagnosticsPanel(
            Supplier<CompanyOwnershipService> ownershipService,
            Supplier<CompanyAdminService> companyService)
    {
        this.ownershipService = Objects.requireNonNull(ownershipService, "ownershipService");
        this.companyService = Objects.requireNonNull(companyService, "companyService");
        build();
        reload();
    }

    private void build()
    {
        root.setPadding(new Insets(8));
        root.setMinWidth(0.0);
        root.setMinHeight(0.0);

        Label title = new Label("Company Ownership Diagnostics");
        title.getStyleClass().add("panel-title");
        Label help = new Label(
                "These records predate authoritative multi-company ownership or contain conflicting references. "
                        + "Assign an owner only when you know which company historically owned the selected record. "
                        + "No accounting reference is changed automatically.");
        help.setWrapText(true);
        Button refresh = new Button("Refresh");
        refresh.setId("ownershipDiagnosticsRefreshButton");
        refresh.setOnAction(event -> reload());
        status.setId("ownershipDiagnosticsStatus");
        status.setWrapText(true);
        root.setTop(new VBox(6, title, help, refresh, status));

        configureTable();
        VBox tableRegion = new VBox(6, new Label("Open blocking diagnostics"), issues);
        tableRegion.setMinHeight(0.0);
        VBox.setVgrow(issues, Priority.ALWAYS);

        ScrollPane repairScroll = new ScrollPane(buildRepairEditor());
        repairScroll.setId("ownershipDiagnosticsRepairScroll");
        repairScroll.setFitToWidth(true);
        repairScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        repairScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        SplitPane split = new SplitPane(tableRegion, repairScroll);
        split.setId("ownershipDiagnosticsWorkspaceSplit");
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.58);
        CompanySplitPaneStateBinder.bind(split, "ownership-diagnostics", 0.58);
        root.setCenter(split);
    }

    private void configureTable()
    {
        issues.setId("ownershipDiagnosticsTable");
        issues.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        issues.setPlaceholder(new Label("No unresolved ownership diagnostics."));
        addColumn("Type", CompanyOwnershipIssueView::entityType, 170);
        addColumn("Record ID", CompanyOwnershipIssueView::entityId, 110);
        addColumn("Record", CompanyOwnershipIssueView::recordLabel, 260);
        addColumn("Related company evidence", value -> value.relationshipCompanyCodes().isEmpty()
                ? "None" : String.join(", ", value.relationshipCompanyCodes()), 210);
        addColumn("Issue", CompanyOwnershipIssueView::issueCode, 190);
        addColumn("Candidate companies", value -> Integer.toString(value.candidateCompanyCount()), 150);
        addColumn("Detected", value -> value.detectedAt().toString(), 190);
        addColumn("Cause", CompanyOwnershipIssueView::details, 430);
        issues.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> showIssue(newValue));
    }

    private void addColumn(
            String title,
            java.util.function.Function<CompanyOwnershipIssueView, String> extractor,
            double width)
    {
        TableColumn<CompanyOwnershipIssueView, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(extractor.apply(data.getValue())));
        column.setPrefWidth(width);
        column.setMinWidth(80);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        issues.getColumns().add(column);
    }

    private Node buildRepairEditor()
    {
        targetCompany.setId("ownershipDiagnosticsTargetCompany");
        targetCompany.setPromptText("Select the actual owner");
        targetCompany.setMaxWidth(Double.MAX_VALUE);
        targetCompany.valueProperty().addListener((observable, oldValue, newValue) -> updateAvailability());
        actor.setId("ownershipDiagnosticsActor");
        reason.setId("ownershipDiagnosticsReason");
        reason.setPromptText("Explain the evidence used to identify the historical owner.");
        reason.setWrapText(true);
        reason.setPrefRowCount(3);
        reason.textProperty().addListener((observable, oldValue, newValue) -> updateAvailability());
        actor.textProperty().addListener((observable, oldValue, newValue) -> updateAvailability());

        details.setId("ownershipDiagnosticsResolution");
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(5);
        details.setText("Select a diagnostic to see its cause and resolution.");

        assignOwner.setId("ownershipDiagnosticsAssignOwnerButton");
        assignOwner.setDisable(true);
        assignOwner.setOnAction(event -> confirmAssignment());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(8));
        form.addRow(0, new Label("Selected diagnostic"), details);
        form.addRow(1, new Label("Actual owning company"), targetCompany);
        form.addRow(2, new Label("Actor"), actor);
        form.addRow(3, new Label("Reason / evidence"), reason);
        form.add(assignOwner, 1, 4);
        for (Node field : java.util.List.of(details, targetCompany, actor, reason))
        {
            GridPane.setHgrow(field, Priority.ALWAYS);
        }

        Label warning = new Label(
                "Assignment changes only the selected record's company owner and records an audit event. "
                        + "If the row already belongs to another company, is stale, or would violate a company-scoped "
                        + "constraint, the entire operation is rolled back.");
        warning.setWrapText(true);
        return new VBox(8, new Label("Resolve selected diagnostic"), form, warning);
    }

    private void reload()
    {
        Long selectedId = selectedIssueId();
        try
        {
            var open = ownershipService.get().listOpenIssues();
            issues.getItems().setAll(open);
            targetCompany.setItems(FXCollections.observableArrayList(
                    companyService.get().listActiveCompanyViews()));
            if (selectedId != null)
            {
                open.stream().filter(value -> selectedId.equals(value.id())).findFirst()
                        .ifPresent(value -> issues.getSelectionModel().select(value));
            }
            if (issues.getSelectionModel().getSelectedItem() == null && !open.isEmpty())
            {
                issues.getSelectionModel().selectFirst();
            }
            if (open.isEmpty())
            {
                showIssue(null);
                status.setText("No unresolved ownership diagnostics. SCLX preview may be run again.");
            }
            else
            {
                status.setText(open.size() + " unresolved ownership diagnostic(s) still block governed previews and imports.");
            }
        }
        catch (RuntimeException ex)
        {
            issues.getItems().clear();
            status.setText("Could not load ownership diagnostics: " + UiErrors.safeMessage(ex));
        }
        updateAvailability();
    }

    private void showIssue(CompanyOwnershipIssueView issue)
    {
        if (issue == null)
        {
            details.setText("Select a diagnostic to see its cause and resolution.");
        }
        else
        {
            details.setText(issue.entityType() + " " + issue.entityId() + " — " + issue.recordLabel()
                    + "\n" + issue.details()
                    + "\nRelated company evidence: " + (issue.relationshipCompanyCodes().isEmpty()
                    ? "None" : String.join(", ", issue.relationshipCompanyCodes()))
                    + "\n\nResolution: " + issue.resolutionGuidance());
            if (issue.relationshipCompanyCodes().size() == 1)
            {
                targetCompany.getItems().stream()
                        .filter(value -> issue.companyChoiceCompatible(value.code()))
                        .findFirst()
                        .ifPresent(value -> targetCompany.getSelectionModel().select(value));
            }
        }
        updateAvailability();
    }

    private void updateAvailability()
    {
        CompanyOwnershipIssueView issue = issues.getSelectionModel().getSelectedItem();
        assignOwner.setDisable(issue == null
                || !issue.directlyAssignable()
                || targetCompany.getValue() == null
                || !issue.companyChoiceCompatible(targetCompany.getValue().code())
                || actor.getText() == null
                || actor.getText().isBlank()
                || reason.getText() == null
                || reason.getText().isBlank());
    }

    private void confirmAssignment()
    {
        CompanyOwnershipIssueView issue = issues.getSelectionModel().getSelectedItem();
        CompanyView company = targetCompany.getValue();
        if (issue == null || company == null || assignOwner.isDisabled())
        {
            status.setText("Select an assignable diagnostic, active company, actor, and reason first.");
            return;
        }

        Alert confirmation = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Assign " + issue.entityType() + " record " + issue.entityId() + " to "
                        + company.code() + " — " + company.displayName() + "?\n\n"
                        + "Reason: " + reason.getText().trim()
                        + "\n\nThis does not rewrite accounting references and cannot be undone from this screen.",
                ButtonType.OK,
                ButtonType.CANCEL);
        confirmation.setTitle("Confirm Company Ownership Assignment");
        confirmation.setHeaderText("Assign the selected historical record to one company");
        if (root.getScene() != null && root.getScene().getWindow() != null)
        {
            confirmation.initOwner(root.getScene().getWindow());
        }
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
        {
            status.setText("Ownership assignment cancelled; no data was changed.");
            return;
        }

        try
        {
            CompanyOwnershipRepairResult result = ownershipService.get().assignOwner(
                    issue.id(), company.id(), actor.getText(), reason.getText());
            reason.clear();
            reload();
            status.setText("Assigned " + result.entityType() + " " + result.entityId() + " to "
                    + result.companyCode() + ". " + result.remainingOpenIssues()
                    + " unresolved diagnostic(s) remain. Preview the SCLX file again when the count reaches zero.");
        }
        catch (RuntimeException ex)
        {
            status.setText("Ownership assignment failed; no data was changed: " + UiErrors.safeMessage(ex));
        }
    }

    private Long selectedIssueId()
    {
        CompanyOwnershipIssueView selected = issues.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.id();
    }

    @Override
    public String title()
    {
        return "Company Ownership Diagnostics";
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public void onPanelShown()
    {
        reload();
    }

    TableView<CompanyOwnershipIssueView> issuesForTests()
    {
        return issues;
    }
}
