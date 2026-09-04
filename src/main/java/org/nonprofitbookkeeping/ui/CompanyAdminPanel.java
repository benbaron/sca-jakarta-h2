package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.ApplicationPermission;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.report.ReportDefinition;
import org.nonprofitbookkeeping.service.CompanyChartView;
import org.nonprofitbookkeeping.service.CompanyCommand;
import org.nonprofitbookkeeping.service.CompanyReportingDefaults;
import org.nonprofitbookkeeping.service.CompanyView;
import org.nonprofitbookkeeping.service.FinancialReportExportFormat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** H2-authoritative company profile, informational EIN, chart assignment, reporting defaults, and lifecycle editor. */
public class CompanyAdminPanel implements AppPanel
{
    private static final String STATE_PREFIX = "companyAdmin.";

    private final BorderPane root = new BorderPane();
    private final TableView<CompanyView> companies = new TableView<>();
    private final Label status = new Label("Ready.");
    private final Label editMode = new Label("New company");
    private final Label activeCompany = new Label();
    private final TextField code = new TextField();
    private final TextField displayName = new TextField();
    private final TextField legalName = new TextField();
    private final TextField branchType = new TextField();
    private final TextField parentOrganization = new TextField();
    private final TextField ein = new TextField();
    private final ComboBox<Integer> fiscalMonth = new ComboBox<>();
    private final Spinner<Integer> fiscalDay = new Spinner<>();
    private final TextField defaultCurrency = new TextField();
    private final CheckBox active = new CheckBox("Active");
    private final Button selectActive = new Button("Select Active");
    private final ComboBox<CompanyChartView> chartAssignment = new ComboBox<>();
    private final Button makeActiveChart = new Button("Make Active Chart");
    private final Label chartStatus = new Label("Select a persisted company to review its charts.");
    private final ComboBox<ReportDefinition> defaultReport = new ComboBox<>();
    private final ComboBox<FinancialReportExportFormat> defaultReportExportFormat = new ComboBox<>();
    private final Label reportingDefaultsStatus = new Label(
            "Save the company before choosing Report Library defaults.");
    private final SplitPane split = new SplitPane();
    private final PauseTransition stateSaveDelay = new PauseTransition(Duration.millis(350));
    private final Map<String, TableColumn<CompanyView, String>> columnsByKey = new LinkedHashMap<>();
    private final CompanySessionController companyController;
    private final org.nonprofitbookkeeping.service.CompanyUiPreferencesService preferencesService;
    private final String layoutOwnerCompany;

    private Long editingCompanyId;
    private boolean populating;
    private boolean dirty;
    private boolean restoringState;
    private boolean suppressSelection;

    public CompanyAdminPanel()
    {
        this(new CompanySessionController(
                MainWindow.sharedSessionState(),
                UserAppStateStore.create(),
                UiServiceRegistry::companyAdmin));
    }

    CompanyAdminPanel(CompanySessionController companyController)
    {
        this.companyController = Objects.requireNonNull(companyController, "companyController");
        this.preferencesService = UiServiceRegistry.companyUiPreferences();
        this.layoutOwnerCompany = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        build();
        restoreLayoutState();
        installLayoutStateListeners();
        clearForm(false);
        reload(null);
    }

    private void build()
    {
        root.setPadding(new Insets(8));
        root.setMinWidth(0.0);
        root.setMinHeight(0.0);

        Label title = new Label("Company Admin");
        title.getStyleClass().add("panel-title");
        Label help = new Label("Company rows in the active H2 database define which companies exist. Create or edit a profile and informational EIN here, deactivate unused companies without deleting their history, explicitly select an active company for the workspace, select its current Chart of Accounts, and choose safe Report Library opening defaults.");
        help.setWrapText(true);

        Button add = new Button("New");
        add.setOnAction(event -> onNew());
        Button save = new Button("Save");
        save.setOnAction(event -> saveCompany());
        UiPermissionGate.gate(add, ApplicationPermission.COMPANY_ADMIN, "Create a company");
        UiPermissionGate.gate(save, ApplicationPermission.COMPANY_ADMIN, "Save company administration changes");
        UiPermissionGate.gate(makeActiveChart, ApplicationPermission.COMPANY_ADMIN, "Change the active Chart of Accounts");
        UiPermissionGate.gate(defaultReport, ApplicationPermission.COMPANY_ADMIN, "Change company reporting defaults");
        UiPermissionGate.gate(defaultReportExportFormat, ApplicationPermission.COMPANY_ADMIN, "Change company reporting defaults");
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> reload(editingCompanyId));
        selectActive.setDisable(true);
        selectActive.setOnAction(event -> selectActiveCompany());
        activeCompany.setText("Workspace company: " + currentCompanyCode());

        HBox actions = new HBox(8, add, save, selectActive, refresh);
        root.setTop(new VBox(6, title, help, actions, activeCompany, status));

        configureCompaniesTable();
        VBox tableRegion = new VBox(6, new Label("Companies in this database"), companies);
        tableRegion.setMinHeight(0.0);
        VBox.setVgrow(companies, Priority.ALWAYS);

        ScrollPane editorScroll = new ScrollPane(buildEditor());
        editorScroll.setId("companyAdminEditorScroll");
        editorScroll.setFitToWidth(true);
        editorScroll.setMinHeight(0.0);
        editorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editorScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        split.setId("companyAdminWorkspaceSplit");
        split.setOrientation(Orientation.VERTICAL);
        split.getItems().setAll(tableRegion, editorScroll);
        split.setDividerPositions(0.56);
        root.setCenter(split);
        installDirtyListeners();
    }

    private Node buildEditor()
    {
        for (int month = 1; month <= 12; month++)
        {
            fiscalMonth.getItems().add(month);
        }
        fiscalDay.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 31, 1));
        fiscalDay.setEditable(true);
        defaultCurrency.setPromptText("USD");
        ein.setPromptText("Optional informational identifier");
        configureReportingDefaults();

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(8));
        int row = 0;
        form.addRow(row++, new Label("Mode"), editMode);
        form.addRow(row++, new Label("Code"), code);
        form.addRow(row++, new Label("Display name"), displayName);
        form.addRow(row++, new Label("Legal name"), legalName);
        form.addRow(row++, new Label("Branch type"), branchType);
        form.addRow(row++, new Label("Parent organization / kingdom"), parentOrganization);
        form.addRow(row++, new Label("EIN"), ein);
        form.addRow(row++, new Label("Fiscal-year start month"), fiscalMonth);
        form.addRow(row++, new Label("Fiscal-year start day"), fiscalDay);
        form.addRow(row++, new Label("Default currency"), defaultCurrency);
        form.add(active, 1, row);
        for (Node field : List.of(code, displayName, legalName, branchType, parentOrganization, ein, fiscalMonth, fiscalDay, defaultCurrency))
        {
            GridPane.setHgrow(field, Priority.ALWAYS);
        }

        Label lifecycle = new Label("Companies are not hard-deleted. Clear Active and save to deactivate a non-current company. The current company and the last active company are protected by the application service.");
        lifecycle.setWrapText(true);

        chartAssignment.setId("companyChartAssignment");
        chartAssignment.setPromptText("Select an owned Chart of Accounts");
        chartAssignment.setMaxWidth(Double.MAX_VALUE);
        chartAssignment.valueProperty().addListener((obs, oldValue, newValue) -> updateChartActionState());
        makeActiveChart.setId("makeActiveCompanyChart");
        makeActiveChart.setDisable(true);
        makeActiveChart.setOnAction(event -> assignActiveChart());
        chartStatus.setWrapText(true);
        Label chartHelp = new Label("The active-chart pointer controls new account maintenance and chart-targeted imports. Selecting a DRAFT chart promotes it to ACTIVE. Existing ACTIVE charts, accounts, transactions, and historical references remain attached to their original chart; nothing is moved or auto-retired.");
        chartHelp.setWrapText(true);
        VBox chartEditor = new VBox(
                6,
                new Label("Chart of Accounts assignment"),
                chartAssignment,
                makeActiveChart,
                chartStatus,
                chartHelp);
        chartEditor.setPadding(new Insets(8));

        GridPane reportingGrid = new GridPane();
        reportingGrid.setHgap(10);
        reportingGrid.setVgap(8);
        reportingGrid.addRow(0, new Label("Default opening report"), defaultReport);
        reportingGrid.addRow(1, new Label("Default export format"), defaultReportExportFormat);
        GridPane.setHgrow(defaultReport, Priority.ALWAYS);
        GridPane.setHgrow(defaultReportExportFormat, Priority.ALWAYS);
        Label reportingHelp = new Label(
                "These defaults apply only when a new Report Library is opened. Report dates, fund, row limit, account, asset, and inventory filters remain controlled by the active accounting period or the operator and are not stored as company reporting policy.");
        reportingHelp.setWrapText(true);
        reportingDefaultsStatus.setWrapText(true);
        VBox reportingEditor = new VBox(
                6,
                new Label("Reporting defaults"),
                reportingGrid,
                reportingDefaultsStatus,
                reportingHelp);
        reportingEditor.setPadding(new Insets(8));

        Label deferrals = new Label("EIN is stored as informational company metadata only; this application does not provide a tax-filing workflow. Bank accounts are maintained in the Banking workspace.");
        deferrals.setWrapText(true);
        VBox editor = new VBox(
                8,
                new Label("Company profile"),
                form,
                lifecycle,
                chartEditor,
                reportingEditor,
                deferrals);
        editor.setPadding(new Insets(8));
        return editor;
    }

    private void configureReportingDefaults()
    {
        defaultReport.setId("companyDefaultReport");
        defaultReport.getItems().setAll(ReportDefinition.catalog());
        defaultReport.setMaxWidth(Double.MAX_VALUE);
        defaultReport.setConverter(new StringConverter<>()
        {
            @Override
            public String toString(ReportDefinition definition)
            {
                return definition == null ? "" : definition.displayName();
            }

            @Override
            public ReportDefinition fromString(String value)
            {
                return null;
            }
        });
        defaultReportExportFormat.setId("companyDefaultReportExportFormat");
        defaultReportExportFormat.getItems().setAll(FinancialReportExportFormat.values());
        defaultReportExportFormat.setMaxWidth(Double.MAX_VALUE);
        defaultReport.valueProperty().addListener((obs, oldValue, newValue) -> reportingDefaultsChanged());
        defaultReportExportFormat.valueProperty().addListener(
                (obs, oldValue, newValue) -> reportingDefaultsChanged());
    }

    private void configureCompaniesTable()
    {
        companies.setId("companyAdminTable");
        companies.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        CompanyTableStateBinder.markCompanyStateOwned(companies);
        companies.setPlaceholder(new Label("No company rows found in the active database."));
        addColumn("code", "Code", CompanyView::code, 125);
        addColumn("name", "Display Name", CompanyView::displayName, 210);
        addColumn("active", "Active", row -> row.active() ? "Yes" : "No", 90);
        addColumn("fiscal", "Fiscal Start", row -> row.fiscalYearStartMonth() + "/" + row.fiscalYearStartDay(), 115);
        addColumn("currency", "Currency", CompanyView::defaultCurrency, 100);
        addColumn("branch", "Branch Type", CompanyView::branchType, 135);
        addColumn("parent", "Parent Organization", CompanyView::parentOrganization, 180);

        companies.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) ->
        {
            if (suppressSelection || newRow == null)
            {
                return;
            }
            if (dirty && editingCompanyId != null && !Objects.equals(editingCompanyId, newRow.id()) && !confirmDiscard())
            {
                suppressSelection = true;
                companies.getSelectionModel().select(oldRow);
                suppressSelection = false;
                return;
            }
            populate(newRow);
        });
    }

    private void addColumn(
            String key,
            String title,
            java.util.function.Function<CompanyView, String> extractor,
            double preferredWidth)
    {
        TableColumn<CompanyView, String> column = new TableColumn<>(title);
        column.setUserData(key);
        column.setCellValueFactory(data -> new SimpleStringProperty(nullToBlank(extractor.apply(data.getValue()))));
        column.setMinWidth(70);
        column.setPrefWidth(preferredWidth);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        columnsByKey.put(key, column);
        companies.getColumns().add(column);
    }

    private void installDirtyListeners()
    {
        code.textProperty().addListener((obs, oldValue, newValue) -> markDirty());
        displayName.textProperty().addListener((obs, oldValue, newValue) -> markDirty());
        legalName.textProperty().addListener((obs, oldValue, newValue) -> markDirty());
        branchType.textProperty().addListener((obs, oldValue, newValue) -> markDirty());
        parentOrganization.textProperty().addListener((obs, oldValue, newValue) -> markDirty());
        ein.textProperty().addListener((obs, oldValue, newValue) -> markDirty());
        fiscalMonth.valueProperty().addListener((obs, oldValue, newValue) -> markDirty());
        fiscalDay.valueProperty().addListener((obs, oldValue, newValue) -> markDirty());
        defaultCurrency.textProperty().addListener((obs, oldValue, newValue) -> markDirty());
        active.selectedProperty().addListener((obs, oldValue, newValue) -> markDirty());
    }

    private void markDirty()
    {
        if (!populating)
        {
            dirty = true;
            selectActive.setDisable(true);
            makeActiveChart.setDisable(true);
            defaultReport.setDisable(true);
            defaultReportExportFormat.setDisable(true);
            reportingDefaultsStatus.setText(
                    "Save or discard company profile edits before changing reporting defaults.");
        }
    }

    private void populate(CompanyView company)
    {
        populating = true;
        try
        {
            editingCompanyId = company.id();
            code.setText(company.code());
            displayName.setText(company.displayName());
            legalName.setText(nullToBlank(company.legalName()));
            branchType.setText(nullToBlank(company.branchType()));
            parentOrganization.setText(nullToBlank(company.parentOrganization()));
            ein.setText(nullToBlank(company.ein()));
            fiscalMonth.setValue(company.fiscalYearStartMonth());
            fiscalDay.getValueFactory().setValue(company.fiscalYearStartDay());
            defaultCurrency.setText(company.defaultCurrency());
            active.setSelected(company.active());
            editMode.setText("Editing company ID " + company.id());
            dirty = false;
            selectActive.setDisable(!company.active() || company.code().equalsIgnoreCase(currentCompanyCode()));
            status.setText(company.code().equalsIgnoreCase(currentCompanyCode())
                    ? "Editing the current workspace company."
                    : "Editing company " + company.code() + ".");
            loadCompanyCharts(company);
            loadCompanyReportingDefaults(company);
        }
        finally
        {
            populating = false;
        }
    }

    private void clearForm(boolean announce)
    {
        populating = true;
        try
        {
            editingCompanyId = null;
            suppressSelection = true;
            companies.getSelectionModel().clearSelection();
            suppressSelection = false;
            code.clear();
            displayName.clear();
            legalName.clear();
            branchType.clear();
            parentOrganization.clear();
            ein.clear();
            fiscalMonth.setValue(1);
            fiscalDay.getValueFactory().setValue(1);
            defaultCurrency.setText("USD");
            active.setSelected(true);
            editMode.setText("New company");
            selectActive.setDisable(true);
            chartAssignment.getItems().clear();
            chartAssignment.getSelectionModel().clearSelection();
            makeActiveChart.setDisable(true);
            chartStatus.setText("Save the company before assigning a Chart of Accounts.");
            CompanyReportingDefaults defaults = CompanyReportingDefaults.defaults();
            defaultReport.getSelectionModel().select(defaults.defaultReport());
            defaultReportExportFormat.getSelectionModel().select(defaults.defaultExportFormat());
            defaultReport.setDisable(true);
            defaultReportExportFormat.setDisable(true);
            reportingDefaultsStatus.setText("Save the company before choosing Report Library defaults.");
            dirty = false;
            if (announce)
            {
                status.setText("New company: enter the persisted profile fields and choose Save.");
            }
        }
        finally
        {
            populating = false;
        }
    }

    private void saveCompany()
    {
        try
        {
            CompanyView saved = companyController.save(new CompanyCommand(
                    editingCompanyId,
                    code.getText(),
                    displayName.getText(),
                    legalName.getText(),
                    branchType.getText(),
                    parentOrganization.getText(),
                    ein.getText(),
                    active.isSelected(),
                    fiscalMonth.getValue() == null ? 1 : fiscalMonth.getValue(),
                    fiscalDay.getValue(),
                    defaultCurrency.getText()));
            editingCompanyId = saved.id();
            dirty = false;
            status.setText("Saved company " + saved.code() + " in H2.");
            reload(saved.id());
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save company: " + UiErrors.safeMessage(ex));
        }
    }

    private void selectActiveCompany()
    {
        CompanyView selected = companies.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select an existing active company first.");
            return;
        }
        CompanySessionController.SelectionResult result = companyController.select(selected.code());
        status.setText(result.message());
        if (result.selected())
        {
            activeCompany.setText("Workspace company: " + result.company().code());
        }
    }

    private void loadCompanyCharts(CompanyView company)
    {
        try
        {
            List<CompanyChartView> charts = companyController.listCompanyCharts(company.id());
            chartAssignment.setItems(FXCollections.observableArrayList(charts));
            CompanyChartView current = charts.stream()
                    .filter(CompanyChartView::activeForCompany)
                    .findFirst()
                    .orElse(null);
            chartAssignment.getSelectionModel().select(current);
            chartStatus.setText(current == null
                    ? "No active Chart of Accounts is selected for " + company.code()
                            + ". Create/import a company-owned DRAFT chart, then select it here."
                    : "Current chart: " + current.name() + " — " + current.version() + ".");
            updateChartActionState();
        }
        catch (RuntimeException ex)
        {
            chartAssignment.getItems().clear();
            chartAssignment.getSelectionModel().clearSelection();
            makeActiveChart.setDisable(true);
            chartStatus.setText("Could not load company charts: " + UiErrors.safeMessage(ex));
        }
    }

    private void loadCompanyReportingDefaults(CompanyView company)
    {
        try
        {
            CompanyReportingDefaults defaults = preferencesService.loadReportingDefaults(company.code());
            defaultReport.getSelectionModel().select(defaults.defaultReport());
            defaultReportExportFormat.getSelectionModel().select(defaults.defaultExportFormat());
            defaultReport.setDisable(false);
            defaultReportExportFormat.setDisable(false);
            reportingDefaultsStatus.setText(
                    "New Report Library windows for " + company.code()
                            + " open with " + defaults.defaultReport().displayName()
                            + " and " + defaults.defaultExportFormat().label() + ".");
        }
        catch (RuntimeException ex)
        {
            CompanyReportingDefaults defaults = CompanyReportingDefaults.defaults();
            defaultReport.getSelectionModel().select(defaults.defaultReport());
            defaultReportExportFormat.getSelectionModel().select(defaults.defaultExportFormat());
            defaultReport.setDisable(true);
            defaultReportExportFormat.setDisable(true);
            reportingDefaultsStatus.setText(
                    "Could not load reporting defaults: " + UiErrors.safeMessage(ex));
        }
    }

    private void reportingDefaultsChanged()
    {
        if (populating || editingCompanyId == null)
        {
            return;
        }
        if (dirty)
        {
            reportingDefaultsStatus.setText(
                    "Save or discard company profile edits before changing reporting defaults.");
            return;
        }
        CompanyView company = companies.getSelectionModel().getSelectedItem();
        if (company == null || !Objects.equals(company.id(), editingCompanyId)
                || defaultReport.getValue() == null
                || defaultReportExportFormat.getValue() == null)
        {
            return;
        }
        try
        {
            CompanyReportingDefaults defaults = new CompanyReportingDefaults(
                    defaultReport.getValue(),
                    defaultReportExportFormat.getValue());
            preferencesService.saveReportingDefaults(company.code(), defaults);
            reportingDefaultsStatus.setText(
                    "Saved reporting defaults for " + company.code()
                            + ". They apply the next time Report Library is opened.");
        }
        catch (RuntimeException ex)
        {
            reportingDefaultsStatus.setText(
                    "Could not save reporting defaults: " + UiErrors.safeMessage(ex));
        }
    }

    private void updateChartActionState()
    {
        CompanyChartView selected = chartAssignment.getValue();
        makeActiveChart.setDisable(
                dirty
                        || editingCompanyId == null
                        || selected == null
                        || selected.activeForCompany()
                        || selected.status() == ChartStatus.RETIRED);
    }

    private void assignActiveChart()
    {
        CompanyView company = companies.getSelectionModel().getSelectedItem();
        CompanyChartView selected = chartAssignment.getValue();
        if (dirty)
        {
            status.setText("Save or discard company profile edits before changing its active chart.");
            return;
        }
        if (company == null || editingCompanyId == null || selected == null)
        {
            status.setText("Select a persisted company and one of its eligible charts first.");
            return;
        }
        if (!confirmChartAssignment(company, selected))
        {
            status.setText("Chart assignment cancelled; no data changed.");
            return;
        }
        try
        {
            CompanyChartView assigned = companyController.assignActiveChart(company.id(), selected.id());
            loadCompanyCharts(company);
            status.setText("Selected active Chart of Accounts " + assigned.name() + " — "
                    + assigned.version() + " for " + company.code()
                    + ". Existing charts, accounts, transactions, and history were not moved or deleted.");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not change active Chart of Accounts: " + UiErrors.safeMessage(ex));
        }
    }

    private boolean confirmChartAssignment(CompanyView company, CompanyChartView chart)
    {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Change active Chart of Accounts");
        confirmation.setHeaderText("Make " + chart.name() + " — " + chart.version()
                + " the active chart for " + company.code() + "?");
        confirmation.setContentText(
                "This changes which chart new account maintenance and chart-targeted imports use. "
                        + "Existing charts, accounts, transactions, and historical references are not moved, deleted, or auto-retired.");
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void reload(Long reselectId)
    {
        try
        {
            List<CompanyView> rows = companyController.listCompanies();
            companies.setItems(FXCollections.observableArrayList(rows));
            activeCompany.setText("Workspace company: " + currentCompanyCode());
            CompanyView selected = rows.stream()
                    .filter(row -> Objects.equals(row.id(), reselectId))
                    .findFirst()
                    .orElseGet(() -> rows.stream()
                            .filter(row -> row.code().equalsIgnoreCase(currentCompanyCode()))
                            .findFirst()
                            .orElse(null));
            if (selected != null)
            {
                companies.getSelectionModel().select(selected);
            }
            status.setText("Loaded " + rows.size() + " authoritative company row(s).");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load companies: " + UiErrors.safeMessage(ex));
        }
    }

    private boolean confirmDiscard()
    {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Discard company edits");
        confirmation.setHeaderText("Discard unsaved Company changes?");
        confirmation.setContentText("Choose Cancel to remain in the current editor.");
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void restoreLayoutState()
    {
        restoringState = true;
        try
        {
            Map<String, String> state = preferencesService.loadState(layoutOwnerCompany, STATE_PREFIX);
            split.setDividerPositions(clamp(parseDouble(state.get(STATE_PREFIX + "divider"), 0.56)));
            for (Map.Entry<String, TableColumn<CompanyView, String>> entry : columnsByKey.entrySet())
            {
                double width = parseDouble(
                        state.get(STATE_PREFIX + "table.width." + entry.getKey()),
                        entry.getValue().getPrefWidth());
                entry.getValue().setPrefWidth(Math.max(entry.getValue().getMinWidth(), width));
            }
            restoreColumnOrder(state.get(STATE_PREFIX + "table.order"));
            restoreSortOrder(state.get(STATE_PREFIX + "table.sort"));
        }
        finally
        {
            restoringState = false;
        }
    }

    private void restoreColumnOrder(String value)
    {
        if (value == null || value.isBlank())
        {
            return;
        }
        List<TableColumn<CompanyView, ?>> ordered = new ArrayList<>();
        for (String key : value.split(","))
        {
            TableColumn<CompanyView, String> column = columnsByKey.get(key);
            if (column != null && !ordered.contains(column))
            {
                ordered.add(column);
            }
        }
        columnsByKey.values().stream().filter(column -> !ordered.contains(column)).forEach(ordered::add);
        companies.getColumns().setAll(ordered);
    }

    private void restoreSortOrder(String value)
    {
        if (value == null || value.isBlank())
        {
            return;
        }
        for (String part : value.split(","))
        {
            String[] pieces = part.split(":", 2);
            TableColumn<CompanyView, String> column = columnsByKey.get(pieces[0]);
            if (column != null)
            {
                column.setSortType(pieces.length > 1 && "DESC".equals(pieces[1])
                        ? TableColumn.SortType.DESCENDING
                        : TableColumn.SortType.ASCENDING);
                companies.getSortOrder().add(column);
            }
        }
    }

    private void installLayoutStateListeners()
    {
        stateSaveDelay.setOnFinished(event -> saveLayoutState());
        split.getDividers().get(0).positionProperty().addListener((obs, oldValue, newValue) -> queueLayoutSave());
        companies.getColumns().addListener((ListChangeListener<TableColumn<CompanyView, ?>>) change -> queueLayoutSave());
        companies.getSortOrder().addListener((ListChangeListener<TableColumn<CompanyView, ?>>) change -> queueLayoutSave());
        columnsByKey.values().forEach(column ->
        {
            column.widthProperty().addListener((obs, oldValue, newValue) -> queueLayoutSave());
            column.sortTypeProperty().addListener((obs, oldValue, newValue) -> queueLayoutSave());
        });
    }

    private void queueLayoutSave()
    {
        if (!restoringState)
        {
            stateSaveDelay.playFromStart();
        }
    }

    private void saveLayoutState()
    {
        Map<String, String> state = new LinkedHashMap<>();
        state.put(STATE_PREFIX + "divider", Double.toString(split.getDividers().get(0).getPosition()));
        state.put(STATE_PREFIX + "table.order", companies.getColumns().stream()
                .map(column -> String.valueOf(column.getUserData()))
                .reduce((left, right) -> left + "," + right)
                .orElse(""));
        columnsByKey.forEach((key, column) -> state.put(
                STATE_PREFIX + "table.width." + key,
                Double.toString(column.getWidth())));
        state.put(STATE_PREFIX + "table.sort", companies.getSortOrder().stream()
                .map(column -> String.valueOf(column.getUserData()) + ":"
                        + (column.getSortType() == TableColumn.SortType.DESCENDING ? "DESC" : "ASC"))
                .reduce((left, right) -> left + "," + right)
                .orElse(""));
        preferencesService.saveState(layoutOwnerCompany, state);
    }

    private static double parseDouble(String value, double fallback)
    {
        try
        {
            return value == null || value.isBlank() ? fallback : Double.parseDouble(value);
        }
        catch (NumberFormatException ex)
        {
            return fallback;
        }
    }

    private static double clamp(double value)
    {
        return Math.max(0.20, Math.min(0.80, value));
    }

    private static String currentCompanyCode()
    {
        return MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
    }

    private static String nullToBlank(String value)
    {
        return value == null ? "" : value;
    }

    @Override
    public String title()
    {
        return "Company Admin";
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public java.util.Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return switch (command)
        {
            case NEW_ACTIVE, SAVE_ACTIVE -> java.util.Optional.of(ApplicationPermission.COMPANY_ADMIN);
            default -> java.util.Optional.empty();
        };
    }

    @Override
    public java.util.Set<AppCommand> commandCapabilities()
    {
        return AppPanel.capabilities(AppCommand.NEW_ACTIVE, AppCommand.SAVE_ACTIVE);
    }

    @Override
    public void onNew()
    {
        if (!dirty || confirmDiscard())
        {
            clearForm(true);
        }
        else
        {
            status.setText("New company cancelled; unsaved changes remain.");
        }
    }

    @Override
    public void onSave()
    {
        saveCompany();
    }

    @Override
    public String commandResultMessage(AppCommand command)
    {
        return status.getText();
    }

    @Override
    public void onPanelShown()
    {
        if (!dirty)
        {
            reload(editingCompanyId);
        }
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return dirty;
    }
}
