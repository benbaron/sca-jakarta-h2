package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.ApplicationPermission;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
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
    private final Label targetCompany = new Label("No active import company");
    private CompanyView importCompany;
    private final TextField actor = new TextField(DesktopActorIdentity.current());
    private final TextArea reason = new TextArea();
    private final TextArea details = new TextArea();
    private final Button assignOwner = new Button("Assign to Import Company…");
    private final Label status = new Label("Loading ownership diagnostics…");
    private final Supplier<CompanyOwnershipService> ownershipService;
    private final Supplier<CompanyAdminService> companyService;
    private final Supplier<String> activeCompanyCode;

    public CompanyOwnershipDiagnosticsPanel()
    {
        this(UiServiceRegistry::companyOwnership, UiServiceRegistry::companyAdmin,
                () -> MainWindow.sharedSessionState().multiCompany().activeCompanyCode());
    }

    CompanyOwnershipDiagnosticsPanel(
            Supplier<CompanyOwnershipService> ownershipService,
            Supplier<CompanyAdminService> companyService)
    {
        this(ownershipService, companyService, () -> null);
    }

    CompanyOwnershipDiagnosticsPanel(
            Supplier<CompanyOwnershipService> ownershipService,
            Supplier<CompanyAdminService> companyService,
            Supplier<String> activeCompanyCode)
    {
        this.ownershipService = Objects.requireNonNull(ownershipService, "ownershipService");
        this.companyService = Objects.requireNonNull(companyService, "companyService");
        this.activeCompanyCode = Objects.requireNonNull(activeCompanyCode, "activeCompanyCode");
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
                        + "For a direct ownerless record, the active company receiving the import is authoritative. "
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
        targetCompany.setMaxWidth(Double.MAX_VALUE);
        actor.setId("ownershipDiagnosticsActor");
        actor.setPromptText("Authenticated audit actor");
        actor.setEditable(false);
        reason.setId("ownershipDiagnosticsReason");
        reason.setPromptText("Record why this company is receiving the imported record.");
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
        UiPermissionGate.gate(assignOwner, ApplicationPermission.DATABASE_ADMIN, "Repair company ownership");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(8));
        form.addRow(0, new Label("Selected diagnostic"), details);
        form.addRow(1, new Label("Import target company"), targetCompany);
        form.addRow(2, new Label("Actor"), actor);
        form.addRow(3, new Label("Audit note"), reason);
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
            selectActiveImportCompany(companyService.get().listActiveCompanyViews());
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

    private void selectActiveImportCompany(java.util.List<CompanyView> companies)
    {
        String code = activeCompanyCode.get();
        importCompany = code == null || code.isBlank()
                ? (companies.size() == 1 ? companies.get(0) : null)
                : companies.stream()
                        .filter(value -> value.code().equalsIgnoreCase(code))
                        .findFirst().orElse(null);
        targetCompany.setText(importCompany == null
                ? "No active import company"
                : importCompany.code() + " — " + importCompany.displayName());
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
        }
        updateAvailability();
    }

    private void updateAvailability()
    {
        CompanyOwnershipIssueView issue = issues.getSelectionModel().getSelectedItem();
        assignOwner.setDisable(issue == null
                || !issue.directlyAssignable()
                || importCompany == null
                || !issue.companyChoiceCompatible(importCompany.code())
                || actor.getText() == null
                || actor.getText().isBlank()
                || reason.getText() == null
                || reason.getText().isBlank());
    }

    private void confirmAssignment()
    {
        CompanyOwnershipIssueView issue = issues.getSelectionModel().getSelectedItem();
        CompanyView company = importCompany;
        if (issue == null || company == null || assignOwner.isDisabled())
        {
            status.setText("Select an assignable diagnostic and enter an audit note. "
                    + "An authenticated ADMIN session and a compatible active import company are required.");
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
        confirmation.setHeaderText("Assign the selected record to the import target company");
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
                    + " unresolved diagnostic(s) remain. Re-preview the same SCLX file when the count reaches zero.");
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
