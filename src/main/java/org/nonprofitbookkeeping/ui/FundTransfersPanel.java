package org.nonprofitbookkeeping.ui;

import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.FundTransferCommand;
import org.nonprofitbookkeeping.service.FundTransferService.Choice;
import org.nonprofitbookkeeping.service.FundTransferView;

import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Internal resource allocation, with immutable saved history and canonical reversals. */
public final class FundTransfersPanel implements AppPanel
{
    private final VBox root = new VBox();
    private final TableView<FundTransferView> table = new TableView<>();
    private final ComboBox<Choice> from = new ComboBox<>();
    private final ComboBox<Choice> to = new ComboBox<>();
    private final ComboBox<Choice> allocation = new ComboBox<>();
    private final ComboBox<Choice> equity = new ComboBox<>();
    private final DatePicker date = new DatePicker();
    private final DatePicker reversalDate = new DatePicker();
    private final TextField amount = new TextField();
    private final TextArea explanation = new TextArea();
    private final TextField reason = new TextField();
    private final Label status = new Label("Ready.");
    private final GridPane fields = new GridPane();
    private final CompanyUiFormat format = CompanyUiFormat.activeCompany();
    private final BooleanSupplier discardConfirmation;
    private UUID requestId = UUID.randomUUID();
    private boolean dirty;
    private boolean populating;
    private boolean saved;

    public FundTransfersPanel()
    {
        this(() -> new Alert(Alert.AlertType.CONFIRMATION, "Discard the unsaved transfer draft?", ButtonType.OK, ButtonType.CANCEL)
                .showAndWait().filter(ButtonType.OK::equals).isPresent());
    }

    FundTransfersPanel(BooleanSupplier discardConfirmation)
    {
        this.discardConfirmation = discardConfirmation;
        root.setPadding(new Insets(8));
        root.setMinSize(0, 0);
        build();
        refresh();
        newDraft();
    }

    private void build()
    {
        Label title = new Label("Fund Transfers");
        title.getStyleClass().add("panel-title");
        Label help = new Label("Allocate resources between unrestricted or designated funds in this company. This changes fund allocations; it does not move money between bank accounts. History displays the latest 500 transfers.");
        help.setWrapText(true);
        Label retention = new Label("Saved transfers cannot be edited or deleted because the ledger and transfer report retain their history. Reverse Selected, then enter a corrected transfer. SCLX preserves ledger lines but omits transfer links; use a whole-database backup for those links.");
        retention.setId("fundTransfersRetention");
        retention.setWrapText(true);
        Button add = action("New", "fundTransfersNew", this::onNew, true);
        Button save = action("Save", "fundTransfersSave", this::onSave, true);
        Button refresh = action("Refresh", "fundTransfersRefresh", this::refresh, false);
        Button journal = action("Open Selected in Journal", "fundTransfersJournal", () ->
        {
            FundTransferView row = table.getSelectionModel().getSelectedItem();
            if (row == null)
            {
                status.setText("Select a transfer to open its transaction.");
                return;
            }
            DrillThroughCoordinator.openPanelWithContext(AppPanelId.JOURNAL_PANE, "Txn #" + row.transactionId());
        }, false);
        Button funds = action("Return to Funds", "fundTransfersFunds", () ->
                DrillThroughCoordinator.openPanelWithContext(AppPanelId.FUNDS, ""), false);
        Button reverse = action("Reverse Selected", "fundTransfersReverse", this::reverseSelected, true);
        reversalDate.setId("fundTransfersReversalDate");
        reason.setId("fundTransfersReason");
        reason.setPromptText("Required reversal reason");
        format.install(date);
        format.install(reversalDate);
        format.installMoney(amount);
        reversalDate.setValue(ActivePeriodContext.get());
        UiPermissionGate.gate(reversalDate, ApplicationPermission.BOOKKEEPING_WRITE, "Choose reversal date");
        UiPermissionGate.gate(reason, ApplicationPermission.BOOKKEEPING_WRITE, "Explain reversal");
        FlowPane correction = new FlowPane(8, 6, new Label("Reversal date"), reversalDate, reason, reverse);
        VBox header = new VBox(6, title, help, new FlowPane(8, 6, add, save, refresh, journal, funds), correction, status, retention);
        ScrollPane headerScroll = scroll(header, "fundTransfersHeaderScroll");
        headerScroll.setPrefHeight(200);

        fields.setHgap(10);
        fields.setVgap(8);
        Control[] inputs = {date, from, to, allocation, equity, amount, explanation};
        String[] labels = {"Transfer date", "Source fund", "Destination fund", "Allocation ASSET account", "Net-assets EQUITY account", "Amount", "Explanation"};
        String[] ids = {"Date", "From", "To", "Allocation", "Equity", "Amount", "Explanation"};
        for (int i = 0; i < inputs.length; i++)
        {
            fields.add(new Label(labels[i]), 0, i);
            fields.add(inputs[i], 1, i);
            inputs[i].setId("fundTransfers" + ids[i]);
            UiPermissionGate.gate(inputs[i], ApplicationPermission.BOOKKEEPING_WRITE, "Enter transfer details");
            GridPane.setHgrow(inputs[i], Priority.ALWAYS);
        }
        for (ComboBox<Choice> combo : java.util.List.of(from, to, allocation, equity))
        {
            combo.setPrefWidth(350);
            combo.setMinWidth(Region.USE_PREF_SIZE);
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.valueProperty().addListener((obs, old, value) -> markDirty());
        }
        explanation.setPrefRowCount(3);
        date.valueProperty().addListener((obs, old, value) -> markDirty());
        date.getEditor().textProperty().addListener((obs, old, value) -> markDirty());
        amount.textProperty().addListener((obs, old, value) -> markDirty());
        explanation.textProperty().addListener((obs, old, value) -> markDirty());
        VBox editor = new VBox(8, new Label("New transfer — choose New after saving"), fields);
        editor.setPadding(new Insets(8));
        ScrollPane editorScroll = scroll(editor, "fundTransfersEditorScroll");
        table.setId("fundTransfersTable");
        table.setMinSize(0, 0);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        column("Date", "date", FundTransferView::date, format::formatDate, 120);
        column("Source", "from", FundTransferView::fromFund, Function.identity(), 150);
        column("Destination", "to", FundTransferView::toFund, Function.identity(), 150);
        column("Amount", "amount", FundTransferView::amount, format::formatMoney, 140);
        column("State", "state", FundTransferView::state, Function.identity(), 160);
        column("Transaction", "txn", FundTransferView::transactionId, Object::toString, 110);
        column("Explanation", "explanation", FundTransferView::explanation, Function.identity(), 350);
        table.setPlaceholder(new Label("No saved transfers. History displays the latest 500 transfers."));
        SplitPane workspace = new SplitPane(table, editorScroll);
        workspace.setOrientation(Orientation.VERTICAL);
        workspace.setId("fundTransfersWorkspaceSplit");
        workspace.setMinSize(0, 0);
        SplitPane main = new SplitPane(headerScroll, workspace);
        main.setOrientation(Orientation.VERTICAL);
        main.setId("fundTransfersMainSplit");
        main.setMinSize(0, 0);
        VBox.setVgrow(main, Priority.ALWAYS);
        root.getChildren().setAll(main);
        persistDivider(workspace, "workspace", 0.55);
        persistDivider(main, "main", 0.30);
        FullTextTooltipInstaller.install(root);
    }

    private void persistDivider(SplitPane split, String name, double fallback)
    {
        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        String code = company == null || company.isBlank() ? "DEFAULT" : company;
        String key = "ui.fund-transfers.divider." + name;
        var preferences = UiServiceRegistry.companyUiPreferences();
        String value = preferences.loadState(code, "ui.fund-transfers.divider.").get(key);
        double position = fallback;
        try
        {
            if (value != null)
            {
                position = Double.parseDouble(value);
            }
        }
        catch (NumberFormatException invalid)
        {
            position = fallback;
        }
        split.setDividerPositions(Double.isFinite(position) ? Math.max(0.1, Math.min(0.9, position)) : fallback);
        PauseTransition delay = new PauseTransition(Duration.millis(250));
        delay.setOnFinished(event ->
        {
            try
            {
                preferences.saveState(code, Map.of(key, Double.toString(split.getDividerPositions()[0])));
            }
            catch (RuntimeException ex)
            {
                status.setText("Could not save layout: " + UiErrors.safeMessage(ex));
            }
        });
        split.getDividers().get(0).positionProperty().addListener((obs, old, current) -> delay.playFromStart());
    }

    private static ScrollPane scroll(Node node, String id)
    {
        ScrollPane pane = new ScrollPane(node);
        pane.setId(id);
        pane.setFitToWidth(true);
        pane.setMinSize(0, 0);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return pane;
    }

    private Button action(String label, String id, Runnable operation, boolean write)
    {
        Button button = new Button(label);
        button.setId(id);
        button.setOnAction(event -> operation.run());
        if (write)
        {
            UiPermissionGate.gate(button, ApplicationPermission.BOOKKEEPING_WRITE, label);
        }
        return button;
    }

    private <T> void column(String label, String key, Function<FundTransferView, T> value, Function<T, String> render, double width)
    {
        TableColumn<FundTransferView, T> column = new TableColumn<>(label);
        column.setUserData(key);
        column.setId("fundTransferColumn" + key);
        column.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(value.apply(row.getValue())));
        column.setCellFactory(ignored -> new TableCell<>()
        {
            @Override
            protected void updateItem(T item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : render.apply(item));
            }
        });
        column.setPrefWidth(width);
        column.setMinWidth(75);
        table.getColumns().add(column);
    }

    private void refresh()
    {
        try
        {
            SortedList<FundTransferView> sorted = new SortedList<>(FXCollections.observableArrayList(UiServiceRegistry.fundTransfers().list()));
            sorted.comparatorProperty().bind(table.comparatorProperty());
            table.setItems(sorted);
            if (!dirty && !saved)
            {
                populating = true;
                try
                {
                    var choices = UiServiceRegistry.fundTransfers().choices();
                    replaceChoices(from, choices.funds());
                    replaceChoices(to, choices.funds());
                    replaceChoices(allocation, choices.allocationAccounts());
                    replaceChoices(equity, choices.equityAccounts());
                }
                finally
                {
                    populating = false;
                }
            }
            status.setText(dirty ? "History refreshed; unsaved transfer draft preserved." : "History refreshed (latest 500 transfers).");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not refresh transfers: " + UiErrors.safeMessage(ex));
        }
    }

    private static void replaceChoices(ComboBox<Choice> combo, java.util.List<Choice> choices)
    {
        Choice prior = combo.getValue();
        combo.getItems().setAll(choices);
        combo.setValue(prior == null ? null : choices.stream().filter(c -> c.id().equals(prior.id())).findFirst().orElse(prior));
    }

    private void markDirty()
    {
        if (!populating && !saved)
        {
            dirty = true;
        }
    }

    private void newDraft()
    {
        populating = true;
        try
        {
            saved = false;
            fields.setDisable(false);
            requestId = UUID.randomUUID();
            date.setValue(ActivePeriodContext.get());
            from.setValue(null);
            to.setValue(null);
            allocation.setValue(null);
            equity.setValue(null);
            amount.clear();
            explanation.clear();
            dirty = false;
        }
        finally
        {
            populating = false;
        }
        refresh();
    }

    @Override
    public void onSave()
    {
        try
        {
            date.commitValue();
            var result = UiServiceRegistry.fundTransfers().save(new FundTransferCommand(requestId, date.getValue(),
                    id(from), id(to), id(allocation), id(equity), format.parseMoney(amount.getText()), explanation.getText()));
            saved = true;
            dirty = false;
            fields.setDisable(true);
            refresh();
            table.getItems().stream().filter(row -> row.id().equals(result.id())).findFirst().ifPresent(table.getSelectionModel()::select);
            status.setText("Saved transfer " + result.id() + ". Choose New to enter another transfer.");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save transfer: " + UiErrors.safeMessage(ex));
        }
    }

    private static Long id(ComboBox<Choice> combo)
    {
        return combo.getValue() == null ? null : combo.getValue().id();
    }

    private void reverseSelected()
    {
        FundTransferView selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a saved transfer to reverse.");
            return;
        }
        try
        {
            reversalDate.commitValue();
            var reversal = UiServiceRegistry.fundTransfers().reverse(selected.id(), reversalDate.getValue(), reason.getText());
            refresh();
            status.setText("Saved reversal " + reversal.id() + "; original transfer history retained.");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not reverse transfer: " + UiErrors.safeMessage(ex));
        }
    }

    @Override
    public String title()
    {
        return "Fund Transfers";
    }
    @Override
    public Node root()
    {
        return root;
    }
    @Override
    public boolean hasUnsavedChanges()
    {
        return dirty;
    }
    @Override
    public String commandResultMessage(AppCommand command)
    {
        return status.getText();
    }
    @Override
    public java.util.Set<AppCommand> commandCapabilities()
    {
        return AppPanel.capabilities(AppCommand.NEW_ACTIVE, AppCommand.SAVE_ACTIVE);
    }
    @Override
    public java.util.Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return command == AppCommand.NEW_ACTIVE || command == AppCommand.SAVE_ACTIVE
                ? java.util.Optional.of(ApplicationPermission.BOOKKEEPING_WRITE) : java.util.Optional.empty();
    }
    @Override
    public void onNew()
    {
        if (!dirty || discardConfirmation.getAsBoolean())
        {
            newDraft();
        }
    }
    @Override
    public void onPanelShown()
    {
        refresh();
    }
}
