package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
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

/**
 * Bank reconciliation comparison workspace.
 */
public class ReconciliationRunsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<ReconciliationRunRecord> table = new TableView<>();
    private final TableView<ReconciliationComparisonLine> comparisonTable = new TableView<>();
    private final ComboBox<CompanyBankAccount> bankAccountSelect = new ComboBox<>();
    private final DatePicker fromDate = new DatePicker(LocalDate.now().withDayOfMonth(1));
    private final DatePicker statementDate = new DatePicker(LocalDate.now());
    private final CheckBox saveUnresolvedReport = new CheckBox("Save unresolved report");
    private final Label status = new Label();
    private final Label comparisonSummary = new Label("Select a configured bank account and run comparison.");

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
                new HBox(8, refresh, start, record, fail),
                workflowNote,
                comparisonControls,
                comparisonSummary,
                status,
                new Separator()));

        configureRunTable();
        configureComparisonTable();
        root.setCenter(new VBox(8, new Label("Saved Reconciliation Runs"), table,
                new Label("Comparison Report"), comparisonTable));

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

        table.getColumns().setAll(when, format, txns, statusCol, notes);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
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

        comparisonTable.getColumns().setAll(kind, ledgerDate, statementDateColumn, ledgerAmount, statementAmount, description);
        comparisonTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        comparisonTable.setPlaceholder(new Label("No comparison has been run."));
    }

    private void loadBankAccounts()
    {
        UiAsync.run("recon-bank-accounts-load", () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            return UiServiceRegistry.bankConfiguration().listBankAccounts(group).stream()
                    .filter(account -> account.isActive() && account.getBank() != null && account.getAccount() != null)
                    .toList();
        }, accounts -> {
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
                MainWindow.sharedSessionState().multiCompany().activeCompanyCode(),
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
        UiAsync.run("recon-record-run", () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            return UiServiceRegistry.reconciliationService()
                    .recordCompletedRun(group, LocalDate.now(), BankingDataFormat.OFX, 0, "Recorded from UI workspace");
        }, run -> {
            status.setText("Recorded run for " + run.groupCode() + " ending " + run.statementEndingOn() + ".");
            reload();
        }, ex -> status.setText("Could not record run: " + UiErrors.safeMessage(ex)));
    }

    private void recordRunWithStatus(WorkflowRunStatus statusValue, String notes)
    {
        UiAsync.run("recon-record-run-" + statusValue.name().toLowerCase(Locale.ROOT), () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            ReconciliationRunRecord run = new ReconciliationRunRecord(
                    java.util.UUID.randomUUID(),
                    group,
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
        UiAsync.run("recon-runs-load", () -> {
            String group = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
            return UiServiceRegistry.reconciliationRunRepository()
                    .findByGroupAndDateRange(group, LocalDate.now().minusYears(1), LocalDate.now().plusDays(1));
        }, rows -> {
            table.getItems().setAll(rows);
            status.setText("Loaded " + rows.size() +" reconciliation comparison run(s) for active company.");
        }, ex -> status.setText("Could not load reconciliation runs: " + UiErrors.safeMessage(ex)));
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
