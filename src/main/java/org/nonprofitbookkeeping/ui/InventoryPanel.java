package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents the InventoryPanel component in the nonprofit bookkeeping application.
 */
public class InventoryPanel implements AppPanel
{
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BorderPane root = new BorderPane();
    private final TableView<Account> table = new TableView<>();
    private final ListView<String> movementLog = new ListView<>();
    private final Label status = new Label();
    private final TextField quantity = new TextField("1");

    public InventoryPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Inventory");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        Button receive = new Button("Receive Stock");
        receive.setOnAction(e -> recordMovement("RECEIPT"));
        Button issue = new Button("Issue Stock");
        issue.setOnAction(e -> recordMovement("ISSUE"));
        Button adjust = new Button("Adjust Count");
        adjust.setOnAction(e -> recordMovement("ADJUST"));

        quantity.setPromptText("Qty");

        HBox actions = new HBox(8, refresh, new Label("Qty"), quantity, receive, issue, adjust);
        VBox header = new VBox(6, title, actions, status, new Separator());

        root.setTop(header);

        TableColumn<Account, String> code = new TableColumn<>("Inventory Account");
        code.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getCode()));
        TableColumn<Account, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getName()));
        table.getColumns().addAll(code, name);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No inventory accounts found."));

        movementLog.setItems(FXCollections.observableArrayList());
        movementLog.setPlaceholder(new Label("No inventory movements recorded in this session."));
        movementLog.getItems().setAll(UiWorkspaceDataStore.inventoryMovementEntries());

        root.setCenter(new VBox(8, table, new Label("Inventory Runbook"), movementLog));
        reload();
    }

    private void recordMovement(String movementType)
    {
        Account selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select an inventory account first.");
            return;
        }
        int qty;
        try
        {
            qty = Integer.parseInt(quantity.getText().trim());
        }
        catch (RuntimeException ex)
        {
            status.setText("Quantity must be an integer.");
            return;
        }
        if (qty <= 0)
        {
            status.setText("Quantity must be greater than zero.");
            return;
        }

        String line = formatMovementEntry(movementType, qty, selected.getCode(), selected.getName(), LocalDateTime.now());
        UiWorkspaceDataStore.appendInventoryMovementEntry(line);
        movementLog.getItems().setAll(UiWorkspaceDataStore.inventoryMovementEntries());
        status.setText("Recorded inventory " + movementType + " for " + selected.getCode() + " (qty " + qty + ").");
    }

    static String formatMovementEntry(String movementType, int qty, String accountCode, String accountName, LocalDateTime at)
    {
        return at.format(TS) + " | " + movementType + " | qty=" + qty + " | " + accountCode + " | " + accountName;
    }

    private void reload()
    {
        status.setText("Loading inventory accounts...");
        UiAsync.run("inventory-load",
                () -> UiServiceRegistry.accountLookup().listActivePostingAccounts().stream()
                        .filter(a -> a.getSubtype() == AccountSubtype.INVENTORY)
                        .toList(),
                rows -> {
                    table.getItems().setAll(rows);
                    status.setText("Loaded " + rows.size() + " inventory account(s). Use runbook actions to record stock movement lifecycle.");
                },
                ex -> status.setText("Could not load inventory accounts: " + UiErrors.safeMessage(ex)));
    }

    @Override public String title() { return "Inventory"; }
    @Override public Node root() { return root; }
}
