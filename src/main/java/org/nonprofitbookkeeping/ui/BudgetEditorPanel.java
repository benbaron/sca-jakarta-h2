package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.service.BudgetLineCommand;
import org.nonprofitbookkeeping.service.BudgetPlanCommand;
import org.nonprofitbookkeeping.service.BudgetPlanService;
import org.nonprofitbookkeeping.service.BudgetPlanView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Budget editor backed by normalized H2 budget plans and lines. */
public class BudgetEditorPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<CategoryBudgetRow> table = new TableView<>();
    private final Label status = new Label();
    private final TextField amountField = new TextField();
    private BudgetPlanView currentPlan;

    public BudgetEditorPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Budget Editor");
        title.getStyleClass().add("panel-title");

        status.setId("budgetEditorStatus");
        table.setId("budgetEditorCategoryTable");
        amountField.setId("budgetEditorAmountField");

        Button refresh = new Button("Refresh Budget");
        refresh.setId("budgetEditorRefreshButton");
        refresh.setOnAction(e -> reload());
        Button saveTarget = new Button("Save Draft Amount");
        saveTarget.setId("budgetEditorSaveDraftAmountButton");
        saveTarget.setOnAction(e -> saveTarget());
        Button activate = new Button("Activate Version");
        activate.setId("budgetEditorActivateVersionButton");
        activate.setOnAction(e -> activatePlan());

        table.setId("budgetEditorCategoryTable");
        status.setId("budgetEditorStatusLabel");
        amountField.setId("budgetEditorAmountField");
        amountField.setPromptText("Budget amount");
        root.setTop(new VBox(6, title, new HBox(8, refresh, new Label("Amount"), amountField, saveTarget, activate), status, new Separator()));

        TableColumn<CategoryBudgetRow, String> code = new TableColumn<>("Category");
        code.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().categoryCode()));
        TableColumn<CategoryBudgetRow, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().categoryName()));
        TableColumn<CategoryBudgetRow, String> budgetTarget = new TableColumn<>("Budget Amount");
        budgetTarget.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().budgetAmount().toPlainString()));

        table.getColumns().addAll(code, name, budgetTarget);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No active budget categories are available."));
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null)
            {
                amountField.setText(newV.budgetAmount().toPlainString());
            }
        });

        Label details = new Label("Select a row, enter an amount, save the draft amount, then activate the budget version when ready.");
        details.setId("budgetEditorWorkflowHint");
        details.setWrapText(true);
        SplitPane splitPane = new SplitPane(table, details);
        splitPane.setId("budgetEditorSplitPane");
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setDividerPositions(0.82);
        root.setCenter(splitPane);
        reload();
    }

    private void reload()
    {
        status.setText("Loading normalized budget plan...");
        UiAsync.run("budget-editor-load", this::loadOrCreateDraftRows, rows -> {
            table.getItems().setAll(rows);
            String planStatus = currentPlan == null ? "draft" : currentPlan.status().name().toLowerCase();
            status.setText("Loaded " + rows.size() + " category row(s) for " + LocalDate.now().getYear() + " " + planStatus + " budget version.");
        }, ex -> status.setText("Could not load budget rows: " + UiErrors.safeMessage(ex)));
    }

    private List<CategoryBudgetRow> loadOrCreateDraftRows()
    {
        BudgetPlanService service = UiServiceRegistry.budgetPlan();
        int year = LocalDate.now().getYear();
        currentPlan = service.activeForFiscalYear(year).orElseGet(() -> service.createDraft(new BudgetPlanCommand(
                "FY " + year + " Budget", year, "FY" + year + "-DRAFT-" + System.currentTimeMillis(), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), "Created from Budget Editor")));
        List<BudgetCategory> categories = UiServiceRegistry.budgetCategoryLookup().listActiveBudgetCategories();
        Map<Long, BigDecimal> amounts = currentPlan.lines().stream()
                .filter(line -> line.fundId() == null && line.periodMonth() == null)
                .collect(Collectors.toMap(line -> line.budgetCategoryId(), line -> line.amount(), (left, right) -> right));
        return categories.stream()
                .map(category -> new CategoryBudgetRow(category.getId(), category.getCode(), category.getName(), amounts.getOrDefault(category.getId(), BigDecimal.ZERO)))
                .toList();
    }

    private void saveTarget()
    {
        CategoryBudgetRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a budget category before saving an amount.");
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
        UiAsync.run("budget-editor-save", () -> {
            BudgetPlanView draft = ensureDraftPlan();
            Map<Long, CategoryBudgetRow> rows = table.getItems().stream().collect(Collectors.toMap(CategoryBudgetRow::categoryId, Function.identity()));
            List<BudgetLineCommand> commands = new ArrayList<>();
            for (CategoryBudgetRow row : rows.values())
            {
                BigDecimal amount = row.categoryId().equals(selected.categoryId()) ? target : row.budgetAmount();
                if (amount.signum() != 0)
                {
                    commands.add(new BudgetLineCommand(row.categoryId(), null, null, amount, "Budget Editor"));
                }
            }
            return UiServiceRegistry.budgetPlan().replaceDraftLines(draft.id(), commands);
        }, plan -> {
            currentPlan = plan;
            status.setText("Saved draft budget amount for category " + selected.categoryCode() + ".");
            reload();
        }, ex -> status.setText("Could not save budget amount: " + UiErrors.safeMessage(ex)));
    }

    private BudgetPlanView ensureDraftPlan()
    {
        if (currentPlan != null && currentPlan.status() == org.nonprofitbookkeeping.model.BudgetPlan.Status.DRAFT)
        {
            return currentPlan;
        }
        int year = LocalDate.now().getYear();
        return UiServiceRegistry.budgetPlan().createDraft(new BudgetPlanCommand(
                "FY " + year + " Budget Revision", year, "FY" + year + "-REV-" + System.currentTimeMillis(), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), "Created from active budget for revision"));
    }

    private void activatePlan()
    {
        if (currentPlan == null)
        {
            status.setText("Load or save a draft budget before activation.");
            return;
        }
        UiAsync.run("budget-editor-activate", () -> UiServiceRegistry.budgetPlan().activate(currentPlan.id()), plan -> {
            currentPlan = plan;
            status.setText("Activated budget version " + plan.versionCode() + " for fiscal year " + plan.fiscalYear() + ".");
            reload();
        }, ex -> status.setText("Could not activate budget version: " + UiErrors.safeMessage(ex)));
    }

    static BigDecimal parseTargetAmount(String input)
    {
        if (input == null || input.isBlank())
        {
            throw new IllegalArgumentException("Enter an amount before saving.");
        }
        try
        {
            BigDecimal amount = new BigDecimal(input.trim());
            if (amount.signum() < 0 || amount.scale() > 4)
            {
                throw new IllegalArgumentException("Budget amount must be non-negative with up to 4 decimal places.");
            }
            return amount;
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalArgumentException("Enter a valid numeric budget amount.");
        }
    }

    record CategoryBudgetRow(Long categoryId, String categoryCode, String categoryName, BigDecimal budgetAmount)
    {
    }

    @Override public String title() { return "Budget Editor"; }
    @Override public Node root() { return root; }
}
