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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.InventoryMovement;
import org.nonprofitbookkeeping.service.InventoryItemCommand;
import org.nonprofitbookkeeping.service.InventoryItemView;
import org.nonprofitbookkeeping.service.InventoryMovementCommand;
import org.nonprofitbookkeeping.service.InventoryMovementView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/** H2-backed Inventory panel. */
public class InventoryPanel implements AppPanel
{
    private static final String INVALID_FIELD_STYLE = "field-invalid";

    private final BorderPane root = new BorderPane();
    private final VBox listPanel = new VBox(8);
    private final VBox itemEditorPanel = new VBox(8);
    private final TableView<InventoryItemView> itemTable = new TableView<>();
    private final TableView<InventoryMovementView> movementTable = new TableView<>();
    private final Label status = new Label();
    private final Label editorTitle = new Label("Inventory Item");
    private final CompanyUiFormat companyFormat = CompanyUiFormat.activeCompany();

    private final ComboBox<Account> inventoryAccount = new ComboBox<>();
    private final ComboBox<Fund> fund = new ComboBox<>();
    private final TextField name = new TextField();
    private final TextField itemType = new TextField();
    private final TextField quantity = new TextField("0.00");
    private final TextField unit = new TextField("each");
    private final TextField unitValue = new TextField("0.00");
    private final DatePicker acquisitionDate = new DatePicker(LocalDate.now());
    private final TextField custodian = new TextField();
    private final TextField storageLocation = new TextField();
    private final ComboBox<InventoryItem.Condition> condition = new ComboBox<>();
    private final ComboBox<InventoryItem.Status> itemStatus = new ComboBox<>();
    private final TextArea notes = new TextArea();
    private final TextField movementQuantity = new TextField("1.0000");
    private final DatePicker movementDate = new DatePicker(LocalDate.now());
    private final TextField movementNotes = new TextField();

    private boolean suppressDirty;
    private boolean dirty;
    private boolean editorOpen;
    private Long editingItemId;

    public InventoryPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Inventory");
        title.getStyleClass().add("panel-title");
        root.setTop(new VBox(6, title, status, new Separator()));

        configureSelectors();
        configureItemTable();
        configureMovementTable();
        configureListPanel();
        configureItemEditorPanel();
        companyFormat.install(acquisitionDate);
        companyFormat.install(movementDate);
        installFormatCorrection();
        installDirtyTracking();
        reload();
        resetEditorFields();
        showListPanel("Loaded inventory workspace.");
    }

    @Override
    public void onNew()
    {
        openNewItemEditor();
    }

    @Override
    public void onSave()
    {
        if (editorOpen)
        {
            saveItem();
        }
        else
        {
            status.setText("Open New Item or Edit Selected before using Save for inventory items.");
        }
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return dirty;
    }

    private void configureListPanel()
    {
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        Button newItem = new Button("New Item");
        newItem.setOnAction(e -> openNewItemEditor());
        Button editItem = new Button("Edit Selected");
        editItem.setOnAction(e -> openEditItemEditor());
        Button receive = new Button("Receive Quantity");
        receive.setOnAction(e -> recordMovement(InventoryMovement.MovementType.RECEIPT));
        Button issue = new Button("Issue Quantity");
        issue.setOnAction(e -> recordMovement(InventoryMovement.MovementType.ISSUE));
        Button adjust = new Button("Adjust Count To Quantity");
        adjust.setOnAction(e -> recordMovement(InventoryMovement.MovementType.ADJUSTMENT));

        HBox itemActions = new HBox(8, refresh, newItem, editItem);
        HBox movementActions = new HBox(8,
                new Label("Movement qty"), movementQuantity,
                new Label("Movement date"), movementDate,
                new Label("Movement notes"), movementNotes,
                receive, issue, adjust);
        movementQuantity.setPrefWidth(90);
        movementDate.setPrefWidth(130);
        movementNotes.setPrefWidth(240);

        SplitPane split = new SplitPane(new VBox(6, new Label("Inventory Items"), itemTable), new VBox(6, new Label("Movement History"), movementTable));
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.58);
        split.setId("inventoryTablesSplit");
        CompanySplitPaneStateBinder.bind(split, "inventory-tables", 0.58);
        VBox.setVgrow(itemTable, Priority.ALWAYS);
        VBox.setVgrow(movementTable, Priority.ALWAYS);
        VBox.setVgrow(split, Priority.ALWAYS);
        listPanel.getChildren().setAll(itemActions, movementActions, split);
    }

    private void configureItemEditorPanel()
    {
        editorTitle.getStyleClass().add("panel-title");
        Button save = new Button("Save Item");
        save.setOnAction(e -> saveItem());
        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> cancelItemEditor());
        HBox actions = new HBox(8, save, cancel);
        ScrollPane editorScroll = new ScrollPane(form());
        editorScroll.setFitToWidth(true);
        VBox.setVgrow(editorScroll, Priority.ALWAYS);
        itemEditorPanel.getChildren().setAll(editorTitle, actions, editorScroll);
    }

    private void configureItemTable()
    {
        itemTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        addItemColumn("Name", InventoryItemView::name, 180);
        addItemColumn("Type", InventoryItemView::itemType, 120);
        addItemColumn("Qty", v -> formatQuantity(v.quantity()), 90);
        addItemColumn("Unit", InventoryItemView::unit, 80);
        addItemColumn("Value", v -> formatMoney(v.unitValue()), 110);
        addItemColumn("Total", v -> formatMoney(v.totalValue()), 120);
        addItemColumn("Custodian", InventoryItemView::custodian, 140);
        addItemColumn("Location", InventoryItemView::storageLocation, 160);
        addItemColumn("Acquired", v -> formatDate(v.acquisitionDate()), 120);
        addItemColumn("Status", v -> v.status().name(), 100);
        itemTable.setPlaceholder(new Label("No inventory items found."));
        itemTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && itemTable.getSelectionModel().getSelectedItem() != null)
            {
                openEditItemEditor();
            }
        });
    }

    private void configureMovementTable()
    {
        movementTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        addMovementColumn("Date", v -> formatDate(v.movementDate()), 120);
        addMovementColumn("Item", InventoryMovementView::inventoryItemName, 180);
        addMovementColumn("Type", v -> v.movementType().name(), 120);
        addMovementColumn("Change", v -> formatQuantity(v.quantityChange()), 100);
        addMovementColumn("Result", v -> formatQuantity(v.resultingQuantity()), 100);
        addMovementColumn("Unit Value", v -> formatMoney(v.unitValue()), 120);
        addMovementColumn("Txn", v -> v.transactionId() == null ? "" : String.valueOf(v.transactionId()), 80);
        addMovementColumn("Notes", InventoryMovementView::notes, 220);
        movementTable.setPlaceholder(new Label("No inventory movements recorded."));
    }

    private VBox form()
    {
        notes.setPrefRowCount(4);
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.setPadding(new Insets(4));
        int row = 0;
        row = addRow(grid, row, "Inventory account", inventoryAccount);
        row = addRow(grid, row, "Fund", fund);
        row = addRow(grid, row, "Item name", name);
        row = addRow(grid, row, "Item type", itemType);
        row = addRow(grid, row, "Quantity", quantity);
        row = addRow(grid, row, "Unit", unit);
        row = addRow(grid, row, "Value", unitValue);
        row = addRow(grid, row, "Acquired", acquisitionDate);
        row = addRow(grid, row, "Custodian", custodian);
        row = addRow(grid, row, "Storage", storageLocation);
        row = addRow(grid, row, "Condition", condition);
        row = addRow(grid, row, "Status", itemStatus);
        row = addRow(grid, row, "Notes", notes);
        VBox box = new VBox(8, new Label("Inventory Item Details"), grid);
        box.setPadding(new Insets(8));
        return box;
    }

    private int addRow(GridPane grid, int row, String label, Node editor)
    {
        grid.add(new Label(label), 0, row);
        grid.add(editor, 1, row);
        GridPane.setHgrow(editor, Priority.ALWAYS);
        if (editor instanceof TextField textField)
        {
            textField.setMaxWidth(Double.MAX_VALUE);
        }
        else if (editor instanceof TextArea textArea)
        {
            textArea.setMaxWidth(Double.MAX_VALUE);
        }
        else if (editor instanceof ComboBox<?> comboBox)
        {
            comboBox.setMaxWidth(Double.MAX_VALUE);
        }
        else if (editor instanceof DatePicker datePicker)
        {
            datePicker.setMaxWidth(Double.MAX_VALUE);
        }
        return row + 1;
    }

    private void configureSelectors()
    {
        inventoryAccount.setConverter(new StringConverter<>()
        {
            @Override public String toString(Account account) { return account == null ? "" : account.getCode() + " — " + account.getName(); }
            @Override public Account fromString(String string) { return null; }
        });
        fund.setConverter(new StringConverter<>()
        {
            @Override public String toString(Fund f) { return f == null ? "" : f.getCode() + " — " + f.getName(); }
            @Override public Fund fromString(String string) { return null; }
        });
        condition.getItems().setAll(InventoryItem.Condition.values());
        itemStatus.getItems().setAll(InventoryItem.Status.values());
    }

    private void reload()
    {
        status.setText("Loading inventory...");
        try
        {
            List<Account> inventoryAccounts = UiServiceRegistry.accountLookup().listActivePostingAccounts().stream()
                    .filter(a -> a.getSubtype() == AccountSubtype.INVENTORY)
                    .toList();
            inventoryAccount.getItems().setAll(inventoryAccounts);
            fund.getItems().setAll(UiServiceRegistry.fundLookup().listActiveFunds());
            Long selectedId = selectedItemId();
            itemTable.getItems().setAll(UiServiceRegistry.inventory().listItems(activeCompanyCode()));
            movementTable.getItems().setAll(UiServiceRegistry.inventory().listMovements(activeCompanyCode()));
            if (selectedId != null)
            {
                selectItem(selectedId);
            }
            status.setText("Loaded " + itemTable.getItems().size() + " inventory item(s) and " + movementTable.getItems().size() + " movement record(s).");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load inventory: " + UiErrors.safeMessage(ex));
        }
    }

    private void openNewItemEditor()
    {
        itemTable.getSelectionModel().clearSelection();
        editingItemId = null;
        resetEditorFields();
        editorTitle.setText("New Inventory Item");
        dirty = false;
        showEditorPanel("Enter inventory item details, then Save Item.");
    }

    private void openEditItemEditor()
    {
        InventoryItemView selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select an inventory item before choosing Edit Selected.");
            return;
        }
        loadEditor(selected);
        editorTitle.setText("Edit Inventory Item — " + selected.name());
        dirty = false;
        showEditorPanel("Editing inventory item " + selected.name() + ".");
    }

    private void cancelItemEditor()
    {
        resetEditorFields();
        showListPanel("Inventory item edit cancelled.");
    }

    private void showListPanel(String message)
    {
        editorOpen = false;
        dirty = false;
        root.setCenter(listPanel);
        status.setText(message);
    }

    private void showEditorPanel(String message)
    {
        editorOpen = true;
        root.setCenter(itemEditorPanel);
        status.setText(message);
    }

    private void saveItem()
    {
        if (!editorOpen)
        {
            status.setText("Open New Item or Edit Selected before saving an inventory item.");
            return;
        }
        try
        {
            InventoryItemCommand command = commandFromForm();
            InventoryItemView saved = editingItemId == null
                    ? UiServiceRegistry.inventory().create(command)
                    : UiServiceRegistry.inventory().update(editingItemId, command);
            editingItemId = saved.id();
            reload();
            selectItem(saved.id());
            dirty = false;
            showListPanel("Saved inventory item " + saved.name() + ".");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save inventory item: " + UiErrors.safeMessage(ex));
        }
    }

    private void recordMovement(InventoryMovement.MovementType type)
    {
        InventoryItemView selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select an inventory item first.");
            return;
        }
        try
        {
            clearValidation();
            UiServiceRegistry.inventory().recordMovement(selected.id(), new InventoryMovementCommand(
                    type,
                    parseQuantityField(movementQuantity, "Movement quantity"),
                    requiredDate(movementDate, "Movement date"),
                    movementNotes.getText()));
            reload();
            selectItem(selected.id());
            status.setText("Recorded inventory " + type + " for " + selected.name() + ".");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not record inventory movement: " + UiErrors.safeMessage(ex));
        }
    }

    private InventoryItemCommand commandFromForm()
    {
        clearValidation();
        return new InventoryItemCommand(
                activeCompanyCode(),
                requireSelected(inventoryAccount, "inventory account").getId(),
                requireSelected(fund, "fund").getId(),
                requiredText(name, "Item name"),
                requiredText(itemType, "Item type"),
                parseQuantityField(quantity, "Quantity"),
                requiredText(unit, "Unit"),
                parseMoneyField(unitValue, "Value"),
                requiredDate(acquisitionDate, "Acquisition date"),
                custodian.getText(),
                storageLocation.getText(),
                condition.getValue(),
                itemStatus.getValue(),
                notes.getText());
    }

    private void resetEditorFields()
    {
        suppressDirty = true;
        try
        {
            editingItemId = null;
            inventoryAccount.getSelectionModel().clearSelection();
            fund.getSelectionModel().clearSelection();
            name.clear();
            itemType.clear();
            quantity.setText("0.0000");
            unit.setText("each");
            unitValue.setText("$0.00");
            acquisitionDate.setValue(LocalDate.now());
            custodian.clear();
            storageLocation.clear();
            condition.setValue(InventoryItem.Condition.UNKNOWN);
            itemStatus.setValue(InventoryItem.Status.ACTIVE);
            notes.clear();
            clearValidation();
            dirty = false;
        }
        finally
        {
            suppressDirty = false;
        }
    }

    private void loadEditor(InventoryItemView item)
    {
        suppressDirty = true;
        try
        {
            editingItemId = item.id();
            selectAccountById(inventoryAccount, item.inventoryAccountId());
            selectFundById(fund, item.fundId());
            name.setText(item.name());
            itemType.setText(item.itemType());
            quantity.setText(formatQuantity(item.quantity()));
            unit.setText(item.unit());
            unitValue.setText(formatMoney(item.unitValue()));
            acquisitionDate.setValue(item.acquisitionDate());
            custodian.setText(item.custodian());
            storageLocation.setText(item.storageLocation());
            condition.setValue(item.condition());
            itemStatus.setValue(item.status());
            notes.setText(item.notes());
            clearValidation();
            dirty = false;
        }
        finally
        {
            suppressDirty = false;
        }
    }

    private void installDirtyTracking()
    {
        for (TextField field : List.of(name, itemType, quantity, unit, unitValue, custodian, storageLocation))
        {
            field.textProperty().addListener((observable, oldValue, newValue) -> markDirty());
        }
        notes.textProperty().addListener((observable, oldValue, newValue) -> markDirty());
        for (ComboBox<?> comboBox : List.of(inventoryAccount, fund, condition, itemStatus))
        {
            comboBox.valueProperty().addListener((observable, oldValue, newValue) -> markDirty());
        }
        acquisitionDate.valueProperty().addListener((observable, oldValue, newValue) -> markDirty());
    }

    private void markDirty()
    {
        if (!suppressDirty && editorOpen)
        {
            dirty = true;
        }
    }

    private void installFormatCorrection()
    {
        installDecimalCorrection(quantity, false);
        installDecimalCorrection(movementQuantity, false);
        installDecimalCorrection(unitValue, true);
        installDateCorrection(acquisitionDate);
        installDateCorrection(movementDate);
    }

    private void installDecimalCorrection(TextField field, boolean money)
    {
        field.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused && !field.getText().isBlank())
            {
                try
                {
                    field.setText(money ? formatMoney(parseMoney(field.getText())) : formatQuantity(parseQuantity(field.getText())));
                    clearInvalid(field);
                }
                catch (RuntimeException ex)
                {
                    markInvalid(field);
                    status.setText((money ? "Money" : "Quantity") + " fields need a valid number.");
                }
            }
        });
    }

    private void installDateCorrection(DatePicker picker)
    {
        picker.getEditor().focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused && !picker.getEditor().getText().isBlank())
            {
                try
                {
                    LocalDate parsed = parseDate(picker.getEditor().getText());
                    picker.setValue(parsed);
                    picker.getEditor().setText(formatDate(parsed));
                    clearInvalid(picker);
                }
                catch (RuntimeException ex)
                {
                    markInvalid(picker);
                    status.setText("Date fields need a valid date using " + picker.getPromptText() + " ordering.");
                }
            }
        });
    }

    private Long selectedItemId()
    {
        InventoryItemView selected = itemTable.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.id();
    }

    private void selectItem(Long id)
    {
        itemTable.getItems().stream()
                .filter(row -> row.id().equals(id))
                .findFirst()
                .ifPresent(row -> itemTable.getSelectionModel().select(row));
    }

    private static void selectAccountById(ComboBox<Account> box, Long id)
    {
        box.getItems().stream().filter(item -> item.getId().equals(id)).findFirst().ifPresent(box::setValue);
    }

    private static void selectFundById(ComboBox<Fund> box, Long id)
    {
        box.getItems().stream().filter(item -> item.getId().equals(id)).findFirst().ifPresent(box::setValue);
    }

    private <T> T requireSelected(ComboBox<T> box, String label)
    {
        T selected = box.getValue();
        if (selected == null)
        {
            markInvalid(box);
            throw new IllegalArgumentException("Select " + label + ".");
        }
        clearInvalid(box);
        return selected;
    }

    private String requiredText(TextField field, String label)
    {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isBlank())
        {
            markInvalid(field);
            throw new IllegalArgumentException(label + " is required.");
        }
        clearInvalid(field);
        return value;
    }

    private BigDecimal parseMoneyField(TextField field, String label)
    {
        try
        {
            BigDecimal parsed = parseMoney(field.getText());
            field.setText(formatMoney(parsed));
            clearInvalid(field);
            return parsed;
        }
        catch (RuntimeException ex)
        {
            markInvalid(field);
            throw new IllegalArgumentException(label + " needs a valid money amount.");
        }
    }

    private BigDecimal parseQuantityField(TextField field, String label)
    {
        try
        {
            BigDecimal parsed = parseQuantity(field.getText());
            field.setText(formatQuantity(parsed));
            clearInvalid(field);
            return parsed;
        }
        catch (RuntimeException ex)
        {
            markInvalid(field);
            throw new IllegalArgumentException(label + " needs a valid quantity.");
        }
    }

    private LocalDate requiredDate(DatePicker picker, String label)
    {
        try
        {
            LocalDate value = picker.getValue() == null && !picker.getEditor().getText().isBlank()
                    ? parseDate(picker.getEditor().getText())
                    : picker.getValue();
            if (value == null)
            {
                markInvalid(picker);
                throw new IllegalArgumentException(label + " is required.");
            }
            picker.setValue(value);
            picker.getEditor().setText(formatDate(value));
            clearInvalid(picker);
            return value;
        }
        catch (RuntimeException ex)
        {
            markInvalid(picker);
            throw new IllegalArgumentException(label + " needs a valid date.");
        }
    }

    private void clearValidation()
    {
        for (Node node : List.of(inventoryAccount, fund, name, itemType, quantity, unit, unitValue, acquisitionDate, condition, itemStatus, movementQuantity, movementDate))
        {
            clearInvalid(node);
        }
    }

    private static void markInvalid(Node node)
    {
        if (!node.getStyleClass().contains(INVALID_FIELD_STYLE))
        {
            node.getStyleClass().add(INVALID_FIELD_STYLE);
        }
    }

    private static void clearInvalid(Node node)
    {
        node.getStyleClass().remove(INVALID_FIELD_STYLE);
    }

    private String formatMoney(BigDecimal value)
    {
        return value == null ? "" : companyFormat.formatMoney(value);
    }

    private BigDecimal parseMoney(String raw)
    {
        BigDecimal parsed = companyFormat.parseMoney(raw);
        if (parsed == null)
        {
            throw new IllegalArgumentException("Money amount is invalid.");
        }
        return parsed.setScale(4, RoundingMode.HALF_UP);
    }

    private static String formatQuantity(BigDecimal value)
    {
        return value == null ? "" : value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal parseQuantity(String raw)
    {
        String normalized = raw == null ? "" : raw.trim().replace(",", "");
        return normalized.isBlank() ? BigDecimal.ZERO : new BigDecimal(normalized).setScale(4, RoundingMode.HALF_UP);
    }

    private String formatDate(LocalDate value)
    {
        return companyFormat.formatDate(value);
    }

    private LocalDate parseDate(String value)
    {
        LocalDate parsed = companyFormat.parseDate(value);
        if (parsed == null && value != null && !value.isBlank())
        {
            throw new IllegalArgumentException("Date must be valid.");
        }
        return parsed;
    }

    private static String activeCompanyCode()
    {
        return MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
    }

    private void addItemColumn(String title, Function<InventoryItemView, String> extractor, double width)
    {
        TableColumn<InventoryItemView, String> column = new TableColumn<>(title);
        column.setId(title.replaceAll("\\W+", "_"));
        column.setUserData(column.getId());
        column.setCellValueFactory(row -> new SimpleStringProperty(extractor.apply(row.getValue())));
        column.setPrefWidth(width);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        itemTable.getColumns().add(column);
    }

    private void addMovementColumn(String title, Function<InventoryMovementView, String> extractor, double width)
    {
        TableColumn<InventoryMovementView, String> column = new TableColumn<>(title);
        column.setId(title.replaceAll("\\W+", "_"));
        column.setUserData(column.getId());
        column.setCellValueFactory(row -> new SimpleStringProperty(extractor.apply(row.getValue())));
        column.setPrefWidth(width);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        movementTable.getColumns().add(column);
    }

    @Override public String title() { return "Inventory"; }
    @Override public Node root() { return root; }
}
