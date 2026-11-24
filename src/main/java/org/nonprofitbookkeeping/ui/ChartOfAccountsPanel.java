package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.ApplicationPermission;
import javafx.beans.property.SimpleStringProperty;
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
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.service.AccountCommand;
import org.nonprofitbookkeeping.service.CompanyChartView;
import org.nonprofitbookkeeping.model.ChartStatus;
import java.util.function.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the ChartOfAccountsPanel component in the nonprofit bookkeeping application.
 */
public class ChartOfAccountsPanel implements AppPanel
{
    private static final FunctionChoice NO_FUNCTION = new FunctionChoice("None", null);

    private final BorderPane root = new BorderPane();
    private final TableView<Account> table = new TableView<>();
    private final Label status = new Label();
    private final TextField codeField = new TextField();
    private final TextField nameField = new TextField();
    private final ComboBox<AccountType> typeField = new ComboBox<>();
    private final ComboBox<FunctionChoice> functionField = new ComboBox<>();
    private final ComboBox<NormalBalance> balanceField = new ComboBox<>();
    private final ComboBox<AccountSubtype> subtypeField = new ComboBox<>();
    private final TextField parentCodeField = new TextField();
    private final CheckBox activeField = new CheckBox("Active");
    private Button refresh;
    private final Button add = new Button("+ Add");
    private final Button save = new Button("Save");
    private final MaintenanceReturn returnAction = new MaintenanceReturn(AppPanelId.CHART_OF_ACCOUNTS, "chartOfAccountsReturn");
    private final MaintenanceListFeedback listFeedback = new MaintenanceListFeedback("chartOfAccounts", "posting accounts", "the selected chart", "+ Add");
    private long loadGeneration;

    private final ComboBox<CompanyChartView> chartSelection = new ComboBox<>();
    private final Button makeActiveChart = new Button("Make Active Chart");
    private final Label chartStatus = new Label();
    private final ChartActions chartActions;
    private final Predicate<CompanyChartView> confirmActivation;
    private List<Account> companyAccounts = List.of();
    private Long companyId;
    private boolean changingCharts;
    private boolean loadingCharts = true;
    private Node accountEditor;
    private Runnable capabilitiesChanged = () -> {};
    private final javafx.beans.value.ChangeListener<Boolean> permissionChanged =
            (observable, prior, denied) -> updateChartState();
    private java.util.function.BooleanSupplier discardConfirmation;
    private final FormDirtyTracker dirtyState;
    private Long editingAccountId;
    private boolean suppressSelection;
    private String pendingDrillContext = "";

    public ChartOfAccountsPanel()
    {
        this(new ChartActions()
        {
            @Override public ChartData load()
            {
                String code = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
                var company = UiServiceRegistry.companyAdmin().requireActiveCompany(code);
                return new ChartData(company.id(), UiServiceRegistry.companyAdmin().listCompanyCharts(company.id()),
                        UiServiceRegistry.accountLookup().listPostingAccountsIncludingInactive());
            }

            @Override public void activate(long companyId, long chartId)
            {
                UiServiceRegistry.companyAdmin().assignActiveChart(companyId, chartId);
            }
        }, ChartOfAccountsPanel::confirmChartActivation);
    }

    ChartOfAccountsPanel(ChartActions chartActions, Predicate<CompanyChartView> confirmActivation)
    {
        this.chartActions = Objects.requireNonNull(chartActions);
        this.confirmActivation = Objects.requireNonNull(confirmActivation);
        root.setPadding(new Insets(8));

        Label title = new Label("Chart of Accounts");
        title.getStyleClass().add("panel-title");
        Label lifecycle = new Label("Accounts cannot be deleted; clear Active and Save to retire them.");
        lifecycle.setWrapText(true);

        add.setOnAction(e -> onNew());

        save.setOnAction(e -> saveForm());
        UiPermissionGate.gate(add, ApplicationPermission.BOOKKEEPING_WRITE, "Create an account");
        UiPermissionGate.gate(save, ApplicationPermission.BOOKKEEPING_WRITE, "Save an account");

        refresh = new Button("Refresh");
        refresh.setId("chartOfAccountsRefresh");
        refresh.setOnAction(e -> reload(editingAccountId));
        Button showAll = new Button("Show All");
        showAll.setId("chartOfAccountsShowAll");
        showAll.setTooltip(new javafx.scene.control.Tooltip("Reload active and inactive accounts in the viewed chart without discarding editor changes."));
        showAll.setOnAction(e -> reload(editingAccountId));

        FlowPane actions = new FlowPane(8, 6, add, save, refresh, showAll, returnAction.button());
        chartSelection.setId("chartOfAccountsSelection");
        chartSelection.setPromptText("Select a Chart of Accounts");
        chartSelection.setMaxWidth(Double.MAX_VALUE);
        chartSelection.setMinWidth(0);
        chartSelection.valueProperty().addListener((obs, prior, selected) -> selectChart(prior, selected));
        makeActiveChart.setId("makeActiveChart");
        makeActiveChart.setOnAction(event -> activateChart());
        UiPermissionGate.gate(makeActiveChart, ApplicationPermission.COMPANY_ADMIN, "Change the active Chart of Accounts");
        UiPermissionGate.deniedProperty(ApplicationPermission.COMPANY_ADMIN).addListener(
                new javafx.beans.value.WeakChangeListener<>(permissionChanged));
        chartStatus.setWrapText(true);
        status.setWrapText(true);
        VBox chartActionsBar = new VBox(6, new Label("View chart"), chartSelection,
                new FlowPane(8, 6, makeActiveChart));
        Node editorForm = buildEditorForm();
        accountEditor = editorForm;
        VBox header = new VBox(6, title, actions, chartActionsBar, chartStatus, lifecycle, listFeedback.summary(), status, new Separator());

        root.setTop(header);
        dirtyState = new FormDirtyTracker(this::formSnapshot);

        TableColumn<Account, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getCode()));

        TableColumn<Account, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getName()));

        TableColumn<Account, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getAccountType())));

        TableColumn<Account, String> function = new TableColumn<>("Function");
        function.setCellValueFactory(v -> new SimpleStringProperty(
                v.getValue().getAccountFunction() == null ? "" : v.getValue().getAccountFunction().name()));

        TableColumn<Account, String> normalBalance = new TableColumn<>("Normal");
        normalBalance.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getNormalBalance())));

        TableColumn<Account, String> subtype = new TableColumn<>("Subtype");
        subtype.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getSubtype())));

        TableColumn<Account, String> parentCode = new TableColumn<>("Parent");
        parentCode.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getParent() == null ? "" : v.getValue().getParent().getCode()));

        TableColumn<Account, String> active = new TableColumn<>("Active");
        active.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().isActive() ? "Y" : "N"));

        table.getColumns().addAll(code, name, type, function, normalBalance, subtype, parentCode, active);
        table.setPlaceholder(listFeedback.placeholder());
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> {
            if (suppressSelection || newRow == null)
            {
                return;
            }
            if (dirtyState.isDirty() && !confirmDiscard())
            {
                suppressSelection = true;
                table.getSelectionModel().select(oldRow);
                suppressSelection = false;
                return;
            }
            loadRowIntoForm(newRow);
        });

        VBox tableRegion = new VBox(6, new Label("Posting accounts"), table);
        tableRegion.setMinHeight(0.0);
        VBox.setVgrow(table, Priority.ALWAYS);
        ScrollPane editorScroll = new ScrollPane(editorForm);
        editorScroll.setId("chartOfAccountsEditorScroll");
        editorScroll.setFitToWidth(true);
        editorScroll.setMinHeight(0.0);
        editorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editorScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        SplitPane split = new SplitPane(tableRegion, editorScroll);
        split.setId("chartOfAccountsWorkspaceSplit");
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.62);
        CompanySplitPaneStateBinder.bind(split, "chart-of-accounts-workspace", 0.62);
        root.setCenter(split);

        clearFormForNew();
        updateChartState();
        reload(null);
    }

    @Override public String title() { return "Chart of Accounts"; }
    @Override public Node root() { return root; }

    @Override
    public java.util.Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return switch (command)
        {
            case NEW_ACTIVE, SAVE_ACTIVE -> java.util.Optional.of(ApplicationPermission.BOOKKEEPING_WRITE);
            default -> java.util.Optional.empty();
        };
    }

    @Override
    public java.util.Set<AppCommand> commandCapabilities()
    {
        return canEditChart() ? AppPanel.capabilities(AppCommand.NEW_ACTIVE, AppCommand.SAVE_ACTIVE) : java.util.Set.of();
    }

    @Override
    public void onNew()
    {
        if (!canEditChart())
        {
            status.setText("View the active chart before creating accounts.");
            return;
        }
        if (!dirtyState.isDirty() || confirmDiscard())
        {
            clearFormForNew();
            status.setText("Create mode: enter account details and click Save.");
        }
        else
        {
            status.setText("New account cancelled; unsaved changes remain.");
        }
    }

    @Override
    public String commandResultMessage(AppCommand command)
    {
        return status.getText();
    }

    private Node buildEditorForm()
    {
        codeField.setId("chartOfAccountsCode");
        nameField.setId("chartOfAccountsName");
        typeField.setId("chartOfAccountsType");
        balanceField.setId("chartOfAccountsBalance");
        activeField.setId("chartOfAccountsActive");
        typeField.getItems().setAll(AccountType.values());
        functionField.getItems().setAll(functionChoices());
        balanceField.getItems().setAll(NormalBalance.values());
        subtypeField.getItems().setAll(AccountSubtype.values());
        activeField.setSelected(true);

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);

        int row = 0;
        form.add(new Label("Code"), 0, row);
        form.add(codeField, 1, row);
        form.add(new Label("Name"), 2, row);
        form.add(nameField, 3, row);
        row++;
        form.add(new Label("Type"), 0, row);
        form.add(typeField, 1, row);
        form.add(new Label("Function"), 2, row);
        form.add(functionField, 3, row);
        row++;
        form.add(new Label("Normal"), 0, row);
        form.add(balanceField, 1, row);
        form.add(new Label("Subtype"), 2, row);
        form.add(subtypeField, 3, row);
        row++;
        form.add(new Label("Parent code"), 0, row);
        form.add(parentCodeField, 1, row);
        row++;
        form.add(activeField, 0, row, 2, 1);
        for (Node field : java.util.List.of(
                codeField, nameField, typeField, functionField, balanceField, subtypeField, parentCodeField))
        {
            GridPane.setHgrow(field, Priority.ALWAYS);
        }

        return form;
    }

    private void loadRowIntoForm(Account row)
    {
        if (row == null)
        {
            return;
        }
        editingAccountId = row.getId();
        codeField.setText(row.getCode());
        nameField.setText(row.getName());
        typeField.setValue(row.getAccountType());
        functionField.setValue(functionChoice(row.getAccountFunction()));
        balanceField.setValue(row.getNormalBalance());
        activeField.setSelected(row.isActive());
        subtypeField.setValue(row.getSubtype());
        parentCodeField.setText(row.getParent() == null ? "" : row.getParent().getCode());
        status.setText("Edit mode for account ID " + row.getId() + " (" + row.getCode() + ").");
        dirtyState.markClean();
    }

    private void clearFormForNew()
    {
        editingAccountId = null;
        table.getSelectionModel().clearSelection();
        codeField.clear();
        nameField.clear();
        typeField.getSelectionModel().clearSelection();
        functionField.setValue(NO_FUNCTION);
        balanceField.getSelectionModel().clearSelection();
        subtypeField.getSelectionModel().clearSelection();
        parentCodeField.clear();
        activeField.setSelected(true);
        dirtyState.markClean();
    }

    private void saveForm()
    {
        if (!canEditChart())
        {
            status.setText("View the active chart before saving accounts.");
            return;
        }
        try
        {
            AccountCommand command = new AccountCommand(
                    editingAccountId,
                    codeField.getText(),
                    nameField.getText(),
                    typeField.getValue(),
                    selectedFunction(),
                    balanceField.getValue(),
                    subtypeField.getValue(),
                    parentCodeField.getText(),
                    activeField.isSelected());
            Account saved = UiServiceRegistry.accountAdmin().save(command);
            editingAccountId = saved.getId();
            status.setText("Saved account " + saved.getCode() + " (ID " + saved.getId() + ").");
            dirtyState.markClean();
            reload(saved.getId());
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save account: " + UiErrors.safeMessage(ex));
        }
    }

    FormState readFormStateForTests()
    {
        return new FormState(
                editingAccountId,
                codeField.getText(),
                nameField.getText(),
                typeField.getValue(),
                selectedFunction(),
                balanceField.getValue(),
                subtypeField.getValue(),
                parentCodeField.getText(),
                activeField.isSelected());
    }

    void setFormStateForTests(FormState formState)
    {
        editingAccountId = formState.accountId();
        codeField.setText(formState.code());
        nameField.setText(formState.name());
        typeField.setValue(formState.accountType());
        functionField.setValue(functionChoice(formState.accountFunction()));
        balanceField.setValue(formState.normalBalance());
        subtypeField.setValue(formState.subtype());
        parentCodeField.setText(formState.parentCode());
        activeField.setSelected(formState.active());
    }

    String functionDisplayForTests()
    {
        FunctionChoice selected = functionField.getValue();
        return selected == null ? "" : selected.toString();
    }

    void clearFunctionForTests()
    {
        functionField.setValue(NO_FUNCTION);
    }

    record FormState(Long accountId,
                     String code,
                     String name,
                     AccountType accountType,
                     AccountFunction accountFunction,
                     NormalBalance normalBalance,
                     AccountSubtype subtype,
                     String parentCode,
                     boolean active)
    {
    }

    private static List<FunctionChoice> functionChoices()
    {
        List<FunctionChoice> choices = new ArrayList<>();
        choices.add(NO_FUNCTION);
        for (AccountFunction function : AccountFunction.values())
        {
            choices.add(new FunctionChoice(function.name(), function));
        }
        return List.copyOf(choices);
    }

    private static FunctionChoice functionChoice(AccountFunction function)
    {
        if (function == null)
        {
            return NO_FUNCTION;
        }
        return new FunctionChoice(function.name(), function);
    }

    private AccountFunction selectedFunction()
    {
        FunctionChoice selected = functionField.getValue();
        return selected == null ? null : selected.function();
    }

    private record FunctionChoice(String label, AccountFunction function)
    {
        private FunctionChoice
        {
            Objects.requireNonNull(label, "label");
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    private String formatStatus(String message)
    {
        if (pendingDrillContext == null || pendingDrillContext.isBlank())
        {
            return message;
        }
        String combined = message + " | " + pendingDrillContext;
        pendingDrillContext = "";
        return combined;
    }

    private void reload(Long reselectId)
    {
        long generation = ++loadGeneration;
        FormState requestedForm = formSnapshot();
        listFeedback.loading();
        Long selectedId = chartSelection.getValue() == null ? null : chartSelection.getValue().id();
        loadingCharts = true;
        updateChartState();
        String incomingContext = DrillThroughCoordinator.consumeContext(AppPanelId.CHART_OF_ACCOUNTS);
        if (!incomingContext.isBlank())
        {
            pendingDrillContext = incomingContext;
        }
        status.setText(formatStatus("Loading accounts..."));
        UiAsync.run("coa-load", chartActions::load, data ->
        {
            if (generation != loadGeneration)
            {
                return;
            }
            boolean keepDraft = dirtyState.isDirty() || !Objects.equals(requestedForm, formSnapshot());
            companyId = data.companyId();
            companyAccounts = List.copyOf(data.accounts());
            changingCharts = true;
            chartSelection.getItems().setAll(data.charts());
            CompanyChartView selected = data.charts().stream().filter(chart -> Objects.equals(chart.id(), selectedId))
                    .findFirst().orElseGet(() -> data.charts().stream().filter(CompanyChartView::activeForCompany)
                            .findFirst().orElse(null));
            chartSelection.setValue(selected);
            changingCharts = false;
            loadingCharts = false;
            showChartAccounts();
            if (reselectId != null)
            {
                suppressSelection = true;
                try
                {
                    table.getItems().stream().filter(row -> Objects.equals(row.getId(), reselectId)).findFirst().ifPresent(row ->
                    {
                        table.getSelectionModel().select(row);
                        if (!keepDraft)
                        {
                            loadRowIntoForm(row);
                        }
                    });
                }
                finally
                {
                    suppressSelection = false;
                }
            }
        }, ex ->
        {
            if (generation != loadGeneration)
            {
                return;
            }
            loadingCharts = false;
            companyId = null;
            companyAccounts = List.of();
            changingCharts = true;
            chartSelection.getItems().clear();
            chartSelection.setValue(null);
            changingCharts = false;
            table.getItems().clear();
            updateChartState();
            listFeedback.failed();
            status.setText(formatStatus("Failed to load accounts: " + UiErrors.safeMessage(ex)));
        });
    }

    private boolean canEditChart()
    {
        return !loadingCharts && chartSelection.getValue() != null && chartSelection.getValue().activeForCompany();
    }

    private void updateChartState()
    {
        CompanyChartView selected = chartSelection.getValue();
        chartSelection.setDisable(loadingCharts);
        accountEditor.setDisable(!canEditChart());
        refresh.setDisable(loadingCharts);
        add.setDisable(!canEditChart());
        save.setDisable(!canEditChart());
        makeActiveChart.setDisable(loadingCharts || companyId == null || selected == null
                || selected.activeForCompany() || selected.status() == ChartStatus.RETIRED);
        String state = selected == null ? "No chart selected."
                : selected.activeForCompany() ? "Viewing the active chart."
                : "Viewing another chart (read-only). Making it active does not move existing history.";
        if (!UiPermissionGate.allows(ApplicationPermission.COMPANY_ADMIN))
        {
            state += " Making a chart active requires a MANAGER or ADMIN role for this company.";
        }
        else if (selected != null && selected.status() == ChartStatus.RETIRED)
        {
            state += " Retired charts cannot be made active.";
        }
        CompanyChartView current = chartSelection.getItems().stream().filter(CompanyChartView::activeForCompany)
                .findFirst().orElse(null);
        chartStatus.setText((current == null ? "No active chart. "
                : "Active chart: " + current.name() + " — " + current.version() + ". ") + state);
        capabilitiesChanged.run();
    }

    private void selectChart(CompanyChartView prior, CompanyChartView selected)
    {
        if (changingCharts)
        {
            return;
        }
        if (dirtyState.isDirty() && !confirmDiscard())
        {
            changingCharts = true;
            chartSelection.setValue(prior);
            changingCharts = false;
            return;
        }
        clearFormForNew();
        showChartAccounts();
    }

    private void showChartAccounts()
    {
        CompanyChartView selected = chartSelection.getValue();
        boolean priorSuppression = suppressSelection;
        suppressSelection = true;
        try
        {
            table.getItems().setAll(companyAccounts.stream()
                    .filter(account -> selected != null && account.getChart() != null
                            && Objects.equals(account.getChart().getId(), selected.id())).toList());
        }
        finally
        {
            suppressSelection = priorSuppression;
        }
        updateChartState();
        listFeedback.loaded(table.getItems().size(), table.getItems().size());
        status.setText(formatStatus("Loaded " + table.getItems().size() + " posting account(s) (active + inactive)."
                + (dirtyState.isDirty() ? " Unsaved editor changes retained." : "")));
    }

    private void activateChart()
    {
        CompanyChartView selected = chartSelection.getValue();
        if (loadingCharts || companyId == null || selected == null || selected.activeForCompany()
                || selected.status() == ChartStatus.RETIRED || !UiPermissionGate.allows(ApplicationPermission.COMPANY_ADMIN))
        {
            return;
        }
        if (dirtyState.isDirty())
        {
            status.setText("Save or discard account edits before changing the active chart.");
            return;
        }
        if (!confirmActivation.test(selected))
        {
            status.setText("Chart assignment cancelled; no data changed.");
            return;
        }
        long targetCompanyId = companyId;
        loadingCharts = true;
        updateChartState();
        UiAsync.run("coa-activate", () ->
        {
            chartActions.activate(targetCompanyId, selected.id());
            return selected;
        }, assigned -> reload(null), ex ->
        {
            loadingCharts = false;
            updateChartState();
            status.setText("Could not change active Chart of Accounts: " + UiErrors.safeMessage(ex));
        });
    }

    private static boolean confirmChartActivation(CompanyChartView chart)
    {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Change active Chart of Accounts");
        confirmation.setHeaderText("Make " + chart.name() + " — " + chart.version() + " the active chart?");
        confirmation.setContentText("This changes the chart used for new account maintenance and chart-targeted imports. "
                + "Existing charts, accounts, transactions, and historical references are not moved, deleted, or auto-retired.");
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    interface ChartActions
    {
        ChartData load();
        void activate(long companyId, long chartId);
    }

    record ChartData(Long companyId, List<CompanyChartView> charts, List<Account> accounts) {}

    private FormState formSnapshot()
    {
        return readFormStateForTests();
    }

    private boolean confirmDiscard()
    {
        if (discardConfirmation != null)
        {
            return discardConfirmation.getAsBoolean();
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Discard account edits");
        confirmation.setHeaderText("Discard unsaved Chart of Accounts changes?");
        confirmation.setContentText("Choose Cancel to remain in the current editor.");
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    void setDiscardConfirmationForTests(java.util.function.BooleanSupplier confirmation)
    {
        discardConfirmation = confirmation;
    }

    @Override
    public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        capabilitiesChanged = Objects.requireNonNull(listener);
    }

    @Override
    public void onPanelShown()
    {
        if (!loadingCharts)
        {
            reload(editingAccountId);
        }
    }

    @Override
    public void onPanelShownFrom(AppPanelId origin, String originTitle)
    {
        returnAction.shownFrom(origin, originTitle);
        onPanelShown();
    }

    @Override
    public String saveUnavailableReason()
    {
        return "View the company's active chart before editing accounts; other charts are read-only.";
    }

    @Override
    public void onSave()
    {
        saveForm();
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return dirtyState.isDirty();
    }
}
