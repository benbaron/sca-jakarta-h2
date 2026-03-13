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
import org.nonprofitbookkeeping.service.FundBalanceRow;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents the BudgetVsActualPanel component in the nonprofit bookkeeping application.
 */
public class BudgetVsActualPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<FundBalanceRow> table = new TableView<>();
    private final Label status = new Label();

    public BudgetVsActualPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Budget vs Actual");
        title.getStyleClass().add("panel-title");

        Button run = new Button("Run");
        run.setOnAction(e -> reload());
        HBox actions = new HBox(8, run);

        root.setTop(new VBox(6, title, actions, status, new Separator()));

        TableColumn<FundBalanceRow, String> fund = new TableColumn<>("Fund");
        fund.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getFundCode()));
        TableColumn<FundBalanceRow, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getFundName()));
        TableColumn<FundBalanceRow, String> actual = new TableColumn<>("Actual (Net)");
        actual.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getBalance().toPlainString()));
        table.getColumns().addAll(fund, name, actual);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No posted activity found for the selected period."));

        root.setCenter(table);
        reload();
    }

    private void reload()
    {
        status.setText("Running Budget vs Actual snapshot from posted transactions...");
        UiAsync.run("budget-vs-actual",
                () -> UiServiceRegistry.fundBalance().balancesAsOf(LocalDate.now()),
                rows -> {
                    table.getItems().setAll(rows);
                    BigDecimal net = rows.stream().map(FundBalanceRow::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
                    status.setText("Loaded " + rows.size() + " fund row(s). Net actual = " + net.toPlainString());
                },
                ex -> status.setText("Could not compute Budget vs Actual view: " + UiErrors.safeMessage(ex)));
    }

    @Override public String title() { return "Budget vs Actual"; }
    @Override public Node root() { return root; }
}
