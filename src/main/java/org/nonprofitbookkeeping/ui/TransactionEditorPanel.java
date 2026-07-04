package org.nonprofitbookkeeping.ui;

import javafx.event.Event;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.AccountingJournalProjection;
import org.nonprofitbookkeeping.service.TransactionCommandValidator;
import org.nonprofitbookkeeping.service.TransactionEntryService;
import org.nonprofitbookkeeping.service.TransactionValidationResult;
import org.nonprofitbookkeeping.service.TransactionView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents the TransactionEditorPanel component in the nonprofit bookkeeping application.
 */
public class TransactionEditorPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<SplitRow> splitTable = new TableView<>();
    private final Label status = new Label("Prepare split lines, then save to the canonical ledger.");
    private ValidationResult lastValidationResult;
    private final TextField dateField = new TextField();
    private final TextField payeeField = new TextField();
    private final TextField memoField = new TextField();
    private final TextField bankField = new TextField();
    private final Button openSavedInLedger = new Button("Open Saved in Ledger");
    private Long lastSavedTransactionId;
    private boolean dirty;

    public TransactionEditorPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Transaction Editor");
        title.getStyleClass().add("panel-title");

        Button save = new Button("Save");
        Button post = new Button("Validate");
        Button journal = new Button("Journal View");
        openSavedInLedger.setDisable(true);
        HBox actions = new HBox(8, save, post, journal, openSavedInLedger);

        VBox top = new VBox(6, title, actions, status, new Separator(), buildHeaderForm());
        root.setTop(top);

        buildSplitTable();
        root.setCenter(buildSplitEditor());

        save.setOnAction(e -> onSave());
        post.setOnAction(e -> validateOrPost());
        journal.setOnAction(e -> showJournal());
        openSavedInLedger.setOnAction(e -> openSavedTransactionInLedger());

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
        splitTable.setEditable(true);
        splitTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        splitTable.getColumns().add(optionCol("Account", TransactionLineEditorModel.ReferenceData::accounts, SplitRow::account, SplitRow::setAccount));
        splitTable.getColumns().add(optionCol("Fund", TransactionLineEditorModel.ReferenceData::funds, SplitRow::fund, SplitRow::setFund));
        splitTable.getColumns().add(optionCol("Budget", TransactionLineEditorModel.ReferenceData::budgetCategories, SplitRow::budgetCategory, SplitRow::setBudgetCategory));
        splitTable.getColumns().add(editableCol("Debit", SplitRow::debit, SplitRow::setDebit));
        splitTable.getColumns().add(editableCol("Credit", SplitRow::credit, SplitRow::setCredit));
        splitTable.getColumns().add(optionCol("Activity", TransactionLineEditorModel.ReferenceData::activities, SplitRow::activity, SplitRow::setActivity));
        splitTable.getColumns().add(optionCol("Merchant", TransactionLineEditorModel.ReferenceData::merchants, SplitRow::merchant, SplitRow::setMerchant));
        splitTable.getColumns().add(optionCol("Counterparty", TransactionLineEditorModel.ReferenceData::counterparties, SplitRow::counterparty, SplitRow::setCounterparty));
        splitTable.getColumns().add(editableCol("NMR", SplitRow::nmr, SplitRow::setNmr));
        splitTable.getColumns().add(editableCol("Notes", SplitRow::notes, SplitRow::setNotes));

        splitTable.getItems().addAll(
                new SplitRow("", "", "", "", "", "", "", "", "", ""),
                new SplitRow("", "", "", "", "", "", "", "", "", "")
        );
        UiAsync.run("txn-editor-reference-data", () -> UiServiceRegistry.transactionReferenceData().loadActiveReferenceData(),
                referenceData -> {
                    lineEditorModel.replaceOptions(referenceData);
                    splitTable.getProperties().put("referenceData", referenceData);
                    splitTable.refresh();
                },
                ex -> status.setText("Reference choices unavailable: " + UiErrors.safeMessage(ex)));

        splitTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.INSERT)
            {
                addEmptySplitRow();
                event.consume();
            }
        });
    }

    private Node buildSplitEditor()
    {
        Label lbl = new Label("Splits");
        lbl.getStyleClass().add("subheader");

        Button addLine = new Button("+ Add Line");
        Button removeLine = new Button("– Remove");
        ToolBar tb = new ToolBar(addLine, removeLine);

        addLine.setOnAction(e -> {
            addEmptySplitRow();
        });
        removeLine.setOnAction(e -> {
            int selectedIndex = splitTable.getSelectionModel().getSelectedIndex();
            SplitRow sel = splitTable.getSelectionModel().getSelectedItem();
            if (sel != null && lineEditorModel.removeRow(selectedIndex))
            {
                splitTable.getItems().remove(sel);
                dirty = true;
                refreshTotals();
            }
            else
            {
                status.setText("At least two split rows are required for a balanced transaction.");
            }
        });

        VBox box = new VBox(6, lbl, tb, splitTable, totals);
        VBox.setVgrow(splitTable, Priority.ALWAYS);
        return box;
    }

    private void addEmptySplitRow()
    {
        SplitRow row = new SplitRow("", "", "", "", "", "", "", "", "", "");
        splitTable.getItems().add(row);
        lineEditorModel.addRow();
        splitTable.getSelectionModel().select(row);
        splitTable.scrollTo(row);
        splitTable.edit(splitTable.getItems().indexOf(row), splitTable.getColumns().get(0));
        status.setText("Added split row. Enter an account code or choose the Account cell to begin editing.");
        dirty = true;
        refreshTotals();
    }


    private TableColumn<SplitRow, String> optionCol(String name,
                                                     java.util.function.Function<TransactionLineEditorModel.ReferenceData, List<TransactionLineEditorModel.Option>> options,
                                                     java.util.function.Function<SplitRow, String> labelGetter,
                                                     RowOptionSetter setter)
    {
        TableColumn<SplitRow, String> c = new TableColumn<>(name);
        c.setCellValueFactory(v -> new SimpleStringProperty(labelGetter.apply(v.getValue())));
        c.setCellFactory(column -> new OptionCommitCell(options));
        c.setOnEditCommit(event -> {
            TransactionLineEditorModel.Option option = optionByLabel(options, event.getNewValue());
            setter.accept(event.getRowValue(), option);
            syncModelRow(event.getTablePosition().getRow(), event.getRowValue());
            dirty = true;
            refreshTotals();
        });
        return c;
    }

    private TransactionLineEditorModel.Option optionByLabel(java.util.function.Function<TransactionLineEditorModel.ReferenceData, List<TransactionLineEditorModel.Option>> options,
                                                            String label)
    {
        TransactionLineEditorModel.ReferenceData data = currentReferenceData();
        if (data == null || label == null || label.isBlank())
        {
            return null;
        }
        return options.apply(data).stream()
                .filter(option -> option.label().equals(label))
                .findFirst()
                .orElse(null);
    }

    private TransactionLineEditorModel.ReferenceData currentReferenceData()
    {
        Object data = splitTable.getProperties().get("referenceData");
        return data instanceof TransactionLineEditorModel.ReferenceData referenceData ? referenceData : null;
    }

    private final class OptionCommitCell extends TableCell<SplitRow, String>
    {
        private final java.util.function.Function<TransactionLineEditorModel.ReferenceData, List<TransactionLineEditorModel.Option>> options;
        private ComboBox<String> editor;

        private OptionCommitCell(java.util.function.Function<TransactionLineEditorModel.ReferenceData, List<TransactionLineEditorModel.Option>> options)
        {
            this.options = options;
        }

        @Override
        public void startEdit()
        {
            if (!isEditable() || !getTableView().isEditable() || !getTableColumn().isEditable())
            {
                return;
            }
            super.startEdit();
            if (editor == null)
            {
                editor = new ComboBox<>();
                editor.setMaxWidth(Double.MAX_VALUE);
                editor.setOnAction(event -> commitEdit(editor.getValue()));
                editor.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
                    if (!isFocused)
                    {
                        commitEdit(editor.getValue());
                    }
                });
            }
            TransactionLineEditorModel.ReferenceData data = currentReferenceData();
            editor.getItems().setAll(data == null ? List.of() : options.apply(data).stream().map(TransactionLineEditorModel.Option::label).toList());
            editor.setValue(getItem() == null ? "" : getItem());
            setText(null);
            setGraphic(editor);
            editor.show();
        }

        @Override
        public void updateItem(String item, boolean empty)
        {
            super.updateItem(item, empty);
            if (empty)
            {
                setText(null);
                setGraphic(null);
            }
            else if (isEditing())
            {
                setText(null);
                setGraphic(editor);
            }
            else
            {
                setText(item == null ? "" : item);
                setGraphic(null);
            }
        }
    }

    @FunctionalInterface
    private interface RowOptionSetter
    {
        void accept(SplitRow row, TransactionLineEditorModel.Option option);
    }

    private TableColumn<SplitRow, String> editableCol(String name,
                                                      java.util.function.Function<SplitRow, String> getter,
                                                      java.util.function.BiConsumer<SplitRow, String> setter)
    {
        TableColumn<SplitRow, String> c = new TableColumn<>(name);
        c.setCellValueFactory(v -> new SimpleStringProperty(getter.apply(v.getValue())));
        c.setCellFactory(column -> new FocusCommitTextCell());
        c.setOnEditCommit(event -> {
            setter.accept(event.getRowValue(), event.getNewValue());
            syncModelRow(event.getTablePosition().getRow(), event.getRowValue());
            dirty = true;
            refreshTotals();
        });
        return c;
    }

    private static class FocusCommitTextCell extends TableCell<SplitRow, String>
    {
        private TextField editor;

        @Override
        public void startEdit()
        {
            if (!isEditable() || !getTableView().isEditable() || !getTableColumn().isEditable())
            {
                return;
            }
            super.startEdit();
            if (editor == null)
            {
                editor = new TextField();
                editor.setOnAction(event -> commitEditorValue());
                editor.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
                    if (!isFocused)
                    {
                        commitEditorValue();
                    }
                });
            }
            editor.setText(getItem() == null ? "" : getItem());
            setText(null);
            setGraphic(editor);
            editor.selectAll();
            editor.requestFocus();
        }

        @Override
        public void cancelEdit()
        {
            if (editor != null && !Objects.equals(getItem(), editor.getText()))
            {
                commitEditorValue();
                return;
            }
            super.cancelEdit();
            setText(getItem() == null ? "" : getItem());
            setGraphic(null);
        }

        @Override
        public void updateItem(String item, boolean empty)
        {
            super.updateItem(item, empty);
            if (empty)
            {
                setText(null);
                setGraphic(null);
            }
            else if (isEditing())
            {
                if (editor != null)
                {
                    editor.setText(item == null ? "" : item);
                    setText(null);
                    setGraphic(editor);
                }
            }
            else
            {
                setText(item == null ? "" : item);
                setGraphic(null);
            }
        }

        private void commitEditorValue()
        {
            if (editor == null)
            {
                return;
            }
            String value = editor.getText();
            if (isEditing())
            {
                commitEdit(value);
            }
            else
            {
                commitValueWhenFocusLossAlreadyCancelled(value);
            }
        }

        @Override
        public void commitEdit(String newValue)
        {
            super.commitEdit(newValue);
            setText(newValue == null ? "" : newValue);
            setGraphic(null);
        }

        private void commitValueWhenFocusLossAlreadyCancelled(String value)
        {
            TableView<SplitRow> table = getTableView();
            TableColumn<SplitRow, String> column = getTableColumn();
            if (table == null || column == null || getIndex() < 0 || getIndex() >= table.getItems().size())
            {
                return;
            }
            CellEditEvent<SplitRow, String> event = new CellEditEvent<>(
                    table,
                    new TablePosition<>(table, getIndex(), column),
                    TableColumn.editCommitEvent(),
                    value);
            Event.fireEvent(column, event);
            updateItem(value, false);
        }
    }

    private void validateOrPost()
    {
        TransactionValidationResult result = lineEditorModel.validate(parseDateOrNull(dateField.getText()), null, memoField.getText(), null);
        lastValidationResult = new ValidationResult(
                result.valid() ? "Validation result: ready to save through the transaction service."
                        : "Validation result: " + String.join(" ", result.errors()),
                lineEditorModel.toCommand(parseDateOrNull(dateField.getText()), null, memoField.getText(), null).lines().size(),
                result.valid() ? lineEditorModel.toCommand(parseDateOrNull(dateField.getText()), null, memoField.getText(), null).lines().size() : 0,
                result.valid() ? 0 : result.errors().size(),
                lineEditorModel.totals().difference());
        status.setText(lastValidationResult.message());
    }

    private static LocalDate parseDateOrNull(String value)
    {
        if (isBlank(value))
        {
            return null;
        }
        try
        {
            return LocalDate.parse(value.trim());
        }
        catch (RuntimeException ex)
        {
            return null;
        }
    }

    private void syncModelRow(int index, SplitRow splitRow)
    {
        if (index < 0 || index >= lineEditorModel.rows().size())
        {
            return;
        }
        TransactionLineEditorModel.Row row = lineEditorModel.rows().get(index);
        row.setAccountId(splitRow.accountId());
        row.setFundId(splitRow.fundId());
        row.setBudgetCategoryId(splitRow.budgetCategoryId());
        row.setActivityId(splitRow.activityId());
        row.setMerchantId(splitRow.merchantId());
        row.setCounterpartyId(splitRow.counterpartyId());
        row.setDebit(parseOptionalAmount(splitRow.debit()));
        row.setCredit(parseOptionalAmount(splitRow.credit()));
        row.setNmr(Boolean.parseBoolean(splitRow.nmr()));
        row.setNotes(splitRow.notes());
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
            String accountToken = row.account().contains(" — ") ? row.account().substring(0, row.account().indexOf(" — ")) : row.account();
            String fundToken = row.fund().contains(" — ") ? row.fund().substring(0, row.fund().indexOf(" — ")) : row.fund();
            if (isBlank(accountToken) || !accountCodes.contains(accountToken.trim()))
            {
                rowValid = false;
            }
            if (isBlank(fundToken) || !fundCodes.contains(fundToken.trim()))
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
            message += " (ready to save)";
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
            return "Validate completed: run validation first to review row readiness.";
        }
        if (result.errorCount() > 0)
        {
            return "Validate blocked: fix validation errors before posting.";
        }
        if (result.netAmount().compareTo(BigDecimal.ZERO) != 0)
        {
            return "Validate blocked: split rows are not balanced (net=" + result.netAmount().toPlainString() + ").";
        }
        return "Validate accepted: transaction is balanced and ready to save.";
    }

    private void showJournal()
    {
        if (lastSavedTransactionId == null)
        {
            status.setText("Journal preview unavailable until the transaction has been saved through the transaction service.");
            return;
        }
        status.setText("Loading journal preview for saved transaction #" + lastSavedTransactionId + "...");
        UiAsync.run("txn-editor-journal-preview", () -> UiServiceRegistry.transactionEntry().journalView(lastSavedTransactionId),
                preview -> status.setText(renderJournalPreview(preview)),
                ex -> status.setText("Journal preview failed: " + UiErrors.safeMessage(ex)));
    }

    static String renderJournalPreview(AccountingJournalProjection projection)
    {
        StringBuilder body = new StringBuilder();
        body.append("Journal preview: Txn #")
                .append(projection.transactionId())
                .append(" on ")
                .append(projection.date())
                .append(" (lines: ")
                .append(projection.lines().size())
                .append(")");
        if (!projection.lines().isEmpty())
        {
            AccountingJournalProjection.Line first = projection.lines().get(0);
            body.append(" | first line ")
                    .append(first.accountCode())
                    .append("/")
                    .append(first.fundCode() == null ? "" : first.fundCode())
                    .append(" DR=")
                    .append(first.debit().toPlainString())
                    .append(" CR=")
                    .append(first.credit().toPlainString());
        }
        return body.toString();
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
        LocalDate date = parseDateOrNull(dateField.getText());
        if (date == null)
        {
            status.setText("Save blocked: enter a transaction date as YYYY-MM-DD.");
            return;
        }
        for (int i = 0; i < splitTable.getItems().size(); i++)
        {
            syncModelRow(i, splitTable.getItems().get(i));
        }
        TransactionEntryService service = UiServiceRegistry.transactionEntry();
        UiAsync.run("txn-editor-save", () -> service.enter(lineEditorModel.toCommand(date, null, memoField.getText(), null)),
                view -> {
                    lastSavedTransactionId = view.id();
                    applySavedView(view);
                    dirty = false;
                    lineEditorModel.markClean();
                    status.setText("Saved transaction #" + view.id() + " through TransactionEntryService with "
                            + view.lines().size() + " split line(s). Use Journal View to preview the persisted journal.");
                },
                ex -> status.setText("Save failed: " + UiErrors.safeMessage(ex)));
    }

    private void applySavedView(TransactionView view)
    {
        dateField.setText(view.date() == null ? "" : view.date().toString());
        memoField.setText(view.memo() == null ? "" : view.memo());
        payeeField.setText(view.payeeName() == null ? "" : view.payeeName());
        bankField.setText(view.bankAccountName() == null ? "" : view.bankAccountName());
        splitTable.getItems().setAll(view.lines().stream().map(SplitRow::fromViewLine).toList());
        while (lineEditorModel.rows().size() < splitTable.getItems().size())
        {
            lineEditorModel.addRow();
        }
        for (int i = 0; i < splitTable.getItems().size(); i++)
        {
            syncModelRow(i, splitTable.getItems().get(i));
        }
        for (int i = splitTable.getItems().size(); i < lineEditorModel.rows().size(); i++)
        {
            TransactionLineEditorModel.Row row = lineEditorModel.rows().get(i);
            row.setAccountId(null);
            row.setFundId(null);
            row.setBudgetCategoryId(null);
            row.setActivityId(null);
            row.setMerchantId(null);
            row.setCounterpartyId(null);
            row.setDebit(BigDecimal.ZERO);
            row.setCredit(BigDecimal.ZERO);
            row.setNmr(false);
            row.setNotes("");
        }
    }

    private static String normalizeCode(String value)
    {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
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
        status.setText("Saving transaction to the canonical ledger...");
        UiAsync.run("txn-editor-save", this::saveToCanonicalLedger,
                saved -> {
                    dirty = false;
                    lastSavedTransactionId = saved.id();
                    openSavedInLedger.setDisable(false);
                    status.setText("Saved Txn #" + saved.id() + " to the canonical ledger with "
                            + saved.lines().size() + " split line(s). Use Open Saved in Ledger to review it.");
                },
                ex -> status.setText("Save failed: " + UiErrors.safeMessage(ex)));
    }
    private void openSavedTransactionInLedger()
    {
        if (lastSavedTransactionId == null)
        {
            status.setText("Save a transaction before opening it in the ledger register.");
            return;
        }
        DrillThroughCoordinator.openLedgerWithContext(savedLedgerContext(lastSavedTransactionId));
    }

    static String savedLedgerContext(long transactionId)
    {
        return "Saved transaction Txn #" + transactionId;
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
        return new RunCommandResult(true, "Validate command delegated to Transaction Editor validation.");
    }

    record ValidationResult(String message, int rowCount, int validCount, int errorCount, BigDecimal netAmount)
    {
    }

    public static class SplitRow
    {
        private Long accountId;
        private Long fundId;
        private Long budgetCategoryId;
        private Long activityId;
        private Long merchantId;
        private Long counterpartyId;
        private String account;
        private String fund;
        private String budgetCategory;
        private String debit;
        private String credit;
        private String activity;
        private String merchant;
        private String counterparty;
        private String nmr;
        private String notes;

        public SplitRow(String account, String fund, String amount, String activity, String merchant, String nmr, String notes)
        {
            this(account, fund, "", amount == null || amount.startsWith("-") ? "" : amount,
                    amount != null && amount.startsWith("-") ? amount.substring(1) : "",
                    activity, merchant, "", nmr, notes);
        }

        public SplitRow(String account,
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
            this.account = value(account);
            this.fund = value(fund);
            this.budgetCategory = value(budgetCategory);
            this.debit = value(debit);
            this.credit = value(credit);
            this.activity = value(activity);
            this.merchant = value(merchant);
            this.counterparty = value(counterparty);
            this.nmr = value(nmr);
            this.notes = value(notes);
        }

        static SplitRow fromViewLine(TransactionView.Line line)
        {
            SplitRow row = new SplitRow(
                    label(line.accountCode(), line.accountName()),
                    label(line.fundCode(), line.fundName()),
                    "",
                    line.debit().signum() == 0 ? "" : line.debit().toPlainString(),
                    line.credit().signum() == 0 ? "" : line.credit().toPlainString(),
                    "",
                    "",
                    "",
                    Boolean.toString(line.nmr()),
                    line.notes());
            row.accountId = line.accountId();
            row.fundId = line.fundId();
            row.budgetCategoryId = line.budgetCategoryId();
            row.activityId = line.activityId();
            row.merchantId = line.merchantId();
            return row;
        }

        public Long accountId() { return accountId; }
        public Long fundId() { return fundId; }
        public Long budgetCategoryId() { return budgetCategoryId; }
        public Long activityId() { return activityId; }
        public Long merchantId() { return merchantId; }
        public Long counterpartyId() { return counterpartyId; }

        public String account() { return account; }
        public void setAccount(String account) { this.account = value(account); }
        public void setAccount(TransactionLineEditorModel.Option option) { this.accountId = id(option); this.account = label(option); }
        public String fund() { return fund; }
        public void setFund(String fund) { this.fund = value(fund); }
        public void setFund(TransactionLineEditorModel.Option option) { this.fundId = id(option); this.fund = label(option); }
        public String budgetCategory() { return budgetCategory; }
        public void setBudgetCategory(String budgetCategory) { this.budgetCategory = value(budgetCategory); }
        public void setBudgetCategory(TransactionLineEditorModel.Option option) { this.budgetCategoryId = id(option); this.budgetCategory = label(option); }
        public String debit() { return debit; }
        public void setDebit(String debit) { this.debit = value(debit); }
        public String credit() { return credit; }
        public void setCredit(String credit) { this.credit = value(credit); }
        public String amount() { return debit.isBlank() ? credit : debit; }
        public String activity() { return activity; }
        public void setActivity(String activity) { this.activity = value(activity); }
        public void setActivity(TransactionLineEditorModel.Option option) { this.activityId = id(option); this.activity = label(option); }
        public String merchant() { return merchant; }
        public void setMerchant(String merchant) { this.merchant = value(merchant); }
        public void setMerchant(TransactionLineEditorModel.Option option) { this.merchantId = id(option); this.merchant = label(option); }
        public String counterparty() { return counterparty; }
        public void setCounterparty(String counterparty) { this.counterparty = value(counterparty); }
        public void setCounterparty(TransactionLineEditorModel.Option option) { this.counterpartyId = id(option); this.counterparty = label(option); }
        public String nmr() { return nmr; }
        public void setNmr(String nmr) { this.nmr = value(nmr); }
        public String notes() { return notes; }
        public void setNotes(String notes) { this.notes = value(notes); }

        private static Long id(TransactionLineEditorModel.Option option)
        {
            return option == null ? null : option.id();
        }

        private static String label(TransactionLineEditorModel.Option option)
        {
            return option == null ? "" : option.label();
        }

        private static String label(String code, String name)
        {
            String safeCode = code == null ? "" : code;
            String safeName = name == null ? "" : name;
            return (safeCode + " — " + safeName).trim();
        }

        private static String value(String value)
        {
            return value == null ? "" : value;
        }
    }
}
