package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.service.TransactionCommandValidator;
import org.nonprofitbookkeeping.service.JournalLine;
import org.nonprofitbookkeeping.service.LedgerQueryService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Represents the TransactionEditorPanel component in the nonprofit bookkeeping application.
 */
public class TransactionEditorPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<SplitRow> splitTable = new TableView<>();
    private final TransactionLineEditorModel lineEditorModel = new TransactionLineEditorModel(new TransactionCommandValidator());
    private final Label status = new Label("Prepare debit and credit split lines, then validate before saving.");
    private final Label totals = new Label("Debits=0 Credits=0 Difference=0");
    private ValidationResult lastValidationResult;
    private final TextField dateField = new TextField();
    private final TextField payeeField = new TextField();
    private final TextField memoField = new TextField();
    private final TextField bankField = new TextField();
    private boolean dirty;

    public TransactionEditorPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Transaction Editor");
        title.getStyleClass().add("panel-title");

        Button save = new Button("Save");
        Button post = new Button("Validate Lines");
        Button journal = new Button("Journal View");
        HBox actions = new HBox(8, save, post, journal);

        VBox top = new VBox(6, title, actions, status, new Separator(), buildHeaderForm());
        root.setTop(top);

        buildSplitTable();
        root.setCenter(buildSplitEditor());

        save.setOnAction(e -> onSave());
        post.setOnAction(e -> validateOrPost());
        journal.setOnAction(e -> showJournal());

        dateField.textProperty().addListener((observable, oldValue, newValue) -> dirty = true);
        payeeField.textProperty().addListener((observable, oldValue, newValue) -> dirty = true);
        memoField.textProperty().addListener((observable, oldValue, newValue) -> dirty = true);
        bankField.textProperty().addListener((observable, oldValue, newValue) -> dirty = true);
    }

    private Node buildHeaderForm()
    {
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.setPadding(new Insets(8, 0, 8, 0));

        int r = 0;
        g.add(new Label("Date"), 0, r);
        g.add(dateField, 1, r);
        g.add(new Label("Payee"), 2, r);
        g.add(payeeField, 3, r);
        r++;
        g.add(new Label("Memo"), 0, r);
        g.add(memoField, 1, r, 3, 1);
        r++;
        g.add(new Label("Bank"), 0, r);
        g.add(bankField, 1, r);

        g.getColumnConstraints().addAll(
                new ColumnConstraints(70),
                new ColumnConstraints(220),
                new ColumnConstraints(70),
                new ColumnConstraints(220)
        );
        g.getColumnConstraints().get(1).setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().get(3).setHgrow(Priority.ALWAYS);

        return g;
    }

    private void buildSplitTable()
    {
        splitTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        splitTable.getColumns().add(col("Account", SplitRow::account));
        splitTable.getColumns().add(col("Fund", SplitRow::fund));
        splitTable.getColumns().add(col("Budget", SplitRow::budgetCategory));
        splitTable.getColumns().add(col("Debit", SplitRow::debit));
        splitTable.getColumns().add(col("Credit", SplitRow::credit));
        splitTable.getColumns().add(col("Activity", SplitRow::activity));
        splitTable.getColumns().add(col("Merchant", SplitRow::merchant));
        splitTable.getColumns().add(col("Counterparty", SplitRow::counterparty));
        splitTable.getColumns().add(col("NMR", SplitRow::nmr));
        splitTable.getColumns().add(col("Notes", SplitRow::notes));

        splitTable.getItems().addAll(
                new SplitRow("", "", "", "", "", "", "", "", "", ""),
                new SplitRow("", "", "", "", "", "", "", "", "", "")
        );
    }

    private Node buildSplitEditor()
    {
        Label lbl = new Label("Splits");
        lbl.getStyleClass().add("subheader");

        Button addLine = new Button("+ Add Line");
        Button removeLine = new Button("– Remove");
        ToolBar tb = new ToolBar(addLine, removeLine);

        addLine.setOnAction(e -> {
            splitTable.getItems().add(new SplitRow("", "", "", "", "", "", "", "", "", ""));
            lineEditorModel.addRow();
            dirty = true;
            refreshTotals();
        });
        removeLine.setOnAction(e -> {
            int selectedIndex = splitTable.getSelectionModel().getSelectedIndex();
            SplitRow sel = splitTable.getSelectionModel().getSelectedItem();
            if (sel != null)
            {
                splitTable.getItems().remove(sel);
                lineEditorModel.removeRow(selectedIndex);
                dirty = true;
                refreshTotals();
            }
        });

        VBox box = new VBox(6, lbl, tb, splitTable, totals);
        VBox.setVgrow(splitTable, Priority.ALWAYS);
        return box;
    }

    private TableColumn<SplitRow, String> col(String name, java.util.function.Function<SplitRow, String> getter)
    {
        TableColumn<SplitRow, String> c = new TableColumn<>(name);
        c.setCellValueFactory(v -> new SimpleStringProperty(getter.apply(v.getValue())));
        return c;
    }

    private void validateOrPost()
    {
        status.setText("Validating debit and credit rows against current account/fund catalogs...");
        UiAsync.run("txn-editor-validate", this::validateAgainstReferenceData,
                result -> {
                    lastValidationResult = result;
                    status.setText(result.message());
                },
                ex -> status.setText("Validation failed: " + UiErrors.safeMessage(ex)));
    }

    private ValidationResult validateAgainstReferenceData()
    {
        Set<String> accountCodes = UiServiceRegistry.accountLookup().listActivePostingAccounts()
                .stream()
                .map(Account::getCode)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> fundCodes = UiServiceRegistry.fundLookup().listActiveFunds()
                .stream()
                .map(Fund::getCode)
                .collect(java.util.stream.Collectors.toSet());
        return validateSplits(splitTable.getItems(), accountCodes, fundCodes);
    }

    static ValidationResult validateSplits(List<SplitRow> rows, Set<String> accountCodes, Set<String> fundCodes)
    {
        int nonEmpty = 0;
        int valid = 0;
        int errors = 0;
        BigDecimal net = BigDecimal.ZERO;

        for (SplitRow row : rows)
        {
            boolean hasData = !(isBlank(row.account()) && isBlank(row.fund()) && isBlank(row.debit()) && isBlank(row.credit()));
            if (!hasData)
            {
                continue;
            }
            nonEmpty++;

            boolean rowValid = true;
            if (isBlank(row.account()) || !accountCodes.contains(row.account().trim()))
            {
                rowValid = false;
            }
            if (isBlank(row.fund()) || !fundCodes.contains(row.fund().trim()))
            {
                rowValid = false;
            }

            BigDecimal debit = parseOptionalAmount(row.debit());
            BigDecimal credit = parseOptionalAmount(row.credit());
            if (debit == null || credit == null)
            {
                rowValid = false;
            }
            else if (debit.signum() < 0 || credit.signum() < 0 || (debit.signum() > 0 && credit.signum() > 0))
            {
                rowValid = false;
            }
            else if (debit.signum() == 0 && credit.signum() == 0)
            {
                rowValid = false;
            }
            else
            {
                net = net.add(debit).subtract(credit);
            }

            if (rowValid)
            {
                valid++;
            }
            else
            {
                errors++;
            }
        }

        if (nonEmpty == 0)
        {
            return new ValidationResult("Validation result: no split rows entered.", 0, 0, 0, BigDecimal.ZERO);
        }

        String message = "Validation result: rows=" + nonEmpty
                + ", valid=" + valid
                + ", errors=" + errors
                + ", debit-credit difference=" + net.toPlainString();
        if (errors == 0 && net.compareTo(BigDecimal.ZERO) == 0)
        {
            message += " (ready to post/save)";
        }
        else if (errors == 0)
        {
            message += " (warning: not balanced)";
        }
        return new ValidationResult(message, nonEmpty, valid, errors, net);
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }

    private static BigDecimal parseOptionalAmount(String value)
    {
        if (isBlank(value))
        {
            return BigDecimal.ZERO;
        }
        try
        {
            return new BigDecimal(value.trim());
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    private void refreshTotals()
    {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (SplitRow row : splitTable.getItems())
        {
            BigDecimal rowDebit = parseOptionalAmount(row.debit());
            BigDecimal rowCredit = parseOptionalAmount(row.credit());
            if (rowDebit != null)
            {
                debit = debit.add(rowDebit);
            }
            if (rowCredit != null)
            {
                credit = credit.add(rowCredit);
            }
        }
        totals.setText("Debits=" + debit.toPlainString()
                + " Credits=" + credit.toPlainString()
                + " Difference=" + debit.subtract(credit).toPlainString());
    }


    static String postValidateStatusFor(ValidationResult result)
    {
        if (result == null)
        {
            return "Line validation completed: run validation first to review row readiness.";
        }
        if (result.errorCount() > 0)
        {
            return "Line validation blocked: fix validation errors before saving.";
        }
        if (result.netAmount().compareTo(BigDecimal.ZERO) != 0)
        {
            return "Line validation blocked: split rows are not balanced (difference=" + result.netAmount().toPlainString() + ").";
        }
        return "Line validation accepted: transaction is balanced and ready to save.";
    }

    private void showJournal()
    {
        status.setText("Loading journal preview for current transaction context...");
        UiAsync.run("txn-editor-journal-preview", this::buildJournalPreviewForCurrentContext,
                preview -> status.setText(preview),
                ex -> status.setText("Journal preview failed: " + UiErrors.safeMessage(ex)));
    }

    String buildJournalPreviewForCurrentContext()
    {
        LedgerQueryService ledger = UiServiceRegistry.ledgerQuery();
        List<LedgerQueryService.LedgerRow> recent = ledger.listRecent(250);
        List<LedgerQueryService.LedgerRow> matches = findContextMatches(
                recent,
                dateField.getText(),
                payeeField.getText(),
                memoField.getText(),
                bankField.getText());

        if (matches.isEmpty())
        {
            return "Journal preview: no posted transaction matched current date/payee/memo/bank context.";
        }

        LedgerQueryService.LedgerRow match = matches.get(0);
        List<JournalLine> lines = ledger.journalForTxn(match.id());
        return renderContextJournalPreview(match, lines);
    }

    static List<LedgerQueryService.LedgerRow> findContextMatches(List<LedgerQueryService.LedgerRow> rows,
                                                                  String date,
                                                                  String payee,
                                                                  String memo,
                                                                  String bank)
    {
        String dateQuery = normalizeToken(date);
        String payeeQuery = normalizeToken(payee);
        String memoQuery = normalizeToken(memo);
        String bankQuery = normalizeToken(bank);

        return rows.stream()
                .filter(row -> matches(dateQuery, row.date() == null ? "" : row.date().toString()))
                .filter(row -> matches(payeeQuery, row.payee()))
                .filter(row -> matches(memoQuery, row.memo()))
                .filter(row -> matches(bankQuery, row.bank()))
                .toList();
    }

    static String renderContextJournalPreview(LedgerQueryService.LedgerRow row, List<JournalLine> lines)
    {
        StringBuilder body = new StringBuilder();
        body.append("Journal preview: matched Txn #")
                .append(row.id())
                .append(" on ")
                .append(row.date())
                .append(" (splits: ")
                .append(row.splitCount())
                .append(")");

        if (lines.isEmpty())
        {
            body.append(" | journal lines: none");
            return body.toString();
        }

        JournalLine first = lines.get(0);
        body.append(" | first line ")
                .append(first.getAccountCode())
                .append("/")
                .append(first.getFundCode() == null ? "" : first.getFundCode())
                .append(" DR=")
                .append(first.getDebit().toPlainString())
                .append(" CR=")
                .append(first.getCredit().toPlainString());
        return body.toString();
    }

    private static String normalizeToken(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matches(String query, String value)
    {
        if (query.isBlank())
        {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public String title()
    {
        return "Transaction Editor";
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public void onSave()
    {
        long draftedRows = splitTable.getItems().stream()
                .filter(r -> !(isBlank(r.account()) && isBlank(r.fund()) && isBlank(r.debit()) && isBlank(r.credit())))
                .count();
        dirty = false;
        status.setText("Draft saved in session with " + draftedRows + " populated split row(s). " + postValidateStatusFor(lastValidationResult));
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return dirty;
    }


    @Override
    public RunCommandResult onRunCommand(AppCommand command)
    {
        if (command != AppCommand.POST_VALIDATE)
        {
            return new RunCommandResult(false, "Unsupported run command: " + command);
        }
        validateOrPost();
        return new RunCommandResult(true, "Validate Lines command delegated to Transaction Editor validation.");
    }

    record ValidationResult(String message, int rowCount, int validCount, int errorCount, BigDecimal netAmount)
    {
    }

    public record SplitRow(String account,
                           String fund,
                           String budgetCategory,
                           String debit,
                           String credit,
                           String activity,
                           String merchant,
                           String counterparty,
                           String nmr,
                           String notes)
    {
        public SplitRow(String account, String fund, String amount, String activity, String merchant, String nmr, String notes)
        {
            this(account, fund, "", amount == null || amount.startsWith("-") ? "" : amount,
                    amount != null && amount.startsWith("-") ? amount.substring(1) : "",
                    activity, merchant, "", nmr, notes);
        }
    }
}
