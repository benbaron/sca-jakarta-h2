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
import javafx.scene.control.TextArea;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.service.AccountCommand;
import org.nonprofitbookkeeping.service.AccountDefinitionDetails;
import org.nonprofitbookkeeping.service.CompanyChartView;
import org.nonprofitbookkeeping.model.ChartStatus;
import java.util.function.Consumer;
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
    private final ImportAddPrefill importPrefill = new ImportAddPrefill("chartOfAccounts");
    private final TextField codeField = new TextField();
    private final TextField nameField = new TextField();
    private final ComboBox<AccountType> typeField = new ComboBox<>();
    private final ComboBox<FunctionChoice> functionField = new ComboBox<>();
    private final ComboBox<NormalBalance> balanceField = new ComboBox<>();
    private final ComboBox<AccountSubtype> subtypeField = new ComboBox<>();
    private final TextField parentCodeField = new TextField();
    private final CheckBox activeField = new CheckBox("Active");
    private final CheckBox postingField = new CheckBox("Posting account (clear for a group) ");
    private final DatePicker effectiveFrom = new DatePicker();
    private final DatePicker effectiveTo = new DatePicker();
    private final TextField openingBalance = new TextField();
    private final TextArea description = new TextArea();
    private final CompanyUiFormat format = CompanyUiFormat.activeCompany();
    private final Button delete = new Button("Delete Account");
    private final Label lifecycle = new Label();
    private boolean deletingAccount;
    private Predicate<org.nonprofitbookkeeping.service.AccountDeletionPreview> confirmDeletion = this::confirmAccountDeletion;
    private final Button cancel = new Button("Cancel Account Changes");
    private Button refresh;
    private final Button add = new Button("+ Add");
    private final Button save = new Button("Save");
    private final MaintenanceReturn returnAction = new MaintenanceReturn(AppPanelId.CHART_OF_ACCOUNTS, "chartOfAccountsReturn");
    private final MaintenanceListFeedback listFeedback = new MaintenanceListFeedback("chartOfAccounts", "accounts", "the selected chart", "New Account");
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
    private Consumer<RunCommandResult> resultListener = ignored -> {};
    private Runnable capabilitiesChanged = () -> {};
    private final javafx.beans.value.ChangeListener<Boolean> permissionChanged =
            (observable, prior, denied) -> updateChartState();
    private java.util.function.BooleanSupplier discardConfirmation;
    private final FormDirtyTracker dirtyState;
    private Long editingAccountId;
    private boolean suppressSelection;
    private String pendingDrillContext = "";
    private Long pendingFocusChartId;
    private CoaJsonReviewPane.Scope accountDraftScope;

    public ChartOfAccountsPanel()
    {
        this(new ChartActions()
        {
            @Override public ChartData load()
            {
                return captureLoad().get();
            }

            @Override public java.util.function.Supplier<ChartData> captureLoad()
            {
                return UiServiceRegistry.chartDataLoader();
            }

            @Override public void activate(long companyId, long chartId)
            {
                UiServiceRegistry.companyAdmin().assignActiveChart(companyId, chartId);
            }

            @Override public Runnable captureActivation(long companyId, long chartId)
            {
                var service = UiServiceRegistry.companyAdmin();
                return () -> service.assignActiveChart(companyId,chartId);
            }
        }, ChartOfAccountsPanel::confirmChartActivation);
    }

    ChartOfAccountsPanel(ChartActions chartActions, Predicate<CompanyChartView> confirmActivation)
    {
        this.chartActions = Objects.requireNonNull(chartActions);
        this.confirmActivation = Objects.requireNonNull(confirmActivation);
        root.setPadding(new Insets(8));
        root.setMinSize(0,0);

        Label title = new Label("Chart of Accounts");
        title.getStyleClass().add("panel-title");
        lifecycle.setId("chartOfAccountsLifecycle");
        lifecycle.setWrapText(true);

        add.setOnAction(e -> onNew());

        save.setOnAction(e -> onSave());
        delete.setId("chartOfAccountsDeleteAccount");
        delete.setOnAction(e -> deleteAccount());
        add.setText("New Account"); add.setId("chartOfAccountsNewAccount");
        save.setText("Save Account"); save.setId("chartOfAccountsSaveAccount");
        cancel.setId("chartOfAccountsCancelAccount");
        cancel.setOnAction(e ->
        {
            Account selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) clearFormForNew(); else loadRowIntoForm(selected);
            status.setText("Unapplied account changes cancelled.");
            capabilitiesChanged.run();
        });
        UiPermissionGate.gate(add, ApplicationPermission.BOOKKEEPING_WRITE, "Create an account");
        UiPermissionGate.gate(save, ApplicationPermission.BOOKKEEPING_WRITE, "Save an account");

        refresh = new Button("Refresh");
        refresh.setId("chartOfAccountsRefresh");
        refresh.setOnAction(e -> reload(editingAccountId));
        Button showAll = new Button("Show All");
        showAll.setId("chartOfAccountsShowAll");
        showAll.setTooltip(new javafx.scene.control.Tooltip("Reload active and inactive accounts in the viewed chart without discarding editor changes."));
        showAll.setOnAction(e -> reload(editingAccountId));

        FlowPane actions = new FlowPane(8, 6, add, save, cancel, delete, refresh, showAll, returnAction.button());
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
        UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE).addListener(
                new javafx.beans.value.WeakChangeListener<>(permissionChanged));
        chartStatus.setWrapText(true);
        status.setId("chartOfAccountsStatus");
        status.setWrapText(true);
        VBox chartActionsBar = new VBox(6, new Label("View chart"), chartSelection,
                new FlowPane(8, 6, makeActiveChart));
        Node editorForm = buildEditorForm();
        accountEditor = editorForm;
        VBox header = new VBox(6, title, actions, status, chartActionsBar, chartStatus, lifecycle, listFeedback.summary(), importPrefill.notice(), new Separator());

        header.setMinWidth(0);
        ScrollPane headerScroll = new ScrollPane(header);
        headerScroll.setId("chartOfAccountsHeaderScroll");
        headerScroll.setMinSize(0,0);
        headerScroll.setFitToWidth(true);
        headerScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        headerScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
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

        TableColumn<Account, String> posting = new TableColumn<>("Posting");
        posting.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().isPosting() ? "Yes" : "Group"));
        table.getColumns().add(posting);
        VBox tableRegion = new VBox(6, new Label("Accounts — including groups and inactive definitions"), table);
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
        split.setMinSize(0,0);
        SplitPane chrome = ResizablePanelHeader.split(headerScroll,split,"chart-of-accounts-chrome",.38);
        chrome.setId("chartOfAccountsChromeSplit");
        root.setCenter(chrome);

        clearFormForNew();
        for (TextField field : List.of(codeField, nameField, parentCodeField, openingBalance, effectiveFrom.getEditor(), effectiveTo.getEditor()))
        {
            field.textProperty().addListener((o,a,b) -> accountFieldsChanged());
        }
        description.textProperty().addListener((o,a,b) -> accountFieldsChanged());
        for (ComboBox<?> field : List.of(typeField, functionField, balanceField, subtypeField))
        {
            field.valueProperty().addListener((o,a,b) -> accountFieldsChanged());
        }
        postingField.selectedProperty().addListener((o,a,b) -> accountFieldsChanged());
        activeField.selectedProperty().addListener((o,a,b) -> accountFieldsChanged());
        var weakPanel = new java.lang.ref.WeakReference<>(this);
        Runnable scopeChanged = () ->
        {
            var panel = weakPanel.get();
            if (panel != null) panel.updateChartState();
        };
        MainWindow.sharedSessionState().onMultiCompanyChanged(ignored -> onFx(scopeChanged));
        MainWindow.sharedSessionState().onDatabaseSelectionChanged(ignored -> onFx(scopeChanged));
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
            status.setText(saveUnavailableReason());
            return;
        }
        if (!dirtyState.isDirty() || confirmDiscard())
        {
            clearFormForNew();
            importPrefill.applyIfReady(true, this::startImportDraft);
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
        functionField.setId("chartOfAccountsFunction");
        subtypeField.setId("chartOfAccountsSubtype");
        parentCodeField.setId("chartOfAccountsParent");
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
        Button clearSubtype = new Button("Clear");
        clearSubtype.setId("chartOfAccountsClearSubtype");
        clearSubtype.setOnAction(event -> subtypeField.setValue(null));
        form.add(new HBox(6,subtypeField,clearSubtype), 3, row);
        row++;
        form.add(new Label("Parent code"), 0, row);
        form.add(parentCodeField, 1, row);
        row++;
        form.add(activeField, 0, row, 2, 1);
        form.add(postingField, 2, row, 2, 1);
        row++;
        postingField.setId("chartOfAccountsPosting");
        effectiveFrom.setId("chartOfAccountsEffectiveFrom");
        effectiveTo.setId("chartOfAccountsEffectiveTo");
        openingBalance.setId("chartOfAccountsOpeningBalance");
        description.setId("chartOfAccountsDescription");
        format.install(effectiveFrom); format.install(effectiveTo);
        description.setWrapText(true); description.setPrefRowCount(3);
        form.addRow(row++, new Label("Effective from"), effectiveFrom, new Label("Effective to"), effectiveTo);
        form.addRow(row++, new Label("Opening balance field"), openingBalance);
        form.addRow(row++, new Label("Description"), description);
        GridPane.setColumnSpan(description,3);
        Label openingInfo = new Label("Opening balance is an account field; saving it creates no journal entry. History protects structural changes.");
        openingInfo.setWrapText(true); openingInfo.setMaxWidth(750);
        form.add(openingInfo,0,row,4,1);
        form.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
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
        postingField.setSelected(row.isPosting());
        effectiveFrom.setValue(row.getEffectiveFrom()); effectiveFrom.getEditor().setText(format.formatDate(row.getEffectiveFrom()));
        effectiveTo.setValue(row.getEffectiveTo()); effectiveTo.getEditor().setText(format.formatDate(row.getEffectiveTo()));
        openingBalance.setText(format.formatMoney(row.getOpeningBalance()));
        description.setText(row.getDescription() == null ? "" : row.getDescription());
        status.setText("Edit mode for account ID " + row.getId() + " (" + row.getCode() + ").");
        dirtyState.markClean();
        accountDraftScope = CoaJsonReviewPane.Scope.current();
        accountFieldsChanged();
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
        postingField.setSelected(true);
        effectiveFrom.setValue(null); effectiveFrom.getEditor().clear();
        effectiveTo.setValue(null); effectiveTo.getEditor().clear();
        openingBalance.setText(format.formatMoney(java.math.BigDecimal.ZERO));
        description.clear();
        dirtyState.markClean();
        accountDraftScope = CoaJsonReviewPane.Scope.current();
        accountFieldsChanged();
    }

    private RunCommandResult saveForm()
    {
        if (!canEditChart())
        {
            status.setText(saveUnavailableReason());
            return new RunCommandResult(false, status.getText());
        }
        try
        {
            AccountCommand command = accountCommand();
            Account saved = UiServiceRegistry.accountAdmin().save(command);
            editingAccountId = saved.getId();
            dirtyState.markClean();
            accountFieldsChanged();
            String message = "Saved account " + saved.getCode() + " (ID " + saved.getId() + ").";
            reload(saved.getId(), message);
            return new RunCommandResult(true, message);
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save account: " + UiErrors.safeMessage(ex));
            return new RunCommandResult(false, status.getText());
        }
    }

    private AccountCommand accountCommand()
    {
        return new AccountCommand(
                editingAccountId,
                codeField.getText(),
                nameField.getText(),
                typeField.getValue(),
                selectedFunction(),
                balanceField.getValue(),
                subtypeField.getValue(),
                parentCodeField.getText(),
                activeField.isSelected(), chartSelection.getValue().id(),
                new AccountDefinitionDetails(postingField.isSelected(), optionalDate(effectiveFrom), optionalDate(effectiveTo),
                        openingAmount(), description.getText()));
    }

    AccountCommand pendingAccountForChart(long chartId)
    {
        if (!dirtyState.isDirty()) return null;
        if (!canEditChart() || !Objects.equals(selectedChart().id(), chartId))
        {
            throw new IllegalStateException("Return to the chart and company that own the pending account edit.");
        }
        return accountCommand();
    }

    void acceptChartSave(long chartId, Long accountId)
    {
        if (accountId != null)
        {
            editingAccountId = accountId;
            dirtyState.markClean();
        }
        focusChart(chartId);
    }

    javafx.beans.value.ObservableValue<String> returnLabel()
    {
        return returnAction.button().textProperty();
    }

    void returnToOrigin()
    {
        returnAction.button().fire();
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
        reload(reselectId, null);
    }

    private void reload(Long reselectId, String saveConfirmation)
    {
        if (deletingAccount) return;
        long generation = ++loadGeneration;
        FullFormState requestedForm = formSnapshot();
        listFeedback.loading();
        Long selectedId = pendingFocusChartId != null ? pendingFocusChartId
                : chartSelection.getValue() == null ? null : chartSelection.getValue().id();
        loadingCharts = true;
        updateChartState();
        String incomingContext = DrillThroughCoordinator.consumeContext(AppPanelId.CHART_OF_ACCOUNTS);
        if (!incomingContext.isBlank())
        {
            pendingDrillContext = incomingContext;
        }
        status.setText(formatStatus(saveConfirmation == null ? "Loading accounts..." : saveConfirmation));
        var started = CoaJsonReviewPane.Scope.current();
        var load = chartActions.captureLoad();
        UiAsync.run("coa-load", load, data ->
        {
            if (generation != loadGeneration)
            {
                return;
            }
            boolean keepDraft = dirtyState.isDirty() || !Objects.equals(requestedForm, formSnapshot());
            if (!started.currentScope())
            {
                loadingCharts = false; updateChartState();
                status.setText("Company/database changed; account draft retained. Return to its original scope or Cancel Account Changes.");
                return;
            }
            companyId = data.companyId();
            companyAccounts = List.copyOf(data.accounts());
            changingCharts = true;
            chartSelection.getItems().setAll(data.charts());
            CompanyChartView selected = data.charts().stream().filter(chart -> Objects.equals(chart.id(), selectedId))
                    .findFirst().orElseGet(() -> data.charts().stream().filter(CompanyChartView::activeForCompany)
                            .findFirst().orElse(data.charts().isEmpty() ? null : data.charts().get(0)));
            chartSelection.setValue(selected);
            pendingFocusChartId = null;
            changingCharts = false;
            loadingCharts = false;
            showChartAccounts();
            if (!keepDraft && accountDraftScope != null && !accountDraftScope.currentScope()) clearFormForNew();
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
            if (saveConfirmation != null && !keepDraft)
            {
                status.setText(saveConfirmation);
            }
            importPrefill.applyWhenReady(!dirtyState.isDirty(), this::startImportDraft);
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
            status.setText(formatStatus((saveConfirmation == null ? "" : saveConfirmation + " ")
                    + "Failed to load accounts: " + UiErrors.safeMessage(ex)));
        });
    }

    private boolean canEditChart()
    {
        return !loadingCharts && !deletingAccount && chartSelection.getValue() != null
                && chartSelection.getValue().status() != ChartStatus.RETIRED
                && (accountDraftScope == null || accountDraftScope.currentScope())
                && UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE);
    }

    private void updateChartState()
    {
        CompanyChartView selected = chartSelection.getValue();
        chartSelection.setDisable(loadingCharts || deletingAccount);
        table.setDisable(deletingAccount);
        accountEditor.setDisable(!canEditChart());
        refresh.setDisable(loadingCharts);
        add.setDisable(!canEditChart());
        save.setDisable(!canEditChart());
        cancel.setDisable(loadingCharts || deletingAccount);
        delete.setDisable(!canDeleteAccount());
        lifecycle.setText(deletionExplanation());
        makeActiveChart.setDisable(loadingCharts || deletingAccount || companyId == null || selected == null
                || selected.activeForCompany() || selected.status() == ChartStatus.RETIRED);
        String state = selected == null ? "No chart selected."
                : selected.activeForCompany() ? "Viewing the active chart."
                : selected.status() == ChartStatus.RETIRED ? "Retired chart: accounts are read-only."
                : "Editing the viewed chart without activating it. Journal and imports still use the current chart.";
        if (!UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE)) state += " Account editing requires BOOKKEEPING_WRITE.";
        if (accountDraftScope != null && !accountDraftScope.currentScope()) state += " Retained account fields belong to the original company/database. Return there or Cancel Account Changes.";
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

    private void accountFieldsChanged()
    {
        delete.setDisable(!canDeleteAccount());
        lifecycle.setText(deletionExplanation());
        capabilitiesChanged.run();
    }

    private boolean canDeleteAccount()
    {
        CompanyChartView chart = selectedChart();
        return canEditChart() && chart.status() == ChartStatus.DRAFT && !chart.activeForCompany()
                && editingAccountId != null && !dirtyState.isDirty();
    }

    private String deletionExplanation()
    {
        if (deletingAccount) return "Checking/deleting the draft account; other commands are disabled.";
        if (selectedChart() == null || selectedChart().status() != ChartStatus.DRAFT || selectedChart().activeForCompany())
            return "Only unused accounts in a non-current DRAFT chart can be deleted. In other charts, clear Active and Save to deactivate an account.";
        if (!UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE)) return "Delete Account requires BOOKKEEPING_WRITE.";
        if (accountDraftScope != null && !accountDraftScope.currentScope()) return "Return to the original company/database before deleting.";
        if (dirtyState.isDirty()) return "Save or Cancel Account Changes before deleting a saved draft account.";
        return "Select a saved draft account, then Delete Account. Journal, banking, assets, inventory and child references prevent deletion.";
    }

    boolean deletingAccount() { return deletingAccount; }

    private void deleteAccount()
    {
        if (!canDeleteAccount()) return;
        var scope = CoaJsonReviewPane.Scope.current();
        var service = UiServiceRegistry.chartAdmin();
        long chart = selectedChart().id();
        long account = editingAccountId;
        deletingAccount = true;
        updateChartState();
        status.setText("Checking draft account references…");
        UiAsync.run("coa-account-delete-preview", () -> service.previewAccountDeletion(chart,account), preview ->
        {
            if (!scope.currentScope())
            {
                deletionFinished("Company/database changed. Return to the original scope; account fields retained.");
                return;
            }
            if (!preview.canDelete())
            {
                deletionFinished("Cannot delete account " + preview.accountCode() + ": " + String.join("; ",preview.blockers()));
                return;
            }
            if (!confirmDeletion.test(preview))
            {
                deletionFinished("Account deletion cancelled; the saved account and editor are unchanged.");
                return;
            }
            if (!scope.currentScope())
            {
                deletionFinished("Company/database changed; deletion cancelled and account fields retained.");
                return;
            }
            status.setText("Deleting draft account " + preview.accountCode() + "…");
            UiAsync.run("coa-account-delete", () ->
            {
                service.deleteAccount(chart,account,preview.revision());
                return preview.accountCode();
            }, code ->
            {
                deletingAccount = false;
                if (scope.currentScope())
                {
                    suppressSelection = true;
                    table.getSelectionModel().clearSelection();
                    suppressSelection = false;
                    clearFormForNew();
                    reload(null);
                }
                updateChartState();
                status.setText("Deleted draft account " + code + ". Other accounts and history are unchanged.");
            }, error -> deletionFinished("Could not delete account: " + UiErrors.safeMessage(error)));
        }, error -> deletionFinished("Could not inspect account deletion: " + UiErrors.safeMessage(error)));
    }

    private void deletionFinished(String message)
    {
        deletingAccount = false;
        updateChartState();
        status.setText(message);
    }

    private boolean confirmAccountDeletion(org.nonprofitbookkeeping.service.AccountDeletionPreview preview)
    {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete draft account");
        confirmation.setHeaderText("Delete " + preview.accountCode() + " — " + preview.accountName() + " from '" + preview.chartName() + "'?");
        confirmation.setContentText("This permanently removes this unused draft account and " + preview.effects()
                + ". Children and operational history are never deleted. The deletion is audited.");
        if (root.getScene() != null) confirmation.initOwner(root.getScene().getWindow());
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    void setDeleteConfirmationForTests(Predicate<org.nonprofitbookkeeping.service.AccountDeletionPreview> confirmation)
    {
        confirmDeletion = confirmation;
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
        status.setText(formatStatus("Loaded " + table.getItems().size() + " account(s), including groups and inactive definitions."
                + (dirtyState.isDirty() ? " Unsaved editor changes retained." : "")));
    }

    private void activateChart()
    {
        CompanyChartView selected = chartSelection.getValue();
        if (loadingCharts || deletingAccount || companyId == null || selected == null || selected.activeForCompany()
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
        Runnable activation = chartActions.captureActivation(targetCompanyId,selected.id());
        var started = CoaJsonReviewPane.Scope.current();
        loadingCharts = true;
        updateChartState();
        UiAsync.run("coa-activate", () ->
        {
            activation.run();
            return selected;
        }, assigned ->
        {
            if (started.currentScope()) reload(null);
            else { loadingCharts = false; updateChartState(); status.setText("Activation completed in the original scope; reload its charts when returning."); }
        }, ex ->
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
        default java.util.function.Supplier<ChartData> captureLoad() { return this::load; }
        default Runnable captureActivation(long companyId, long chartId) { return () -> activate(companyId,chartId); }
    }

    record ChartData(Long companyId, List<CompanyChartView> charts, List<Account> accounts) {}

    private FullFormState formSnapshot()
    {
        return new FullFormState(readFormStateForTests(), postingField.isSelected(), effectiveFrom.getEditor().getText(),
                effectiveTo.getEditor().getText(), openingBalance.getText(), description.getText());
    }

    private record FullFormState(FormState core, boolean posting, String from, String to, String opening, String description) {}

    private java.time.LocalDate optionalDate(DatePicker picker)
    {
        String raw = picker.getEditor().getText();
        if (raw == null || raw.isBlank()) return null;
        var parsed = format.parseDate(raw);
        if (parsed == null) throw new IllegalArgumentException("Enter a valid effective date; the text has been retained.");
        return parsed;
    }

    private java.math.BigDecimal openingAmount()
    {
        var amount = format.parseMoney(openingBalance.getText());
        if (amount == null) throw new IllegalArgumentException("Enter a valid opening balance.");
        return amount;
    }

    CompanyChartView selectedChart() { return chartSelection.getValue(); }
    List<CompanyChartView> chartChoices()
    {
        return List.copyOf(chartSelection.getItems());
    }

    void afterChartDeletion()
    {
        clearFormForNew();
        reload(null);
    }

    boolean loadingCharts() { return loadingCharts; }

    boolean focusChart(long id)
    {
        if (dirtyState.isDirty() && (selectedChart() == null || !Objects.equals(selectedChart().id(), id)))
        {
            status.setText("Chart saved. Account changes retained; save or cancel them before selecting the new chart.");
            return false;
        }
        pendingFocusChartId = id;
        if (selectedChart() == null || !Objects.equals(selectedChart().id(),id)) clearFormForNew();
        reload(editingAccountId);
        return true;
    }

    private static void onFx(Runnable action)
    {
        if (javafx.application.Platform.isFxApplicationThread()) action.run(); else javafx.application.Platform.runLater(action);
    }

    @Override
    public String commandLabel(AppCommand command)
    {
        return command == AppCommand.NEW_ACTIVE ? "New Account" : "Save Account";
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
        return "Select an owned DRAFT/ACTIVE chart and a bookkeeping role to edit accounts. Retired charts are read-only; use New Chart when none exists.";
    }

    @Override
    public void onSave()
    {
        resultListener.accept(saveForm());
    }

    @Override
    public RunCommandResult executeCommand(AppCommand command)
    {
        return command == AppCommand.SAVE_ACTIVE ? saveForm() : AppPanel.super.executeCommand(command);
    }

    @Override
    public void setCommandResultListener(Consumer<RunCommandResult> listener)
    {
        resultListener = Objects.requireNonNull(listener);
    }

    @Override
    public void onImportAddSuggestion(ImportAddSuggestion suggestion)
    {
        importPrefill.offer(suggestion, dirtyState.isDirty(), !loadingCharts, this::startImportDraft);
    }

    private void startImportDraft(String literalName)
    {
        CompanyChartView current = chartSelection.getItems().stream()
                .filter(CompanyChartView::activeForCompany).findFirst().orElse(null);
        changingCharts = true;
        try
        {
            chartSelection.setValue(current);
        }
        finally
        {
            changingCharts = false;
        }
        clearFormForNew();
        showChartAccounts();
        nameField.setText(literalName);
        status.setText("New record: Name copied literally from Import. Enter remaining required details and choose Save.");
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return dirtyState.isDirty();
    }
}
