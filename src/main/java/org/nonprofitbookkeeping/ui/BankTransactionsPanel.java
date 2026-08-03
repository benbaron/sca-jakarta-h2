package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.nonprofitbookkeeping.interchange.bank.BankReviewQueryService;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.service.BankTransactionRecord;
import org.nonprofitbookkeeping.service.ImportExportOrchestrationService;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private final ImportExportOrchestrationService importExportService = new ImportExportOrchestrationService();
    private final BankReviewQueryService reviewQuery;
    private final Supplier<String> companyCode;

    public BankTransactionsPanel()
    {
        this(UiServiceRegistry.bankReviewQuery(), () ->
                MainWindow.sharedSessionState().multiCompany().activeCompanyCode());
    }

    BankTransactionsPanel(BankReviewQueryService reviewQuery, Supplier<String> companyCode)
    {
        this.reviewQuery = Objects.requireNonNull(reviewQuery, "reviewQuery");
        this.companyCode = Objects.requireNonNull(companyCode, "companyCode");
        root.setPadding(new Insets(8));

        Label title = new Label("Bank Transactions");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        Button drill = new Button("Drill to Ledger");
        drill.setOnAction(e -> drillSelectedToLedger());
        Button exportSelected = new Button("Export Selected");
        exportSelected.setOnAction(e -> exportSelectedRows());

        root.setTop(new VBox(6, title, new HBox(8, refresh, drill, exportSelected), status, new Separator()));
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
        table.setPlaceholder(new Label("No durable bank review rows for the active company."));
    }

    private void reload()
    {
        try
        {
            table.getItems().setAll(reviewQuery.listRows(companyCode.get()));
            status.setText("Loaded " + table.getItems().size()
                    + " durable bank review row(s) for " + companyCode.get() + ".");
        }
        catch (RuntimeException ex)
        {
            table.getItems().clear();
            status.setText("Could not load durable bank review rows: " + UiErrors.safeMessage(ex));
        }
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
        if (first.matchedTransactionId() == null)
        {
            status.setText("Selected durable review row is not matched to a canonical ledger transaction.");
            return;
        }
        DrillThroughCoordinator.openLedgerWithContext("Matched bank review row " + first.statementLineId()
                + " → transaction " + first.matchedTransactionId()
                + " (selected=" + selected.size() + ")");
    }

    private void exportSelectedRows()
    {
        List<BankReviewQueryService.ReviewRow> selected = selectedRows();
        if (selected.isEmpty())
        {
            status.setText("Select at least one bank transaction to export.");
            return;
        }

        if (root.getScene() == null || root.getScene().getWindow() == null)
        {
            status.setText("Export unavailable: window is not ready.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Selected Bank Transactions");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("OFX/QFX", "*.ofx", "*.qfx"));
        File selectedFile = chooser.showSaveDialog(root.getScene().getWindow());
        if (selectedFile == null)
        {
            status.setText("Export cancelled.");
            return;
        }

        Path path = selectedFile.toPath();
        String lower = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        BankingDataFormat format = lower.endsWith(".qfx") ? BankingDataFormat.QFX : BankingDataFormat.OFX;
        try
        {
            List<BankTransactionRecord> exportRows = selected.stream()
                    .map(BankTransactionsPanel::exportRow)
                    .toList();
            importExportService.exportBankDataFile(format, exportRows, path);
            status.setText("Exported " + selected.size() + " selected bank transaction(s) to " + path.getFileName() + ".");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not export selected bank transactions: " + UiErrors.safeMessage(ex));
        }
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

    private static BankTransactionRecord exportRow(BankReviewQueryService.ReviewRow row)
    {
        LocalDate date = row.postedDate() == null ? row.transactionDate() : row.postedDate();
        return new BankTransactionRecord(
                blank(row.sourceTransactionId()),
                date == null ? "" : date.format(DateTimeFormatter.BASIC_ISO_DATE),
                row.amount(), blank(row.transactionType()), blank(row.payeeName()), blank(row.memo()));
    }

    private static String blank(String value)
    {
        return value == null ? "" : value;
    }
}
