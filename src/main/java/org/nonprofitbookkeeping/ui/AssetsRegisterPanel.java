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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents the AssetsRegisterPanel component in the nonprofit bookkeeping application.
 */
public class AssetsRegisterPanel implements AppPanel
{
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BorderPane root = new BorderPane();
    private final TableView<Account> table = new TableView<>();
    private final ListView<String> lifecycleLog = new ListView<>();
    private final Label status = new Label();

    public AssetsRegisterPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Asset Register");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        Button acquisition = new Button("Record Acquisition");
        acquisition.setOnAction(e -> recordLifecycle("ACQUIRED"));
        Button disposal = new Button("Record Disposal");
        disposal.setOnAction(e -> recordLifecycle("DISPOSED"));
        Button impairment = new Button("Record Impairment");
        impairment.setOnAction(e -> recordLifecycle("IMPAIRED"));
        HBox actions = new HBox(8, refresh, acquisition, disposal, impairment);

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

        lifecycleLog.setItems(FXCollections.observableArrayList());
        lifecycleLog.setPlaceholder(new Label("No asset lifecycle events recorded in this session."));
        lifecycleLog.getItems().setAll(UiWorkspaceDataStore.assetLifecycleEntries());

        root.setCenter(new VBox(8, table, new Label("Lifecycle Runbook"), lifecycleLog));
        reload();
    }

    private void recordLifecycle(String action)
    {
        Account selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select an asset account first.");
            return;
        }
        String line = formatLifecycleEntry(action, selected.getCode(), selected.getName(), LocalDateTime.now());
        UiWorkspaceDataStore.appendAssetLifecycleEntry(line);
        lifecycleLog.getItems().setAll(UiWorkspaceDataStore.assetLifecycleEntries());
        status.setText("Recorded asset lifecycle action: " + action + " for " + selected.getCode() + ".");
    }

    static String formatLifecycleEntry(String action, String accountCode, String accountName, LocalDateTime at)
    {
        return at.format(TS) + " | " + action + " | " + accountCode + " | " + accountName;
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
                    status.setText("Loaded " + rows.size() + " fixed-asset account(s). Use lifecycle buttons to record runbook entries.");
                },
                ex -> status.setText("Could not load fixed-asset accounts: " + UiErrors.safeMessage(ex)));
    }

    @Override public String title() { return "Asset Register"; }
    @Override public Node root() { return root; }
}
