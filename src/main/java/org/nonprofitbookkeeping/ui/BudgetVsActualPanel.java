package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.BudgetVarianceView;
import org.nonprofitbookkeeping.service.FiscalPeriodRange;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.service.ActivityView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Budget-vs-actual panel backed by active normalized budget plans. */
public class BudgetVsActualPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<BudgetVarianceView> table = new TableView<>();
    private final Label status = new Label();
    private final ComboBox<ScopeFilter> fundFilter = new ComboBox<>();
    private final ComboBox<ScopeFilter> eventFilter = new ComboBox<>();
    private final CompanyUiFormat companyFormat = CompanyUiFormat.activeCompany();
    private boolean suppressFilters;

    public BudgetVsActualPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Budget vs Actual");
        title.getStyleClass().add("panel-title");
        status.setId("budgetVsActualStatus");
        table.setId("budgetVsActualTable");
        Button run = new Button("Run");
        run.setId("budgetVsActualRunButton");
        run.setOnAction(e -> reload());
        table.setId("budgetVsActualTable");
        status.setId("budgetVsActualStatusLabel");
        fundFilter.setId("budgetVsActualFundFilter");
        eventFilter.setId("budgetVsActualEventFilter");
        fundFilter.getItems().setAll(ScopeFilter.all("All funds"));
        eventFilter.getItems().setAll(ScopeFilter.all("All events"), ScopeFilter.general("General / no event"));
        fundFilter.setValue(fundFilter.getItems().get(0));
        eventFilter.setValue(eventFilter.getItems().get(0));
        fundFilter.valueProperty().addListener((obs, oldValue, newValue) -> { if (!suppressFilters) reload(); });
        eventFilter.valueProperty().addListener((obs, oldValue, newValue) -> { if (!suppressFilters) reload(); });
        root.setTop(new VBox(6, title,
                new HBox(8, run, new Label("Fund"), fundFilter, new Label("Event"), eventFilter),
                status, new Separator()));

        TableColumn<BudgetVarianceView, String> category = new TableColumn<>("Budget Category");
        category.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().budgetCategoryCode() + " — " + v.getValue().budgetCategoryName()));
        TableColumn<BudgetVarianceView, String> fund = new TableColumn<>("Fund");
        fund.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().fundCode().orElse("All funds")));
        TableColumn<BudgetVarianceView, String> event = new TableColumn<>("Event");
        event.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().activityCode().orElse("General / no event")));
        TableColumn<BudgetVarianceView, String> budget = new TableColumn<>("Budget");
        budget.setCellValueFactory(v -> new SimpleStringProperty(companyFormat.formatMoney(v.getValue().budget())));
        TableColumn<BudgetVarianceView, String> actual = new TableColumn<>("Actual");
        actual.setCellValueFactory(v -> new SimpleStringProperty(companyFormat.formatMoney(v.getValue().actual())));
        TableColumn<BudgetVarianceView, String> variance = new TableColumn<>("Variance (Actual-Budget)");
        variance.setCellValueFactory(v -> new SimpleStringProperty(companyFormat.formatMoney(v.getValue().variance())));
        table.getColumns().addAll(category, fund, event, budget, actual, variance);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No active budget version is selected for comparison."));
        Label explanation = new Label("Compares actual activity through the active period against the active normalized budget version.");
        explanation.setId("budgetVsActualExplanation");
        explanation.setWrapText(true);
        SplitPane splitPane = new SplitPane(table, explanation);
        splitPane.setId("budgetVsActualSplitPane");
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setDividerPositions(0.82);
        CompanySplitPaneStateBinder.bind(splitPane, "budget-vs-actual", 0.82);
        root.setCenter(splitPane);
        ActivePeriodContext.activeDateProperty().addListener((obs, oldDate, newDate) -> reload());
        reload();
    }

    private void reload()
    {
        LocalDate selectedPeriodStart = ActivePeriodContext.get();
        status.setText("Running active budget comparison for the selected accounting period...");
        UiAsync.run("budget-vs-actual", () -> {
            FiscalPeriodRange range = UiServiceRegistry.budgetPlan().fiscalRange(selectedPeriodStart);
            return new VarianceResult(range, UiServiceRegistry.budgetPlan().activeVariance(range),
                    UiServiceRegistry.fundLookup().listAllFunds(),
                    UiServiceRegistry.activityLookup().listAllActivities());
        }, result -> {
            setFilterOptions(result.funds(), result.activities());
            ScopeFilter selectedFund = fundFilter.getValue();
            ScopeFilter selectedEvent = eventFilter.getValue();
            List<BudgetVarianceView> rows = result.rows().stream()
                    .filter(row -> selectedFund == null || selectedFund.matches(row.fundId()))
                    .filter(row -> selectedEvent == null || selectedEvent.matches(row.activityId()))
                    .toList();
            table.getItems().setAll(rows);
            BigDecimal netActual = total(rows, BudgetVarianceView::actual);
            BigDecimal netBudget = total(rows, BudgetVarianceView::budget);
            BigDecimal netVariance = total(rows, BudgetVarianceView::variance);
            status.setText("Loaded " + rows.size() + " active budget row(s) for " + result.range().displayLabel()
                    + " through " + companyFormat.formatDate(result.range().periodEnd()) + ". Net actual = "
                    + companyFormat.formatMoney(netActual) + ", net budget = " + companyFormat.formatMoney(netBudget)
                    + ", net variance = " + companyFormat.formatMoney(netVariance));
        }, ex -> status.setText("Could not compute Budget vs Actual view: " + UiErrors.safeMessage(ex)));
    }

    private void setFilterOptions(List<Fund> funds, List<ActivityView> activities)
    {
        ScopeFilter selectedFund = fundFilter.getValue();
        ScopeFilter selectedEvent = eventFilter.getValue();
        suppressFilters = true;
        fundFilter.getItems().setAll(java.util.stream.Stream.concat(
                java.util.stream.Stream.of(ScopeFilter.all("All funds")),
                funds.stream().map(fund -> ScopeFilter.specific(fund.getId(), fund.getCode() + " — " + fund.getName())))
                .toList());
        eventFilter.getItems().setAll(java.util.stream.Stream.concat(
                java.util.stream.Stream.of(ScopeFilter.all("All events"), ScopeFilter.general("General / no event")),
                activities.stream().map(activity -> ScopeFilter.specific(activity.id(), activity.code() + " — " + activity.name())))
                .toList());
        fundFilter.setValue(findFilter(fundFilter.getItems(), selectedFund));
        eventFilter.setValue(findFilter(eventFilter.getItems(), selectedEvent));
        suppressFilters = false;
    }

    private static ScopeFilter findFilter(List<ScopeFilter> options, ScopeFilter selected)
    {
        return selected == null ? options.get(0) : options.stream()
                .filter(option -> option.mode() == selected.mode() && java.util.Objects.equals(option.id(), selected.id()))
                .findFirst().orElse(options.get(0));
    }

    private record VarianceResult(
            FiscalPeriodRange range,
            List<BudgetVarianceView> rows,
            List<Fund> funds,
            List<ActivityView> activities)
    {
        private VarianceResult
        {
            rows = List.copyOf(rows);
            funds = List.copyOf(funds);
            activities = List.copyOf(activities);
        }
    }

    private record ScopeFilter(FilterMode mode, Long id, String label)
    {
        static ScopeFilter all(String label) { return new ScopeFilter(FilterMode.ALL, null, label); }
        static ScopeFilter general(String label) { return new ScopeFilter(FilterMode.GENERAL, null, label); }
        static ScopeFilter specific(Long id, String label) { return new ScopeFilter(FilterMode.SPECIFIC, id, label); }
        boolean matches(java.util.Optional<Long> value)
        {
            return mode == FilterMode.ALL || (mode == FilterMode.GENERAL && value.isEmpty())
                    || (mode == FilterMode.SPECIFIC && value.filter(id::equals).isPresent());
        }
        @Override public String toString() { return label; }
    }

    private enum FilterMode { ALL, GENERAL, SPECIFIC }

    private static BigDecimal total(List<BudgetVarianceView> rows, java.util.function.Function<BudgetVarianceView, BigDecimal> mapper)
    {
        return rows.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override public String title() { return "Budget vs Actual"; }
    @Override public Node root() { return root; }
}
