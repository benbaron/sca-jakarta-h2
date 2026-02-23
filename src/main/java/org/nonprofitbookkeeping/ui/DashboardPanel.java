package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import nonprofitbookkeeping.ui.panels.DashboardPanelFX;
import org.nonprofitbookkeeping.service.FundBalanceRow;

import java.time.LocalDate;
import java.util.List;

/**
 * Keeps the shell-consistent dashboard layout and embeds imported dashboard workspace as a subpanel.
 */
public class DashboardPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final Label totals = new Label();
    private final Label status = new Label();
    private final TableView<FundBalanceRow> balances = new TableView<>();
    private final DashboardPanelFX importedDashboard = new DashboardPanelFX();
    private Button refresh;

    public DashboardPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Dashboard");
        title.getStyleClass().add("panel-title");

        Button add = new Button("+ Add");
        add.setOnAction(e -> onNew());

        refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());

        HBox actions = new HBox(8, add, refresh);
        VBox header = new VBox(6, title, actions, status, new Separator());
        root.setTop(header);

        TableColumn<FundBalanceRow, String> code = new TableColumn<>("Fund");
        code.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getFundCode()));
        TableColumn<FundBalanceRow, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getFundName()));
        TableColumn<FundBalanceRow, String> balance = new TableColumn<>("Balance As Of Today");
        balance.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getBalance().toPlainString()));
        balances.getColumns().addAll(code, name, balance);

        TitledPane importedWorkspace = new TitledPane("NonprofitBookkeeping workspace", importedDashboard);
        importedWorkspace.setCollapsible(false);

        VBox center = new VBox(8,
            new Label("Fund balances"),
            totals,
            balances,
            importedWorkspace);
        VBox.setVgrow(balances, Priority.ALWAYS);
        VBox.setVgrow(importedWorkspace, Priority.ALWAYS);
        root.setCenter(center);

        reload();
    }

    @Override public String title() { return "Dashboard"; }
    @Override public Node root() { return root; }

    @Override public void onNew()
    {
        reload();
        importedDashboard.reloadData();
    }

    private void reload()
    {
        refresh.setDisable(true);
        status.setText("Loading dashboard data...");

        UiAsync.run("dashboard-load",
            this::loadDashboardData,
            data -> {
                balances.getItems().setAll(data.rows());
                totals.setText("Active posting accounts: " + data.accountCount() + " | Active funds: " + data.fundCount());
                status.setText("Dashboard updated.");
                refresh.setDisable(false);
            },
            ex -> {
                status.setText("Failed to load dashboard: " + UiErrors.safeMessage(ex));
                refresh.setDisable(false);
            });
    }

    private DashboardData loadDashboardData()
    {
        List<FundBalanceRow> rows = UiServiceRegistry.fundBalance().balancesAsOf(LocalDate.now());
        int accountCount = UiServiceRegistry.accountLookup().listActivePostingAccounts().size();
        int fundCount = UiServiceRegistry.fundLookup().listActiveFunds().size();
        return new DashboardData(rows, accountCount, fundCount);
    }

    private record DashboardData(List<FundBalanceRow> rows, int accountCount, int fundCount) {}
}
