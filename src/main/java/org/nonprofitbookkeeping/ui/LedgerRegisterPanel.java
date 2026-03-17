package org.nonprofitbookkeeping.ui;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.JournalLine;
import org.nonprofitbookkeeping.service.LedgerQueryService;

import java.util.List;

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

    public LedgerRegisterPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Ledger Register");
        Label range = new Label();
        range.textProperty().bind(Bindings.createStringBinding(() -> "Date Range: " + DateRangeContext.get(), DateRangeContext.selectedProperty()));
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        Button inspect = new Button("Inspect Journal");
        HBox actions = new HBox(8, refresh, inspect);

        VBox header = new VBox(6, title, range, actions, drillContext, status, new Separator());
        root.setTop(header);

        buildTable();

        details.setEditable(false);
        details.setWrapText(false);
        details.setPrefRowCount(8);

        VBox center = new VBox(8, txnTable, new Label("Transaction journal details"), details);
        VBox.setVgrow(txnTable, Priority.ALWAYS);
        root.setCenter(center);

        refresh.setOnAction(e -> reload());
        inspect.setOnAction(e -> inspectSelected());

        txnTable.setRowFactory(tv -> {
            TableRow<Row> r = new TableRow<>();
            r.setOnMouseClicked(e -> {
                if (r.isEmpty())
                {
                    return;
                }
                if (e.getClickCount() == 2 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY)
                {
                    inspectRow(r.getItem());
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
        drillContext.setText(context.isBlank() ? "" : context);
        status.setText("Loading ledger transactions...");
        UiAsync.run("ledger-register-load",
                () -> UiServiceRegistry.ledgerQuery().listRecent(250),
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

    private void inspectSelected()
    {
        Row sel = txnTable.getSelectionModel().getSelectedItem();
        if (sel != null)
        {
            inspectRow(sel);
        }
    }

    private void inspectRow(Row row)
    {
        UiAsync.run("ledger-journal-inspect-" + row.id(),
                () -> UiServiceRegistry.ledgerQuery().journalForTxn(row.id()),
                lines -> details.setText(LedgerRegisterPanel.renderJournal(row, lines)),
                ex -> details.setText("Could not load journal for txn " + row.id() + ": " + UiErrors.safeMessage(ex)));
    }

    static String renderJournal(Row row, List<JournalLine> lines)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Txn #").append(row.id())
                .append(" | Date ").append(row.date())
                .append(" | Payee ").append(row.payee())
                .append("\nMemo: ").append(row.memo())
                .append("\n\n");

        for (JournalLine line : lines)
        {
            sb.append(line.getAccountCode())
                    .append(" ")
                    .append(line.getAccountName())
                    .append(" | Fund ")
                    .append(line.getFundCode())
                    .append(" | DR ")
                    .append(line.getDebit().toPlainString())
                    .append(" | CR ")
                    .append(line.getCredit().toPlainString())
                    .append("\n");
        }
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

    public record Row(Long id, String date, String payee, String memo, String bank, String splitCount, String status) {}
}
