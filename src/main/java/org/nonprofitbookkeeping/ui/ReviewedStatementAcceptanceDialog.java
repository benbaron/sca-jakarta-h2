package org.nonprofitbookkeeping.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.ReviewedStatementAcceptanceService;
import org.nonprofitbookkeeping.service.TransactionCommand;
import org.nonprofitbookkeeping.service.TransactionCommandValidator;
import org.nonprofitbookkeeping.service.TransactionLineCommand;
import org.nonprofitbookkeeping.service.TransactionValidationResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Modal editor for explicit reviewed-statement acceptance into the canonical ledger. */
final class ReviewedStatementAcceptanceDialog extends Dialog<ReviewedStatementAcceptanceDialog.AcceptanceDraft>
{
    private final ReviewedStatementAcceptanceService.AcceptancePreview preview;
    private final CompanyUiFormat companyFormat = CompanyUiFormat.activeCompany();
    private final DatePicker date = new DatePicker();
    private final TextArea memo = new TextArea();
    private final ComboBox<TransactionLineEditorModel.Option> payee = new ComboBox<>();
    private final ComboBox<TransactionLineEditorModel.Option> bankFund = new ComboBox<>();
    private final ComboBox<TransactionLineEditorModel.Option> bankActivity = new ComboBox<>();
    private final TableView<CounterSplit> counterSplits = new TableView<>();
    private final ComboBox<TransactionLineEditorModel.Option> splitAccount = new ComboBox<>();
    private final ComboBox<TransactionLineEditorModel.Option> splitFund = new ComboBox<>();
    private final ComboBox<TransactionLineEditorModel.Option> splitActivity = new ComboBox<>();
    private final ComboBox<TransactionLineEditorModel.Option> splitMerchant = new ComboBox<>();
    private final TextField splitDebit = new TextField();
    private final TextField splitCredit = new TextField();
    private final TextField splitNotes = new TextField();
    private final Label validation = new Label();
    private final CheckBox probableDuplicateConfirmation = new CheckBox(
            "I reviewed the probable-duplicate warning and explicitly want to create this transaction.");

    ReviewedStatementAcceptanceDialog(
            ReviewedStatementAcceptanceService.AcceptancePreview preview,
            TransactionLineEditorModel.ReferenceData referenceData)
    {
        this.preview = Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(referenceData, "referenceData");
        setTitle("Create Transaction from Reviewed Row");
        setHeaderText("Review the frozen bank source and complete a balanced canonical transaction.");
        getDialogPane().getButtonTypes().setAll(
                new ButtonType("Create Transaction", ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL);
        getDialogPane().setPrefWidth(980);
        getDialogPane().setPrefHeight(760);

        companyFormat.install(date);
        configureChoices(referenceData);
        configureDefaults();
        buildCounterTable();
        getDialogPane().setContent(buildContent());
        FullTextTooltipInstaller.install(getDialogPane());

        Button accept = (Button) getDialogPane().lookupButton(getDialogPane().getButtonTypes().get(0));
        UiPermissionGate.gate(
                accept,
                ApplicationPermission.BOOKKEEPING_WRITE,
                "Create a transaction from the reviewed bank row");
        accept.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try
            {
                AcceptanceDraft draft = buildDraft();
                TransactionValidationResult result = new TransactionCommandValidator().validate(draft.command());
                if (!result.valid())
                {
                    validation.setText(String.join(" ", result.errors()));
                    event.consume();
                }
                else if (preview.probableDuplicate() && !draft.probableDuplicateConfirmed())
                {
                    validation.setText("Explicit probable-duplicate confirmation is required before acceptance.");
                    event.consume();
                }
            }
            catch (RuntimeException ex)
            {
                validation.setText(UiErrors.safeMessage(ex));
                event.consume();
            }
        });
        setResultConverter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE
                ? buildDraft()
                : null);
    }

    private void configureChoices(TransactionLineEditorModel.ReferenceData data)
    {
        payee.setItems(FXCollections.observableArrayList(data.counterparties()));
        bankFund.setItems(FXCollections.observableArrayList(data.funds()));
        bankActivity.setItems(withBlank(data.activities()));
        splitAccount.setItems(FXCollections.observableArrayList(data.accounts().stream()
                .filter(option -> !Objects.equals(option.id(), preview.ledgerAccountId()))
                .toList()));
        splitFund.setItems(FXCollections.observableArrayList(data.funds()));
        splitActivity.setItems(withBlank(data.activities()));
        splitMerchant.setItems(withBlank(data.merchants()));
        List.of(payee, bankFund, bankActivity, splitAccount, splitFund, splitActivity, splitMerchant)
                .forEach(combo -> {
                    combo.setMaxWidth(Double.MAX_VALUE);
                    combo.setButtonCell(new OptionListCell());
                    combo.setCellFactory(list -> new OptionListCell());
                });
        if (data.funds().size() == 1)
        {
            bankFund.getSelectionModel().selectFirst();
            splitFund.getSelectionModel().selectFirst();
        }
    }

    private static javafx.collections.ObservableList<TransactionLineEditorModel.Option> withBlank(
            List<TransactionLineEditorModel.Option> values)
    {
        List<TransactionLineEditorModel.Option> result = new ArrayList<>();
        result.add(new TransactionLineEditorModel.Option(null, "", "(None)"));
        result.addAll(values);
        return FXCollections.observableArrayList(result);
    }

    private void configureDefaults()
    {
        date.setValue(preview.effectiveSourceDate());
        String defaultMemo = blank(preview.memo()).isBlank() ? blank(preview.payeeName()) : preview.memo();
        memo.setText(defaultMemo);
        memo.setWrapText(true);
        memo.setPrefRowCount(2);
        probableDuplicateConfirmation.setVisible(preview.probableDuplicate());
        probableDuplicateConfirmation.setManaged(preview.probableDuplicate());

        BigDecimal amount = preview.amount();
        if (amount.signum() >= 0)
        {
            splitCredit.setText(amount.abs().toPlainString());
            splitDebit.setText("");
        }
        else
        {
            splitDebit.setText(amount.abs().toPlainString());
            splitCredit.setText("");
        }
        splitNotes.setText(defaultMemo);
    }

    private VBox buildContent()
    {
        Label sourceHeading = heading("Frozen reviewed source");
        GridPane source = new GridPane();
        source.setHgap(12);
        source.setVgap(5);
        int row = 0;
        sourceRow(source, row++, "Row", Long.toString(preview.statementLineId()));
        sourceRow(source, row++, "Configured account", blank(preview.bankAccountName()));
        sourceRow(source, row++, "Ledger bank account", preview.ledgerAccountCode() + " — " + preview.ledgerAccountName());
        sourceRow(source, row++, "Source date", companyFormat.formatDate(preview.effectiveSourceDate()));
        sourceRow(source, row++, "Source amount", companyFormat.formatMoney(preview.amount()) + " " + blank(preview.currency()));
        sourceRow(source, row++, "Source name", blank(preview.payeeName()));
        sourceRow(source, row++, "Source memo", blank(preview.memo()));
        sourceRow(source, row++, "Reference / source ID", blank(preview.reference()) + " / " + blank(preview.sourceTransactionId()));
        sourceRow(source, row++, "Eligibility", preview.eligibilityMessage());

        TextArea issues = new TextArea(String.join("\n", preview.issues()));
        issues.setEditable(false);
        issues.setWrapText(true);
        issues.setPrefRowCount(Math.min(4, Math.max(1, preview.issues().size())));
        issues.setPromptText("No durable import issues on this row.");

        GridPane header = new GridPane();
        header.setHgap(10);
        header.setVgap(8);
        header.add(new Label("Transaction date"), 0, 0); header.add(date, 1, 0);
        header.add(new Label("Payee / counterparty"), 0, 1); header.add(payee, 1, 1);
        header.add(new Label("Memo"), 0, 2); header.add(memo, 1, 2);
        header.add(new Label("Bank split fund"), 0, 3); header.add(bankFund, 1, 3);
        header.add(new Label("Bank split activity"), 0, 4); header.add(bankActivity, 1, 4);
        GridPane.setHgrow(memo, Priority.ALWAYS);

        Button addSplit = new Button("Add Counter Split");
        addSplit.setOnAction(event -> addCounterSplit());
        Button replaceSplit = new Button("Replace Selected");
        replaceSplit.setOnAction(event -> replaceCounterSplit());
        Button removeSplit = new Button("Remove Selected");
        removeSplit.setOnAction(event -> {
            CounterSplit selected = counterSplits.getSelectionModel().getSelectedItem();
            if (selected != null) counterSplits.getItems().remove(selected);
            refreshValidation();
        });
        GridPane splitEditor = new GridPane();
        splitEditor.setHgap(8); splitEditor.setVgap(6);
        splitEditor.add(new Label("Account"), 0, 0); splitEditor.add(splitAccount, 1, 0);
        splitEditor.add(new Label("Fund"), 2, 0); splitEditor.add(splitFund, 3, 0);
        splitEditor.add(new Label("Activity"), 0, 1); splitEditor.add(splitActivity, 1, 1);
        splitEditor.add(new Label("Merchant"), 2, 1); splitEditor.add(splitMerchant, 3, 1);
        splitEditor.add(new Label("Debit"), 0, 2); splitEditor.add(splitDebit, 1, 2);
        splitEditor.add(new Label("Credit"), 2, 2); splitEditor.add(splitCredit, 3, 2);
        splitEditor.add(new Label("Notes"), 0, 3); splitEditor.add(splitNotes, 1, 3, 3, 1);
        splitEditor.add(new HBox(8, addSplit, replaceSplit, removeSplit), 0, 4, 4, 1);

        validation.setWrapText(true);
        validation.setStyle("-fx-font-weight: bold;");
        probableDuplicateConfirmation.setWrapText(true);

        VBox body = new VBox(10,
                sourceHeading, source,
                new Label("Import issues"), issues,
                heading("Canonical transaction header and bank split"), header,
                heading("Counter-account splits"), counterSplits, splitEditor,
                probableDuplicateConfirmation,
                validation);
        body.setPadding(new Insets(8));
        VBox.setVgrow(counterSplits, Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        VBox outer = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        refreshValidation();
        return outer;
    }

    private void buildCounterTable()
    {
        counterSplits.setPrefHeight(180);
        TableColumn<CounterSplit, String> account = textColumn("Account", value -> value.account().label());
        TableColumn<CounterSplit, String> fund = textColumn("Fund", value -> value.fund().label());
        TableColumn<CounterSplit, String> activity = textColumn("Activity", value -> label(value.activity()));
        TableColumn<CounterSplit, String> merchant = textColumn("Merchant", value -> label(value.merchant()));
        TableColumn<CounterSplit, String> debit = textColumn("Debit", value -> money(value.debit()));
        TableColumn<CounterSplit, String> credit = textColumn("Credit", value -> money(value.credit()));
        TableColumn<CounterSplit, String> notes = textColumn("Notes", CounterSplit::notes);
        counterSplits.getColumns().addAll(account, fund, activity, merchant, debit, credit, notes);
        counterSplits.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        counterSplits.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> loadSplit(selected));
    }

    private void addCounterSplit()
    {
        try
        {
            counterSplits.getItems().add(readSplitEditor());
            clearSplitAmounts();
            refreshValidation();
        }
        catch (RuntimeException ex)
        {
            validation.setText(UiErrors.safeMessage(ex));
        }
    }

    private void replaceCounterSplit()
    {
        int index = counterSplits.getSelectionModel().getSelectedIndex();
        if (index < 0)
        {
            validation.setText("Select a counter split to replace.");
            return;
        }
        try
        {
            counterSplits.getItems().set(index, readSplitEditor());
            counterSplits.getSelectionModel().select(index);
            refreshValidation();
        }
        catch (RuntimeException ex)
        {
            validation.setText(UiErrors.safeMessage(ex));
        }
    }

    private CounterSplit readSplitEditor()
    {
        TransactionLineEditorModel.Option account = required(splitAccount.getValue(), "Counter account");
        TransactionLineEditorModel.Option fund = required(splitFund.getValue(), "Counter fund");
        BigDecimal debit = parseAmount(splitDebit.getText());
        BigDecimal credit = parseAmount(splitCredit.getText());
        if (debit.signum() < 0 || credit.signum() < 0 || (debit.signum() > 0 && credit.signum() > 0)
                || (debit.signum() == 0 && credit.signum() == 0))
        {
            throw new IllegalArgumentException("Counter split requires one positive debit or credit amount.");
        }
        return new CounterSplit(
                account, fund, optional(splitActivity.getValue()), optional(splitMerchant.getValue()),
                debit, credit, splitNotes.getText() == null ? "" : splitNotes.getText().trim());
    }

    private AcceptanceDraft buildDraft()
    {
        TransactionLineEditorModel.Option fund = required(bankFund.getValue(), "Bank split fund");
        List<TransactionLineCommand> lines = new ArrayList<>();
        lines.add(bankLine(fund, optional(bankActivity.getValue())));
        counterSplits.getItems().stream().map(CounterSplit::toCommand).forEach(lines::add);
        TransactionLineEditorModel.Option selectedPayee = payee.getValue();
        TransactionCommand command = new TransactionCommand(
                date.getValue(),
                selectedPayee == null ? null : selectedPayee.id(),
                memo.getText() == null ? "" : memo.getText().trim(),
                preview.ledgerAccountId(),
                lines);
        return new AcceptanceDraft(command, probableDuplicateConfirmation.isSelected());
    }

    private TransactionLineCommand bankLine(
            TransactionLineEditorModel.Option fund,
            TransactionLineEditorModel.Option activity)
    {
        BigDecimal amount = preview.amount().abs();
        boolean debit = preview.ledgerAccountNormalBalance() == NormalBalance.DEBIT
                ? preview.amount().signum() > 0
                : preview.amount().signum() < 0;
        return new TransactionLineCommand(
                preview.ledgerAccountId(), fund.id(), null,
                activity == null ? null : activity.id(), null,
                debit ? amount : BigDecimal.ZERO,
                debit ? BigDecimal.ZERO : amount,
                false,
                "Reviewed bank statement row " + preview.statementLineId());
    }

    private void refreshValidation()
    {
        try
        {
            AcceptanceDraft draft = buildDraft();
            TransactionValidationResult result = new TransactionCommandValidator().validate(draft.command());
            validation.setText(result.valid()
                    ? "Balanced canonical transaction is ready for explicit creation."
                    : String.join(" ", result.errors()));
        }
        catch (RuntimeException ex)
        {
            validation.setText(UiErrors.safeMessage(ex));
        }
    }

    private void loadSplit(CounterSplit value)
    {
        if (value == null) return;
        splitAccount.setValue(value.account());
        splitFund.setValue(value.fund());
        splitActivity.setValue(value.activity() == null ? splitActivity.getItems().get(0) : value.activity());
        splitMerchant.setValue(value.merchant() == null ? splitMerchant.getItems().get(0) : value.merchant());
        splitDebit.setText(money(value.debit()));
        splitCredit.setText(money(value.credit()));
        splitNotes.setText(value.notes());
    }

    private void clearSplitAmounts()
    {
        splitDebit.clear();
        splitCredit.clear();
        splitNotes.clear();
        counterSplits.getSelectionModel().clearSelection();
    }

    private static void sourceRow(GridPane pane, int row, String label, String value)
    {
        Label key = new Label(label);
        key.setStyle("-fx-font-weight: bold;");
        Label text = new Label(blank(value));
        text.setWrapText(true);
        pane.add(key, 0, row);
        pane.add(text, 1, row);
        GridPane.setHgrow(text, Priority.ALWAYS);
    }

    private static Label heading(String value)
    {
        Label label = new Label(value);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private static <T> TableColumn<T, String> textColumn(String title, java.util.function.Function<T, String> value)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private static BigDecimal parseAmount(String text)
    {
        if (text == null || text.isBlank()) return BigDecimal.ZERO;
        try
        {
            BigDecimal parsed = CompanyUiFormat.parseMoneyLenient(text);
            if (parsed == null)
            {
                throw new NumberFormatException(text);
            }
            return parsed;
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalArgumentException("Debit and credit amounts must be numeric.");
        }
    }

    private static TransactionLineEditorModel.Option required(TransactionLineEditorModel.Option value, String label)
    {
        if (value == null || value.id() == null)
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    private static TransactionLineEditorModel.Option optional(TransactionLineEditorModel.Option value)
    {
        return value == null || value.id() == null ? null : value;
    }

    private static String label(TransactionLineEditorModel.Option value)
    {
        return value == null ? "" : value.label();
    }

    private static String money(BigDecimal value)
    {
        return value == null || value.signum() == 0 ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String blank(String value)
    {
        return value == null ? "" : value;
    }

    record AcceptanceDraft(TransactionCommand command, boolean probableDuplicateConfirmed) { }

    private record CounterSplit(
            TransactionLineEditorModel.Option account,
            TransactionLineEditorModel.Option fund,
            TransactionLineEditorModel.Option activity,
            TransactionLineEditorModel.Option merchant,
            BigDecimal debit,
            BigDecimal credit,
            String notes)
    {
        TransactionLineCommand toCommand()
        {
            return new TransactionLineCommand(
                    account.id(), fund.id(), null,
                    activity == null ? null : activity.id(),
                    merchant == null ? null : merchant.id(),
                    debit, credit, false, notes);
        }
    }

    private static final class OptionListCell extends javafx.scene.control.ListCell<TransactionLineEditorModel.Option>
    {
        @Override
        protected void updateItem(TransactionLineEditorModel.Option item, boolean empty)
        {
            super.updateItem(item, empty);
            setText(empty || item == null ? "" : item.label());
        }
    }
}
