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
 * Represents the DepreciationRunsPanel component in the nonprofit bookkeeping application.
 */
public class DepreciationRunsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<Account> fixedAssetAccounts = new TableView<>();
    private final Label status = new Label();

    public DepreciationRunsPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Depreciation Runs");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        HBox actions = new HBox(8, refresh);

        root.setTop(new VBox(6, title, actions, status, new Separator()));

        TableColumn<Account, String> code = new TableColumn<>("Fixed Asset Account");
        code.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getCode()));
        TableColumn<Account, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getName()));
        fixedAssetAccounts.getColumns().addAll(code, name);
        fixedAssetAccounts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        fixedAssetAccounts.setPlaceholder(new Label("No fixed-asset accounts are configured for depreciation."));

        root.setCenter(fixedAssetAccounts);
        reload();
    }

    private void reload()
    {
        status.setText("Loading depreciation-eligible accounts...");
        UiAsync.run("depreciation-run-load",
                () -> UiServiceRegistry.accountLookup().listActivePostingAccounts().stream()
                        .filter(a -> a.getSubtype() == AccountSubtype.FIXED_ASSET)
                        .toList(),
                rows -> {
                    fixedAssetAccounts.getItems().setAll(rows);
                    status.setText("Loaded " + rows.size() + " fixed-asset account(s). Run logic can be executed from this basis.");
                },
                ex -> status.setText("Could not load depreciation basis: " + UiErrors.safeMessage(ex)));
    }

    @Override public String title() { return "Depreciation Runs"; }
    @Override public Node root() { return root; }
}
