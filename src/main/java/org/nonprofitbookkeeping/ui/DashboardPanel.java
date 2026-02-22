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
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;

import org.nonprofitbookkeeping.service.FundBalanceRow;

import java.time.LocalDate;
import java.util.List;

public class DashboardPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final Label totals = new Label();
    private final Label status = new Label();
    private final TableView<FundBalanceRow> balances = new TableView<>();
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

        VBox center = new VBox(8, new Label("Fund balances"), totals, balances);
        VBox.setVgrow(balances, Priority.ALWAYS);
        root.setCenter(center);

        reload();
    }

    @Override public String title() { return "Dashboard"; }
    @Override public Node root() { return root; }

    @Override public void onNew() {
        reload();
    }

    private void reload()
    {
        refresh.setDisable(true);
        status.setText("Loading dashboard data...");

        Task<DashboardData> task = new Task<>()
        {
            @Override
            protected DashboardData call()
            {
                List<FundBalanceRow> rows = UiServiceRegistry.fundBalance().balancesAsOf(LocalDate.now());
                int accountCount = UiServiceRegistry.accountLookup().listActivePostingAccounts().size();
                int fundCount = UiServiceRegistry.fundLookup().listActiveFunds().size();
                return new DashboardData(rows, accountCount, fundCount);
            }
        };

        task.setOnSucceeded(e -> {
            DashboardData data = task.getValue();
            balances.getItems().setAll(data.rows());
            totals.setText("Active posting accounts: " + data.accountCount() + " | Active funds: " + data.fundCount());
            status.setText("Dashboard updated.");
            refresh.setDisable(false);
        });

        task.setOnFailed(e -> {
            status.setText("Failed to load dashboard: " + task.getException().getMessage());
            refresh.setDisable(false);
        });

        Thread t = new Thread(task, "dashboard-load");
        t.setDaemon(true);
        t.start();
    }

    private record DashboardData(List<FundBalanceRow> rows, int accountCount, int fundCount) {}
}
