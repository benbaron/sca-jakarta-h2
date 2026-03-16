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
 * Represents the DepreciationRunsPanel component in the nonprofit bookkeeping application.
 */
public class DepreciationRunsPanel implements AppPanel
{
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BorderPane root = new BorderPane();
    private final TableView<Account> fixedAssetAccounts = new TableView<>();
    private final ListView<String> runHistory = new ListView<>();
    private final Label status = new Label();

    public DepreciationRunsPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Depreciation Runs");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());
        Button start = new Button("Record Started");
        start.setOnAction(e -> recordRunState("STARTED"));
        Button complete = new Button("Record Completed");
        complete.setOnAction(e -> recordRunState("COMPLETED"));
        Button fail = new Button("Record Failed");
        fail.setOnAction(e -> recordRunState("FAILED"));
        HBox actions = new HBox(8, refresh, start, complete, fail);

        root.setTop(new VBox(6, title, actions, status, new Separator()));

        TableColumn<Account, String> code = new TableColumn<>("Fixed Asset Account");
        code.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getCode()));
        TableColumn<Account, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getName()));
        fixedAssetAccounts.getColumns().addAll(code, name);
        fixedAssetAccounts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        fixedAssetAccounts.setPlaceholder(new Label("No fixed-asset accounts are configured for depreciation."));

        runHistory.setItems(FXCollections.observableArrayList());
        runHistory.setPlaceholder(new Label("No depreciation run events recorded yet."));
        runHistory.getItems().setAll(UiWorkspaceDataStore.depreciationRunEntries());

        root.setCenter(new VBox(8, fixedAssetAccounts, new Label("Depreciation Run History"), runHistory));
        reload();
    }

    private void recordRunState(String state)
    {
        Account selected = fixedAssetAccounts.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a fixed-asset account first.");
            return;
        }
        String entry = formatRunEntry(state, selected.getCode(), selected.getName(), LocalDateTime.now());
        UiWorkspaceDataStore.appendDepreciationRunEntry(entry);
        runHistory.getItems().setAll(UiWorkspaceDataStore.depreciationRunEntries());
        status.setText("Depreciation run state " + state + " recorded for " + selected.getCode() + ".");
    }

    static String formatRunEntry(String state, String accountCode, String accountName, LocalDateTime at)
    {
        return at.format(TS) + " | " + state + " | " + accountCode + " | " + accountName;
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
                    status.setText("Loaded " + rows.size() + " fixed-asset account(s). Record run lifecycle states from this basis.");
                },
                ex -> status.setText("Could not load depreciation basis: " + UiErrors.safeMessage(ex)));
    }

    @Override public String title() { return "Depreciation Runs"; }
    @Override public Node root() { return root; }
}
