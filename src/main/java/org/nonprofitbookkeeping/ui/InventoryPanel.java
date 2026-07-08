package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
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
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/** H2-backed Inventory panel. */
public class InventoryPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<InventoryItemView> itemTable = new TableView<>();
    private final TableView<InventoryMovementView> movementTable = new TableView<>();
    private final Label status = new Label();

    private final ComboBox<Account> inventoryAccount = new ComboBox<>();
    private final ComboBox<Fund> fund = new ComboBox<>();
    private final TextField name = new TextField();
    private final TextField itemType = new TextField();
    private final TextField quantity = new TextField("0.0000");
    private final TextField unit = new TextField("each");
    private final TextField unitValue = new TextField("0.0000");
    private final DatePicker acquisitionDate = new DatePicker(LocalDate.now());
    private final TextField custodian = new TextField();
    private final TextField storageLocation = new TextField();
    private final ComboBox<InventoryItem.Condition> condition = new ComboBox<>();
    private final ComboBox<InventoryItem.Status> itemStatus = new ComboBox<>();
    private final TextArea notes = new TextArea();
    private final TextField movementQuantity = new TextField("1.0000");
    private final DatePicker movementDate = new DatePicker(LocalDate.now());
    private final TextField movementNotes = new TextField();

    public InventoryPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Inventory");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        Button newItem = new Button("New Item");
        newItem.setOnAction(e -> clearForm());
        Button save = new Button("Save Item");
        save.setOnAction(e -> saveItem());
        Button receive = new Button("Receive Quantity");
        receive.setOnAction(e -> recordMovement(InventoryMovement.MovementType.RECEIPT));
        Button issue = new Button("Issue Quantity");
        issue.setOnAction(e -> recordMovement(InventoryMovement.MovementType.ISSUE));
        Button adjust = new Button("Adjust Count To Quantity");
        adjust.setOnAction(e -> recordMovement(InventoryMovement.MovementType.ADJUSTMENT));
        Button deleteUnavailable = new Button("Delete unavailable — deactivate or dispose inventory item");
        deleteUnavailable.setDisable(true);

        HBox actions = new HBox(8, refresh, newItem, save, receive, issue, adjust, deleteUnavailable);
        VBox header = new VBox(6, title, actions, status, new Separator());
        root.setTop(header);

        configureItemTable();
        configureMovementTable();
        SplitPane split = new SplitPane(new VBox(6, new Label("Inventory Items"), itemTable), new VBox(6, new Label("Movement History"), movementTable));
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.58);
        root.setCenter(split);
        root.setRight(form());

        itemTable.getSelectionModel().selectedItemProperty().addListener((observable, oldSelection, selected) -> fillForm(selected));
        configureSelectors();
        reload();
        clearForm();
    }

    private void configureItemTable()
    {
        addItemColumn("Name", InventoryItemView::name, 180);
        addItemColumn("Type", InventoryItemView::itemType, 100);
        addItemColumn("Qty", v -> v.quantity().toPlainString(), 90);
        addItemColumn("Unit", InventoryItemView::unit, 70);
        addItemColumn("Value", v -> v.unitValue().toPlainString(), 90);
        addItemColumn("Total", v -> v.totalValue().toPlainString(), 100);
        addItemColumn("Custodian", InventoryItemView::custodian, 120);
        addItemColumn("Location", InventoryItemView::storageLocation, 130);
        addItemColumn("Status", v -> v.status().name(), 90);
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        itemTable.setPlaceholder(new Label("No inventory items found."));
    }

    private void configureMovementTable()
    {
        addMovementColumn("Date", v -> String.valueOf(v.movementDate()), 100);
        addMovementColumn("Item", InventoryMovementView::inventoryItemName, 160);
        addMovementColumn("Type", v -> v.movementType().name(), 110);
        addMovementColumn("Change", v -> v.quantityChange().toPlainString(), 90);
        addMovementColumn("Result", v -> v.resultingQuantity().toPlainString(), 90);
        addMovementColumn("Txn", v -> v.transactionId() == null ? "" : String.valueOf(v.transactionId()), 70);
        addMovementColumn("Notes", InventoryMovementView::notes, 180);
        movementTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        movementTable.setPlaceholder(new Label("No inventory movements recorded."));
    }

    private VBox form()
    {
        notes.setPrefRowCount(4);
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
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
        row = addRow(grid, row, "Movement qty", movementQuantity);
        row = addRow(grid, row, "Movement date", movementDate);
        row = addRow(grid, row, "Movement notes", movementNotes);
        row = addRow(grid, row, "Notes", notes);
        VBox box = new VBox(8, new Label("Inventory Item"), grid);
        box.setPadding(new Insets(0, 0, 0, 10));
        box.setPrefWidth(360);
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
            itemTable.getItems().setAll(UiServiceRegistry.inventory().listItems(activeCompanyCode()));
            movementTable.getItems().setAll(UiServiceRegistry.inventory().listMovements(activeCompanyCode()));
            status.setText("Loaded " + itemTable.getItems().size() + " inventory item(s) and " + movementTable.getItems().size() + " movement record(s).");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load inventory: " + UiErrors.safeMessage(ex));
        }
    }

    private void saveItem()
    {
        try
        {
            InventoryItemView selected = itemTable.getSelectionModel().getSelectedItem();
            InventoryItemCommand command = commandFromForm();
            InventoryItemView saved = selected == null
                    ? UiServiceRegistry.inventory().create(command)
                    : UiServiceRegistry.inventory().update(selected.id(), command);
            reload();
            selectItem(saved.id());
            status.setText("Saved inventory item " + saved.name() + ".");
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
            UiServiceRegistry.inventory().recordMovement(selected.id(), new InventoryMovementCommand(
                    type,
                    parseMoney(movementQuantity),
                    movementDate.getValue(),
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
        return new InventoryItemCommand(
                activeCompanyCode(),
                inventoryAccount.getValue() == null ? null : inventoryAccount.getValue().getId(),
                fund.getValue() == null ? null : fund.getValue().getId(),
                name.getText(),
                itemType.getText(),
                parseMoney(quantity),
                unit.getText(),
                parseMoney(unitValue),
                acquisitionDate.getValue(),
                custodian.getText(),
                storageLocation.getText(),
                condition.getValue(),
                itemStatus.getValue(),
                notes.getText());
    }

    private void clearForm()
    {
        itemTable.getSelectionModel().clearSelection();
        inventoryAccount.getSelectionModel().clearSelection();
        fund.getSelectionModel().clearSelection();
        name.clear();
        itemType.clear();
        quantity.setText("0.0000");
        unit.setText("each");
        unitValue.setText("0.0000");
        acquisitionDate.setValue(LocalDate.now());
        custodian.clear();
        storageLocation.clear();
        condition.setValue(InventoryItem.Condition.UNKNOWN);
        itemStatus.setValue(InventoryItem.Status.ACTIVE);
        notes.clear();
        movementQuantity.setText("1.0000");
        movementDate.setValue(LocalDate.now());
        movementNotes.clear();
    }

    private void fillForm(InventoryItemView item)
    {
        if (item == null)
        {
            return;
        }
        selectAccountById(inventoryAccount, item.inventoryAccountId());
        selectFundById(fund, item.fundId());
        name.setText(item.name());
        itemType.setText(item.itemType());
        quantity.setText(item.quantity().toPlainString());
        unit.setText(item.unit());
        unitValue.setText(item.unitValue().toPlainString());
        acquisitionDate.setValue(item.acquisitionDate());
        custodian.setText(item.custodian());
        storageLocation.setText(item.storageLocation());
        condition.setValue(item.condition());
        itemStatus.setValue(item.status());
        notes.setText(item.notes());
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

    private static BigDecimal parseMoney(TextField field)
    {
        return new BigDecimal(field.getText().trim());
    }

    private static String activeCompanyCode()
    {
        return MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
    }

    private void addItemColumn(String title, Function<InventoryItemView, String> extractor, double width)
    {
        TableColumn<InventoryItemView, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new SimpleStringProperty(extractor.apply(row.getValue())));
        column.setPrefWidth(width);
        itemTable.getColumns().add(column);
    }

    private void addMovementColumn(String title, Function<InventoryMovementView, String> extractor, double width)
    {
        TableColumn<InventoryMovementView, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new SimpleStringProperty(extractor.apply(row.getValue())));
        column.setPrefWidth(width);
        movementTable.getColumns().add(column);
    }

    @Override public String title() { return "Inventory"; }
    @Override public Node root() { return root; }
}
