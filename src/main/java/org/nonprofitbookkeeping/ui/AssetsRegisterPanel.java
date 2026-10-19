package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.ApplicationPermission;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetLifecycleEvent;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.service.FixedAssetCommand;
import org.nonprofitbookkeeping.service.FixedAssetLifecycleEventView;
import org.nonprofitbookkeeping.service.FixedAssetService;
import org.nonprofitbookkeeping.service.FixedAssetView;
import org.nonprofitbookkeeping.service.AssetAcquisitionCommand;
import org.nonprofitbookkeeping.service.AssetAcquisitionPreview;
import org.nonprofitbookkeeping.service.AssetAcquisitionCandidate;
import org.nonprofitbookkeeping.model.AssetAcquisitionSource;
import org.nonprofitbookkeeping.model.AccountClassification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/** H2-backed fixed asset register panel. */
public class AssetsRegisterPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<FixedAssetView> table = new TableView<>();
    private final TableView<FixedAssetLifecycleEventView> lifecycleTable = new TableView<>();
    private final ComboBox<Account> assetAccount = new ComboBox<>();
    private final ComboBox<Account> accumulatedDepreciationAccount = new ComboBox<>();
    private final ComboBox<Account> depreciationExpenseAccount = new ComboBox<>();
    private final ComboBox<Fund> fund = new ComboBox<>();
    private final ComboBox<Integer> usefulLifeMonths = new ComboBox<>();
    private final Label lifecycleStatus = new Label("ACTIVE");
    private final TextField lifecycleActor = new TextField(DesktopActorIdentity.current());
    private final TextField lifecycleReason = new TextField();
    private final Button deactivateAsset = new Button("Deactivate Asset");
    private final Button reactivateAsset = new Button("Reactivate Asset");
    private final TextField name = new TextField();
    private final DatePicker acquisitionDate = new DatePicker(LocalDate.now());
    private final TextField acquisitionCost = new TextField("0.00");
    private final TextField salvageValue = new TextField("0.00");
    private final TextField openingAccumulatedDepreciation = new TextField("0.00");
    private final TextArea notes = new TextArea();
    private final Label status = new Label();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final CompanyUiFormat companyFormat = CompanyUiFormat.activeCompany();
    private final ComboBox<AssetAcquisitionSource> acquisitionSource = new ComboBox<>();
    private final ComboBox<Account> acquisitionBank = new ComboBox<>();
    private final ComboBox<AssetAcquisitionCandidate> acquisitionLine = new ComboBox<>();
    private final Label acquisitionBankLabel = new Label("Purchase BANK account");
    private final Label acquisitionLineLabel = new Label("Existing asset debit");
    private final Label acquisitionSummary = new Label("Select an explicit acquisition route.");
    private final Button unlinkAcquisition = new Button("Remove Acquisition Link...");
    private final Button drillAcquisition = new Button("Open Acquisition in Journal");
    private final java.util.Map<TextField, ExactMoneyInput> moneyInputs = new java.util.IdentityHashMap<>();
    private java.util.UUID acquisitionRequest = java.util.UUID.randomUUID();
    private FixedAssetView loadedAsset;
    private Runnable capabilitiesChanged = () -> {};
    private final FormDirtyTracker dirtyState;
    private List<Account> postingAccounts = List.of();
    private Long selectedAssetId;
    private FixedAsset.Status editingStatus = FixedAsset.Status.ACTIVE;
    private boolean suppressSelection;

    public AssetsRegisterPanel()
    {
        root.setPadding(new Insets(8));
        root.setMinSize(0, 0);
        status.setWrapText(true);
        name.setId("assetName"); assetAccount.setId("assetAccount");
        accumulatedDepreciationAccount.setId("assetAccumulatedAccount");
        depreciationExpenseAccount.setId("assetExpenseAccount"); fund.setId("assetFund");
        acquisitionDate.setId("assetAcquisitionDate"); acquisitionCost.setId("assetAcquisitionCost");
        salvageValue.setId("assetSalvageValue"); openingAccumulatedDepreciation.setId("assetOpeningDepreciation");
        acquisitionSource.setId("assetAcquisitionSource"); acquisitionBank.setId("assetAcquisitionBank");
        acquisitionLine.setId("assetAcquisitionLine"); acquisitionSummary.setId("assetAcquisitionSummary");
        acquisitionSummary.setWrapText(true); acquisitionSource.setPromptText("Select acquisition route");
        table.setId("assetRegisterTable"); lifecycleTable.setId("assetLifecycleTable"); status.setId("assetRegisterStatus");
        table.disableProperty().bind(busy); lifecycleTable.disableProperty().bind(busy);
        for (TextField field : List.of(acquisitionCost, salvageValue, openingAccumulatedDepreciation))
        {
            moneyInputs.put(field, new ExactMoneyInput(field));
        }
        busy.addListener((obs, oldValue, newValue) ->
        {
            capabilitiesChanged.run();
            updateAcquisitionControls();
        });
        Label title = new Label("Asset Register");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.disableProperty().bind(busy);
        refresh.setOnAction(e -> reloadWithDiscardProtection());
        Button newAsset = new Button("New Asset");
        newAsset.setId("assetRegisterNew");
        newAsset.disableProperty().bind(
                busy.or(UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE)));
        newAsset.setOnAction(e -> onNew());
        Button save = new Button("Save Asset");
        save.setId("assetRegisterSave");
        save.disableProperty().bind(
                busy.or(UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE)));
        save.setOnAction(e -> saveAsset());
        deactivateAsset.setId("deactivateFixedAssetButton");
        deactivateAsset.setDisable(true);
        deactivateAsset.setOnAction(e -> changeSelectedStatus(FixedAsset.Status.INACTIVE));
        reactivateAsset.setId("reactivateFixedAssetButton");
        reactivateAsset.setDisable(true);
        reactivateAsset.setOnAction(e -> changeSelectedStatus(FixedAsset.Status.ACTIVE));
        UiPermissionGate.gate(deactivateAsset, ApplicationPermission.BOOKKEEPING_WRITE, "Deactivate a fixed asset");
        UiPermissionGate.gate(reactivateAsset, ApplicationPermission.BOOKKEEPING_WRITE, "Reactivate a fixed asset");
        busy.addListener((obs, oldValue, newValue) ->
                updateLifecycleActions(table.getSelectionModel().getSelectedItem()));
        Button lifecycle = new Button("Record Lifecycle Event...");
        lifecycle.setId("recordFixedAssetLifecycleButton");
        lifecycle.disableProperty().bind(
                busy.or(table.getSelectionModel().selectedItemProperty().isNull())
                        .or(UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE)));
        lifecycle.setOnAction(e -> recordLifecycleEvent());
        Button reverse = new Button("Reverse Selected Lifecycle Event");
        reverse.setId("reverseFixedAssetLifecycleButton");
        reverse.disableProperty().bind(
                busy.or(lifecycleTable.getSelectionModel().selectedItemProperty().isNull())
                        .or(UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE)));
        reverse.setOnAction(e -> reverseSelectedLifecycleEvent());
        Button drill = new Button("Drill Selected Event to Ledger");
        drill.disableProperty().bind(
                busy.or(lifecycleTable.getSelectionModel().selectedItemProperty().isNull()));
        drill.setOnAction(e -> drillSelectedLifecycleEvent());
        unlinkAcquisition.setId("assetAcquisitionUnlink");
        unlinkAcquisition.setOnAction(event -> removeAcquisitionLink());
        UiPermissionGate.gate(unlinkAcquisition, ApplicationPermission.BOOKKEEPING_WRITE, "Remove acquisition link");
        drillAcquisition.setId("assetAcquisitionJournal");
        drillAcquisition.setOnAction(event -> openAcquisition());
        FlowPane actions = new FlowPane(
                8, 6, refresh, newAsset, save, deactivateAsset, reactivateAsset,
                lifecycle, reverse, drill, unlinkAcquisition, drillAcquisition);

        ScrollPane actionScroll = new ScrollPane(actions);
        actionScroll.setId("assetRegisterActionsScroll");
        actionScroll.setFitToWidth(true);
        actionScroll.setPrefViewportHeight(68);
        actionScroll.setMaxHeight(104);
        actionScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        actionScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        root.setTop(new VBox(6, title, actionScroll, status, new Separator()));
        configureTable();
        configureLifecycleTable();
        configureChoices();
        dirtyState = new FormDirtyTracker(this::formSnapshot);
        VBox tableRegion = new VBox(6, new Label("Fixed assets"), table);
        tableRegion.setMinHeight(0.0);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox lifecycleRegion = new VBox(6, new Label("Lifecycle History"), lifecycleTable);
        lifecycleRegion.setMinHeight(0.0);
        VBox.setVgrow(lifecycleTable, Priority.ALWAYS);
        SplitPane historySplit = new SplitPane(tableRegion, lifecycleRegion);
        historySplit.setMinHeight(0);
        table.setMinHeight(0); lifecycleTable.setMinHeight(0);
        historySplit.setOrientation(Orientation.VERTICAL);
        historySplit.setDividerPositions(0.62);
        historySplit.setId("assetRegisterHistorySplit");
        CompanySplitPaneStateBinder.bind(historySplit, "asset-register-history", 0.62);
        ScrollPane editorScroll = new ScrollPane(buildForm());
        editorScroll.setId("assetRegisterEditorScroll");
        editorScroll.setFitToWidth(true);
        editorScroll.setMinHeight(0.0);
        editorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editorScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        SplitPane split = new SplitPane(historySplit, editorScroll);
        split.setId("assetRegisterWorkspaceSplit");
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.55);
        CompanySplitPaneStateBinder.bind(split, "asset-register-workspace", 0.55);
        root.setCenter(split);
        companyFormat.install(acquisitionDate);
        clearForm();
        reload();
    }

    private GridPane buildForm()
    {
        GridPane form = new GridPane();
        form.setId("assetRegisterForm");
        form.disableProperty().bind(busy.or(UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE)));
        form.setHgap(8);
        form.setVgap(6);
        int row = 0;
        form.add(new Label("Acquisition route"), 0, row);
        form.add(acquisitionSource, 1, row++);
        form.add(acquisitionBankLabel, 0, row); form.add(acquisitionBank, 1, row++);
        form.add(acquisitionLineLabel, 0, row); form.add(acquisitionLine, 1, row++);
        form.add(acquisitionSummary, 0, row++, 2, 1);
        Label acquisitionHelp = new Label("Save Asset / File Save accepts the register and acquisition together. For payable invoices, checks, or richer allocations, enter Journal first and Link existing entry. Opening / register only creates no accounting; legacy links are never guessed.");
        acquisitionHelp.setWrapText(true);
        form.add(acquisitionHelp, 0, row++, 2, 1);
        GridPane.setHgrow(acquisitionSource, Priority.ALWAYS);
        GridPane.setHgrow(acquisitionBank, Priority.ALWAYS);
        GridPane.setHgrow(acquisitionLine, Priority.ALWAYS);
        acquisitionSource.setMaxWidth(Double.MAX_VALUE); acquisitionSource.setMinWidth(0);
        acquisitionBank.setMaxWidth(Double.MAX_VALUE); acquisitionBank.setMinWidth(0);
        acquisitionLine.setMaxWidth(Double.MAX_VALUE); acquisitionLine.setMinWidth(0);
        form.add(new Label("Name"), 0, row);
        form.add(name, 1, row++);
        form.add(new Label("Asset account"), 0, row);
        form.add(assetAccount, 1, row++);
        form.add(new Label("Accumulated depreciation account"), 0, row);
        form.add(accumulatedDepreciationAccount, 1, row++);
        form.add(new Label("Depreciation expense account"), 0, row);
        form.add(depreciationExpenseAccount, 1, row++);
        form.add(new Label("Fund"), 0, row);
        form.add(fund, 1, row++);
        form.add(new Label("Acquisition date"), 0, row);
        form.add(acquisitionDate, 1, row++);
        form.add(new Label("Acquisition cost"), 0, row);
        form.add(acquisitionCost, 1, row++);
        form.add(new Label("Salvage value"), 0, row);
        form.add(salvageValue, 1, row++);
        form.add(new Label("Useful life"), 0, row);
        form.add(usefulLifeMonths, 1, row++);
        form.add(new Label("Opening accumulated depreciation"), 0, row);
        form.add(openingAccumulatedDepreciation, 1, row++);
        form.add(new Label("Status"), 0, row);
        form.add(lifecycleStatus, 1, row++);
        notes.setPrefRowCount(2);
        form.add(new Label("Notes"), 0, row);
        form.add(notes, 1, row++);
        lifecycleActor.setPromptText("Authenticated audit actor");
        lifecycleActor.setEditable(false);
        lifecycleReason.setPromptText("Required for Activate / Deactivate");
        form.add(new Label("Lifecycle actor"), 0, row);
        form.add(lifecycleActor, 1, row++);
        form.add(new Label("Lifecycle reason"), 0, row);
        form.add(lifecycleReason, 1, row++);
        Label lifecycleGuidance = new Label(
                "Fixed assets are retained and never physically deleted. Deactivate temporarily stops depreciation and financial lifecycle actions; Reactivate resumes them. Use Sale or Retirement for financial disposal; DISPOSED remains retained history until that lifecycle event is reversed.");
        lifecycleGuidance.setWrapText(true);
        form.add(lifecycleGuidance, 0, row, 2, 1);
        for (Node field : java.util.List.of(
                name, assetAccount, accumulatedDepreciationAccount, depreciationExpenseAccount,
                fund, acquisitionDate, acquisitionCost, salvageValue, usefulLifeMonths,
                openingAccumulatedDepreciation, notes, lifecycleActor, lifecycleReason))
        {
            GridPane.setHgrow(field, Priority.ALWAYS);
        }
        // Keep field labels readable; the editor scrolls when their natural widths cannot fit.
        form.getChildren().stream().filter(node -> node instanceof Label label && !label.isWrapText())
                .map(node -> (Label)node).forEach(label -> label.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE));
        return form;
    }

    private void configureTable()
    {
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No fixed assets have been recorded."));
        addColumn("Name", FixedAssetView::name, 180);
        addColumn("Asset Account", FixedAssetView::assetAccountCode, 120);
        addColumn("Fund", FixedAssetView::fundCode, 100);
        addFormattedColumn("Acquired", FixedAssetView::acquisitionDate, companyFormat::formatDate, 110);
        addFormattedColumn("Cost", FixedAssetView::acquisitionCost, companyFormat::formatMoney, 110);
        addFormattedColumn("Accum. Dep.", FixedAssetView::accumulatedDepreciation, companyFormat::formatMoney, 110);
        addFormattedColumn("Impairment", FixedAssetView::accumulatedImpairment, companyFormat::formatMoney, 110);
        addFormattedColumn("Book Value", FixedAssetView::currentBookValue, companyFormat::formatMoney, 110);
        addFormattedColumn("Next Dep.", FixedAssetView::nextDepreciationAmount, companyFormat::formatMoney, 110);
        addColumn("Status", value -> value.status().name(), 100);
        addAcquisitionColumn("Acquisition source", "acquisitionSource", value -> value.acquisition() == null ? "Legacy unlinked" : value.acquisition().label(), 260);
        addAcquisitionColumn("Acquisition transaction", "acquisitionTransaction", value -> value.acquisition() == null || value.acquisition().transactionId() == null ? "" : "Txn #" + value.acquisition().transactionId(), 180);
        addAcquisitionColumn("Source debit / claimed / remaining", "acquisitionControls", value -> value.acquisition() == null || value.acquisition().sourceDebit() == null ? "" : companyFormat.formatMoney(value.acquisition().sourceDebit()) + " / " + companyFormat.formatMoney(value.acquisition().claimed()) + " / " + companyFormat.formatMoney(value.acquisition().remaining()), 310);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (busy.get() || suppressSelection || selected == null)
            {
                return;
            }
            if (dirtyState.isDirty() && !confirmDiscard())
            {
                suppressSelection = true;
                table.getSelectionModel().select(old);
                suppressSelection = false;
                return;
            }
            fillForm(selected);
            updateLifecycleActions(selected);
        });
    }

    private void configureLifecycleTable()
    {
        lifecycleTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        lifecycleTable.setPlaceholder(new Label("No fixed-asset lifecycle events have been recorded."));
        addLifecycleColumn("Date", value -> companyFormat.formatDate(value.eventDate()), 110);
        addLifecycleColumn("Asset", FixedAssetLifecycleEventView::assetName, 180);
        addLifecycleColumn("Type", value -> value.eventType().name(), 110);
        addLifecycleColumn("Carrying Before", value -> companyFormat.formatMoney(value.carryingAmountBefore()), 130);
        addLifecycleColumn("Proceeds", value -> companyFormat.formatMoney(value.proceeds()), 110);
        addLifecycleColumn("Impairment", value -> companyFormat.formatMoney(value.impairmentAmount()), 110);
        addLifecycleColumn("Gain", value -> companyFormat.formatMoney(value.gainAmount()), 100);
        addLifecycleColumn("Loss", value -> companyFormat.formatMoney(value.lossAmount()), 100);
        addLifecycleColumn("Txn", value -> String.valueOf(value.transactionId()), 80);
        addLifecycleColumn("Reversal Txn", value -> value.reversalTransactionId() == null
                ? "" : String.valueOf(value.reversalTransactionId()), 100);
        addLifecycleColumn("Notes", FixedAssetLifecycleEventView::notes, 220);
    }

    private void addLifecycleColumn(
            String title,
            Function<FixedAssetLifecycleEventView, String> value,
            double width)
    {
        TableColumn<FixedAssetLifecycleEventView, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new SimpleStringProperty(value.apply(row.getValue())));
        column.setPrefWidth(width);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        lifecycleTable.getColumns().add(column);
    }

    private void addColumn(String title, Function<FixedAssetView, String> value, double width)
    {
        TableColumn<FixedAssetView, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        table.getColumns().add(column);
    }

    private <T extends Comparable<? super T>> void addFormattedColumn(String title,
            Function<FixedAssetView, T> value, Function<T, String> display, double width)
    {
        TableColumn<FixedAssetView, T> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setCellFactory(ignored -> new javafx.scene.control.TableCell<>()
        {
            @Override
            protected void updateItem(T item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : display.apply(item));
            }
        });
        column.setPrefWidth(width); column.setMinWidth(72);
        column.setSortable(true); column.setResizable(true); column.setReorderable(true);
        table.getColumns().add(column);
    }

    private void addAcquisitionColumn(String title, String id, Function<FixedAssetView, String> value, double width)
    {
        TableColumn<FixedAssetView, String> column = new TableColumn<>(title);
        column.setId(id);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width); column.setMinWidth(72);
        column.setSortable(true); column.setResizable(true); column.setReorderable(true);
        table.getColumns().add(column);
    }

    private void configureChoices()
    {
        StringConverter<Account> accountConverter = new StringConverter<>()
        {
            @Override
            public String toString(Account account)
            {
                return account == null ? "" : accountLabel(account);
            }

            @Override
            public Account fromString(String string)
            {
                return null;
            }
        };
        acquisitionBank.setConverter(accountConverter);
        acquisitionSource.getItems().setAll(AssetAcquisitionSource.values());
        acquisitionSource.valueProperty().addListener((obs, oldValue, newValue) -> updateAcquisitionControls());
        acquisitionLine.setConverter(new StringConverter<>()
        {
            @Override
            public String toString(AssetAcquisitionCandidate candidate)
            {
                return candidate == null ? "" : "Txn #" + candidate.transactionId() + " — " + companyFormat.formatDate(candidate.date())
                        + " — " + candidate.account() + " / " + candidate.fund() + " — unclaimed "
                        + companyFormat.formatMoney(candidate.remaining()) + " — " + candidate.memo();
            }

            @Override
            public AssetAcquisitionCandidate fromString(String text)
            {
                throw new UnsupportedOperationException("Select an existing asset debit.");
            }
        });
        assetAccount.setConverter(accountConverter);
        accumulatedDepreciationAccount.setConverter(accountConverter);
        depreciationExpenseAccount.setConverter(accountConverter);
        fund.setConverter(new StringConverter<>()
        {
            @Override
            public String toString(Fund selectedFund)
            {
                return selectedFund == null ? "" : fundLabel(selectedFund);
            }

            @Override
            public Fund fromString(String string)
            {
                return null;
            }
        });
        usefulLifeMonths.setItems(FXCollections.observableArrayList(36, 60, 84));
        usefulLifeMonths.getSelectionModel().select(Integer.valueOf(60));
    }

    private void reload()
    {
        reload(null);
    }

    private void reload(String operationOutcome)
    {
        busy.set(true);
        status.setText(operationOutcome == null
                ? "Loading fixed assets..."
                : operationOutcome + " Refreshing authoritative fixed-asset data...");
        UiAsync.run("asset-register-load",
                () -> new AssetPanelData(
                        UiServiceRegistry.accountLookup().listPostingAccountsIncludingInactive(),
                        UiServiceRegistry.fundLookup().listAllFunds(),
                        UiServiceRegistry.fixedAssets().listAssets(activeCompanyCode()),
                        UiServiceRegistry.fixedAssets().listLifecycleEvents(activeCompanyCode()),
                        UiServiceRegistry.fixedAssets().acquisitionCandidates()),
                data -> {
                    boolean restoreSelection = selectedAssetId != null && !dirtyState.isDirty();
                    postingAccounts = List.copyOf(data.accounts().stream().filter(Account::isActive).toList());
                    acquisitionBank.getItems().setAll(data.accounts().stream().filter(Account::isActive).filter(AccountClassification::isBank).toList());
                    acquisitionLine.getItems().setAll(data.acquisitions());
                    assetAccount.setItems(FXCollections.observableArrayList(data.accounts().stream()
                            .filter(a -> a.getAccountType() == AccountType.ASSET && a.getSubtype() == AccountSubtype.FIXED_ASSET)
                            .toList()));
                    accumulatedDepreciationAccount.setItems(FXCollections.observableArrayList(data.accounts().stream()
                            .filter(a -> a.getAccountType() == AccountType.ASSET)
                            .toList()));
                    depreciationExpenseAccount.setItems(FXCollections.observableArrayList(data.accounts().stream()
                            .filter(a -> a.getAccountType() == AccountType.EXPENSE)
                            .toList()));
                    fund.setItems(FXCollections.observableArrayList(data.funds()));
                    table.getItems().setAll(data.assets());
                    lifecycleTable.getItems().setAll(data.lifecycleEvents());
                    if (restoreSelection)
                    {
                        table.getItems().stream().filter(value -> value.id().equals(selectedAssetId)).findFirst()
                                .ifPresent(value -> { fillForm(value); table.getSelectionModel().select(value); });
                    }
                    busy.set(false);
                    updateAcquisitionControls();
                    String loaded = "Loaded " + data.assets().size() + " fixed asset(s) and "
                            + data.lifecycleEvents().size() + " lifecycle event(s) from H2.";
                    status.setText(operationOutcome == null ? loaded : operationOutcome + " " + loaded);
                },
                ex -> {
                    busy.set(false);
                    String failure = "Could not load fixed assets: " + UiErrors.safeMessage(ex);
                    status.setText(operationOutcome == null ? failure : operationOutcome + " " + failure);
                });
    }

    private void saveAsset()
    {
        if (busy.get())
        {
            status.setText("Asset operation is in progress; the draft is retained.");
            return;
        }
        try
        {
            FixedAssetCommand command = commandFromForm();
            AssetAcquisitionSource source = acquisitionSource.getValue();
            AssetAcquisitionSource existingSource = loadedAsset == null || loadedAsset.acquisition() == null ? null : loadedAsset.acquisition().source();
            boolean acceptAcquisition = selectedAssetId == null || source != existingSource;
            if (!acceptAcquisition)
            {
                long id = selectedAssetId;
                busy.set(true);
                UiAsync.run("asset-register-save", () -> UiServiceRegistry.fixedAssets().update(id, command),
                        this::savedAsset, this::saveFailed);
                return;
            }
            if (source == null)
            {
                throw new IllegalArgumentException("Select an explicit acquisition route before saving a new asset.");
            }
            var acquisition = new AssetAcquisitionCommand(selectedAssetId, command, source,
                    source == AssetAcquisitionSource.NEW_PURCHASE ? requireSelected(acquisitionBank, "purchase BANK account").getId() : null,
                    source == AssetAcquisitionSource.EXISTING_ENTRY ? requireSelected(acquisitionLine, "existing asset debit").splitId() : null);
            busy.set(true);
            status.setText("Preparing acquisition preview...");
            UiAsync.run("asset-acquisition-preview", () -> UiServiceRegistry.fixedAssets().previewAcquisition(acquisition, acquisitionRequest),
                    preview ->
                    {
                        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
                        confirmation.getDialogPane().setId("assetAcquisitionConfirmation");
                        confirmation.setTitle("Save Asset Acquisition");
                        confirmation.setHeaderText(preview.command().source().label() + ": " + command.name());
                        confirmation.setContentText(acquisitionPreviewText(preview));
                        CompanyDialogUiCompliance.install(confirmation.getDialogPane(), AppPanelId.ASSETS_REGISTER);
                        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty())
                        {
                            busy.set(false);
                            status.setText("Acquisition Save cancelled; draft retained and no accounting changed.");
                            return;
                        }
                        status.setText("Saving asset and acquisition atomically...");
                        UiAsync.run("asset-acquisition-save", () -> UiServiceRegistry.fixedAssets().recordAcquisition(preview),
                                this::savedAsset, this::saveFailed);
                    }, this::saveFailed);
        }
        catch (RuntimeException ex)
        {
            saveFailed(ex);
        }
    }

    private String acquisitionPreviewText(AssetAcquisitionPreview preview)
    {
        String text = preview.assetAccount() + "\n" + companyFormat.formatDate(preview.command().asset().acquisitionDate())
                + "\nCost: " + companyFormat.formatMoney(preview.command().asset().acquisitionCost())
                + "\n" + preview.sourceLabel();
        if (preview.acceptedAssetId() != null)
        {
            return text + "\nAlready accepted: reload the retained asset without new accounting.";
        }
        if (preview.transaction() != null)
        {
            return text + "\nDebit asset / credit BANK: " + companyFormat.formatMoney(preview.command().asset().acquisitionCost())
                    + "\nOne canonical transaction and one register record will be saved together.";
        }
        if (preview.sourceDebit() != null)
        {
            return text + "\nSource debit: " + companyFormat.formatMoney(preview.sourceDebit())
                    + "\nAlready claimed: " + companyFormat.formatMoney(preview.alreadyClaimed())
                    + "\nRemaining after this claim: " + companyFormat.formatMoney(preview.remaining())
                    + "\nLink only: existing accounting is retained without reposting.";
        }
        return text + "\nRegister only: no purchase transaction or opening GL is created.";
    }

    private void savedAsset(FixedAssetView saved)
    {
        fillForm(saved);
        reload("Saved fixed asset: " + saved.name() + ".");
    }

    private void saveFailed(Throwable failure)
    {
        busy.set(false);
        status.setText("Could not save asset; draft retained: " + UiErrors.safeMessage(failure));
    }

    private void updateAcquisitionControls()
    {
        boolean linked = loadedAsset != null && loadedAsset.acquisition() != null && loadedAsset.acquisition().splitId() != null;
        boolean disposed = loadedAsset != null && loadedAsset.status() == FixedAsset.Status.DISPOSED;
        acquisitionSource.setDisable(linked || disposed);
        boolean purchase = selectedAssetId == null && acquisitionSource.getValue() == AssetAcquisitionSource.NEW_PURCHASE;
        boolean link = !linked && acquisitionSource.getValue() == AssetAcquisitionSource.EXISTING_ENTRY;
        acquisitionBank.setVisible(purchase); acquisitionBank.setManaged(purchase);
        acquisitionBankLabel.setVisible(purchase); acquisitionBankLabel.setManaged(purchase);
        acquisitionLine.setVisible(link); acquisitionLine.setManaged(link);
        acquisitionLineLabel.setVisible(link); acquisitionLineLabel.setManaged(link);
        unlinkAcquisition.setDisable(busy.get() || !linked);
        drillAcquisition.setDisable(busy.get() || !linked);
        if (loadedAsset != null && loadedAsset.acquisition() != null)
        {
            var provenance = loadedAsset.acquisition();
            acquisitionSummary.setText(provenance.label() + (provenance.transactionId() == null ? ""
                    : " — Txn #" + provenance.transactionId() + "; source " + companyFormat.formatMoney(provenance.sourceDebit())
                    + ", claimed " + companyFormat.formatMoney(provenance.claimed()) + ", remaining " + companyFormat.formatMoney(provenance.remaining())));
        }
        else
        {
            acquisitionSummary.setText("Select acquisition provenance. Save accepts register and accounting together; no automatic route is inferred.");
        }
    }

    private void openAcquisition()
    {
        if (loadedAsset != null && loadedAsset.acquisition() != null && loadedAsset.acquisition().transactionId() != null)
        {
            long transaction = loadedAsset.acquisition().transactionId();
            DrillThroughCoordinator.openPanelWithContext(AppPanelId.JOURNAL_PANE, "Txn #" + transaction + " from asset acquisition " + loadedAsset.name());
        }
    }

    private void removeAcquisitionLink()
    {
        if (busy.get() || loadedAsset == null)
        {
            return;
        }
        if (dirtyState.isDirty())
        {
            status.setText("Save or discard register edits before removing an acquisition link.");
            return;
        }
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        dialog.setTitle("Remove Acquisition Link");
        dialog.setHeaderText("Accounting and asset remain. Only an open, unreconciled link without depreciation/lifecycle history may be removed.");
        dialog.setContentText("Required reason:");
        CompanyDialogUiCompliance.install(dialog.getDialogPane(), AppPanelId.ASSETS_REGISTER);
        dialog.showAndWait().filter(value -> !value.isBlank()).ifPresent(reason ->
        {
            long id = loadedAsset.id();
            busy.set(true);
            UiAsync.run("asset-acquisition-unlink", () -> UiServiceRegistry.fixedAssets().removeAcquisitionLink(id, reason),
                    this::savedAsset, this::saveFailed);
        });
    }

    private void changeSelectedStatus(FixedAsset.Status targetStatus)
    {
        FixedAssetView selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a fixed asset before changing its lifecycle status.");
            return;
        }
        if (dirtyState.isDirty())
        {
            status.setText("Save or discard the current asset edits before changing lifecycle status.");
            return;
        }
        String actor = lifecycleActor.getText() == null ? "" : lifecycleActor.getText().trim();
        String reason = lifecycleReason.getText() == null ? "" : lifecycleReason.getText().trim();
        if (actor.isBlank() || reason.isBlank())
        {
            status.setText("Lifecycle actor and reason are required.");
            return;
        }
        String verb = targetStatus == FixedAsset.Status.INACTIVE ? "Deactivate" : "Reactivate";
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle(verb + " Fixed Asset");
        confirmation.setHeaderText(verb + " " + selected.name() + "?");
        confirmation.setContentText(targetStatus == FixedAsset.Status.INACTIVE
                ? "Deactivation retains the asset and all accounting history but stops depreciation and Sale/Retirement/Impairment actions until the asset is reactivated.\n\nReason: " + reason
                : "Reactivation resumes depreciation and governed financial lifecycle eligibility for this retained asset.\n\nReason: " + reason);
        CompanyDialogUiCompliance.install(confirmation.getDialogPane(), AppPanelId.ASSETS_REGISTER);
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty())
        {
            status.setText(verb + " cancelled; no asset status changed.");
            return;
        }
        busy.set(true);
        updateLifecycleActions(selected);
        status.setText(verb + "ing fixed asset...");
        UiAsync.run("fixed-asset-status-change",
                () -> UiServiceRegistry.fixedAssets().changeStatus(
                        selected.id(), targetStatus, actor, reason),
                changed -> {
                    editingStatus = changed.status();
                    lifecycleStatus.setText(changed.status().name());
                    lifecycleReason.clear();
                    dirtyState.markClean();
                    reload(verb + "d fixed asset " + changed.name() + ".");
                },
                ex -> {
                    busy.set(false);
                    updateLifecycleActions(selected);
                    status.setText("Could not " + verb.toLowerCase() + " fixed asset: "
                            + UiErrors.safeMessage(ex));
                });
    }

    private void updateLifecycleActions(FixedAssetView selected)
    {
        boolean unavailable = busy.get() || selected == null;
        deactivateAsset.setDisable(unavailable || selected.status() != FixedAsset.Status.ACTIVE);
        reactivateAsset.setDisable(unavailable || selected.status() != FixedAsset.Status.INACTIVE);
    }

    private void recordLifecycleEvent()
    {
        FixedAssetView selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select an active fixed asset before recording a lifecycle event.");
            return;
        }
        if (selected.status() != FixedAsset.Status.ACTIVE)
        {
            status.setText("Only an active fixed asset can receive lifecycle accounting.");
            return;
        }
        if (dirtyState.isDirty())
        {
            status.setText("Save or discard the current asset edits before lifecycle accounting.");
            return;
        }
        try
        {
            FixedAssetLifecycleDialog.Request request = FixedAssetLifecycleDialog.show(
                    postingAccounts, companyFormat);
            if (request == null)
            {
                status.setText("Fixed-asset lifecycle action cancelled; no accounting changed.");
                return;
            }
            busy.set(true);
            status.setText("Calculating fixed-asset lifecycle preview...");
            UiAsync.run("fixed-asset-lifecycle-preview",
                    () -> UiServiceRegistry.fixedAssets().previewLifecycleEvent(
                            selected.id(), request.command()),
                    preview -> {
                        if (!confirmLifecycle(preview))
                        {
                            busy.set(false);
                            status.setText("Fixed-asset lifecycle action cancelled; no accounting changed.");
                            return;
                        }
                        status.setText("Recording fixed-asset lifecycle accounting atomically...");
                        UiAsync.run("fixed-asset-lifecycle-commit",
                                () -> UiServiceRegistry.fixedAssets().recordLifecycleEvent(
                                        preview, request.actor()),
                                completed -> {
                                    clearForm();
                                    reload("Recorded fixed-asset " + completed.eventType()
                                            + " in canonical transaction #"
                                            + completed.transactionId() + ".");
                                },
                                ex -> {
                                    busy.set(false);
                                    status.setText("Could not record fixed-asset lifecycle event: "
                                            + UiErrors.safeMessage(ex)
                                            + " No partial accounting was retained.");
                                });
                    },
                    ex -> {
                        busy.set(false);
                        status.setText("Could not preview fixed-asset lifecycle event: "
                                + UiErrors.safeMessage(ex) + " No accounting changed.");
                    });
        }
        catch (RuntimeException ex)
        {
            busy.set(false);
            status.setText("Could not record fixed-asset lifecycle event: "
                    + UiErrors.safeMessage(ex) + " No partial accounting was retained.");
        }
    }

    private boolean confirmLifecycle(FixedAssetService.LifecyclePreview preview)
    {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Fixed-Asset Lifecycle Accounting");
        confirmation.setHeaderText("Create this lifecycle fact and balanced canonical transaction?");
        String proceedsLine = preview.proceeds().signum() == 0
                ? "none"
                : companyFormat.formatMoney(preview.proceeds()) + " to "
                + preview.proceedsAccountCode() + " — " + preview.proceedsAccountName();
        String result = preview.eventType() == FixedAssetLifecycleEvent.EventType.IMPAIRMENT
                ? "Impairment: " + companyFormat.formatMoney(preview.impairmentAmount())
                + " using " + preview.lossAccountCode() + " — " + preview.lossAccountName()
                : "Gain: " + companyFormat.formatMoney(preview.gainAmount())
                + " / Loss: " + companyFormat.formatMoney(preview.lossAmount());
        confirmation.setContentText(
                "Asset: " + preview.assetName()
                        + "\nOperation/date: " + preview.eventType() + " / "
                        + companyFormat.formatDate(preview.eventDate())
                        + "\nAcquisition cost: " + companyFormat.formatMoney(preview.acquisitionCost())
                        + "\nAccumulated depreciation: "
                        + companyFormat.formatMoney(preview.accumulatedDepreciation())
                        + "\nPrior impairment: "
                        + companyFormat.formatMoney(preview.accumulatedImpairmentBefore())
                        + "\nCarrying amount: "
                        + companyFormat.formatMoney(preview.carryingAmountBefore())
                        + "\nProceeds: " + proceedsLine
                        + "\n" + result
                        + "\nFund: " + preview.fundCode() + " — " + preview.fundName()
                        + "\nStatus: " + preview.assetStatusBefore() + " → "
                        + preview.assetStatusAfter()
                        + "\n\nProposed balanced entry:\n" + lifecycleEntryText(preview));
        ButtonType save = new ButtonType("Save Lifecycle Event", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        confirmation.getButtonTypes().setAll(save, ButtonType.CANCEL);
        return confirmation.showAndWait().filter(save::equals).isPresent();
    }

    private String lifecycleEntryText(FixedAssetService.LifecyclePreview preview)
    {
        List<String> lines = new java.util.ArrayList<>();
        if (preview.eventType() == FixedAssetLifecycleEvent.EventType.IMPAIRMENT)
        {
            lines.add(entryLine("DR", preview.lossAccountCode(), preview.lossAccountName(),
                    preview.impairmentAmount()));
            lines.add(entryLine("CR", preview.accumulatedAccountCode(),
                    preview.accumulatedAccountName(), preview.impairmentAmount()));
        }
        else
        {
            if (preview.proceeds().signum() > 0)
            {
                lines.add(entryLine("DR", preview.proceedsAccountCode(),
                        preview.proceedsAccountName(), preview.proceeds()));
            }
            BigDecimal accumulatedContra = preview.accumulatedDepreciation()
                    .add(preview.accumulatedImpairmentBefore());
            if (accumulatedContra.signum() > 0)
            {
                lines.add(entryLine("DR", preview.accumulatedAccountCode(),
                        preview.accumulatedAccountName(), accumulatedContra));
            }
            if (preview.lossAmount().signum() > 0)
            {
                lines.add(entryLine("DR", preview.lossAccountCode(),
                        preview.lossAccountName(), preview.lossAmount()));
            }
            lines.add(entryLine("CR", preview.assetAccountCode(), preview.assetAccountName(),
                    preview.acquisitionCost()));
            if (preview.gainAmount().signum() > 0)
            {
                lines.add(entryLine("CR", preview.gainAccountCode(),
                        preview.gainAccountName(), preview.gainAmount()));
            }
        }
        return String.join("\n", lines);
    }

    private String entryLine(
            String side,
            String accountCode,
            String accountName,
            BigDecimal amount)
    {
        return side + "  " + accountCode + " — " + accountName + "  "
                + companyFormat.formatMoney(amount);
    }

    private void reverseSelectedLifecycleEvent()
    {
        FixedAssetLifecycleEventView selected = lifecycleTable.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a fixed-asset lifecycle event to reverse.");
            return;
        }
        if (selected.reversalTransactionId() != null)
        {
            status.setText("The selected lifecycle event is already reversed.");
            return;
        }
        try
        {
            FixedAssetLifecycleDialog.ReversalRequest request =
                    FixedAssetLifecycleDialog.showReversal(companyFormat);
            if (request == null)
            {
                status.setText("Fixed-asset lifecycle reversal cancelled.");
                return;
            }
            busy.set(true);
            status.setText("Checking fixed-asset lifecycle reversal...");
            UiAsync.run("fixed-asset-lifecycle-reversal-preview",
                    () -> UiServiceRegistry.fixedAssets().previewLifecycleReversal(
                            selected.id(), request.date(), request.reason()),
                    preview -> {
                        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
                        confirmation.setTitle("Confirm Fixed-Asset Lifecycle Reversal");
                        confirmation.setHeaderText(
                                "Reverse the canonical transaction and restore the governed asset state?");
                        confirmation.setContentText(
                                "Asset: " + preview.assetName()
                                        + "\nEvent: " + preview.eventType() + " on "
                                        + companyFormat.formatDate(preview.eventDate())
                                        + "\nOriginal transaction: " + preview.originalTransactionId()
                                        + "\nReversal date: "
                                        + companyFormat.formatDate(preview.reversalDate())
                                        + "\nReason: " + preview.reason());
                        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty())
                        {
                            busy.set(false);
                            status.setText("Fixed-asset lifecycle reversal cancelled.");
                            return;
                        }
                        status.setText("Recording canonical fixed-asset reversal atomically...");
                        UiAsync.run("fixed-asset-lifecycle-reversal-commit",
                                () -> UiServiceRegistry.fixedAssets().reverseLifecycleEvent(
                                        preview, request.actor()),
                                reversed -> {
                                    clearForm();
                                    reload("Reversed fixed-asset lifecycle event #" + reversed.id()
                                            + " in transaction #"
                                            + reversed.reversalTransactionId() + ".");
                                },
                                ex -> {
                                    busy.set(false);
                                    status.setText("Could not reverse fixed-asset lifecycle event: "
                                            + UiErrors.safeMessage(ex)
                                            + " No partial accounting was retained.");
                                });
                    },
                    ex -> {
                        busy.set(false);
                        status.setText("Could not preview fixed-asset lifecycle reversal: "
                                + UiErrors.safeMessage(ex) + " No accounting changed.");
                    });
        }
        catch (RuntimeException ex)
        {
            busy.set(false);
            status.setText("Could not reverse fixed-asset lifecycle event: "
                    + UiErrors.safeMessage(ex) + " No partial accounting was retained.");
        }
    }

    private void drillSelectedLifecycleEvent()
    {
        FixedAssetLifecycleEventView selected = lifecycleTable.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a lifecycle event linked to a canonical transaction.");
            return;
        }
        long transactionId = selected.reversalTransactionId() == null
                ? selected.transactionId() : selected.reversalTransactionId();
        DrillThroughCoordinator.openLedgerWithContext(
                "Fixed-asset lifecycle event " + selected.id() + " → transaction " + transactionId);
        status.setText("Opened Journal context for fixed-asset transaction #" + transactionId + ".");
    }

    private FixedAssetCommand commandFromForm()
    {
        return new FixedAssetCommand(
                activeCompanyCode(),
                requireSelected(assetAccount, "asset account").getId(),
                requireSelected(accumulatedDepreciationAccount, "accumulated depreciation account").getId(),
                requireSelected(depreciationExpenseAccount, "depreciation expense account").getId(),
                requireSelected(fund, "fund").getId(),
                name.getText(),
                acquisitionDate.getValue(),
                moneyInputs.get(acquisitionCost).value(),
                moneyInputs.get(salvageValue).value(),
                requireSelected(usefulLifeMonths, "useful life"),
                FixedAsset.DepreciationMethod.STRAIGHT_LINE,
                moneyInputs.get(openingAccumulatedDepreciation).value(),
                editingStatus,
                notes.getText());
    }

    private void fillForm(FixedAssetView asset)
    {
        if (asset == null)
        {
            return;
        }
        selectedAssetId = asset.id();
        loadedAsset = asset;
        acquisitionRequest = java.util.UUID.randomUUID();
        acquisitionSource.getItems().setAll(AssetAcquisitionSource.REGISTER_ONLY, AssetAcquisitionSource.EXISTING_ENTRY);
        if (asset.acquisition() != null && asset.acquisition().source() == AssetAcquisitionSource.NEW_PURCHASE)
        {
            acquisitionSource.getItems().add(AssetAcquisitionSource.NEW_PURCHASE);
        }
        acquisitionSource.setValue(asset.acquisition() == null ? null : asset.acquisition().source());
        acquisitionBank.getSelectionModel().clearSelection();
        acquisitionLine.getSelectionModel().clearSelection();
        name.setText(asset.name());
        acquisitionDate.setValue(asset.acquisitionDate());
        moneyInputs.get(acquisitionCost).set(asset.acquisitionCost());
        moneyInputs.get(salvageValue).set(asset.salvageValue());
        moneyInputs.get(openingAccumulatedDepreciation).set(asset.openingAccumulatedDepreciation());
        usefulLifeMonths.getSelectionModel().select(Integer.valueOf(asset.usefulLifeMonths()));
        editingStatus = asset.status();
        lifecycleStatus.setText(asset.status().name());
        lifecycleReason.clear();
        notes.setText(asset.notes());
        selectAccountById(assetAccount, asset.assetAccountId());
        selectAccountById(accumulatedDepreciationAccount, asset.accumulatedDepreciationAccountId());
        selectAccountById(depreciationExpenseAccount, asset.depreciationExpenseAccountId());
        selectFundById(fund, asset.fundId());
        setAssetFormDisabled(asset.status() == FixedAsset.Status.DISPOSED);
        updateLifecycleActions(asset);
        updateAcquisitionControls();
        dirtyState.markClean();
    }

    private void clearForm()
    {
        selectedAssetId = null;
        loadedAsset = null;
        acquisitionRequest = java.util.UUID.randomUUID();
        acquisitionSource.getItems().setAll(AssetAcquisitionSource.values());
        acquisitionSource.getSelectionModel().clearSelection();
        acquisitionBank.getSelectionModel().clearSelection(); acquisitionLine.getSelectionModel().clearSelection();
        name.clear();
        acquisitionDate.setValue(LocalDate.now());
        moneyInputs.get(acquisitionCost).set(BigDecimal.ZERO);
        moneyInputs.get(salvageValue).set(BigDecimal.ZERO);
        moneyInputs.get(openingAccumulatedDepreciation).set(BigDecimal.ZERO);
        notes.clear();
        editingStatus = FixedAsset.Status.ACTIVE;
        lifecycleStatus.setText(FixedAsset.Status.ACTIVE.name());
        lifecycleReason.clear();
        setAssetFormDisabled(false);
        updateLifecycleActions(null);
        usefulLifeMonths.getSelectionModel().select(Integer.valueOf(60));
        table.getSelectionModel().clearSelection();
        updateAcquisitionControls();
        status.setText("Ready for a new fixed asset; select an explicit acquisition route.");
        dirtyState.markClean();
    }

    private void setAssetFormDisabled(boolean disabled)
    {
        for (Node field : List.of(
                name, assetAccount, accumulatedDepreciationAccount, depreciationExpenseAccount,
                fund, acquisitionDate, acquisitionCost, salvageValue, usefulLifeMonths,
                openingAccumulatedDepreciation, notes))
        {
            field.setDisable(disabled);
        }
    }

    private AssetFormSnapshot formSnapshot()
    {
        return new AssetFormSnapshot(
                name.getText(), selectedId(assetAccount), selectedId(accumulatedDepreciationAccount),
                selectedId(depreciationExpenseAccount), selectedId(fund), acquisitionDate.getValue(),
                moneyInputs.get(acquisitionCost).snapshot(), moneyInputs.get(salvageValue).snapshot(), usefulLifeMonths.getValue(),
                moneyInputs.get(openingAccumulatedDepreciation).snapshot(), notes.getText(), acquisitionSource.getValue(),
                selectedId(acquisitionBank), acquisitionLine.getValue() == null ? null : acquisitionLine.getValue().splitId());
    }

    private static Long selectedId(ComboBox<?> box)
    {
        Object value = box.getValue();
        if (value instanceof Account account)
        {
            return account.getId();
        }
        return value instanceof Fund selectedFund ? selectedFund.getId() : null;
    }

    private void reloadWithDiscardProtection()
    {
        if (!dirtyState.isDirty() || confirmDiscard())
        {
            clearForm();
            reload();
        }
    }

    private boolean confirmDiscard()
    {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Discard asset edits");
        confirmation.setHeaderText("Discard unsaved Asset Register changes?");
        confirmation.setContentText("Choose Cancel to remain in the current editor.");
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private static <T> T requireSelected(ComboBox<T> comboBox, String label)
    {
        T selected = comboBox.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            throw new IllegalArgumentException("Select " + label);
        }
        return selected;
    }

    private static BigDecimal money(String raw)
    {
        return new BigDecimal(raw == null || raw.isBlank() ? "0" : raw.trim());
    }

    private static void selectAccountById(ComboBox<Account> comboBox, Long id)
    {
        comboBox.getItems().stream().filter(a -> a.getId().equals(id)).findFirst().ifPresent(comboBox.getSelectionModel()::select);
    }

    private static void selectFundById(ComboBox<Fund> comboBox, Long id)
    {
        comboBox.getItems().stream().filter(f -> f.getId().equals(id)).findFirst().ifPresent(comboBox.getSelectionModel()::select);
    }

    static String accountLabel(Account account)
    {
        String code = account.getCode() == null ? "" : account.getCode();
        String name = account.getName() == null ? "" : account.getName();
        return code.isBlank() ? name : code + " — " + name;
    }

    static String fundLabel(Fund selectedFund)
    {
        String code = selectedFund.getCode() == null ? "" : selectedFund.getCode();
        String name = selectedFund.getName() == null ? "" : selectedFund.getName();
        return code.isBlank() ? name : code + " — " + name;
    }

    private static String activeCompanyCode()
    {
        return MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
    }

    private record AssetPanelData(
            java.util.List<Account> accounts,
            java.util.List<Fund> funds,
            java.util.List<FixedAssetView> assets,
            java.util.List<FixedAssetLifecycleEventView> lifecycleEvents,
            java.util.List<AssetAcquisitionCandidate> acquisitions)
    {
    }

    void setNameForTests(String value)
    {
        name.setText(value);
    }

    @Override public String title() { return "Asset Register"; }
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
        return busy.get() ? java.util.Set.of() : AppPanel.capabilities(AppCommand.NEW_ACTIVE, AppCommand.SAVE_ACTIVE);
    }
    @Override
    public void onNew()
    {
        if (busy.get())
        {
            status.setText("Asset operation is in progress; draft retained.");
            return;
        }
        if (!dirtyState.isDirty() || confirmDiscard())
        {
            clearForm();
        }
        else
        {
            status.setText("New asset cancelled; unsaved changes remain.");
        }
    }
    @Override public void onSave() { saveAsset(); }
    @Override
    public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        capabilitiesChanged = listener == null ? () -> {} : listener;
    }
    @Override
    public String commandResultMessage(AppCommand command)
    {
        return status.getText();
    }
    @Override public boolean hasUnsavedChanges() { return dirtyState.isDirty(); }

    /** Company display is separate from the exact editable command amount. */
    private final class ExactMoneyInput
    {
        private final TextField field;
        private BigDecimal amount;
        private boolean displaying;

        ExactMoneyInput(TextField field)
        {
            this.field = field;
            amount = companyFormat.parseMoney(field.getText());
            field.textProperty().addListener((obs, oldValue, newValue) ->
            {
                if (!displaying)
                {
                    amount = companyFormat.parseMoney(newValue);
                }
            });
            field.focusedProperty().addListener((obs, oldValue, focused) ->
            {
                if (amount != null)
                {
                    show(focused ? amount.toPlainString() : companyFormat.formatMoney(amount));
                }
            });
        }

        void set(BigDecimal value)
        {
            amount = value;
            show(companyFormat.formatMoney(value));
        }

        BigDecimal value()
        {
            if (amount == null)
            {
                throw new IllegalArgumentException("Invalid asset amount: " + field.getText());
            }
            return amount;
        }

        String snapshot()
        {
            return amount == null ? field.getText() : amount.stripTrailingZeros().toPlainString();
        }

        private void show(String text)
        {
            displaying = true;
            try
            {
                field.setText(text);
            }
            finally
            {
                displaying = false;
            }
        }
    }

    private record AssetFormSnapshot(
            String name,
            Long assetAccountId,
            Long accumulatedDepreciationAccountId,
            Long depreciationExpenseAccountId,
            Long fundId,
            LocalDate acquisitionDate,
            String acquisitionCost,
            String salvageValue,
            Integer usefulLifeMonths,
            String openingAccumulatedDepreciation,
            String notes,
            AssetAcquisitionSource acquisitionSource,
            Long acquisitionBank,
            Long acquisitionSplit)
    {
    }
}
