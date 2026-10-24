package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountClassification;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ExpenseFunction;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.MerchantSettlementAssistantService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Guided canonical Journal entry for an existing merchant receivable settlement. */
public final class MerchantSettlementAssistantPanel implements AppPanel
{
    private final VBox root = new VBox(8);
    private final TableView<MerchantSettlementAssistantService.Receivable> receivables = new TableView<>();
    private final TableView<MerchantSettlementAssistantService.NmrItem> nmrItems = new TableView<>();
    private final DatePicker date = new DatePicker(ActivePeriodContext.get());
    private final ComboBox<Account> bankAccount = new ComboBox<>();
    private final TextField payout = amountField("merchantSettlementPayout", "0.00");
    private final ComboBox<Account> feeAccount = new ComboBox<>();
    private final TextField fee = amountField("merchantSettlementFee", "0.00");
    private final ComboBox<ExpenseFunction> feeFunction = new ComboBox<>();
    private final TextField nmrDischarge = amountField("merchantSettlementNmr", "0.00");
    private final TextField withheld = amountField("merchantSettlementWithheld", "blank means unknown");
    private final TextArea evidence = new TextArea();
    private final TextField memo = new TextField();
    private final Label previewSummary = new Label("Select an open merchant receivable and enter actual settlement components.");
    private final Label status = new Label();
    private final Button save = new Button("Save Settlement");
    private final Supplier<MerchantSettlementAssistantService> services;
    private final Supplier<List<Account>> accounts;
    private final CompanyUiFormat companyFormat = CompanyUiFormat.activeCompany();
    private MerchantSettlementAssistantService.Preview preview;
    private Long savedTransactionId;
    private Runnable capabilitiesChanged = () -> { };

    public MerchantSettlementAssistantPanel()
    {
        this(UiServiceRegistry::merchantSettlementAssistant,
                () -> UiServiceRegistry.accountLookup().listActivePostingAccounts());
    }

    MerchantSettlementAssistantPanel(
            Supplier<MerchantSettlementAssistantService> services,
            Supplier<List<Account>> accounts)
    {
        this.services = Objects.requireNonNull(services, "services");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        build();
        refresh();
    }

    private void build()
    {
        root.setPadding(new Insets(8));
        root.setMinSize(0, 0);
        Label title = new Label("Merchant Settlement Assistant");
        title.getStyleClass().add("panel-title");
        Label guidance = new Label("Preview actual payout, fee, and optional evidenced NMR discharge against one existing merchant receivable. The saved result is one canonical Journal transaction; bank clearing remains in reconciliation.");
        guidance.setWrapText(true);
        Button refresh = new Button("Refresh");
        refresh.setId("merchantSettlementRefresh");
        refresh.setOnAction(event -> refresh());
        Button correction = new Button("Open Journal for Chargeback / Refund");
        correction.setId("merchantSettlementCorrectionRoute");
        correction.setOnAction(event -> DrillThroughCoordinator.openPanelWithContext(
                AppPanelId.JOURNAL_PANE,
                "Use the correction workflow and identify the original merchant receipt or settlement."));
        status.setId("merchantSettlementStatus");
        status.setWrapText(true);
        root.getChildren().add(new VBox(6, title, guidance, new FlowPane(8, 6, refresh, correction), status, new Separator()));

        configureReceivables();
        configureNmrItems();
        receivables.setId("merchantSettlementReceivables");
        nmrItems.setId("merchantSettlementNmrItems");
        receivables.setPlaceholder(new Label("No open designated merchant receivables are available on this date."));
        nmrItems.setPlaceholder(new Label("No open NMR liability items are available. NMR discharge is optional."));

        GridPane form = new GridPane();
        form.setId("merchantSettlementForm");
        form.setHgap(8);
        form.setVgap(8);
        form.setPadding(new Insets(8));
        date.setId("merchantSettlementDate");
        bankAccount.setId("merchantSettlementBank");
        feeAccount.setId("merchantSettlementFeeAccount");
        feeFunction.setId("merchantSettlementFeeFunction");
        feeFunction.getItems().setAll(ExpenseFunction.values());
        evidence.setId("merchantSettlementEvidence");
        evidence.setPromptText("Required for observed withholding or NMR discharge; maximum 500 characters");
        evidence.setPrefRowCount(2);
        memo.setId("merchantSettlementMemo");
        memo.setPromptText("Optional Journal memo");
        accountConverter(bankAccount);
        accountConverter(feeAccount);
        Button previewButton = new Button("Preview Settlement");
        previewButton.setId("merchantSettlementPreview");
        previewButton.setOnAction(event -> preview());
        save.setId("merchantSettlementSave");
        save.setOnAction(event -> onSave());
        UiPermissionGate.gate(save, ApplicationPermission.BOOKKEEPING_WRITE, "Saving merchant settlement");
        Button clearNmr = new Button("Clear NMR Selection");
        clearNmr.setId("merchantSettlementClearNmr");
        clearNmr.setOnAction(event -> nmrItems.getSelectionModel().clearSelection());

        form.addRow(0, new Label("Settlement date"), date);
        form.addRow(1, new Label("Bank payout"), payout, new Label("Bank account"), bankAccount);
        form.addRow(2, new Label("Processor fee"), fee, new Label("Fee account"), feeAccount);
        form.addRow(3, new Label("Fee function"), feeFunction);
        form.addRow(4, new Label("NMR discharge"), nmrDischarge, clearNmr);
        form.addRow(5, new Label("Observed withholding"), withheld);
        form.addRow(6, new Label("Evidence reference"), evidence);
        form.addRow(7, new Label("Memo"), memo);
        form.addRow(8, previewButton, save);
        previewSummary.setId("merchantSettlementPreviewSummary");
        previewSummary.setWrapText(true);
        form.add(previewSummary, 0, 9, 4, 1);
        GridPane.setHgrow(bankAccount, Priority.ALWAYS);
        GridPane.setHgrow(feeAccount, Priority.ALWAYS);
        GridPane.setHgrow(evidence, Priority.ALWAYS);
        GridPane.setHgrow(memo, Priority.ALWAYS);
        bankAccount.setMaxWidth(Double.MAX_VALUE);
        feeAccount.setMaxWidth(Double.MAX_VALUE);

        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setId("merchantSettlementFormScroll");
        formScroll.setFitToWidth(true);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        formScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        SplitPane lower = new SplitPane(nmrItems, formScroll);
        lower.setId("merchantSettlementDetailSplit");
        lower.setDividerPositions(0.42);
        SplitPane workspace = new SplitPane(receivables, lower);
        workspace.setId("merchantSettlementWorkspaceSplit");
        workspace.setOrientation(Orientation.VERTICAL);
        workspace.setDividerPositions(0.42);
        CompanySplitPaneStateBinder.bind(workspace, "merchant-settlement-workspace", 0.42);
        CompanySplitPaneStateBinder.bind(lower, "merchant-settlement-detail", 0.42);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        root.getChildren().add(workspace);

        receivables.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> invalidate());
        nmrItems.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> invalidate());
        date.valueProperty().addListener((obs, oldValue, newValue) -> refresh());
        bankAccount.valueProperty().addListener((obs, oldValue, newValue) -> invalidate());
        feeAccount.valueProperty().addListener((obs, oldValue, newValue) -> invalidate());
        feeFunction.valueProperty().addListener((obs, oldValue, newValue) -> invalidate());
        payout.textProperty().addListener((obs, oldValue, newValue) -> invalidate());
        fee.textProperty().addListener((obs, oldValue, newValue) -> invalidate());
        nmrDischarge.textProperty().addListener((obs, oldValue, newValue) -> invalidate());
        withheld.textProperty().addListener((obs, oldValue, newValue) -> invalidate());
        evidence.textProperty().addListener((obs, oldValue, newValue) -> invalidate());
        memo.textProperty().addListener((obs, oldValue, newValue) -> invalidate());
        companyFormat.install(date);
        updateSaveState();
    }

    private void configureReceivables()
    {
        add(receivables, "Merchant", MerchantSettlementAssistantService.Receivable::merchantName, 170);
        add(receivables, "Description", MerchantSettlementAssistantService.Receivable::description, 220);
        add(receivables, "Open", value -> companyFormat.formatMoney(value.openBalance()), 110);
        add(receivables, "Account", MerchantSettlementAssistantService.Receivable::accountCode, 90);
        add(receivables, "Fund", MerchantSettlementAssistantService.Receivable::fundCode, 90);
        add(receivables, "Event", MerchantSettlementAssistantService.Receivable::eventCode, 100);
    }

    private void configureNmrItems()
    {
        add(nmrItems, "Recipient", MerchantSettlementAssistantService.NmrItem::recipientName, 170);
        add(nmrItems, "Description", MerchantSettlementAssistantService.NmrItem::description, 210);
        add(nmrItems, "Open", value -> companyFormat.formatMoney(value.openBalance()), 110);
        add(nmrItems, "Fund", MerchantSettlementAssistantService.NmrItem::fundCode, 90);
        add(nmrItems, "Event", MerchantSettlementAssistantService.NmrItem::eventCode, 100);
    }

    private static <T> void add(TableView<T> table, String title,
            java.util.function.Function<T, String> value, double width)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        table.getColumns().add(column);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private void refresh()
    {
        invalidate();
        LocalDate asOf = date.getValue() == null ? ActivePeriodContext.get() : date.getValue();
        try
        {
            MerchantSettlementAssistantService service = services.get();
            receivables.getItems().setAll(service.openReceivables(asOf));
            nmrItems.getItems().setAll(service.openNmrItems(asOf));
            List<Account> active = accounts.get();
            bankAccount.getItems().setAll(active.stream().filter(AccountClassification::isBankLedgerAccount).toList());
            feeAccount.getItems().setAll(active.stream().filter(value -> value.getAccountType() == AccountType.EXPENSE).toList());
            status.setText("Loaded " + receivables.getItems().size() + " open merchant receivable(s) and "
                    + nmrItems.getItems().size() + " open NMR item(s) as of " + asOf + ".");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load merchant settlement facts: " + UiErrors.safeMessage(ex));
        }
    }

    private void preview()
    {
        MerchantSettlementAssistantService.Receivable selected = receivables.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select an open merchant receivable before previewing.");
            return;
        }
        try
        {
            MerchantSettlementAssistantService.NmrItem nmr = nmrItems.getSelectionModel().getSelectedItem();
            preview = services.get().preview(new MerchantSettlementAssistantService.Request(
                    selected.itemId(), date.getValue(), id(bankAccount.getValue()), decimal(payout.getText()),
                    id(feeAccount.getValue()), decimal(fee.getText()), feeFunction.getValue(),
                    nmr == null ? null : nmr.itemId(), decimal(nmrDischarge.getText()), optionalDecimal(withheld.getText()),
                    evidence.getText(), memo.getText()));
            savedTransactionId = null;
            previewSummary.setText("Payout " + companyFormat.formatMoney(preview.payout())
                    + "; fee " + companyFormat.formatMoney(preview.fee())
                    + "; NMR discharge " + companyFormat.formatMoney(preview.nmrDischarge())
                    + "; receivable reduction " + companyFormat.formatMoney(preview.total())
                    + "; remaining " + companyFormat.formatMoney(preview.remainingReceivable()) + ".");
            status.setText("Preview is ready. Review the entered components, then save the canonical Journal transaction.");
        }
        catch (RuntimeException ex)
        {
            preview = null;
            status.setText("Could not preview merchant settlement: " + UiErrors.safeMessage(ex));
        }
        updateSaveState();
    }

    private void invalidate()
    {
        preview = null;
        updateSaveState();
    }

    private void updateSaveState()
    {
        save.setDisable(preview == null);
        capabilitiesChanged.run();
    }

    @Override public void onSave()
    {
        if (preview == null)
        {
            throw new IllegalStateException(saveUnavailableReason());
        }
        try
        {
            var result = services.get().save(preview);
            savedTransactionId = result.id();
            preview = null;
            status.setText("Saved merchant settlement transaction " + savedTransactionId
                    + ". Use reconciliation to clear its bank line.");
            updateSaveState();
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save merchant settlement: " + UiErrors.safeMessage(ex));
            throw ex;
        }
    }

    @Override public String title() { return "Merchant Settlement Assistant"; }
    @Override public Node root() { return root; }
    @Override public Set<AppCommand> commandCapabilities()
    {
        return preview != null && UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE)
                ? Set.of(AppCommand.SAVE_ACTIVE) : Set.of();
    }
    @Override public Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return command == AppCommand.SAVE_ACTIVE
                ? Optional.of(ApplicationPermission.BOOKKEEPING_WRITE) : Optional.empty();
    }
    @Override public String commandLabel(AppCommand command)
    {
        return command == AppCommand.SAVE_ACTIVE ? "Save Settlement" : AppPanel.super.commandLabel(command);
    }
    @Override public boolean hasUnsavedChanges() { return preview != null; }
    @Override public String saveUnavailableReason()
    {
        return UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE)
                ? "Preview a valid merchant settlement before saving."
                : UiPermissionGate.deniedExplanation(ApplicationPermission.BOOKKEEPING_WRITE, "Saving merchant settlement");
    }
    @Override public String commandResultMessage(AppCommand command) { return status.getText(); }
    @Override public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        capabilitiesChanged = listener == null ? () -> { } : listener;
        capabilitiesChanged.run();
    }

    Long savedTransactionId() { return savedTransactionId; }

    private static TextField amountField(String id, String prompt)
    {
        TextField field = new TextField();
        field.setId(id);
        field.setPromptText(prompt);
        return field;
    }

    private static void accountConverter(ComboBox<Account> box)
    {
        box.setConverter(new StringConverter<>()
        {
            @Override public String toString(Account value)
            {
                return value == null ? "" : value.getCode() + " — " + value.getName();
            }
            @Override public Account fromString(String value) { return null; }
        });
    }

    private static Long id(Account account) { return account == null ? null : account.getId(); }

    private static BigDecimal decimal(String value)
    {
        return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value.strip());
    }

    private static BigDecimal optionalDecimal(String value)
    {
        return value == null || value.isBlank() ? null : new BigDecimal(value.strip());
    }
}
