package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.interchange.bank.BankReviewQueryService;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.service.BankConfigurationService;
import org.nonprofitbookkeeping.service.ReviewedStatementAcceptanceService;
import org.nonprofitbookkeeping.service.TransactionReferenceDataService;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * BankTransactionsPanel component.
 */
public class BankTransactionsPanel implements AppPanel
{
    private final CompanyUiFormat companyFormat = CompanyUiFormat.activeCompany();
    private final BorderPane root = new BorderPane();
    private final TableView<BankReviewQueryService.ReviewRow> table = new TableView<>();
    private final Label status = new Label("Durable bank review rows for the active company appear here.");
    private final Label exportStatus = new Label();
    private final ComboBox<BankAccountOption> exportAccount = new ComboBox<>();
    private final DatePicker exportFrom = new DatePicker();
    private final DatePicker exportThrough = new DatePicker();
    private final Button exportCsv = new Button("Export Bank CSV…");
    private final Button exportOfx = new Button("Export OFX 2.x…");
    private final Button exportQfx = new Button("Export QFX…");
    private final ProgressIndicator exportProgress = new ProgressIndicator();
    private final BankReviewQueryService reviewQuery;
    private final Supplier<String> companyCode;
    private final Supplier<BankConfigurationService> bankConfigurationService;
    private final BankStatementExportActions exportActions;
    private final Supplier<ReviewedStatementAcceptanceService> acceptanceService;
    private final Supplier<TransactionReferenceDataService> transactionReferenceData;
    private final Button acceptReviewedRow = new Button("Create Transaction from Reviewed Row…");

    public BankTransactionsPanel()
    {
        this(UiServiceRegistry.bankReviewQuery(), () ->
                        MainWindow.sharedSessionState().multiCompany().activeCompanyCode(),
                UiServiceRegistry::bankConfiguration,
                BankStatementExportActions.unavailable(),
                UiServiceRegistry::reviewedStatementAcceptance,
                UiServiceRegistry::transactionReferenceData);
    }

    BankTransactionsPanel(
            BankReviewQueryService reviewQuery,
            Supplier<String> companyCode,
            Supplier<BankConfigurationService> bankConfigurationService,
            BankStatementExportActions exportActions,
            Supplier<ReviewedStatementAcceptanceService> acceptanceService,
            Supplier<TransactionReferenceDataService> transactionReferenceData)
    {
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

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        Button drill = new Button("Drill to Ledger");
        drill.setOnAction(e -> drillSelectedToLedger());
        acceptReviewedRow.setId("bankTransactionsAcceptReviewedRowButton");
        acceptReviewedRow.setDisable(true);
        acceptReviewedRow.setOnAction(e -> acceptSelectedReviewedRow());
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

        root.setTop(new VBox(6,
                title,
                new HBox(8, refresh, acceptReviewedRow, drill),
                status,
                new Separator(),
                new Label("Export durable statement activity"),
                exportControlsScroll,
                exportStatus,
                new Separator()));
        buildTable();
        root.setCenter(table);

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

    private void buildTable()
    {
        TableColumn<BankReviewQueryService.ReviewRow, String> source = new TableColumn<>("Source");
        source.setCellValueFactory(v -> new SimpleStringProperty(blank(v.getValue().sourceName())));
        TableColumn<BankReviewQueryService.ReviewRow, String> account = new TableColumn<>("Configured Account");
        account.setCellValueFactory(v -> new SimpleStringProperty(blank(v.getValue().bankAccountName())));
        TableColumn<BankReviewQueryService.ReviewRow, String> fit = new TableColumn<>("Source ID");
        fit.setCellValueFactory(v -> new SimpleStringProperty(blank(v.getValue().sourceTransactionId())));
        TableColumn<BankReviewQueryService.ReviewRow, String> posted = new TableColumn<>("Posted On");
        posted.setCellValueFactory(v -> new SimpleStringProperty(formatDate(v.getValue().postedDate(), v.getValue().transactionDate())));
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

        table.getColumns().addAll(source, account, fit, posted, amount, currency, statusColumn, type, name, memo);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.getSelectionModel().setCellSelectionEnabled(false);
        table.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        table.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<BankReviewQueryService.ReviewRow>) change -> updateAcceptanceEnablement());
        table.setPlaceholder(new Label("No durable bank review rows for the active company."));
    }

    private void reload()
    {
        try
        {
            String activeCompany = companyCode.get();
            table.getItems().setAll(reviewQuery.listRows(activeCompany));
            reloadExportAccounts(activeCompany);
            status.setText("Loaded " + table.getItems().size()
                    + " durable bank review row(s) for " + activeCompany + ".");
        }
        catch (RuntimeException ex)
        {
            table.getItems().clear();
            status.setText("Could not load durable bank review rows: " + UiErrors.safeMessage(ex));
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

    private void requestStatementExport(BankStatementExportFormat format)
    {
        BankAccountOption account = exportAccount.getValue();
        if (account == null)
        {
            status.setText("Select one configured bank account before exporting.");
            return;
        }
        LocalDate fromDate = exportFrom.getValue();
        LocalDate throughDate = exportThrough.getValue();
        if (fromDate == null || throughDate == null)
        {
            status.setText("Choose both export dates.");
            return;
        }
        exportActions.requestExport(account.id(), fromDate, throughDate, format);
    }

    private void updateAcceptanceEnablement()
    {
        List<BankReviewQueryService.ReviewRow> selected = selectedRows();
        boolean enabled = selected.size() == 1
                && "IMPORTED".equals(selected.get(0).status())
                && selected.get(0).matchedTransactionId() == null
                && selected.get(0).acceptedTransactionId() == null;
        acceptReviewedRow.setDisable(!enabled);
    }

    private void acceptSelectedReviewedRow()
    {
        List<BankReviewQueryService.ReviewRow> selected = selectedRows();
        if (selected.size() != 1)
        {
            status.setText("Select exactly one unmatched imported review row to create a transaction.");
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
                status.setText("Reviewed row cannot be accepted: " + preview.eligibilityMessage());
                return;
            }
            TransactionLineEditorModel.ReferenceData references =
                    transactionReferenceData.get().loadActiveReferenceData();
            ReviewedStatementAcceptanceDialog dialog = new ReviewedStatementAcceptanceDialog(preview, references);
            dialog.showAndWait().ifPresent(draft -> commitAcceptance(preview, draft));
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not prepare reviewed-row acceptance: " + UiErrors.safeMessage(ex));
        }
    }

    private void commitAcceptance(
            ReviewedStatementAcceptanceService.AcceptancePreview preview,
            ReviewedStatementAcceptanceDialog.AcceptanceDraft draft)
    {
        status.setText("Creating canonical transaction from reviewed row " + preview.statementLineId() + "...");
        UiAsync.run(
                "bank-review-accept-" + preview.statementLineId(),
                () -> {
                    ReviewedStatementAcceptanceService acceptanceService = this.acceptanceService.get();
                    return acceptanceService.accept(
                            preview, draft.command(), draft.probableDuplicateConfirmed(), "ui");
                },
                result -> {
                    reload();
                    status.setText(result.message());
                    DrillThroughCoordinator.openLedgerWithContext(
                            "Accepted reviewed bank row " + result.statementLineId()
                                    + " → transaction " + result.transactionId());
                },
                ex -> {
                    reload();
                    status.setText("Reviewed-row acceptance failed; no partial ledger acceptance was kept: "
                            + UiErrors.safeMessage(ex));
                });
    }

    private void drillSelectedToLedger()
    {
        List<BankReviewQueryService.ReviewRow> selected = selectedRows();
        if (selected.isEmpty())
        {
            status.setText("Select at least one bank transaction to drill into the ledger.");
            return;
        }
        BankReviewQueryService.ReviewRow first = selected.get(0);
        Long transactionId = first.matchedTransactionId() != null
                ? first.matchedTransactionId()
                : first.acceptedTransactionId();
        if (transactionId == null)
        {
            status.setText("Selected durable review row is not linked to a canonical ledger transaction.");
            return;
        }
        DrillThroughCoordinator.openLedgerWithContext("Bank review row " + first.statementLineId()
                + " → transaction " + transactionId
                + " (selected=" + selected.size() + ")");
    }

    private List<BankReviewQueryService.ReviewRow> selectedRows()
    {
        return List.copyOf(table.getSelectionModel().getSelectedItems());
    }

    private String formatDate(LocalDate posted, LocalDate transaction)
    {
        LocalDate value = posted == null ? transaction : posted;
        return value == null ? "" : companyFormat.formatDate(value);
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

    private static String blank(String value)
    {
        return value == null ? "" : value;
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
