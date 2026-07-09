package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.CorrectionMethod;
import org.nonprofitbookkeeping.service.AccountingJournalProjection;
import org.nonprofitbookkeeping.service.TransactionView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.prefs.Preferences;

/** General-journal inspection panel backed by canonical Txn projections. */
public class JournalPane implements AppPanel
{
    private static final String TABLE_ID = "journalPaneTable";
    private static final Preferences TABLE_STATE = Preferences.userNodeForPackage(JournalPane.class)
            .node("company-table-state")
            .node(TABLE_ID);

    private final BorderPane root = new BorderPane();
    private final TextField fromDate = new TextField();
    private final TextField toDate = new TextField();
    private final TextField searchText = new TextField();
    private final Label status = new Label("Journal opens unfiltered.");
    private final Label contextLabel = new Label();
    private final TableView<JournalRow> table = new TableView<>();
    private final TextArea supplementalRecords = new TextArea();
    private Long centeredTransactionId;
    private boolean restoringTableState;

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
        table.setId(TABLE_ID);
        supplementalRecords.setId("journalPaneSupplementalRecordsArea");

        fromDate.setPromptText("From YYYY-MM-DD");
        toDate.setPromptText("To YYYY-MM-DD");
        searchText.setPromptText("Memo or payee");

        Button apply = new Button("Apply Filters");
        apply.setId("journalPaneApplyFiltersButton");
        Button newTransaction = new Button("New");
        newTransaction.setId("journalPaneNewButton");
        Button editSelected = new Button("Edit");
        editSelected.setId("journalPaneEditSelectedButton");
        Button deleteSelected = new Button("Delete");
        deleteSelected.setId("journalPaneDeleteSelectedButton");
        Button refresh = new Button("Refresh");
        refresh.setId("journalPaneRefreshButton");
        editSelected.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        deleteSelected.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        HBox filters = new HBox(8, new Label("From"), fromDate, new Label("To"), toDate, new Label("Search"), searchText, apply);
        HBox actions = new HBox(8, newTransaction, editSelected, deleteSelected, refresh);
        VBox header = new VBox(6, title, filters, actions, contextLabel, status, new Separator());
        root.setTop(header);

        buildTable();
        table.setPlaceholder(new Label("No journal transactions found."));
        VBox.setVgrow(table, Priority.ALWAYS);

        supplementalRecords.setEditable(false);
        supplementalRecords.setWrapText(false);
        supplementalRecords.setText(defaultSupplementalText());
        VBox supplementalPane = new VBox(6, new Label("Transaction supplemental detail viewer"), supplementalRecords);
        VBox.setVgrow(supplementalRecords, Priority.ALWAYS);

        SplitPane tableRegion = new SplitPane(table, supplementalPane);
        tableRegion.setId("journalPaneTableSplitPane");
        tableRegion.setDividerPositions(0.78);
        root.setCenter(tableRegion);

        apply.setOnAction(event -> reload());
        refresh.setOnAction(event -> reload());
        newTransaction.setOnAction(event -> openNewTransaction());
        editSelected.setOnAction(event -> openSelectedInEditor());
        deleteSelected.setOnAction(event -> deleteSelectedTransaction());
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null)
            {
                openSelectedInEditor();
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> updateSupplementalRecords(newRow));
    }

    private void buildTable()
    {
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.getColumns().add(col("Date", "date", 116, JournalRow::date));
        table.getColumns().add(wrappingCol("Account Title and Description", "account", 360, JournalRow::account));
        table.getColumns().add(wrappingCol("Fund", "fund", 180, JournalRow::fund));
        table.getColumns().add(col("Cleared", "cleared", 120, JournalRow::cleared));
        table.getColumns().add(wrappingCol("Debit", "debit", 130, JournalRow::debit));
        table.getColumns().add(wrappingCol("Credit", "credit", 130, JournalRow::credit));
        table.getColumns().add(col("Transaction ID", "transaction", 110, row -> String.valueOf(row.transactionId())));
        table.getColumns().add(col("Supplemental", "supplemental", 150, JournalRow::supplemental));
        table.getColumns().add(wrappingCol("Memo / Details", "memo", 360, JournalRow::memo));
        restoreTableState();
        installTableStatePersistence();
    }

    private TableColumn<JournalRow, String> col(String name, String key, double prefWidth, java.util.function.Function<JournalRow, String> getter)
    {
        TableColumn<JournalRow, String> column = configuredColumn(name, key, prefWidth);
        column.setCellValueFactory(value -> new SimpleStringProperty(getter.apply(value.getValue())));
        return column;
    }

    private TableColumn<JournalRow, String> wrappingCol(String name, String key, double prefWidth, java.util.function.Function<JournalRow, String> getter)
    {
        TableColumn<JournalRow, String> column = col(name, key, prefWidth, getter);
        column.setCellFactory(col -> new TableCell<>()
        {
            @Override
            protected void updateItem(String item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setWrapText(true);
            }
        });
        return column;
    }

    private TableColumn<JournalRow, String> configuredColumn(String name, String key, double prefWidth)
    {
        TableColumn<JournalRow, String> column = new TableColumn<>(name);
        column.setId(key);
        column.setUserData(key);
        column.setPrefWidth(prefWidth);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        return column;
    }

    private void installTableStatePersistence()
    {
        table.getColumns().addListener((ListChangeListener<TableColumn<JournalRow, ?>>) change -> saveTableState());
        table.getSortOrder().addListener((ListChangeListener<TableColumn<JournalRow, ?>>) change -> saveTableState());
        for (TableColumn<JournalRow, ?> column : table.getColumns())
        {
            column.widthProperty().addListener((obs, oldWidth, newWidth) -> saveTableState());
            column.sortTypeProperty().addListener((obs, oldType, newType) -> saveTableState());
        }
    }

    private void restoreTableState()
    {
        restoringTableState = true;
        try
        {
            String prefix = tableStatePrefix();
            for (TableColumn<JournalRow, ?> column : table.getColumns())
            {
                column.setPrefWidth(TABLE_STATE.getDouble(prefix + columnKey(column) + ".width", column.getPrefWidth()));
                String sort = TABLE_STATE.get(prefix + columnKey(column) + ".sort", "");
                if ("ASCENDING".equals(sort)) column.setSortType(TableColumn.SortType.ASCENDING);
                else if ("DESCENDING".equals(sort)) column.setSortType(TableColumn.SortType.DESCENDING);
            }
            restoreColumnOrder(prefix);
            restoreSortOrder(prefix);
        }
        finally
        {
            restoringTableState = false;
        }
    }

    private void restoreColumnOrder(String prefix)
    {
        String order = TABLE_STATE.get(prefix + "order", "");
        if (order.isBlank()) return;
        List<String> keys = List.of(order.split(","));
        List<TableColumn<JournalRow, ?>> columns = new ArrayList<>(table.getColumns());
        columns.sort(Comparator.comparingInt(column -> {
            int index = keys.indexOf(columnKey(column));
            return index < 0 ? Integer.MAX_VALUE : index;
        }));
        table.getColumns().setAll(columns);
    }

    private void restoreSortOrder(String prefix)
    {
        String sortOrder = TABLE_STATE.get(prefix + "sortOrder", "");
        if (sortOrder.isBlank()) return;
        List<String> keys = List.of(sortOrder.split(","));
        List<TableColumn<JournalRow, ?>> restored = new ArrayList<>();
        for (String key : keys)
        {
            table.getColumns().stream().filter(column -> Objects.equals(columnKey(column), key)).findFirst().ifPresent(restored::add);
        }
        table.getSortOrder().setAll(restored);
    }

    private void saveTableState()
    {
        if (restoringTableState) return;
        String prefix = tableStatePrefix();
        TABLE_STATE.put(prefix + "order", String.join(",", table.getColumns().stream().map(JournalPane::columnKey).toList()));
        TABLE_STATE.put(prefix + "sortOrder", String.join(",", table.getSortOrder().stream().map(JournalPane::columnKey).toList()));
        for (TableColumn<JournalRow, ?> column : table.getColumns())
        {
            TABLE_STATE.putDouble(prefix + columnKey(column) + ".width", column.getWidth() > 0 ? column.getWidth() : column.getPrefWidth());
            TABLE_STATE.put(prefix + columnKey(column) + ".sort", column.getSortType() == null ? "" : column.getSortType().name());
        }
    }

    private String tableStatePrefix()
    {
        return sanitizeCompany(MainWindow.sharedSessionState().multiCompany().activeCompanyCode()) + ".";
    }

    private static String sanitizeCompany(String company)
    {
        String value = company == null || company.isBlank() ? "DEFAULT" : company.trim().toUpperCase(Locale.ROOT);
        return value.replaceAll("[^A-Z0-9_-]", "_");
    }

    private static String columnKey(TableColumn<?, ?> column)
    {
        Object key = column.getUserData();
        return key == null ? column.getText() : String.valueOf(key);
    }

    private void reload()
    {
        status.setText("Loading journal transactions...");
        Long centerId = centeredTransactionId;
        UiAsync.run("journal-pane-load", () -> loadRows(parseDateOrNull(fromDate.getText()), parseDateOrNull(toDate.getText()), searchText.getText()), rows -> {
            table.getItems().setAll(rows);
            centerOn(centerId);
            status.setText("Loaded " + rows.size() + " journal transaction(s)." + (centerId == null ? "" : " Centered at Txn #" + centerId + "."));
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
        if (projection == null)
        {
            return List.of();
        }
        StringBuilder account = new StringBuilder();
        StringBuilder fund = new StringBuilder();
        StringBuilder debit = new StringBuilder();
        StringBuilder credit = new StringBuilder();
        StringBuilder detail = new StringBuilder();
        int lineNumber = 0;
        for (AccountingJournalProjection.Line line : projection.lines())
        {
            lineNumber++;
            appendLine(account, line.accountCode() + " " + line.accountName());
            appendLine(fund, line.fundCode() + " " + line.fundName());
            appendLine(debit, line.debit().signum() == 0 ? "" : money(line.debit()));
            appendLine(credit, line.credit().signum() == 0 ? "" : money(line.credit()));
            if (line.notes() != null && !line.notes().isBlank())
            {
                appendLine(detail, "Line " + lineNumber + ": " + line.notes());
            }
        }
        if (projection.memo() != null && !projection.memo().isBlank())
        {
            appendLine(detail, "Memo: " + projection.memo());
        }
        if (projection.payeeName() != null && !projection.payeeName().isBlank())
        {
            appendLine(detail, "Payee: " + projection.payeeName());
        }
        return List.of(new JournalRow(
                projection.transactionId(),
                String.valueOf(projection.date()),
                account.toString(),
                fund.toString(),
                "Uncleared",
                debit.toString(),
                credit.toString(),
                "Schedules (0)",
                detail.length() == 0 ? "(none)" : detail.toString()));
    }

    private static void appendLine(StringBuilder builder, String value)
    {
        if (builder.length() > 0)
        {
            builder.append('\n');
        }
        builder.append(value == null ? "" : value.trim());
    }

    private void centerOn(Long transactionId)
    {
        if (transactionId == null) return;
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

    private void updateSupplementalRecords(JournalRow row)
    {
        if (row == null)
        {
            supplementalRecords.setText(defaultSupplementalText());
            return;
        }
        supplementalRecords.setText("Txn #" + row.transactionId() + " supplemental detail panels\n"
                + "Supplemental schedules are transaction-local details, not the eliminated generic Schedules module.\n"
                + "No supplemental detail records are available from a P03-owned H2 service yet.");
    }

    private String defaultSupplementalText()
    {
        return "Select a journal transaction to inspect transaction supplemental detail panels. "
                + "Receivable, Payable, Prepaid Expense, Deferred Revenue, Other Asset, and Other Liability details "
                + "will appear here when backed by their authoritative services.";
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
            status.setText("Select a journal transaction before editing.");
            return;
        }
        DrillThroughCoordinator.openTransactionEditorWithContext(LedgerRegisterPanel.editorContext(row.transactionId()));
        status.setText("Opened Txn #" + row.transactionId() + " in Transaction Editor.");
    }

    private void deleteSelectedTransaction()
    {
        JournalRow row = table.getSelectionModel().getSelectedItem();
        if (row == null)
        {
            status.setText("Select a journal transaction before deleting or reversing.");
            return;
        }
        long transactionId = row.transactionId();
        CorrectionMethod method = MainWindow.sharedSessionState().preferences().correctionMethod();
        if (method == CorrectionMethod.DIRECT_EDIT)
        {
            if (!confirm("Delete transaction #" + transactionId + "?", "This removes the entered transaction after period and reconciliation checks and writes an audit snapshot."))
            {
                status.setText("Delete cancelled for Txn #" + transactionId + ".");
                return;
            }
            UiAsync.run("journal-delete-" + transactionId, () -> {
                        UiServiceRegistry.transactionCorrection().delete(transactionId, "ui", "Deleted from Journal Pane");
                        return transactionId;
                    },
                    deletedId -> {
                        status.setText("Deleted Txn #" + deletedId + ".");
                        reload();
                    },
                    ex -> status.setText("Delete failed for Txn #" + transactionId + ": " + UiErrors.safeMessage(ex)));
        }
        else
        {
            if (!confirm("Reverse transaction #" + transactionId + " instead of deleting?", "Current correction settings do not allow hard deletion. A reversing entry will be created using the active period date."))
            {
                status.setText("Reversal cancelled for Txn #" + transactionId + ".");
                return;
            }
            UiAsync.run("journal-reverse-" + transactionId,
                    () -> UiServiceRegistry.transactionCorrection().reverse(transactionId, ActivePeriodContext.get(), "ui", "Reversed from Journal Pane delete action", false),
                    result -> {
                        status.setText("Created reversing Txn #" + result.reversalTransactionId() + " for original Txn #" + transactionId + ".");
                        reload();
                    },
                    ex -> status.setText("Reversal failed for Txn #" + transactionId + ": " + UiErrors.safeMessage(ex)));
        }
    }

    private boolean confirm(String header, String content)
    {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("Confirm journal transaction action");
        alert.setHeaderText(header);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static LocalDate parseDateOrNull(String value)
    {
        return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
    }

    private static String money(BigDecimal value)
    {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    @Override public String title() { return "Inspect Journal"; }
    @Override public Node root() { return root; }

    @Override public void onNew() { openNewTransaction(); }

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
        if (context == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Txn #(\\d+)").matcher(context);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    public record JournalRow(Long transactionId,
                             String date,
                             String account,
                             String fund,
                             String cleared,
                             String debit,
                             String credit,
                             String supplemental,
                             String memo)
    {
        public String lineDetails()
        {
            return memo;
        }
    }
}
