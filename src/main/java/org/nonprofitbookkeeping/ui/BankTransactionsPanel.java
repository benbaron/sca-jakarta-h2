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
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.service.BankTransactionRecord;
import org.nonprofitbookkeeping.service.ImportExportOrchestrationService;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * BankTransactionsPanel component.
 */
public class BankTransactionsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<BankTransactionRecord> table = new TableView<>();
    private final Label status = new Label("Imported bank transactions for the active session appear here.");
    private final ImportExportOrchestrationService importExportService = new ImportExportOrchestrationService();

    public BankTransactionsPanel()
    {
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
        TableColumn<BankTransactionRecord, String> fit = new TableColumn<>("FITID");
        fit.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().fitId()));
        TableColumn<BankTransactionRecord, String> posted = new TableColumn<>("Posted On");
        posted.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().postedOn()));
        TableColumn<BankTransactionRecord, String> amount = new TableColumn<>("Amount");
        amount.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().amount().toPlainString()));
        TableColumn<BankTransactionRecord, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().transactionType()));
        TableColumn<BankTransactionRecord, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().name()));
        TableColumn<BankTransactionRecord, String> memo = new TableColumn<>("Memo");
        memo.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().memo()));

        table.getColumns().addAll(fit, posted, amount, type, name, memo);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getSelectionModel().setCellSelectionEnabled(false);
        table.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        table.setPlaceholder(new Label("No bank transactions imported yet."));
    }

    private void reload()
    {
        table.getItems().setAll(UiWorkspaceDataStore.bankTransactions());
        status.setText("Loaded " + table.getItems().size() + " bank transaction row(s).");
    }

    private void drillSelectedToLedger()
    {
        List<BankTransactionRecord> selected = selectedRows();
        if (selected.isEmpty())
        {
            status.setText("Select at least one bank transaction to drill into the ledger.");
            return;
        }
        DrillThroughCoordinator.openLedgerWithContext("Bank transaction drill-through: " + selected.get(0).fitId()
                + " on " + LocalDate.now() + " (selected=" + selected.size() + ")");
    }

    private void exportSelectedRows()
    {
        List<BankTransactionRecord> selected = selectedRows();
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
            importExportService.exportBankDataFile(format, selected, path);
            UiWorkspaceDataStore.appendJob(new UiWorkspaceDataStore.ImportExportJob(
                    java.time.LocalDateTime.now(),
                    "EXPORT_BANK_SELECTED",
                    "(panel selection)",
                    path.toString(),
                    format,
                    0,
                    selected.size(),
                    "SUCCESS",
                    ""));
            status.setText("Exported " + selected.size() + " selected bank transaction(s) to " + path.getFileName() + ".");
        }
        catch (RuntimeException ex)
        {
            UiWorkspaceDataStore.appendJob(new UiWorkspaceDataStore.ImportExportJob(
                    java.time.LocalDateTime.now(),
                    "EXPORT_BANK_SELECTED",
                    "(panel selection)",
                    path.toString(),
                    format,
                    0,
                    selected.size(),
                    "FAILED",
                    UiErrors.safeMessage(ex)));
            status.setText("Could not export selected bank transactions: " + UiErrors.safeMessage(ex));
        }
    }

    private List<BankTransactionRecord> selectedRows()
    {
        return List.copyOf(table.getSelectionModel().getSelectedItems());
    }
}
