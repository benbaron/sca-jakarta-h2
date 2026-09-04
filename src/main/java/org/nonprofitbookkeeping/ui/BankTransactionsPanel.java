package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.ApplicationPermission;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.interchange.bank.BankReviewQueryService;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.service.BankConfigurationService;
import org.nonprofitbookkeeping.service.LedgerQueryService;
import org.nonprofitbookkeeping.service.ReviewedStatementAcceptanceService;
import org.nonprofitbookkeeping.service.TransactionReferenceDataService;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Canonical bank-ledger activity plus the deliberately separate imported
 * statement-review workflow.
 */
public class BankTransactionsPanel implements AppPanel
{
    private static final int DEFAULT_LEDGER_ROW_LIMIT = 1000;

    private final CompanyUiFormat companyFormat = CompanyUiFormat.activeCompany();
    private final BorderPane root = new BorderPane();
    private final TableView<LedgerQueryService.BankLedgerRow> ledgerTable = new TableView<>();
    private final TableView<BankReviewQueryService.ReviewRow> reviewTable = new TableView<>();
    private final Label ledgerStatus = new Label(
            "Canonical journal lines affecting configured bank accounts appear here.");
    private final Label reviewStatus = new Label(
            "Imported statement facts remain separate from the canonical ledger.");
    private final ComboBox<LedgerAccountOption> ledgerAccount = new ComboBox<>();
    private final Label exportStatus = new Label();
    private final ComboBox<BankAccountOption> exportAccount = new ComboBox<>();
    private final DatePicker exportFrom = new DatePicker();
    private final DatePicker exportThrough = new DatePicker();
    private final Button exportCsv = new Button("Export Bank CSV…");
    private final Button exportOfx = new Button("Export OFX 2.x…");
    private final Button exportQfx = new Button("Export QFX…");
    private final ProgressIndicator exportProgress = new ProgressIndicator();
    private final LedgerQueryService ledgerQuery;
    private final BankReviewQueryService reviewQuery;
    private final Supplier<String> companyCode;
    private final Supplier<BankConfigurationService> bankConfigurationService;
    private final BankStatementExportActions exportActions;
    private final Supplier<ReviewedStatementAcceptanceService> acceptanceService;
    private final Supplier<TransactionReferenceDataService> transactionReferenceData;
    private final Button acceptReviewedRow = new Button("Create Transaction from Reviewed Row…");

    public BankTransactionsPanel()
    {
        this(UiServiceRegistry.ledgerQuery(),
                UiServiceRegistry.bankReviewQuery(),
                () -> MainWindow.sharedSessionState().multiCompany().activeCompanyCode(),
                UiServiceRegistry::bankConfiguration,
                BankStatementExportActions.unavailable(),
                UiServiceRegistry::reviewedStatementAcceptance,
                UiServiceRegistry::transactionReferenceData);
    }

    BankTransactionsPanel(
            LedgerQueryService ledgerQuery,
            BankReviewQueryService reviewQuery,
            Supplier<String> companyCode,
            Supplier<BankConfigurationService> bankConfigurationService,
            BankStatementExportActions exportActions,
            Supplier<ReviewedStatementAcceptanceService> acceptanceService,
            Supplier<TransactionReferenceDataService> transactionReferenceData)
    {
        this.ledgerQuery = Objects.requireNonNull(ledgerQuery, "ledgerQuery");
        this.reviewQuery = Objects.requireNonNull(reviewQuery, "reviewQuery");
        this.companyCode = Objects.requireNonNull(companyCode, "companyCode");
        this.bankConfigurationService = Objects.requireNonNull(
                bankConfigurationService, "bankConfigurationService");
        this.exportActions = Objects.requireNonNull(exportActions, "exportActions");
        this.acceptanceService = Objects.requireNonNull(acceptanceService, "acceptanceService");
        this.transactionReferenceData = Objects.requireNonNull(transactionReferenceData, "transactionReferenceData");

        root.setPadding(new Insets(8));
        Label title = new Label("Bank Transactions");
        title.getStyleClass().add("panel-title");
        Label authority = new Label(
                "Ledger Activity is the accounting record. Statement Review contains imported bank evidence used for matching or explicit acceptance.");
        authority.setWrapText(true);
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> reload());
        root.setTop(new VBox(6, title, authority, refresh));

        Tab ledgerTab = new Tab("Ledger Activity", buildLedgerActivityPane());
        ledgerTab.setClosable(false);
        Tab statementReviewTab = new Tab("Statement Review", buildStatementReviewPane());
        statementReviewTab.setClosable(false);
        TabPane tabs = new TabPane(ledgerTab, statementReviewTab);
        tabs.setId("bankTransactionsAuthorityTabs");
        root.setCenter(tabs);

        reload();
    }

    @Override
    public String title()
    {
        return "Bank Transactions";
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

    private Node buildLedgerActivityPane()
    {
        ledgerAccount.setId("bankLedgerConfiguredAccount");
        ledgerAccount.setPrefWidth(320);
        ledgerAccount.setOnAction(event -> reloadLedgerActivity());

        Button drill = new Button("Drill to Journal");
        drill.setId("bankLedgerDrillToJournalButton");
        drill.setOnAction(event -> drillLedgerSelectionToJournal());

        HBox controls = new HBox(8,
                new Label("Configured account"), ledgerAccount, drill);
        ScrollPane controlsScroll = new ScrollPane(controls);
        controlsScroll.setFitToWidth(false);
        controlsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        controlsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        controlsScroll.setPannable(true);

        buildLedgerTable();
        VBox controlsPane = new VBox(6, controlsScroll, ledgerStatus, new Separator());
        SplitPane splitPane = new SplitPane(controlsPane, ledgerTable);
        splitPane.setId("bankLedgerActivitySplitPane");
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setDividerPositions(0.18);
        CompanySplitPaneStateBinder.bind(splitPane, "bank-transactions-ledger-activity", 0.18);
        BorderPane pane = new BorderPane(splitPane);
        pane.setPadding(new Insets(8, 0, 0, 0));
        return pane;
    }

    private Node buildStatementReviewPane()
    {
        Button drill = new Button("Drill to Journal");
        drill.setOnAction(event -> drillReviewSelectionToJournal());
        acceptReviewedRow.setId("bankTransactionsAcceptReviewedRowButton");
        acceptReviewedRow.setDisable(true);
        acceptReviewedRow.setOnAction(event -> acceptSelectedReviewedRow());
        UiPermissionGate.gate(acceptReviewedRow, ApplicationPermission.BOOKKEEPING_WRITE, "Create a transaction from a reviewed bank row");
        UiPermissionGate.gate(exportCsv, ApplicationPermission.EXPORT, "Export bank CSV");
        UiPermissionGate.gate(exportOfx, ApplicationPermission.EXPORT, "Export OFX");
        UiPermissionGate.gate(exportQfx, ApplicationPermission.EXPORT, "Export QFX");
        configureStatementExport();

        HBox exportControls = new HBox(8,
                new Label("Account"), exportAccount,
                new Label("From"), exportFrom,
                new Label("Through"), exportThrough,
                exportCsv, exportOfx, exportQfx, exportProgress);
        ScrollPane exportControlsScroll = new ScrollPane(exportControls);
        exportControlsScroll.setId("bankStatementExportControlsScroll");
        exportControlsScroll.setFitToWidth(false);
        exportControlsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        exportControlsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        exportControlsScroll.setPannable(true);

        buildReviewTable();
        VBox controlsPane = new VBox(6,
                new HBox(8, acceptReviewedRow, drill),
                reviewStatus,
                new Separator(),
                new Label("Export durable statement activity"),
                exportControlsScroll,
                exportStatus,
                new Separator());
        SplitPane splitPane = new SplitPane(controlsPane, reviewTable);
        splitPane.setId("bankStatementReviewSplitPane");
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setDividerPositions(0.34);
        CompanySplitPaneStateBinder.bind(splitPane, "bank-transactions-statement-review", 0.34);
        BorderPane pane = new BorderPane(splitPane);
        pane.setPadding(new Insets(8, 0, 0, 0));
        return pane;
    }

    private void buildLedgerTable()
    {
        TableColumn<LedgerQueryService.BankLedgerRow, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(v -> new SimpleStringProperty(formatDate(v.getValue().transactionDate())));
        TableColumn<LedgerQueryService.BankLedgerRow, String> txn = new TableColumn<>("Transaction");
        txn.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().transactionId())));
        TableColumn<LedgerQueryService.BankLedgerRow, String> configured = new TableColumn<>("Configured Account");
        configured.setCellValueFactory(v -> new SimpleStringProperty(blank(v.getValue().configuredBankAccountName())));
        TableColumn<LedgerQueryService.BankLedgerRow, String> account = new TableColumn<>("Ledger Account");
        account.setCellValueFactory(v -> new SimpleStringProperty(
                codeAndName(v.getValue().accountCode(), v.getValue().accountName())));
        TableColumn<LedgerQueryService.BankLedgerRow, String> payee = new TableColumn<>("Payee");
        payee.setCellValueFactory(v -> new SimpleStringProperty(blank(v.getValue().payee())));
        TableColumn<LedgerQueryService.BankLedgerRow, String> memo = new TableColumn<>("Memo");
        memo.setCellValueFactory(v -> new SimpleStringProperty(blank(v.getValue().memo())));
        TableColumn<LedgerQueryService.BankLedgerRow, String> fund = new TableColumn<>("Fund");
        fund.setCellValueFactory(v -> new SimpleStringProperty(
                codeAndName(v.getValue().fundCode(), v.getValue().fundName())));
        TableColumn<LedgerQueryService.BankLedgerRow, String> debit = new TableColumn<>("Debit");
        debit.setCellValueFactory(v -> new SimpleStringProperty(companyFormat.formatMoney(v.getValue().debit())));
        TableColumn<LedgerQueryService.BankLedgerRow, String> credit = new TableColumn<>("Credit");
        credit.setCellValueFactory(v -> new SimpleStringProperty(companyFormat.formatMoney(v.getValue().credit())));
        TableColumn<LedgerQueryService.BankLedgerRow, String> cleared = new TableColumn<>("Cleared");
        cleared.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().cleared() ? "Cleared" : "Uncleared"));
        TableColumn<LedgerQueryService.BankLedgerRow, String> clearedOn = new TableColumn<>("Cleared On");
        clearedOn.setCellValueFactory(v -> new SimpleStringProperty(formatDate(v.getValue().clearedOn())));

        ledgerTable.getColumns().addAll(
                date, txn, configured, account, payee, memo, fund, debit, credit, cleared, clearedOn);
        ledgerTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        ledgerTable.getSelectionModel().setCellSelectionEnabled(false);
        ledgerTable.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        ledgerTable.setPlaceholder(new Label("No canonical ledger activity for the selected configured bank account scope."));
    }

    private void buildReviewTable()
    {
        TableColumn<BankReviewQueryService.ReviewRow, String> source = new TableColumn<>("Source");
        source.setCellValueFactory(v -> new SimpleStringProperty(blank(v.getValue().sourceName())));
        TableColumn<BankReviewQueryService.ReviewRow, String> account = new TableColumn<>("Configured Account");
        account.setCellValueFactory(v -> new SimpleStringProperty(blank(v.getValue().bankAccountName())));
        TableColumn<BankReviewQueryService.ReviewRow, String> fit = new TableColumn<>("Source ID");
        fit.setCellValueFactory(v -> new SimpleStringProperty(blank(v.getValue().sourceTransactionId())));
        TableColumn<BankReviewQueryService.ReviewRow, String> posted = new TableColumn<>("Posted On");
        posted.setCellValueFactory(v -> new SimpleStringProperty(
                formatDate(v.getValue().postedDate(), v.getValue().transactionDate())));
        TableColumn<BankReviewQueryService.ReviewRow, String> amount = new TableColumn<>("Amount");
        amount.setCellValueFactory(v -> new SimpleStringProperty(companyFormat.formatMoney(v.getValue().amount())));
        TableColumn<BankReviewQueryService.ReviewRow, String> currency = new TableColumn<>("Currency");
        currency.setCellValueFactory(v -> new SimpleStringProperty(blank(v.getValue().currency())));
        TableColumn<BankReviewQueryService.ReviewRow, String> statusColumn = new TableColumn<>("Review Status");
        statusColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().status()));
        TableColumn<BankReviewQueryService.ReviewRow, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(v -> new SimpleStringProperty(blank(v.getValue().transactionType())));
        TableColumn<BankReviewQueryService.ReviewRow, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(blank(v.getValue().payeeName())));
        TableColumn<BankReviewQueryService.ReviewRow, String> memo = new TableColumn<>("Memo");
        memo.setCellValueFactory(v -> new SimpleStringProperty(blank(v.getValue().memo())));

        reviewTable.getColumns().addAll(source, account, fit, posted, amount, currency, statusColumn, type, name, memo);
        reviewTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        reviewTable.getSelectionModel().setCellSelectionEnabled(false);
        reviewTable.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        reviewTable.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<BankReviewQueryService.ReviewRow>) change -> updateAcceptanceEnablement());
        reviewTable.setPlaceholder(new Label("No durable bank review rows for the active company."));
    }

    private void reload()
    {
        String activeCompany;
        try
        {
            activeCompany = companyCode.get();
        }
        catch (RuntimeException ex)
        {
            ledgerTable.getItems().clear();
            reviewTable.getItems().clear();
            ledgerStatus.setText("Could not resolve the active company: " + UiErrors.safeMessage(ex));
            reviewStatus.setText("Could not resolve the active company: " + UiErrors.safeMessage(ex));
            return;
        }

        reloadLedgerAccounts(activeCompany);
        reloadLedgerActivity(activeCompany);
        reloadReviewRows(activeCompany);
        reloadExportAccounts(activeCompany);
    }

    private void reloadLedgerAccounts(String activeCompany)
    {
        Long selectedId = ledgerAccount.getValue() == null
                ? null
                : ledgerAccount.getValue().configuredBankAccountId();
        try
        {
            List<LedgerAccountOption> accounts = bankConfigurationService.get()
                    .listBankAccounts(activeCompany)
                    .stream()
                    .filter(account -> account.getAccount() != null)
                    .map(BankTransactionsPanel::ledgerAccountOption)
                    .toList();
            ledgerAccount.getItems().setAll(
                    java.util.stream.Stream.concat(
                                    java.util.stream.Stream.of(new LedgerAccountOption(null, "All configured bank accounts")),
                                    accounts.stream())
                            .toList());
            ledgerAccount.getItems().stream()
                    .filter(option -> Objects.equals(option.configuredBankAccountId(), selectedId))
                    .findFirst()
                    .ifPresentOrElse(
                            ledgerAccount.getSelectionModel()::select,
                            ledgerAccount.getSelectionModel()::selectFirst);
        }
        catch (RuntimeException ex)
        {
            ledgerAccount.getItems().clear();
            ledgerStatus.setText("Could not load configured bank accounts: " + UiErrors.safeMessage(ex));
        }
    }

    private void reloadLedgerActivity()
    {
        try
        {
            reloadLedgerActivity(companyCode.get());
        }
        catch (RuntimeException ex)
        {
            ledgerTable.getItems().clear();
            ledgerStatus.setText("Could not load canonical bank ledger activity: " + UiErrors.safeMessage(ex));
        }
    }

    private void reloadLedgerActivity(String activeCompany)
    {
        LedgerAccountOption selected = ledgerAccount.getValue();
        Long selectedId = selected == null ? null : selected.configuredBankAccountId();
        try
        {
            List<LedgerQueryService.BankLedgerRow> rows = ledgerQuery.listBankLedgerActivity(
                    activeCompany, selectedId, DEFAULT_LEDGER_ROW_LIMIT);
            ledgerTable.getItems().setAll(rows);
            String scope = selected == null || selected.configuredBankAccountId() == null
                    ? "all configured bank accounts"
                    : selected.label();
            ledgerStatus.setText("Loaded " + rows.size()
                    + " canonical bank ledger line(s) for " + scope + ".");
        }
        catch (RuntimeException ex)
        {
            ledgerTable.getItems().clear();
            ledgerStatus.setText("Could not load canonical bank ledger activity: " + UiErrors.safeMessage(ex));
        }
    }

    private void reloadReviewRows(String activeCompany)
    {
        try
        {
            reviewTable.getItems().setAll(reviewQuery.listRows(activeCompany));
            reviewStatus.setText("Loaded " + reviewTable.getItems().size()
                    + " imported statement review row(s) for " + activeCompany + ".");
        }
        catch (RuntimeException ex)
        {
            reviewTable.getItems().clear();
            reviewStatus.setText("Could not load imported statement review rows: " + UiErrors.safeMessage(ex));
        }
    }

    private void configureStatementExport()
    {
        LocalDate activeDate = ActivePeriodContext.get();
        exportFrom.setValue(activeDate.withDayOfMonth(1));
        exportThrough.setValue(activeDate);
        exportAccount.setPrefWidth(260);
        exportCsv.disableProperty().bind(exportActions.busyProperty());
        exportOfx.disableProperty().bind(exportActions.busyProperty());
        exportQfx.disableProperty().bind(exportActions.busyProperty());
        exportProgress.setId("bankStatementExportProgress");
        exportProgress.setMaxSize(22.0, 22.0);
        exportProgress.visibleProperty().bind(exportActions.busyProperty());
        exportProgress.managedProperty().bind(exportProgress.visibleProperty());
        exportCsv.setOnAction(event -> requestStatementExport(BankStatementExportFormat.NORMALIZED_CSV));
        exportOfx.setOnAction(event -> requestStatementExport(BankStatementExportFormat.OFX_2_XML));
        exportQfx.setOnAction(event -> requestStatementExport(BankStatementExportFormat.QFX_2_XML));
        exportStatus.textProperty().bind(exportActions.statusProperty());
    }

    private void reloadExportAccounts(String activeCompany)
    {
        Long selectedId = exportAccount.getValue() == null ? null : exportAccount.getValue().id();
        try
        {
            List<BankAccountOption> accounts = bankConfigurationService.get()
                    .listBankAccounts(activeCompany)
                    .stream()
                    .filter(CompanyBankAccount::isActive)
                    .map(BankTransactionsPanel::accountOption)
                    .toList();
            exportAccount.getItems().setAll(accounts);
            accounts.stream()
                    .filter(option -> Objects.equals(option.id(), selectedId))
                    .findFirst()
                    .ifPresentOrElse(
                            exportAccount.getSelectionModel()::select,
                            () ->
                            {
                                if (accounts.size() == 1)
                                {
                                    exportAccount.getSelectionModel().selectFirst();
                                }
                            });
        }
        catch (RuntimeException ex)
        {
            exportAccount.getItems().clear();
            reviewStatus.setText("Could not load statement-export accounts: " + UiErrors.safeMessage(ex));
        }
    }

    private void requestStatementExport(BankStatementExportFormat format)
    {
        BankAccountOption account = exportAccount.getValue();
        if (account == null)
        {
            reviewStatus.setText("Select one configured bank account before exporting.");
            return;
        }
        LocalDate fromDate = exportFrom.getValue();
        LocalDate throughDate = exportThrough.getValue();
        if (fromDate == null || throughDate == null)
        {
            reviewStatus.setText("Choose both export dates.");
            return;
        }
        exportActions.requestExport(account.id(), fromDate, throughDate, format);
    }

    private void updateAcceptanceEnablement()
    {
        List<BankReviewQueryService.ReviewRow> selected = selectedReviewRows();
        boolean enabled = selected.size() == 1
                && "IMPORTED".equals(selected.get(0).status())
                && selected.get(0).matchedTransactionId() == null
                && selected.get(0).acceptedTransactionId() == null;
        acceptReviewedRow.setDisable(!enabled);
    }

    private void acceptSelectedReviewedRow()
    {
        List<BankReviewQueryService.ReviewRow> selected = selectedReviewRows();
        if (selected.size() != 1)
        {
            reviewStatus.setText("Select exactly one unmatched imported review row to create a transaction.");
            return;
        }
        BankReviewQueryService.ReviewRow row = selected.get(0);
        try
        {
            ReviewedStatementAcceptanceService acceptanceService = this.acceptanceService.get();
            ReviewedStatementAcceptanceService.AcceptancePreview preview =
                    acceptanceService.preview(companyCode.get(), row.statementLineId());
            if (!preview.eligible())
            {
                reviewStatus.setText("Reviewed row cannot be accepted: " + preview.eligibilityMessage());
                return;
            }
            TransactionLineEditorModel.ReferenceData references =
                    transactionReferenceData.get().loadActiveReferenceData();
            ReviewedStatementAcceptanceDialog dialog = new ReviewedStatementAcceptanceDialog(preview, references);
            dialog.showAndWait().ifPresent(draft -> commitAcceptance(preview, draft));
        }
        catch (RuntimeException ex)
        {
            reviewStatus.setText("Could not prepare reviewed-row acceptance: " + UiErrors.safeMessage(ex));
        }
    }

    private void commitAcceptance(
            ReviewedStatementAcceptanceService.AcceptancePreview preview,
            ReviewedStatementAcceptanceDialog.AcceptanceDraft draft)
    {
        reviewStatus.setText("Creating canonical transaction from reviewed row " + preview.statementLineId() + "...");
        UiAsync.run(
                "bank-review-accept-" + preview.statementLineId(),
                () -> {
                    ReviewedStatementAcceptanceService acceptanceService = this.acceptanceService.get();
                    return acceptanceService.accept(
                            preview, draft.command(), draft.probableDuplicateConfirmed(),
                            DesktopActorIdentity.current());
                },
                result -> {
                    reload();
                    reviewStatus.setText(result.message());
                    DrillThroughCoordinator.openLedgerWithContext(
                            "Accepted reviewed bank row " + result.statementLineId()
                                    + " → transaction " + result.transactionId());
                },
                ex -> {
                    reload();
                    reviewStatus.setText("Reviewed-row acceptance failed; no partial ledger acceptance was kept: "
                            + UiErrors.safeMessage(ex));
                });
    }

    private void drillLedgerSelectionToJournal()
    {
        List<LedgerQueryService.BankLedgerRow> selected =
                List.copyOf(ledgerTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty())
        {
            ledgerStatus.setText("Select at least one canonical bank ledger line to drill into Journal.");
            return;
        }
        LedgerQueryService.BankLedgerRow first = selected.get(0);
        DrillThroughCoordinator.openLedgerWithContext(
                "Bank ledger split " + first.splitId()
                        + " → transaction " + first.transactionId()
                        + " (selected=" + selected.size() + ")");
    }

    private void drillReviewSelectionToJournal()
    {
        List<BankReviewQueryService.ReviewRow> selected = selectedReviewRows();
        if (selected.isEmpty())
        {
            reviewStatus.setText("Select at least one statement review row to drill into Journal.");
            return;
        }
        BankReviewQueryService.ReviewRow first = selected.get(0);
        Long transactionId = first.matchedTransactionId() != null
                ? first.matchedTransactionId()
                : first.acceptedTransactionId();
        if (transactionId == null)
        {
            reviewStatus.setText("Selected statement review row is not linked to a canonical ledger transaction.");
            return;
        }
        DrillThroughCoordinator.openLedgerWithContext("Bank review row " + first.statementLineId()
                + " → transaction " + transactionId
                + " (selected=" + selected.size() + ")");
    }

    private List<BankReviewQueryService.ReviewRow> selectedReviewRows()
    {
        return List.copyOf(reviewTable.getSelectionModel().getSelectedItems());
    }

    private String formatDate(LocalDate value)
    {
        return value == null ? "" : companyFormat.formatDate(value);
    }

    private String formatDate(LocalDate posted, LocalDate transaction)
    {
        return formatDate(posted == null ? transaction : posted);
    }

    private static LedgerAccountOption ledgerAccountOption(CompanyBankAccount account)
    {
        String label = accountOption(account).label();
        if (!account.isActive())
        {
            label += " (inactive)";
        }
        return new LedgerAccountOption(account.getId(), label);
    }

    private static BankAccountOption accountOption(CompanyBankAccount account)
    {
        String masked = blank(account.getMaskedAccountNumber());
        if (masked.isBlank())
        {
            masked = account.getLastFour() == null || account.getLastFour().isBlank()
                    ? ""
                    : " ••••" + account.getLastFour().strip();
        }
        return new BankAccountOption(account.getId(), account.getName() + masked);
    }

    private static String codeAndName(String code, String name)
    {
        String cleanCode = blank(code);
        String cleanName = blank(name);
        if (cleanCode.isBlank())
        {
            return cleanName;
        }
        if (cleanName.isBlank())
        {
            return cleanCode;
        }
        return cleanCode + " — " + cleanName;
    }

    private static String blank(String value)
    {
        return value == null ? "" : value;
    }

    private record LedgerAccountOption(Long configuredBankAccountId, String label)
    {
        private LedgerAccountOption
        {
            label = Objects.requireNonNull(label, "label");
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    private record BankAccountOption(long id, String label)
    {
        private BankAccountOption
        {
            label = Objects.requireNonNull(label, "label");
        }

        @Override
        public String toString()
        {
            return label;
        }
    }
}
