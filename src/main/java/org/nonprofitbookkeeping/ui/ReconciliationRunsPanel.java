package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.repository.ReconciliationRunRecord;
import org.nonprofitbookkeeping.repository.WorkflowRunStatus;
import org.nonprofitbookkeeping.service.ReconciliationComparisonCommand;
import org.nonprofitbookkeeping.service.ReconciliationComparisonLine;
import org.nonprofitbookkeeping.service.ReconciliationComparisonReport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Bank reconciliation comparison workspace.
 */
public class ReconciliationRunsPanel implements AppPanel
{
    private static final Preferences TABLE_STATE = Preferences.userNodeForPackage(ReconciliationRunsPanel.class).node("reconciliation-table-state");

    private final BorderPane root = new BorderPane();
    private final TableView<ReconciliationRunRecord> table = new TableView<>();
    private final TableView<ReconciliationComparisonLine> comparisonTable = new TableView<>();
    private final ComboBox<CompanyBankAccount> bankAccountSelect = new ComboBox<>();
    private final DatePicker fromDate = new DatePicker(LocalDate.now().withDayOfMonth(1));
    private final DatePicker statementDate = new DatePicker(LocalDate.now());
    private final CheckBox saveUnresolvedReport = new CheckBox("Save unresolved report");
    private final Label status = new Label();
    private final Label comparisonSummary = new Label("Select a configured bank account and run comparison.");
    private boolean restoringTableState;

    public ReconciliationRunsPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Bank Reconciliation");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh Runs");
        refresh.setOnAction(e -> reload());
        Button record = new Button("Record Completed Run");
        record.setOnAction(e -> recordRun());
        Button start = new Button("Record Started");
        start.setOnAction(e -> recordRunWithStatus(WorkflowRunStatus.STARTED, "Started from UI workspace"));
        Button fail = new Button("Record Failed");
        fail.setOnAction(e -> recordRunWithStatus(WorkflowRunStatus.FAILED, "Failed from UI workspace"));
        Button deleteUnavailable = new Button("Delete unavailable — reconciliation records are factual history");
        deleteUnavailable.setDisable(true);
        deleteUnavailable.setOnAction(event -> status.setText("Reconciliation run deletion is intentionally unavailable; saved comparison records preserve factual history."));
        Label workflowNote = new Label("Reconciliation is a comparison workflow; approve/reject decisions are not part of this panel.");
        workflowNote.getStyleClass().add("help-text");

        configureBankAccountSelect();
        Button compare = new Button("Run Comparison");
        compare.setOnAction(e -> runComparison());
        HBox comparisonControls = new HBox(8,
                new Label("Configured account:"), bankAccountSelect,
                new Label("From:"), fromDate,
                new Label("Statement end:"), statementDate,
                saveUnresolvedReport,
                compare);

        root.setTop(new VBox(6,
                title,
                new HBox(8, refresh, start, record, fail, deleteUnavailable),
                workflowNote,
                comparisonControls,
                comparisonSummary,
                status,
                new Separator()));

        configureRunTable();
        configureComparisonTable();
        installTableStatePersistence(table, "runs");
        installTableStatePersistence(comparisonTable, "comparison");

        VBox savedRunsPane = new VBox(6, new Label("Saved Reconciliation Runs"), table);
        VBox comparisonPane = new VBox(6, new Label("Comparison Report"), comparisonTable);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(comparisonTable, Priority.ALWAYS);
        SplitPane split = new SplitPane(savedRunsPane, comparisonPane);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.35);
        root.setCenter(split);

        loadBankAccounts();
        reload();
    }

    @Override
    public String title()
    {
        return "Bank Reconciliation";
    }

    @Override
    public Node root()
    {
        return root;
    }

    private void configureBankAccountSelect()
    {
        bankAccountSelect.setPrefWidth(260);
        bankAccountSelect.setCellFactory(cb -> new ListCell<>()
        {
            @Override
            protected void updateItem(CompanyBankAccount item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : bankAccountLabel(item));
            }
        });
        bankAccountSelect.setButtonCell(bankAccountSelect.getCellFactory().call(null));
    }

    private void configureRunTable()
    {
        TableColumn<ReconciliationRunRecord, String> when = new TableColumn<>("Statement End");
        when.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().statementEndingOn())));
        TableColumn<ReconciliationRunRecord, String> format = new TableColumn<>("Format");
        format.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().bankFormat().name()));
        TableColumn<ReconciliationRunRecord, String> txns = new TableColumn<>("Statement Lines");
        txns.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().importedTransactionCount())));
        TableColumn<ReconciliationRunRecord, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().status().name()));
        TableColumn<ReconciliationRunRecord, String> notes = new TableColumn<>("Notes");
        notes.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().notes() == null ? "" : v.getValue().notes()));

        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.getColumns().setAll(when, format, txns, statusCol, notes);
        configureColumn(when, "statementEnd", 140);
        configureColumn(format, "format", 90);
        configureColumn(txns, "statementLines", 130);
        configureColumn(statusCol, "status", 110);
        configureColumn(notes, "notes", 420);
        restoreTableState(table, "runs");
        table.setPlaceholder(new Label("No reconciliation runs found for active company."));
    }

    private void configureComparisonTable()
    {
        TableColumn<ReconciliationComparisonLine, String> kind = new TableColumn<>("Issue");
        kind.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().kind().name()));
        TableColumn<ReconciliationComparisonLine, String> ledgerDate = new TableColumn<>("Ledger Date");
        ledgerDate.setCellValueFactory(v -> new SimpleStringProperty(string(v.getValue().ledgerDate())));
        TableColumn<ReconciliationComparisonLine, String> statementDateColumn = new TableColumn<>("Statement Date");
        statementDateColumn.setCellValueFactory(v -> new SimpleStringProperty(string(v.getValue().statementDate())));
        TableColumn<ReconciliationComparisonLine, String> ledgerAmount = new TableColumn<>("Ledger Amount");
        ledgerAmount.setCellValueFactory(v -> new SimpleStringProperty(money(v.getValue().ledgerAmount())));
        TableColumn<ReconciliationComparisonLine, String> statementAmount = new TableColumn<>("Statement Amount");
        statementAmount.setCellValueFactory(v -> new SimpleStringProperty(money(v.getValue().statementAmount())));
        TableColumn<ReconciliationComparisonLine, String> description = new TableColumn<>("Description");
        description.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().description()));

        comparisonTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        comparisonTable.getColumns().setAll(kind, ledgerDate, statementDateColumn, ledgerAmount, statementAmount, description);
        configureColumn(kind, "kind", 170);
        configureColumn(ledgerDate, "ledgerDate", 130);
        configureColumn(statementDateColumn, "statementDate", 140);
        configureColumn(ledgerAmount, "ledgerAmount", 130);
        configureColumn(statementAmount, "statementAmount", 150);
        configureColumn(description, "description", 520);
        restoreTableState(comparisonTable, "comparison");
        comparisonTable.setPlaceholder(new Label("No comparison has been run."));
    }

    private static void configureColumn(TableColumn<?, ?> column, String key, double prefWidth)
    {
        column.setId(key);
        column.setUserData(key);
        column.setPrefWidth(prefWidth);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
    }

    private void installTableStatePersistence(TableView<?> targetTable, String tableKey)
    {
        targetTable.getColumns().addListener((ListChangeListener<TableColumn<?, ?>>) change -> saveTableState(targetTable, tableKey));
        targetTable.getSortOrder().addListener((ListChangeListener<TableColumn<?, ?>>) change -> saveTableState(targetTable, tableKey));
        for (TableColumn<?, ?> column : targetTable.getColumns())
        {
            column.widthProperty().addListener((obs, oldWidth, newWidth) -> saveTableState(targetTable, tableKey));
            column.sortTypeProperty().addListener((obs, oldSort, newSort) -> saveTableState(targetTable, tableKey));
        }
    }

    private void restoreTableState(TableView<?> targetTable, String tableKey)
    {
        restoringTableState = true;
        try
        {
            String prefix = tableStatePrefix(tableKey);
            for (TableColumn<?, ?> column : targetTable.getColumns())
            {
                column.setPrefWidth(TABLE_STATE.getDouble(prefix + columnKey(column) + ".width", column.getPrefWidth()));
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
            restoreColumnOrder(targetTable, prefix);
            restoreSortOrder(targetTable, prefix);
        }
        finally
        {
            restoringTableState = false;
        }
    }

    private void saveTableState(TableView<?> targetTable, String tableKey)
    {
        if (restoringTableState)
        {
            return;
        }
        String prefix = tableStatePrefix(tableKey);
        TABLE_STATE.put(prefix + "order", String.join(",", targetTable.getColumns().stream().map(ReconciliationRunsPanel::columnKey).toList()));
        TABLE_STATE.put(prefix + "sortOrder", String.join(",", targetTable.getSortOrder().stream().map(ReconciliationRunsPanel::columnKey).toList()));
        for (TableColumn<?, ?> column : targetTable.getColumns())
        {
            TABLE_STATE.putDouble(prefix + columnKey(column) + ".width", column.getWidth() > 0 ? column.getWidth() : column.getPrefWidth());
            TABLE_STATE.put(prefix + columnKey(column) + ".sort", column.getSortType() == null ? "" : column.getSortType().name());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreColumnOrder(TableView targetTable, String prefix)
    {
        String order = TABLE_STATE.get(prefix + "order", "");
        if (order.isBlank())
        {
            return;
        }
        List<String> keys = List.of(order.split(","));
        List<TableColumn> ordered = (List<TableColumn>) targetTable.getColumns().stream()
                .sorted(java.util.Comparator.comparingInt(column -> {
                    int index = keys.indexOf(columnKey((TableColumn<?, ?>) column));
                    return index < 0 ? Integer.MAX_VALUE : index;
                }))
                .toList();
        targetTable.getColumns().setAll(ordered);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreSortOrder(TableView targetTable, String prefix)
    {
        String sortOrder = TABLE_STATE.get(prefix + "sortOrder", "");
        if (sortOrder.isBlank())
        {
            return;
        }
        List<String> keys = List.of(sortOrder.split(","));
        targetTable.getSortOrder().setAll((java.util.Collection) targetTable.getColumns().stream()
                .filter(column -> keys.contains(columnKey((TableColumn<?, ?>) column)))
                .sorted(java.util.Comparator.comparingInt(column -> keys.indexOf(columnKey((TableColumn<?, ?>) column))))
                .toList());
    }

    private static String columnKey(TableColumn<?, ?> column)
    {
        Object key = column.getUserData();
        return key == null ? column.getText().replaceAll("\\W+", "_") : key.toString();
    }

    private static String tableStatePrefix(String tableKey)
    {
        return activeCompanyCode().replaceAll("[^A-Za-z0-9_.-]", "_") + "." + tableKey + ".";
    }

    private void loadBankAccounts()
    {
        UiAsync.run("recon-bank-accounts-load", () -> UiServiceRegistry.bankConfiguration().listBankAccounts(activeCompanyCode()).stream()
                .filter(account -> account.isActive() && account.getBank() != null && account.getAccount() != null)
                .toList(), accounts -> {
            bankAccountSelect.getItems().setAll(accounts);
            if (!accounts.isEmpty())
            {
                bankAccountSelect.getSelectionModel().select(0);
            }
        }, ex -> status.setText("Could not load configured bank accounts: " + UiErrors.safeMessage(ex)));
    }

    private void runComparison()
    {
        CompanyBankAccount selected = bankAccountSelect.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a configured bank account before running reconciliation comparison.");
            return;
        }
        if (statementDate.getValue() == null)
        {
            status.setText("Select a statement ending date before running reconciliation comparison.");
            return;
        }
        ReconciliationComparisonCommand command = new ReconciliationComparisonCommand(
                activeCompanyCode(),
                selected.getId(),
                fromDate.getValue(),
                statementDate.getValue(),
                saveUnresolvedReport.isSelected());
        status.setText("Running reconciliation comparison...");
        UiAsync.run("recon-comparison", () -> UiServiceRegistry.reconciliationComparison().compare(command), report -> {
            comparisonTable.getItems().setAll(report.lines());
            comparisonSummary.setText(summary(report));
            status.setText(report.savedRunId() == null
                    ? "Comparison complete. No unresolved report was saved."
                    : "Comparison complete. Saved unresolved reconciliation report " + report.savedRunId() + ".");
            reload();
        }, ex -> status.setText("Could not run comparison: " + UiErrors.safeMessage(ex)));
    }

    private void recordRun()
    {
        UiAsync.run("recon-record-run", () -> UiServiceRegistry.reconciliationService()
                .recordCompletedRun(activeCompanyCode(), LocalDate.now(), BankingDataFormat.OFX, 0, "Recorded from UI workspace"), run -> {
            status.setText("Recorded run for " + run.groupCode() + " ending " + run.statementEndingOn() + ".");
            reload();
        }, ex -> status.setText("Could not record run: " + UiErrors.safeMessage(ex)));
    }

    private void recordRunWithStatus(WorkflowRunStatus statusValue, String notes)
    {
        UiAsync.run("recon-record-run-" + statusValue.name().toLowerCase(Locale.ROOT), () -> {
            ReconciliationRunRecord run = new ReconciliationRunRecord(
                    java.util.UUID.randomUUID(),
                    activeCompanyCode(),
                    LocalDate.now(),
                    BankingDataFormat.OFX,
                    0,
                    statusValue,
                    notes);
            UiServiceRegistry.reconciliationRunRepository().append(run);
            return run;
        }, run -> {
            status.setText("Recorded " + run.status().name() + " run for " + run.groupCode() + " ending " + run.statementEndingOn() + ".");
            reload();
        }, ex -> status.setText("Could not record " + statusValue.name() + " run: " + UiErrors.safeMessage(ex)));
    }

    private void reload()
    {
        status.setText("Loading reconciliation runs...");
        UiAsync.run("recon-runs-load", () -> UiServiceRegistry.reconciliationRunRepository()
                .findByGroupAndDateRange(activeCompanyCode(), LocalDate.now().minusYears(1), LocalDate.now().plusDays(1)), rows -> {
            table.getItems().setAll(rows);
            status.setText("Loaded " + rows.size() + " reconciliation comparison run(s) for active company.");
        }, ex -> status.setText("Could not load reconciliation runs: " + UiErrors.safeMessage(ex)));
    }

    private static String activeCompanyCode()
    {
        return MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
    }

    private static String bankAccountLabel(CompanyBankAccount account)
    {
        String accountCode = account.getAccount() == null ? "" : " — " + account.getAccount().getCode();
        return account.getName() + accountCode;
    }

    private static String summary(ReconciliationComparisonReport report)
    {
        return "Beginning " + money(report.beginningBalance())
                + " | Activity " + money(report.activity())
                + " | Ending " + money(report.endingBookBalance())
                + " | Cleared " + money(report.clearedBookBalance())
                + " | Ledger lines " + report.ledgerLineCount()
                + " | Statement lines " + report.statementLineCount()
                + " | Matched " + report.matchedLineCount()
                + " | Unresolved " + report.unresolvedCount();
    }

    private static String money(BigDecimal value)
    {
        return value == null ? "" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String string(Object value)
    {
        return value == null ? "" : value.toString();
    }
}
