package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
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
        Button editSelected = new Button("Edit Selected");
        editSelected.setId("journalPaneEditSelectedButton");
        editSelected.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        HBox filters = new HBox(8, new Label("From"), fromDate, new Label("To"), toDate, new Label("Search"), searchText, apply);
        HBox actions = new HBox(8, newTransaction, editSelected);
        VBox header = new VBox(6, title, filters, actions, contextLabel, status, new Separator());
        root.setTop(header);

        buildTable();
        table.setPlaceholder(new Label("No journal entries found."));
        VBox.setVgrow(table, Priority.ALWAYS);

        supplementalRecords.setEditable(false);
        supplementalRecords.setWrapText(false);
        supplementalRecords.setText("Supplemental transaction records\n"
                + "Domain-specific records, such as bank clearing, inventory, asset, deferral, or open-item details, "
                + "will appear here when their owning domain slice provides them. They are linked to canonical transactions by stable IDs.");
        VBox supplementalPane = new VBox(6, new Label("Supplemental records"), supplementalRecords);
        VBox.setVgrow(supplementalRecords, Priority.ALWAYS);

        SplitPane tableRegion = new SplitPane(table, supplementalPane);
        tableRegion.setId("journalPaneTableSplitPane");
        tableRegion.setDividerPositions(0.78);
        root.setCenter(tableRegion);

        apply.setOnAction(event -> reload());
        newTransaction.setOnAction(event -> openNewTransaction());
        editSelected.setOnAction(event -> openSelectedInEditor());
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
        table.getColumns().add(col("Txn #", "transaction", 92, row -> String.valueOf(row.transactionId())));
        table.getColumns().add(col("Memo / Reference", "memo", 220, JournalRow::memo));
        table.getColumns().add(col("Account", "account", 220, JournalRow::account));
        table.getColumns().add(col("Fund", "fund", 170, JournalRow::fund));
        table.getColumns().add(col("Debit", "debit", 110, JournalRow::debit));
        table.getColumns().add(col("Credit", "credit", 110, JournalRow::credit));
        table.getColumns().add(col("Line Details", "lineDetails", 240, JournalRow::lineDetails));
        restoreTableState();
        installTableStatePersistence();
    }

    private TableColumn<JournalRow, String> col(
            String name,
            String key,
            double prefWidth,
            java.util.function.Function<JournalRow, String> getter)
    {
        TableColumn<JournalRow, String> column = new TableColumn<>(name);
        column.setId(key);
        column.setUserData(key);
        column.setPrefWidth(prefWidth);
        column.setMinWidth(64);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        column.setCellValueFactory(value -> new SimpleStringProperty(getter.apply(value.getValue())));
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
                double width = TABLE_STATE.getDouble(prefix + columnKey(column) + ".width", column.getPrefWidth());
                column.setPrefWidth(width);
                String sort = TABLE_STATE.get(prefix + columnKey(column) + ".sort", "");
                if ("ASCENDING".equals(sort))
                {
                    column.setSortType(TableColumn.SortType.ASCENDING);
                }
                else if ("DESCENDING".equals(sort))
                {
                    column.setSortType(TableColumn.SortType.DESCENDING);
                }
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
        if (order.isBlank())
        {
            return;
        }
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
        if (sortOrder.isBlank())
        {
            return;
        }
        List<TableColumn<JournalRow, ?>> restored = new ArrayList<>();
        List<String> keys = List.of(sortOrder.split(","));
        for (String key : keys)
        {
            table.getColumns().stream()
                    .filter(column -> Objects.equals(columnKey(column), key))
                    .findFirst()
                    .ifPresent(restored::add);
        }
        table.getSortOrder().setAll(restored);
    }

    private void saveTableState()
    {
        if (restoringTableState)
        {
            return;
        }
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
                    money(line.debit()),
                    money(line.credit()),
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

    private void updateSupplementalRecords(JournalRow row)
    {
        if (row == null)
        {
            supplementalRecords.setText("Select a journal line to inspect supplemental transaction record links."
                    + " Future domain slices will populate this area from their authoritative services.");
            return;
        }
        supplementalRecords.setText("Txn #" + row.transactionId() + " supplemental records\n"
                + "No supplemental records are available from a P03-owned service. "
                + "Future domain slices may attach bank clearing, inventory, asset, deferral, or open-item details here by stable transaction or line ID.");
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

    private static String money(BigDecimal value)
    {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
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
