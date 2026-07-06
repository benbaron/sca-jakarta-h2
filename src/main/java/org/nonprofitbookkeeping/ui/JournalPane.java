package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.AccountingJournalProjection;
import org.nonprofitbookkeeping.service.TransactionView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** General-journal inspection panel backed by canonical Txn projections. */
public class JournalPane implements AppPanel
{
    private final VBox root = new VBox(8);
    private final TextField fromDate = new TextField();
    private final TextField toDate = new TextField();
    private final TextField searchText = new TextField();
    private final Label status = new Label("Journal opens unfiltered.");
    private final Label contextLabel = new Label();
    private final TableView<JournalRow> table = new TableView<>();
    private Long centeredTransactionId;

    public JournalPane()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Inspect Journal");
        title.getStyleClass().add("panel-title");

        fromDate.setId("journalPaneFromDateField");
        toDate.setId("journalPaneToDateField");
        searchText.setId("journalPaneSearchTextField");
        status.setId("journalPaneStatusLabel");
        contextLabel.setId("journalPaneContextLabel");
        table.setId("journalPaneTable");

        fromDate.setPromptText("From YYYY-MM-DD");
        toDate.setPromptText("To YYYY-MM-DD");
        searchText.setPromptText("Memo or payee");

        Button apply = new Button("Apply Filters");
        apply.setId("journalPaneApplyFiltersButton");
        Button newTransaction = new Button("New");
        newTransaction.setId("journalPaneNewButton");
        Button editSelected = new Button("Edit Selected");
        editSelected.setId("journalPaneEditSelectedButton");
        editSelected.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        HBox filters = new HBox(8, new Label("From"), fromDate, new Label("To"), toDate, new Label("Search"), searchText, apply);
        HBox actions = new HBox(8, newTransaction, editSelected);

        buildTable();
        table.setPlaceholder(new Label("No journal entries found."));
        VBox.setVgrow(table, Priority.ALWAYS);
        SplitPane tableRegion = new SplitPane(table);
        tableRegion.setId("journalPaneTableSplitPane");
        VBox.setVgrow(tableRegion, Priority.ALWAYS);

        root.getChildren().addAll(title, filters, actions, contextLabel, status, new Separator(), tableRegion);

        apply.setOnAction(event -> reload());
        newTransaction.setOnAction(event -> openNewTransaction());
        editSelected.setOnAction(event -> openSelectedInEditor());
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null)
            {
                openSelectedInEditor();
            }
        });
    }

    private void buildTable()
    {
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.getColumns().add(col("Date", JournalRow::date));
        table.getColumns().add(col("Txn #", row -> String.valueOf(row.transactionId())));
        table.getColumns().add(col("Memo / Reference", JournalRow::memo));
        table.getColumns().add(col("Account", JournalRow::account));
        table.getColumns().add(col("Fund", JournalRow::fund));
        table.getColumns().add(col("Debit", JournalRow::debit));
        table.getColumns().add(col("Credit", JournalRow::credit));
        table.getColumns().add(col("Line Details", JournalRow::lineDetails));
    }

    private TableColumn<JournalRow, String> col(String name, java.util.function.Function<JournalRow, String> getter)
    {
        TableColumn<JournalRow, String> column = new TableColumn<>(name);
        column.setCellValueFactory(value -> new SimpleStringProperty(getter.apply(value.getValue())));
        return column;
    }

    private void reload()
    {
        status.setText("Loading journal entries...");
        Long centerId = centeredTransactionId;
        UiAsync.run("journal-pane-load", () -> loadRows(parseDateOrNull(fromDate.getText()), parseDateOrNull(toDate.getText()), searchText.getText()), rows -> {
            table.getItems().setAll(rows);
            centerOn(centerId);
            status.setText("Loaded " + rows.size() + " journal line(s)." + (centerId == null ? "" : " Centered at Txn #" + centerId + "."));
        }, ex -> status.setText("Failed to load journal entries: " + UiErrors.safeMessage(ex)));
    }

    private List<JournalRow> loadRows(LocalDate from, LocalDate to, String text)
    {
        List<TransactionView> transactions = UiServiceRegistry.transactionEntry().search(from, to, text, 500);
        List<JournalRow> rows = new ArrayList<>();
        for (TransactionView transaction : transactions)
        {
            rows.addAll(rowsFor(UiServiceRegistry.transactionEntry().journalView(transaction.id())));
        }
        return rows;
    }

    static List<JournalRow> rowsFor(AccountingJournalProjection projection)
    {
        List<JournalRow> rows = new ArrayList<>();
        for (AccountingJournalProjection.Line line : projection.lines())
        {
            rows.add(new JournalRow(
                    projection.transactionId(),
                    String.valueOf(projection.date()),
                    blankToNone(projection.memo()),
                    line.accountCode() + " " + line.accountName(),
                    line.fundCode() + " " + line.fundName(),
                    line.debit().toPlainString(),
                    line.credit().toPlainString(),
                    blankToNone(line.notes())));
        }
        return rows;
    }

    private void centerOn(Long transactionId)
    {
        if (transactionId == null)
        {
            return;
        }
        for (int i = 0; i < table.getItems().size(); i++)
        {
            if (transactionId.equals(table.getItems().get(i).transactionId()))
            {
                table.getSelectionModel().select(i);
                table.scrollTo(i);
                return;
            }
        }
    }

    private void openNewTransaction()
    {
        DrillThroughCoordinator.openTransactionEditorWithContext("New transaction from journal filters");
        status.setText("Opened Transaction Editor in New mode from Journal Pane.");
    }

    private void openSelectedInEditor()
    {
        JournalRow row = table.getSelectionModel().getSelectedItem();
        if (row == null)
        {
            status.setText("Select a journal line before editing its transaction.");
            return;
        }
        DrillThroughCoordinator.openTransactionEditorWithContext(LedgerRegisterPanel.editorContext(row.transactionId()));
        status.setText("Opened Txn #" + row.transactionId() + " in Transaction Editor.");
    }

    private static LocalDate parseDateOrNull(String value)
    {
        return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
    }

    private static String blankToNone(String value)
    {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    @Override public String title() { return "Inspect Journal"; }
    @Override public Node root() { return root; }

    @Override
    public void onNew()
    {
        openNewTransaction();
    }

    @Override
    public void onPanelShown()
    {
        String context = DrillThroughCoordinator.consumeContext(AppPanelId.JOURNAL_PANE);
        centeredTransactionId = transactionIdFromContext(context);
        contextLabel.setText(context == null || context.isBlank() ? "Showing the general journal without filters." : context);
        reload();
    }

    static String centeredContext(long transactionId, String source)
    {
        return "Inspect journal centered at Txn #" + transactionId + " from " + source;
    }

    static Long transactionIdFromContext(String context)
    {
        if (context == null)
        {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Txn #(\\d+)").matcher(context);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    public record JournalRow(Long transactionId, String date, String memo, String account, String fund, String debit, String credit, String lineDetails) {}
}
