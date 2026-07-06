package org.nonprofitbookkeeping.ui;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.geometry.Orientation;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.LedgerQueryService;
import org.nonprofitbookkeeping.service.AccountingJournalProjection;
import org.nonprofitbookkeeping.service.TransactionView;
import org.nonprofitbookkeeping.model.CorrectionMethod;

import java.time.LocalDate;

/**
 * Represents the LedgerRegisterPanel component in the nonprofit bookkeeping application.
 */
public class LedgerRegisterPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<Row> txnTable = new TableView<>();
    private final Label status = new Label();
    private final Label drillContext = new Label();
    private final TextArea details = new TextArea();
    private final TextField fromDate = new TextField();
    private final TextField toDate = new TextField();
    private final TextField searchText = new TextField();

    public LedgerRegisterPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Ledger Register");
        Label range = new Label();
        range.textProperty().bind(Bindings.createStringBinding(() -> "Date Range: " + DateRangeContext.get(), DateRangeContext.selectedProperty()));
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setId("ledgerRegisterRefreshButton");
        Button newTransaction = new Button("New");
        newTransaction.setId("ledgerRegisterNewButton");
        Button inspect = new Button("Inspect Journal");
        inspect.setId("ledgerRegisterInspectJournalButton");
        Button openEditor = new Button("Open Selected");
        openEditor.setId("ledgerRegisterOpenSelectedButton");
        Button deleteCurrent = new Button("Delete Current Line");
        deleteCurrent.setId("ledgerRegisterDeleteCurrentLineButton");
        txnTable.setId("ledgerRegisterTransactionTable");
        status.setId("ledgerRegisterStatusLabel");
        details.setId("ledgerRegisterJournalDetails");
        fromDate.setId("ledgerRegisterFromDateField");
        toDate.setId("ledgerRegisterToDateField");
        searchText.setId("ledgerRegisterSearchTextField");
        fromDate.setPromptText("From YYYY-MM-DD");
        toDate.setPromptText("To YYYY-MM-DD");
        searchText.setPromptText("Memo or payee");
        HBox filters = new HBox(8, new Label("From"), fromDate, new Label("To"), toDate, new Label("Search"), searchText);
        HBox actions = new HBox(8, refresh, newTransaction, openEditor, inspect, deleteCurrent);

        VBox header = new VBox(6, title, range, filters, actions, drillContext, status, new Separator());
        root.setTop(header);

        buildTable();

        details.setEditable(false);
        details.setWrapText(false);
        details.setPrefRowCount(8);

        VBox registerPane = new VBox(6, txnTable);
        VBox.setVgrow(txnTable, Priority.ALWAYS);
        VBox journalPane = new VBox(6, new Label("Transaction journal details"), details);
        VBox.setVgrow(details, Priority.ALWAYS);
        SplitPane middle = new SplitPane(registerPane, journalPane);
        middle.setId("ledgerRegisterMiddleSplitPane");
        middle.setOrientation(Orientation.VERTICAL);
        middle.setDividerPositions(0.72);
        root.setCenter(middle);

        openEditor.disableProperty().bind(Bindings.size(txnTable.getSelectionModel().getSelectedItems()).isNotEqualTo(1));

        refresh.setOnAction(e -> reload());
        newTransaction.setOnAction(e -> openNewTransaction());
        inspect.setOnAction(e -> inspectSelected());
        openEditor.setOnAction(e -> openSelectedInEditor());
        deleteCurrent.setOnAction(e -> deleteSelectedLine());

        txnTable.setRowFactory(tv -> {
            TableRow<Row> r = new TableRow<>();
            r.setOnMouseClicked(e -> {
                if (r.isEmpty())
                {
                    return;
                }
                if (e.getClickCount() == 2 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY)
                {
                    openRowInEditor(r.getItem());
                }
            });
            return r;
        });

        reload();
    }

    private void buildTable()
    {
        txnTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        txnTable.getColumns().add(col("Date", Row::date));
        txnTable.getColumns().add(col("Payee", Row::payee));
        txnTable.getColumns().add(col("Memo", Row::memo));
        txnTable.getColumns().add(col("Bank", Row::bank));
        txnTable.getColumns().add(col("Splits", Row::splitCount));
        txnTable.getColumns().add(col("Status", Row::status));
        txnTable.setPlaceholder(new Label("No transactions found yet. Post a transaction to populate the ledger register."));
    }

    private TableColumn<Row, String> col(String name, java.util.function.Function<Row, String> getter)
    {
        TableColumn<Row, String> c = new TableColumn<>(name);
        c.setCellValueFactory(v -> new SimpleStringProperty(getter.apply(v.getValue())));
        return c;
    }

    private void reload()
    {
        String context = DrillThroughCoordinator.consumeContext();
        if (!context.isBlank())
        {
            drillContext.setText(context);
            UiDebug.log("ledger-register", "Reload received drill-through context '" + context + "'.");
        }
        status.setText("Loading ledger transactions...");
        UiDebug.log("ledger-register", "Reload requested with from='" + fromDate.getText()
                + "', to='" + toDate.getText() + "', search='" + searchText.getText() + "'.");
        UiAsync.run("ledger-register-load",
                () -> UiServiceRegistry.transactionEntry().search(parseDateOrNull(fromDate.getText()), parseDateOrNull(toDate.getText()), searchText.getText(), 250),
                rows -> {
                    txnTable.getItems().setAll(rows.stream().map(LedgerRegisterPanel::toRow).toList());
                    status.setText("Loaded " + rows.size() + " transaction(s).");
                    details.clear();
                    UiDebug.log("ledger-register", "Reload loaded " + rows.size() + " transaction(s).");
                },
                ex -> {
                    status.setText("Failed to load ledger transactions: " + UiErrors.safeMessage(ex));
                    details.clear();
                    UiDebug.log("ledger-register", "Reload failed: " + UiErrors.safeMessage(ex));
                });
    }

    static Row toRow(LedgerQueryService.LedgerRow row)
    {
        return new Row(
                row.id(),
                String.valueOf(row.date()),
                row.payee().isBlank() ? "(none)" : row.payee(),
                row.memo().isBlank() ? "(none)" : row.memo(),
                row.bank().isBlank() ? "(none)" : row.bank(),
                String.valueOf(row.splitCount()),
                "Posted");
    }

    static Row toRow(TransactionView view)
    {
        return new Row(
                view.id(),
                String.valueOf(view.date()),
                blankToNone(view.payeeName()),
                blankToNone(view.memo()),
                blankToNone(view.bankAccountName()),
                String.valueOf(view.lines().size()),
                view.status() == null || view.status().isBlank() ? "ENTERED" : view.status());
    }

    private void inspectSelected()
    {
        Row sel = txnTable.getSelectionModel().getSelectedItem();
        if (sel != null)
        {
            inspectRow(sel);
        }
        else
        {
            status.setText("Select a ledger transaction before inspecting its journal.");
            details.setText("No transaction selected.");
            UiDebug.log("ledger-register", "Inspect Journal requested without a selected row.");
        }
    }

    private void openNewTransaction()
    {
        UiDebug.log("ledger-register", "New requested; opening Transaction Editor in New mode.");
        DrillThroughCoordinator.openTransactionEditorWithContext(newEditorContext());
        status.setText("Opened Transaction Editor in New mode.");
    }

    private void openSelectedInEditor()
    {
        Row sel = txnTable.getSelectionModel().getSelectedItem();
        if (sel != null)
        {
            openRowInEditor(sel);
        }
        else
        {
            status.setText("Select a ledger transaction before opening it in Transaction Editor.");
            UiDebug.log("ledger-register", "Open Selected requested without exactly one selected row.");
        }
    }

    private void deleteSelectedLine()
    {
        Row sel = txnTable.getSelectionModel().getSelectedItem();
        if (sel == null)
        {
            status.setText("Select a ledger transaction before deleting the current line.");
            UiDebug.log("ledger-register", "Delete Current Line requested without a selected row.");
            return;
        }

        CorrectionMethod method = MainWindow.sharedSessionState().preferences().correctionMethod();
        UiDebug.log("ledger-register", "Delete Current Line requested for Txn #" + sel.id()
                + " using correction method " + method + ".");
        if (method == CorrectionMethod.DIRECT_EDIT)
        {
            if (!confirm("Delete transaction #" + sel.id() + "?",
                    "This removes the selected entered transaction after period and reconciliation checks and writes an audit snapshot."))
            {
                status.setText("Delete cancelled for Txn #" + sel.id() + ".");
                UiDebug.log("ledger-register", "Direct delete cancelled for Txn #" + sel.id() + ".");
                return;
            }
            status.setText("Deleting Txn #" + sel.id() + "...");
            UiAsync.run("ledger-register-delete-" + sel.id(), () -> {
                        UiServiceRegistry.transactionCorrection().delete(sel.id(), "ui", "Deleted from Ledger Register");
                        return sel.id();
                    },
                    id -> {
                        status.setText("Deleted Txn #" + id + ".");
                        details.clear();
                        UiDebug.log("ledger-register", "Direct delete completed for Txn #" + id + ".");
                        reload();
                    },
                    ex -> {
                        status.setText("Delete failed for Txn #" + sel.id() + ": " + UiErrors.safeMessage(ex));
                        UiDebug.log("ledger-register", "Direct delete failed for Txn #" + sel.id()
                                + ": " + UiErrors.safeMessage(ex));
                    });
        }
        else
        {
            if (!confirm("Reverse transaction #" + sel.id() + "?",
                    "Current correction settings do not allow hard deletion. A reversing entry will be created using the active period date."))
            {
                status.setText("Reversal cancelled for Txn #" + sel.id() + ".");
                UiDebug.log("ledger-register", "Reversal cancelled for Txn #" + sel.id() + ".");
                return;
            }
            status.setText("Creating reversing entry for Txn #" + sel.id() + "...");
            UiAsync.run("ledger-register-reverse-" + sel.id(),
                    () -> UiServiceRegistry.transactionCorrection().reverse(sel.id(), ActivePeriodContext.get(), "ui", "Reversed from Ledger Register delete action", false),
                    result -> {
                        status.setText("Created reversing Txn #" + result.reversalTransactionId() + " for original Txn #" + sel.id() + ".");
                        details.clear();
                        UiDebug.log("ledger-register", "Reversal completed for Txn #" + sel.id()
                                + " with reversal Txn #" + result.reversalTransactionId() + ".");
                        reload();
                    },
                    ex -> {
                        status.setText("Reversal failed for Txn #" + sel.id() + ": " + UiErrors.safeMessage(ex));
                        UiDebug.log("ledger-register", "Reversal failed for Txn #" + sel.id()
                                + ": " + UiErrors.safeMessage(ex));
                    });
        }
    }

    private boolean confirm(String header, String content)
    {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("Confirm ledger action");
        alert.setHeaderText(header);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void openRowInEditor(Row row)
    {
        String context = editorContext(row.id());
        UiDebug.log("ledger-register", "Open Selected requested for Txn #"
                + row.id() + " with context '" + context + "'.");
        DrillThroughCoordinator.openTransactionEditorWithContext(context);
        status.setText("Opened Txn #" + row.id() + " in Transaction Editor.");
    }

    static String editorContext(long transactionId)
    {
        return "Edit transaction Txn #" + transactionId;
    }

    static String newEditorContext()
    {
        return "New transaction";
    }

    private static LocalDate parseDateOrNull(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        return LocalDate.parse(value.trim());
    }

    private static String blankToNone(String value)
    {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    private void inspectRow(Row row)
    {
        status.setText("Loading journal details for Txn #" + row.id() + "...");
        UiDebug.log("ledger-register", "Inspect Journal requested for Txn #" + row.id() + ".");
        UiAsync.run("ledger-journal-inspect-" + row.id(),
                () -> UiServiceRegistry.transactionEntry().journalView(row.id()),
                projection -> {
                    details.setText(LedgerRegisterPanel.renderJournal(row, projection));
                    status.setText("Loaded journal details for Txn #" + row.id() + ".");
                    UiDebug.log("ledger-register", "Inspect Journal loaded " + projection.lines().size()
                            + " line(s) for Txn #" + row.id() + ".");
                },
                ex -> {
                    details.setText("Could not load journal for txn " + row.id() + ": " + UiErrors.safeMessage(ex));
                    status.setText("Journal inspection failed for Txn #" + row.id() + ".");
                    UiDebug.log("ledger-register", "Inspect Journal failed for Txn #" + row.id()
                            + ": " + UiErrors.safeMessage(ex));
                });
    }

    static String renderJournal(Row row, AccountingJournalProjection projection)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Txn #").append(row.id())
                .append(" | Date ").append(row.date())
                .append(" | Payee ").append(row.payee())
                .append("\nMemo: ").append(row.memo())
                .append("\n\n");

        for (AccountingJournalProjection.Line line : projection.lines())
        {
            sb.append(line.accountCode())
                    .append(" ")
                    .append(line.accountName())
                    .append(" | Fund ")
                    .append(line.fundCode())
                    .append(" | DR ")
                    .append(line.debit().toPlainString())
                    .append(" | CR ")
                    .append(line.credit().toPlainString())
                    .append("\n");
        }
        sb.append("\nDebits=")
                .append(projection.debitTotal().toPlainString())
                .append(" Credits=")
                .append(projection.creditTotal().toPlainString());
        return sb.toString();
    }

    @Override public String title() { return "Ledger Register"; }
    @Override public Node root() { return root; }

    @Override
    public java.util.Optional<JournalSelection> activeJournalSelection()
    {
        Row sel = txnTable.getSelectionModel().getSelectedItem();
        if (sel == null)
        {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new JournalSelection(sel.id(), "Ledger Register table"));
    }

    @Override
    public void onNew()
    {
        details.setText("Use Transaction Editor to post a new transaction, then click Refresh here to load it.");
    }

    @Override
    public void onPanelShown()
    {
        String context = DrillThroughCoordinator.consumeContext();
        if (!context.isBlank())
        {
            drillContext.setText(context);
            reload();
        }
    }

    public record Row(Long id, String date, String payee, String memo, String bank, String splitCount, String status) {}
}
