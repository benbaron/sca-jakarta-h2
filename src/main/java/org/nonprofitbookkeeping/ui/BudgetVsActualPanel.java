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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Represents the BudgetVsActualPanel component in the nonprofit bookkeeping application.
 */
public class BudgetVsActualPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<BudgetActualRow> table = new TableView<>();
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

        TableColumn<BudgetActualRow, String> fund = new TableColumn<>("Fund");
        fund.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().fundCode()));
        TableColumn<BudgetActualRow, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().fundName()));
        TableColumn<BudgetActualRow, String> budget = new TableColumn<>("Budget");
        budget.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().budget().toPlainString()));
        TableColumn<BudgetActualRow, String> actual = new TableColumn<>("Actual (Net)");
        actual.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().actual().toPlainString()));
        TableColumn<BudgetActualRow, String> variance = new TableColumn<>("Variance (Actual-Budget)");
        variance.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().variance().toPlainString()));
        table.getColumns().addAll(fund, name, budget, actual, variance);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No posted activity found for the selected period."));

        root.setCenter(table);
        reload();
    }

    private void reload()
    {
        status.setText("Running Budget vs Actual snapshot from posted transactions...");
        UiAsync.run("budget-vs-actual",
                () -> mergeBudgetAndActual(
                        UiServiceRegistry.fundBalance().balancesAsOf(LocalDate.now()),
                        UiWorkspaceDataStore.budgetTargetsByFundCode()),
                rows -> {
                    table.getItems().setAll(rows);
                    BigDecimal netActual = rows.stream().map(BudgetActualRow::actual).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal netBudget = rows.stream().map(BudgetActualRow::budget).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal netVariance = rows.stream().map(BudgetActualRow::variance).reduce(BigDecimal.ZERO, BigDecimal::add);
                    status.setText("Loaded " + rows.size() + " fund row(s). Net actual = " + netActual.toPlainString()
                            + ", net budget = " + netBudget.toPlainString()
                            + ", net variance = " + netVariance.toPlainString());
                },
                ex -> status.setText("Could not compute Budget vs Actual view: " + UiErrors.safeMessage(ex)));
    }

    static List<BudgetActualRow> mergeBudgetAndActual(List<FundBalanceRow> actualRows,
                                                      Map<String, BigDecimal> targetsByFund)
    {
        return actualRows.stream()
                .map(r -> {
                    BigDecimal budget = targetsByFund.getOrDefault(r.getFundCode(), BigDecimal.ZERO);
                    BigDecimal actual = r.getBalance();
                    return new BudgetActualRow(r.getFundCode(), r.getFundName(), budget, actual, actual.subtract(budget));
                })
                .sorted(Comparator.comparing(BudgetActualRow::fundCode))
                .toList();
    }

    record BudgetActualRow(String fundCode, String fundName, BigDecimal budget, BigDecimal actual, BigDecimal variance)
    {
    }

    @Override public String title() { return "Budget vs Actual"; }
    @Override public Node root() { return root; }
}
