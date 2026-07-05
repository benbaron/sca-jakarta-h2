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
import org.nonprofitbookkeeping.service.BudgetVarianceView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Budget-vs-actual panel backed by active normalized budget plans. */
public class BudgetVsActualPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<BudgetVarianceView> table = new TableView<>();
    private final Label status = new Label();

    public BudgetVsActualPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Budget vs Actual");
        title.getStyleClass().add("panel-title");
        Button run = new Button("Run");
        run.setOnAction(e -> reload());
        root.setTop(new VBox(6, title, new HBox(8, run), status, new Separator()));

        TableColumn<BudgetVarianceView, String> category = new TableColumn<>("Budget Category");
        category.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().budgetCategoryCode() + " — " + v.getValue().budgetCategoryName()));
        TableColumn<BudgetVarianceView, String> fund = new TableColumn<>("Fund");
        fund.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().fundCode().orElse("All funds")));
        TableColumn<BudgetVarianceView, String> budget = new TableColumn<>("Budget");
        budget.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().budget().toPlainString()));
        TableColumn<BudgetVarianceView, String> actual = new TableColumn<>("Actual");
        actual.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().actual().toPlainString()));
        TableColumn<BudgetVarianceView, String> variance = new TableColumn<>("Variance (Actual-Budget)");
        variance.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().variance().toPlainString()));
        table.getColumns().addAll(category, fund, budget, actual, variance);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No active budget version is selected for comparison."));
        root.setCenter(table);
        reload();
    }

    private void reload()
    {
        LocalDate asOfDate = LocalDate.now();
        status.setText("Running active budget comparison...");
        UiAsync.run("budget-vs-actual", () -> UiServiceRegistry.budgetPlan().activeVariance(asOfDate), rows -> {
            table.getItems().setAll(rows);
            BigDecimal netActual = total(rows, BudgetVarianceView::actual);
            BigDecimal netBudget = total(rows, BudgetVarianceView::budget);
            BigDecimal netVariance = total(rows, BudgetVarianceView::variance);
            status.setText("Loaded " + rows.size() + " active budget row(s). Net actual = " + netActual.toPlainString()
                    + ", net budget = " + netBudget.toPlainString() + ", net variance = " + netVariance.toPlainString());
        }, ex -> status.setText("Could not compute Budget vs Actual view: " + UiErrors.safeMessage(ex)));
    }

    private static BigDecimal total(List<BudgetVarianceView> rows, java.util.function.Function<BudgetVarianceView, BigDecimal> mapper)
    {
        return rows.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override public String title() { return "Budget vs Actual"; }
    @Override public Node root() { return root; }
}
