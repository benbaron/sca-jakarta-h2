package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.FundBalanceRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Represents the BudgetEditorPanel component in the nonprofit bookkeeping application.
 */
public class BudgetEditorPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<FundBudgetRow> table = new TableView<>();
    private final Label status = new Label();
    private final TextField amountField = new TextField();

    public BudgetEditorPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Budget Editor");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh Funds");
        refresh.setOnAction(e -> reload());
        Button saveTarget = new Button("Save Target");
        saveTarget.setOnAction(e -> saveTarget());
        Button clearTarget = new Button("Clear Target");
        clearTarget.setOnAction(e -> clearTarget());

        amountField.setPromptText("Budget target amount");

        HBox actions = new HBox(8, refresh, new Label("Target"), amountField, saveTarget, clearTarget);

        root.setTop(new VBox(6, title, actions, status, new Separator()));

        TableColumn<FundBudgetRow, String> fundCode = new TableColumn<>("Fund");
        fundCode.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().fundCode()));
        TableColumn<FundBudgetRow, String> fundName = new TableColumn<>("Name");
        fundName.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().fundName()));
        TableColumn<FundBudgetRow, String> actual = new TableColumn<>("Actual (Net)");
        actual.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().actual().toPlainString()));
        TableColumn<FundBudgetRow, String> budgetTarget = new TableColumn<>("Budget Target");
        budgetTarget.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().budgetTarget().toPlainString()));
        TableColumn<FundBudgetRow, String> variance = new TableColumn<>("Variance (Actual-Budget)");
        variance.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().actual().subtract(v.getValue().budgetTarget()).toPlainString()));

        table.getColumns().addAll(fundCode, fundName, actual, budgetTarget, variance);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No fund activity available for budget planning."));

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null)
            {
                amountField.setText(newV.budgetTarget().toPlainString());
            }
        });

        root.setCenter(table);
        reload();
    }

    private void reload()
    {
        status.setText("Loading budget rows by fund...");
        UiAsync.run("budget-editor-funds",
                () -> buildRows(UiServiceRegistry.fundBalance().balancesAsOf(LocalDate.now()), UiWorkspaceDataStore.budgetTargetsByFundCode()),
                rows -> {
                    table.getItems().setAll(rows);
                    status.setText("Loaded " + rows.size() + " fund budget row(s). Select a fund to update target.");
                },
                ex -> status.setText("Could not load budget rows: " + UiErrors.safeMessage(ex)));
    }

    private void saveTarget()
    {
        FundBudgetRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a fund row before saving a target.");
            return;
        }
        BigDecimal target;
        try
        {
            target = parseTargetAmount(amountField.getText());
        }
        catch (IllegalArgumentException ex)
        {
            status.setText(ex.getMessage());
            return;
        }

        UiWorkspaceDataStore.upsertBudgetTarget(selected.fundCode(), target);
        status.setText("Saved target " + target.toPlainString() + " for fund " + selected.fundCode() + ".");
        reload();
    }

    private void clearTarget()
    {
        FundBudgetRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a fund row before clearing a target.");
            return;
        }
        UiWorkspaceDataStore.removeBudgetTarget(selected.fundCode());
        status.setText("Cleared target for fund " + selected.fundCode() + ".");
        reload();
    }

    static java.util.List<FundBudgetRow> buildRows(java.util.List<FundBalanceRow> actualRows,
                                                   Map<String, BigDecimal> targetsByFund)
    {
        return actualRows.stream()
                .map(r -> new FundBudgetRow(
                        r.getFundCode(),
                        r.getFundName(),
                        r.getBalance(),
                        targetsByFund.getOrDefault(r.getFundCode(), BigDecimal.ZERO)))
                .sorted(java.util.Comparator.comparing(FundBudgetRow::fundCode))
                .toList();
    }

    static BigDecimal parseTargetAmount(String input)
    {
        if (input == null || input.isBlank())
        {
            throw new IllegalArgumentException("Enter a target amount before saving.");
        }
        final BigDecimal amount;
        try
        {
            amount = new BigDecimal(input.trim());
        }
        catch (RuntimeException ex)
        {
            throw new IllegalArgumentException("Enter a valid numeric target amount.");
        }
        if (amount.signum() < 0)
        {
            throw new IllegalArgumentException("Budget target cannot be negative.");
        }
        if (amount.scale() > 2)
        {
            throw new IllegalArgumentException("Budget target supports up to 2 decimal places.");
        }
        return amount;
    }

    record FundBudgetRow(String fundCode, String fundName, BigDecimal actual, BigDecimal budgetTarget)
    {
    }

    @Override public String title() { return "Budget Editor"; }
    @Override public Node root() { return root; }
}
