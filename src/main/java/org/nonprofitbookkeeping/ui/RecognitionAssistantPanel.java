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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ExpenseFunction;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.SupplementalOpenItemQueryService;
import org.nonprofitbookkeeping.service.SupplementalRecognitionService;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Explicit preview-and-accept workflow for prepaid expense and deferred revenue recognition. */
public final class RecognitionAssistantPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final ComboBox<SupplementalOpenItemQueryService.Kind> kind = new ComboBox<>();
    private final TableView<SupplementalOpenItemQueryService.Row> items = new TableView<>();
    private final DatePicker serviceStart = new DatePicker();
    private final DatePicker serviceEnd = new DatePicker();
    private final DatePicker runThrough = new DatePicker(ActivePeriodContext.get());
    private final ComboBox<Account> destination = new ComboBox<>();
    private final ComboBox<ExpenseFunction> expenseFunction = new ComboBox<>();
    private final TableView<SupplementalRecognitionService.Proposal> proposals = new TableView<>();
    private final Label summary = new Label();
    private final Label status = new Label();
    private final Button save = new Button("Save Selected Recognition");
    private final CompanyUiFormat companyFormat = CompanyUiFormat.activeCompany();
    private SupplementalRecognitionService.Preview currentPreview;
    private Runnable capabilitiesChanged = () -> { };

    public RecognitionAssistantPanel()
    {
        root.setPadding(new Insets(8));
        root.setMinSize(0, 0);
        kind.setId("recognitionKind");
        kind.getItems().setAll(SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE,
                SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE);
        kind.setValue(SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE);
        kind.setConverter(new StringConverter<>()
        {
            @Override public String toString(SupplementalOpenItemQueryService.Kind value)
            { return value == null ? "" : value.displayName(); }
            @Override public SupplementalOpenItemQueryService.Kind fromString(String value) { return null; }
        });

        items.setId("recognitionItems");
        proposals.setId("recognitionProposals");
        serviceStart.setId("recognitionServiceStart");
        serviceEnd.setId("recognitionServiceEnd");
        runThrough.setId("recognitionRunThrough");
        destination.setId("recognitionDestination");
        expenseFunction.setId("recognitionExpenseFunction");
        expenseFunction.getItems().setAll(ExpenseFunction.values());
        expenseFunction.setPromptText("Choose AR, OA, or FR");
        destination.setConverter(new StringConverter<>()
        {
            @Override public String toString(Account value)
            { return value == null ? "" : value.getCode() + " — " + value.getName(); }
            @Override public Account fromString(String value) { return null; }
        });
        configureItemTable();
        configureProposalTable();

        Button refresh = new Button("Refresh");
        refresh.setId("recognitionRefresh");
        refresh.setOnAction(event -> reload());
        Button preview = new Button("Preview");
        preview.setId("recognitionPreview");
        preview.setOnAction(event -> preview());
        save.setId("recognitionSave");
        save.setOnAction(event -> saveSelected());
        UiPermissionGate.gate(save, ApplicationPermission.BOOKKEEPING_WRITE, "Save recognition");

        Label title = new Label("Recognition Assistant");
        title.getStyleClass().add("panel-title");
        Label guidance = new Label("Preview straight-line installments from the selected canonical prepaid or deferred item. Nothing is posted until Save Selected Recognition is used.");
        guidance.setWrapText(true);
        status.setId("recognitionStatus");
        status.setWrapText(true);
        root.setTop(new VBox(6, title, new HBox(8, new Label("Item type"), kind, refresh), guidance,
                status, new Separator()));

        GridPane form = new GridPane();
        form.setId("recognitionForm");
        form.setPadding(new Insets(8));
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Service start"), serviceStart);
        form.addRow(1, new Label("Service end"), serviceEnd);
        form.addRow(2, new Label("Run through"), runThrough);
        form.addRow(3, new Label("Destination account"), destination);
        form.addRow(4, new Label("Expense function"), expenseFunction);
        form.add(new HBox(8, preview, save), 1, 5);
        summary.setId("recognitionSummary");
        summary.setWrapText(true);
        form.add(summary, 0, 6, 2, 1);
        destination.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(destination, Priority.ALWAYS);

        VBox proposalRegion = new VBox(6, new Label("Due recognition proposals"), proposals);
        proposalRegion.setMinHeight(0);
        VBox.setVgrow(proposals, Priority.ALWAYS);
        ScrollPane editor = new ScrollPane(new VBox(6, form, proposalRegion));
        editor.setId("recognitionEditorScroll");
        editor.setFitToWidth(true);
        editor.setMinHeight(0);
        editor.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editor.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        SplitPane split = new SplitPane(items, editor);
        split.setId("recognitionWorkspaceSplit");
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.42);
        CompanySplitPaneStateBinder.bind(split, "recognition-assistant-workspace", 0.42);
        root.setCenter(split);

        kind.valueProperty().addListener((obs, oldValue, newValue) -> reload());
        items.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, row) -> selectItem(row));
        proposals.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, row) -> updateSaveState());
        runThrough.valueProperty().addListener((obs, oldValue, newValue) -> clearPreview("Dates changed; preview again before saving."));
        serviceStart.valueProperty().addListener((obs, oldValue, newValue) -> clearPreview(null));
        serviceEnd.valueProperty().addListener((obs, oldValue, newValue) -> clearPreview(null));
        destination.valueProperty().addListener((obs, oldValue, newValue) -> clearPreview(null));
        expenseFunction.valueProperty().addListener((obs, oldValue, newValue) -> clearPreview(null));
        companyFormat.install(serviceStart);
        companyFormat.install(serviceEnd);
        companyFormat.install(runThrough);
        reload();
    }

    private void configureItemTable()
    {
        TableColumn<SupplementalOpenItemQueryService.Row, String> description = new TableColumn<>("Item");
        description.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().description()));
        TableColumn<SupplementalOpenItemQueryService.Row, String> counterparty = new TableColumn<>("Counterparty");
        counterparty.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().counterparty()));
        TableColumn<SupplementalOpenItemQueryService.Row, String> balance = new TableColumn<>("Open balance");
        balance.setCellValueFactory(value -> new SimpleStringProperty(companyFormat.formatMoney(value.getValue().openBalance())));
        TableColumn<SupplementalOpenItemQueryService.Row, String> dates = new TableColumn<>("Service dates");
        dates.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().startDate() + " — " + value.getValue().endDate()));
        items.getColumns().addAll(description, counterparty, balance, dates);
        items.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        items.setPlaceholder(new Label("No open canonical items are available as of the selected run-through date."));
    }

    private void configureProposalTable()
    {
        TableColumn<SupplementalRecognitionService.Proposal, String> installment = new TableColumn<>("Installment");
        installment.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().installment()
                + "/" + value.getValue().installmentCount()));
        TableColumn<SupplementalRecognitionService.Proposal, String> date = new TableColumn<>("Recognition date");
        date.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().recognitionDate().toString()));
        TableColumn<SupplementalRecognitionService.Proposal, String> amount = new TableColumn<>("Amount");
        amount.setCellValueFactory(value -> new SimpleStringProperty(companyFormat.formatMoney(value.getValue().amount())));
        proposals.getColumns().addAll(installment, date, amount);
        proposals.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        proposals.setPlaceholder(new Label("Preview to calculate due installments."));
    }

    private void reload()
    {
        clearPreview(null);
        LocalDate asOf = runThrough.getValue() == null ? ActivePeriodContext.get() : runThrough.getValue();
        try
        {
            items.getItems().setAll(UiServiceRegistry.supplementalRecognition().openItems(kind.getValue(), asOf));
            List<Account> accounts = UiServiceRegistry.accountLookup().listActivePostingAccounts().stream()
                    .filter(account -> account.getAccountType() == requiredDestinationType())
                    .toList();
            destination.getItems().setAll(accounts);
            expenseFunction.setDisable(kind.getValue() == SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE);
            if (expenseFunction.isDisable()) expenseFunction.setValue(null);
            status.setText("Loaded " + items.getItems().size() + " open item(s) as of " + asOf + ".");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load recognition items: " + UiErrors.safeMessage(ex));
        }
    }

    private AccountType requiredDestinationType()
    {
        return kind.getValue() == SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE
                ? AccountType.EXPENSE : AccountType.INCOME;
    }

    private void selectItem(SupplementalOpenItemQueryService.Row row)
    {
        clearPreview(null);
        if (row != null)
        {
            serviceStart.setValue(row.startDate() == null ? row.openingDate() : row.startDate());
            serviceEnd.setValue(row.endDate() == null ? row.openingDate() : row.endDate());
            summary.setText("Selected " + row.description() + "; open balance "
                    + companyFormat.formatMoney(row.openBalance()) + ".");
        }
    }

    private void preview()
    {
        var item = items.getSelectionModel().getSelectedItem();
        Account account = destination.getValue();
        if (item == null || account == null)
        {
            status.setText("Select an open item and a destination account before previewing.");
            return;
        }
        try
        {
            currentPreview = UiServiceRegistry.supplementalRecognition().preview(
                    new SupplementalRecognitionService.Request(kind.getValue(), item.itemId(),
                            serviceStart.getValue(), serviceEnd.getValue(), runThrough.getValue(),
                            account.getId(), expenseFunction.getValue()));
            proposals.getItems().setAll(currentPreview.proposals());
            if (!proposals.getItems().isEmpty()) proposals.getSelectionModel().selectFirst();
            summary.setText("Total " + companyFormat.formatMoney(currentPreview.total())
                    + "; recognized " + companyFormat.formatMoney(currentPreview.recognized())
                    + "; remaining " + companyFormat.formatMoney(currentPreview.remaining())
                    + "; proposed through date " + companyFormat.formatMoney(currentPreview.proposed()) + ".");
            status.setText(proposals.getItems().isEmpty()
                    ? "No unpaid installment is due through the selected date."
                    : "Previewed " + proposals.getItems().size() + " due installment(s). Select one to save.");
            capabilitiesChanged.run();
        }
        catch (RuntimeException ex)
        {
            clearPreview(null);
            status.setText("Could not preview recognition: " + UiErrors.safeMessage(ex));
        }
    }

    private void saveSelected()
    {
        SupplementalRecognitionService.Proposal selected = proposals.getSelectionModel().getSelectedItem();
        if (selected == null) throw new IllegalStateException(saveUnavailableReason());
        try
        {
            var saved = UiServiceRegistry.supplementalRecognition().save(selected);
            status.setText("Saved recognition transaction " + saved.id() + " for "
                    + companyFormat.formatMoney(selected.amount()) + ".");
            proposals.getItems().remove(selected);
            currentPreview = proposals.getItems().isEmpty() ? null : currentPreview;
            if (!proposals.getItems().isEmpty()) proposals.getSelectionModel().selectFirst();
            updateSaveState();
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save recognition: " + UiErrors.safeMessage(ex));
            updateSaveState();
        }
    }

    private void clearPreview(String message)
    {
        currentPreview = null;
        proposals.getItems().clear();
        if (message != null) status.setText(message);
        updateSaveState();
    }

    private void updateSaveState()
    {
        save.setDisable(proposals.getSelectionModel().getSelectedItem() == null);
        capabilitiesChanged.run();
    }

    @Override public String title() { return "Recognition Assistant"; }
    @Override public Node root() { return root; }
    @Override public Set<AppCommand> commandCapabilities()
    {
        return proposals.getSelectionModel().getSelectedItem() == null
                ? Set.of() : AppPanel.capabilities(AppCommand.SAVE_ACTIVE);
    }
    @Override public Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return command == AppCommand.SAVE_ACTIVE
                ? Optional.of(ApplicationPermission.BOOKKEEPING_WRITE) : Optional.empty();
    }
    @Override public String commandLabel(AppCommand command)
    {
        return command == AppCommand.SAVE_ACTIVE ? "Save Selected Recognition" : AppPanel.super.commandLabel(command);
    }
    @Override public void onSave() { saveSelected(); }
    @Override public boolean hasUnsavedChanges() { return currentPreview != null && !proposals.getItems().isEmpty(); }
    @Override public String saveUnavailableReason() { return "Preview and select a due recognition installment before saving."; }
    @Override public String commandResultMessage(AppCommand command) { return status.getText(); }
    @Override public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        capabilitiesChanged = Objects.requireNonNull(listener, "listener");
        listener.run();
    }
}
