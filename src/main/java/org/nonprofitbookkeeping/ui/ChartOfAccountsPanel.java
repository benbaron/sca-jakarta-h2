package org.nonprofitbookkeeping.ui;

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
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;

import org.nonprofitbookkeeping.model.Account;

import java.util.List;

public class ChartOfAccountsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<Account> table = new TableView<>();
    private final Label status = new Label();
    private Button refresh;

    public ChartOfAccountsPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Chart of Accounts");
        title.getStyleClass().add("panel-title");

        Button add = new Button("+ Add");
        add.setOnAction(e -> onNew());

        refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());

        HBox actions = new HBox(8, add, refresh);
        VBox header = new VBox(6, title, actions, status, new Separator());

        root.setTop(header);

        TableColumn<Account, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getCode()));

        TableColumn<Account, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getName()));

        TableColumn<Account, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getAccountType())));

        TableColumn<Account, String> subtype = new TableColumn<>("Subtype");
        subtype.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getSubtype())));

        table.getColumns().addAll(code, name, type, subtype);
        table.setPlaceholder(new Label("No accounts found. Run the seed command to create starter data."));
        root.setCenter(table);

        reload();
    }

    @Override public String title() { return "Chart of Accounts"; }
    @Override public Node root() { return root; }

    @Override public void onNew() {
        new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
            "Create account UI is not built yet. This panel is now connected to AccountLookupService for read-only listing.").showAndWait();
    }

    private void reload()
    {
        refresh.setDisable(true);
        status.setText("Loading accounts...");

        Task<List<Account>> task = new Task<>()
        {
            @Override
            protected List<Account> call()
            {
                return UiServiceRegistry.accountLookup().listActivePostingAccounts();
            }
        };

        task.setOnSucceeded(e -> {
            table.getItems().setAll(task.getValue());
            status.setText("Loaded " + task.getValue().size() + " posting account(s).");
            refresh.setDisable(false);
        });

        task.setOnFailed(e -> {
            status.setText("Failed to load accounts: " + task.getException().getMessage());
            refresh.setDisable(false);
        });

        Thread t = new Thread(task, "coa-load");
        t.setDaemon(true);
        t.start();
    }
}
