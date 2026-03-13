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
 * Represents the AssetsRegisterPanel component in the nonprofit bookkeeping application.
 */
public class AssetsRegisterPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<Account> table = new TableView<>();
    private final Label status = new Label();

    public AssetsRegisterPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Asset Register");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        HBox actions = new HBox(8, refresh);

        root.setTop(new VBox(6, title, actions, status, new Separator()));

        TableColumn<Account, String> code = new TableColumn<>("Account");
        code.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getCode()));
        TableColumn<Account, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getName()));
        TableColumn<Account, String> subtype = new TableColumn<>("Subtype");
        subtype.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getSubtype())));
        table.getColumns().addAll(code, name, subtype);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No fixed-asset posting accounts found."));

        root.setCenter(table);
        reload();
    }

    private void reload()
    {
        status.setText("Loading fixed-asset accounts...");
        UiAsync.run("asset-register-load",
                () -> UiServiceRegistry.accountLookup().listActivePostingAccounts().stream()
                        .filter(a -> a.getSubtype() == AccountSubtype.FIXED_ASSET)
                        .toList(),
                rows -> {
                    table.getItems().setAll(rows);
                    status.setText("Loaded " + rows.size() + " fixed-asset account(s).");
                },
                ex -> status.setText("Could not load fixed-asset accounts: " + UiErrors.safeMessage(ex)));
    }

    @Override public String title() { return "Asset Register"; }
    @Override public Node root() { return root; }
}
