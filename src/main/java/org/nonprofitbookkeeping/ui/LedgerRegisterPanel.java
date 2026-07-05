package org.nonprofitbookkeeping.ui;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
        Button inspect = new Button("Inspect Journal");
        Button openEditor = new Button("Open Selected in Editor");
        fromDate.setPromptText("From YYYY-MM-DD");
        toDate.setPromptText("To YYYY-MM-DD");
        searchText.setPromptText("Memo or payee");
        HBox filters = new HBox(8, new Label("From"), fromDate, new Label("To"), toDate, new Label("Search"), searchText);
        HBox actions = new HBox(8, refresh, inspect, openEditor);

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
        middle.setOrientation(Orientation.VERTICAL);
        middle.setDividerPositions(0.72);
        root.setCenter(middle);

        refresh.setOnAction(e -> reload());
        inspect.setOnAction(e -> inspectSelected());
        openEditor.setOnAction(e -> openSelectedInEditor());

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
        txnTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
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
        }
        status.setText("Loading ledger transactions...");
        UiAsync.run("ledger-register-load",
                () -> UiServiceRegistry.transactionEntry().search(parseDateOrNull(fromDate.getText()), parseDateOrNull(toDate.getText()), searchText.getText(), 250),
                rows -> {
                    txnTable.getItems().setAll(rows.stream().map(LedgerRegisterPanel::toRow).toList());
                    status.setText("Loaded " + rows.size() + " transaction(s).");
                    details.clear();
                },
                ex -> {
                    status.setText("Failed to load ledger transactions: " + UiErrors.safeMessage(ex));
                    details.clear();
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
        }
    }

    private void openSelectedInEditor()
    {
        Row sel = txnTable.getSelectionModel().getSelectedItem();
        if (sel != null)
        {
            openRowInEditor(sel);
        }
    }

    private void openRowInEditor(Row row)
    {
        DrillThroughCoordinator.openTransactionEditorWithContext(editorContext(row.id()));
        status.setText("Opened Txn #" + row.id() + " in Transaction Editor.");
    }

    static String editorContext(long transactionId)
    {
        return "Load transaction Txn #" + transactionId;
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
        UiAsync.run("ledger-journal-inspect-" + row.id(),
                () -> UiServiceRegistry.transactionEntry().journalView(row.id()),
                projection -> {
                    details.setText(LedgerRegisterPanel.renderJournal(row, projection));
                    status.setText("Loaded journal details for Txn #" + row.id() + ".");
                },
                ex -> {
                    details.setText("Could not load journal for txn " + row.id() + ": " + UiErrors.safeMessage(ex));
                    status.setText("Journal inspection failed for Txn #" + row.id() + ".");
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
