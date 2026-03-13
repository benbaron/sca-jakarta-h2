package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;

/**
 * Represents the InventoryPanel component in the nonprofit bookkeeping application.
 */
public class InventoryPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<Account> table = new TableView<>();
    private final Label status = new Label();

    public InventoryPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Inventory");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());

        HBox actions = new HBox(8, refresh);
        VBox header = new VBox(6, title, actions, status, new Separator());

        root.setTop(header);

        TableColumn<Account, String> code = new TableColumn<>("Inventory Account");
        code.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getCode()));
        TableColumn<Account, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getName()));
        table.getColumns().addAll(code, name);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No inventory accounts found."));

        root.setCenter(table);
        reload();
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
                    status.setText("Loaded " + rows.size() + " inventory account(s).");
                },
                ex -> status.setText("Could not load inventory accounts: " + UiErrors.safeMessage(ex)));
    }

    @Override public String title() { return "Inventory"; }
    @Override public Node root() { return root; }
}
