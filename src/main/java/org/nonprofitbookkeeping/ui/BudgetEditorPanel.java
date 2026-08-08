package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.service.BudgetLineCommand;
import org.nonprofitbookkeeping.service.BudgetPlanService;
import org.nonprofitbookkeeping.service.BudgetPlanView;
import org.nonprofitbookkeeping.service.FiscalPeriodRange;

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
    private final ComboBox<BudgetPlanView> planSelector = new ComboBox<>();
    private final Label status = new Label();
    private final TextField amountField = new TextField();
    private final Button saveTargetButton = new Button("Save Draft Amount");
    private final Button createRevisionButton = new Button("Create Revision");
    private final Button activateButton = new Button("Activate Version");
    private final CompanyUiFormat companyFormat = CompanyUiFormat.activeCompany();
    private final FormDirtyTracker dirtyState;
    private boolean suppressCategorySelection;
    private boolean suppressPlanSelection;
    private BudgetPlanView currentPlan;
    private FiscalPeriodRange currentRange;
    private List<BudgetCategory> currentCategories = List.of();

    public BudgetEditorPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Budget Editor");
        title.getStyleClass().add("panel-title");

        status.setId("budgetEditorStatus");
        table.setId("budgetEditorCategoryTable");
        planSelector.setId("budgetEditorPlanSelector");
        amountField.setId("budgetEditorAmountField");
        amountField.setPromptText("Budget amount");

        Button refresh = new Button("Refresh Budget");
        refresh.setId("budgetEditorRefreshButton");
        refresh.setOnAction(e -> reloadWithDiscardProtection());
        Button newDraft = new Button("New Draft");
        newDraft.setId("budgetEditorNewDraftButton");
        newDraft.setOnAction(e -> createDraft());
        createRevisionButton.setId("budgetEditorCreateRevisionButton");
        createRevisionButton.setOnAction(e -> createRevision());
        saveTargetButton.setId("budgetEditorSaveDraftAmountButton");
        saveTargetButton.setOnAction(e -> saveTarget());
        activateButton.setId("budgetEditorActivateVersionButton");
        activateButton.setOnAction(e -> activatePlan());

        planSelector.setPrefWidth(390.0);
        planSelector.setConverter(new StringConverter<>()
        {
            @Override
            public String toString(BudgetPlanView plan)
            {
                if (plan == null)
                {
                    return "";
                }
                return plan.versionCode() + " — " + plan.status() + " — " + plan.name();
            }

            @Override
            public BudgetPlanView fromString(String value)
            {
                return null;
            }
        });
        planSelector.valueProperty().addListener((obs, oldPlan, newPlan) -> selectPlan(oldPlan, newPlan));

        status.setId("budgetEditorStatusLabel");
        HBox versionRow = new HBox(8, new Label("Version"), planSelector);
        HBox actions = new HBox(8, refresh, newDraft, createRevisionButton, activateButton);
        root.setTop(new VBox(6, title, versionRow, actions, status, new Separator()));
        dirtyState = new FormDirtyTracker(this::formSnapshot);

        TableColumn<CategoryBudgetRow, String> code = new TableColumn<>("Category");
        code.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().categoryCode()));
        TableColumn<CategoryBudgetRow, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().categoryName()));
        TableColumn<CategoryBudgetRow, String> budgetTarget = new TableColumn<>("Budget Amount");
        budgetTarget.setCellValueFactory(v -> new SimpleStringProperty(companyFormat.formatMoney(v.getValue().budgetAmount())));

        table.getColumns().addAll(code, name, budgetTarget);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No active budget categories are available."));
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (suppressCategorySelection || newV == null)
            {
                return;
            }
            if (dirtyState.isDirty() && !confirmDiscard())
            {
                suppressCategorySelection = true;
                table.getSelectionModel().select(oldV);
                suppressCategorySelection = false;
                return;
            }
            amountField.setText(newV.budgetAmount().toPlainString());
            dirtyState.markClean();
        });

        Label details = new Label("Select an explicit draft version, edit a category amount, and save it. Create Revision copies the selected active version before editing; activation targets only the selected draft.");
        details.setId("budgetEditorWorkflowHint");
        details.setWrapText(true);
        GridPane editor = new GridPane();
        editor.setHgap(8);
        editor.setVgap(8);
        editor.setPadding(new Insets(8));
        editor.addRow(0, new Label("Budget amount"), amountField, saveTargetButton);
        editor.add(details, 0, 1, 3, 1);
        ScrollPane editorScroll = new ScrollPane(editor);
        editorScroll.setId("budgetEditorScroll");
        editorScroll.setFitToWidth(true);
        editorScroll.setMinHeight(0.0);
        editorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editorScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        SplitPane splitPane = new SplitPane(table, editorScroll);
        splitPane.setId("budgetEditorSplitPane");
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setDividerPositions(0.72);
        table.setMinHeight(0.0);
        CompanySplitPaneStateBinder.bind(splitPane, "budget-editor-workspace", 0.72);
        root.setCenter(splitPane);

        ActivePeriodContext.activeDateProperty().addListener((obs, oldDate, newDate) -> {
            if (dirtyState.isDirty())
            {
                status.setText("The active accounting period changed. Save or discard the current edit, then Refresh Budget to load the new fiscal period.");
            }
            else
            {
                reloadPreferred(currentPlan == null ? null : currentPlan.id());
            }
        });
        reloadPreferred(null);
    }

    private void reloadPreferred(Long preferredPlanId)
    {
        LocalDate selectedPeriodStart = ActivePeriodContext.get();
        status.setText("Loading normalized budget plans for the selected accounting period...");
        UiAsync.run("budget-editor-load", () -> loadSnapshot(selectedPeriodStart), snapshot -> {
            currentRange = snapshot.range();
            currentCategories = snapshot.categories();
            planSelector.getItems().setAll(snapshot.plans());
            BudgetPlanView selected = findPlan(snapshot.plans(), preferredPlanId);
            if (selected == null)
            {
                selected = snapshot.plans().stream()
                        .filter(plan -> plan.status() == BudgetPlan.Status.DRAFT)
                        .findFirst()
                        .orElseGet(() -> snapshot.plans().stream()
                                .filter(plan -> plan.status() == BudgetPlan.Status.ACTIVE)
                                .findFirst()
                                .orElse(null));
            }
            suppressPlanSelection = true;
            planSelector.setValue(selected);
            suppressPlanSelection = false;
            currentPlan = selected;
            renderRows();
            updateCommandState();
            status.setText(snapshot.plans().isEmpty()
                    ? "No budget version exists for " + currentRange.displayLabel() + ". Choose New Draft to create one explicitly."
                    : "Loaded " + snapshot.plans().size() + " editable/active version(s) for " + currentRange.displayLabel() + ".");
        }, ex -> status.setText("Could not load budget rows: " + UiErrors.safeMessage(ex)));
    }

    private BudgetEditorSnapshot loadSnapshot(LocalDate selectedPeriodStart)
    {
        BudgetPlanService service = UiServiceRegistry.budgetPlan();
        FiscalPeriodRange range = service.fiscalRange(selectedPeriodStart);
        return new BudgetEditorSnapshot(
                range,
                service.editableAndActiveForFiscalYear(range.fiscalYear()),
                UiServiceRegistry.budgetCategoryLookup().listActiveBudgetCategories());
    }

    private void selectPlan(BudgetPlanView oldPlan, BudgetPlanView newPlan)
    {
        if (suppressPlanSelection || newPlan == null)
        {
            return;
        }
        if (dirtyState.isDirty() && !confirmDiscard())
        {
            suppressPlanSelection = true;
            planSelector.setValue(oldPlan);
            suppressPlanSelection = false;
            return;
        }
        currentPlan = newPlan;
        amountField.clear();
        table.getSelectionModel().clearSelection();
        dirtyState.markClean();
        renderRows();
        updateCommandState();
        status.setText("Selected " + newPlan.versionCode() + " (" + newPlan.status() + ") for " + currentRange.displayLabel() + ".");
    }

    private void renderRows()
    {
        Map<Long, BigDecimal> amounts = currentPlan == null
                ? Map.of()
                : currentPlan.lines().stream()
                        .filter(line -> line.fundId() == null && line.periodMonth() == null)
                        .collect(Collectors.toMap(line -> line.budgetCategoryId(), line -> line.amount(), (left, right) -> right));
        table.getItems().setAll(currentCategories.stream()
                .map(category -> new CategoryBudgetRow(
                        category.getId(),
                        category.getCode(),
                        category.getName(),
                        amounts.getOrDefault(category.getId(), BigDecimal.ZERO)))
                .toList());
    }

    private void updateCommandState()
    {
        boolean draft = currentPlan != null && currentPlan.status() == BudgetPlan.Status.DRAFT;
        boolean active = currentPlan != null && currentPlan.status() == BudgetPlan.Status.ACTIVE;
        saveTargetButton.setDisable(!draft);
        activateButton.setDisable(!draft);
        createRevisionButton.setDisable(!active);
        amountField.setDisable(!draft);
    }

    private void createDraft()
    {
        if (dirtyState.isDirty())
        {
            status.setText("Save or discard the current edit before creating another draft.");
            return;
        }
        if (currentRange == null)
        {
            status.setText("Refresh the fiscal period before creating a draft.");
            return;
        }
        FiscalPeriodRange range = currentRange;
        UiAsync.run("budget-editor-new-draft", () -> UiServiceRegistry.budgetPlan().createDraft(range),
                plan -> reloadPreferred(plan.id()),
                ex -> status.setText("Could not create budget draft: " + UiErrors.safeMessage(ex)));
    }

    private void createRevision()
    {
        if (dirtyState.isDirty())
        {
            status.setText("Save or discard the current edit before creating a revision.");
            return;
        }
        if (currentPlan == null || currentPlan.status() != BudgetPlan.Status.ACTIVE)
        {
            status.setText("Select the active budget version to create a revision.");
            return;
        }
        long sourcePlanId = currentPlan.id();
        UiAsync.run("budget-editor-create-revision", () -> UiServiceRegistry.budgetPlan().createRevision(sourcePlanId),
                plan -> reloadPreferred(plan.id()),
                ex -> status.setText("Could not create budget revision: " + UiErrors.safeMessage(ex)));
    }

    private void saveTarget()
    {
        CategoryBudgetRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a budget category before saving an amount.");
            return;
        }
        if (currentPlan == null || currentPlan.status() != BudgetPlan.Status.DRAFT)
        {
            status.setText("Select an explicit draft version before editing or saving budget amounts.");
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
        long planId = currentPlan.id();
        Map<Long, CategoryBudgetRow> rows = table.getItems().stream()
                .collect(Collectors.toMap(CategoryBudgetRow::categoryId, Function.identity()));
        List<BudgetLineCommand> commands = new ArrayList<>();
        for (CategoryBudgetRow row : rows.values())
        {
            BigDecimal amount = row.categoryId().equals(selected.categoryId()) ? target : row.budgetAmount();
            if (amount.signum() != 0)
            {
                commands.add(new BudgetLineCommand(row.categoryId(), null, null, amount, "Budget Editor"));
            }
        }
        UiAsync.run("budget-editor-save",
                () -> UiServiceRegistry.budgetPlan().replaceDraftLines(planId, commands),
                plan -> {
                    dirtyState.markClean();
                    reloadPreferred(plan.id());
                },
                ex -> status.setText("Could not save budget amount: " + UiErrors.safeMessage(ex)));
    }

    private void activatePlan()
    {
        if (dirtyState.isDirty())
        {
            status.setText("Save or discard the edited budget amount before activating the version.");
            return;
        }
        if (currentPlan == null || currentPlan.status() != BudgetPlan.Status.DRAFT)
        {
            status.setText("Select the draft budget version to activate.");
            return;
        }
        long planId = currentPlan.id();
        UiAsync.run("budget-editor-activate", () -> UiServiceRegistry.budgetPlan().activate(planId),
                plan -> reloadPreferred(plan.id()),
                ex -> status.setText("Could not activate budget version: " + UiErrors.safeMessage(ex)));
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

    private static BudgetPlanView findPlan(List<BudgetPlanView> plans, Long planId)
    {
        if (planId == null)
        {
            return null;
        }
        return plans.stream().filter(plan -> plan.id().equals(planId)).findFirst().orElse(null);
    }

    record CategoryBudgetRow(Long categoryId, String categoryCode, String categoryName, BigDecimal budgetAmount)
    {
    }

    private record BudgetEditorSnapshot(
            FiscalPeriodRange range,
            List<BudgetPlanView> plans,
            List<BudgetCategory> categories)
    {
        private BudgetEditorSnapshot
        {
            plans = List.copyOf(plans);
            categories = List.copyOf(categories);
        }
    }

    @Override public String title() { return "Budget Editor"; }
    @Override public Node root() { return root; }
    @Override public void onSave() { saveTarget(); }
    @Override public boolean hasUnsavedChanges() { return dirtyState.isDirty(); }

    private BudgetFormSnapshot formSnapshot()
    {
        CategoryBudgetRow selected = table.getSelectionModel().getSelectedItem();
        return new BudgetFormSnapshot(
                currentPlan == null ? null : currentPlan.id(),
                selected == null ? null : selected.categoryId(),
                amountField.getText());
    }

    private void reloadWithDiscardProtection()
    {
        if (!dirtyState.isDirty() || confirmDiscard())
        {
            table.getSelectionModel().clearSelection();
            amountField.clear();
            dirtyState.markClean();
            reloadPreferred(currentPlan == null ? null : currentPlan.id());
        }
    }

    private boolean confirmDiscard()
    {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Discard budget edit");
        confirmation.setHeaderText("Discard the unsaved budget amount?");
        confirmation.setContentText("Choose Cancel to remain on the selected budget category and budget version.");
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private record BudgetFormSnapshot(Long planId, Long categoryId, String amount)
    {
    }

    void setAmountForTests(String value)
    {
        amountField.setText(value);
    }
}
