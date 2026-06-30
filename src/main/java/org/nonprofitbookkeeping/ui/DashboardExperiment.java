package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.dashboard.DashboardQueryService;
import org.nonprofitbookkeeping.service.dashboard.DashboardSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Production dashboard promoted directly from the standalone dashboard
 * experiment. The visual structure and CSS class contract intentionally match
 * the experiment; only its fictional values and placeholder actions have been
 * replaced with production services and drill-through behavior.
 */
public final class DashboardExperiment implements AppPanel
{
    private static final int RECENT_TRANSACTION_LIMIT = 12;
    private static final BigDecimal ON_TRACK_TOLERANCE = new BigDecimal("0.05");

    private final DashboardQueryService dashboardQueryService;
    private final Supplier<LocalDate> asOfDateSupplier;
    private final Supplier<String> groupCodeSupplier;

    private final VBox root = new VBox();
    private final GridPane dashboardGrid = new GridPane();
    private final Label loadMessage = new Label();
    private final Label bookCash = amountLabel();
    private final Label yearToDateSurplus = amountLabel();
    private final Label outstandingBankItems = new Label("0");
    private final Label receivables = new Label("0");
    private final Label payables = new Label("0");
    private final Label timingItems = new Label("0");
    private final Label totalOpenItems = new Label("0");
    private final PieChart budgetPerformanceChart = new PieChart();
    private final Label emptyBudgetChart = new Label(
            "No authoritative budget targets are configured.");
    private final TableView<DashboardSnapshot.RecentTransaction> recentTransactions = new TableView<>();
    private final TableView<DashboardSnapshot.ReconciliationStatus> reconciliations = new TableView<>();
    private final TableView<DashboardSnapshot.BudgetActual> budgetActuals = new TableView<>();

    public DashboardExperiment()
    {
        this(
                UiServiceRegistry.dashboardQuery(),
                ActivePeriodContext::get,
                () -> MainWindow.sharedSessionState().multiCompany().activeCompanyCode());
        ActivePeriodContext.activeDateProperty().addListener(
                (observable, oldDate, newDate) -> reload());
    }

    DashboardExperiment(
            DashboardQueryService dashboardQueryService,
            Supplier<LocalDate> asOfDateSupplier,
            Supplier<String> groupCodeSupplier)
    {
        this.dashboardQueryService = dashboardQueryService;
        this.asOfDateSupplier = asOfDateSupplier;
        this.groupCodeSupplier = groupCodeSupplier;
        buildLayout();
        reload();
    }

    @Override
    public String title()
    {
        return "Dashboard";
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public void onNew()
    {
        DrillThroughCoordinator.openPanelWithContext(
                AppPanelId.TXN_EDITOR,
                "Dashboard quick action: new transaction");
    }

    void reload()
    {
        setLoadMessage("Loading dashboard data...", false);
        LocalDate asOfDate = asOfDateSupplier.get();
        String groupCode = groupCodeSupplier.get();

        UiAsync.run(
                "dashboard-experiment-load",
                () -> dashboardQueryService.load(groupCode, asOfDate, RECENT_TRANSACTION_LIMIT),
                snapshot ->
                {
                    applySnapshot(snapshot);
                    setLoadMessage("", false);
                },
                ex -> setLoadMessage(
                        "Dashboard data could not be loaded: " + UiErrors.safeMessage(ex),
                        true));
    }

    void applySnapshot(DashboardSnapshot snapshot)
    {
        bookCash.setText(DashboardValueFormatter.money(snapshot.bookCash()));
        yearToDateSurplus.setText(DashboardValueFormatter.money(snapshot.yearToDateSurplus()));

        Map<String, Long> openItemCounts = snapshot.openItems().countsByKind();
        outstandingBankItems.setText(Long.toString(
                openItemCounts.getOrDefault("OUTSTANDING_BANK_ITEM", 0L)));
        receivables.setText(Long.toString(
                openItemCounts.getOrDefault("RECEIVABLE", 0L)));
        payables.setText(Long.toString(
                openItemCounts.getOrDefault("PAYABLE", 0L)));
        long timingCount = openItemCounts.getOrDefault("PREPAID_EXPENSE", 0L)
                + openItemCounts.getOrDefault("DEFERRED_REVENUE", 0L);
        timingItems.setText(Long.toString(timingCount));
        totalOpenItems.setText(Long.toString(snapshot.openItems().totalOpenItems()));

        applyBudgetPerformance(snapshot);
        recentTransactions.getItems().setAll(snapshot.recentTransactions());
        reconciliations.getItems().setAll(snapshot.reconciliations());
        budgetActuals.getItems().setAll(snapshot.budgetActuals());
    }

    private void buildLayout()
    {
        root.getStyleClass().add("dashboard-experiment-root");
        loadMessage.getStyleClass().add("dashboard-load-message");
        loadMessage.setWrapText(true);
        loadMessage.setManaged(false);
        loadMessage.setVisible(false);

        ScrollPane dashboard = (ScrollPane) buildDashboard();
        VBox.setVgrow(dashboard, Priority.ALWAYS);
        root.getChildren().addAll(loadMessage, dashboard);
    }

    private Node buildDashboard()
    {
        dashboardGrid.getStyleClass().add("dashboard-grid");
        dashboardGrid.setHgap(12);
        dashboardGrid.setVgap(12);
        dashboardGrid.setPadding(new Insets(14));
        for (int index = 0; index < 4; index++)
        {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25);
            column.setHgrow(Priority.ALWAYS);
            dashboardGrid.getColumnConstraints().add(column);
        }

        dashboardGrid.add(kpi(
                "Cash Balances",
                "All Bank Accounts",
                bookCash,
                "Book cash through the active period"), 0, 0);
        dashboardGrid.add(kpi(
                "YTD Surplus (Deficit)",
                "All Funds",
                yearToDateSurplus,
                "Income less expenses through the active period"), 1, 0);
        dashboardGrid.add(card("Budget Performance", budgetChart()), 2, 0);
        dashboardGrid.add(card("Open Items", keyValues(
                "Outstanding bank items", outstandingBankItems,
                "Receivables", receivables,
                "Payables", payables,
                "Prepaids & deferred", timingItems,
                "Total open items", totalOpenItems)), 3, 0);
        dashboardGrid.add(card("Recent Transactions", transactions()), 0, 1, 4, 1);
        dashboardGrid.add(card("Bank Reconciliation Status", reconciliations()), 0, 2, 2, 1);
        dashboardGrid.add(card("Budget vs Actual (YTD)", budgets()), 2, 2);
        dashboardGrid.add(card("Quick Links", quickLinks()), 3, 2);

        ScrollPane scrollPane = new ScrollPane(dashboardGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.viewportBoundsProperty().addListener(
                (observable, oldBounds, bounds) ->
                        DashboardLayoutPolicy.apply(dashboardGrid, bounds.getWidth()));
        return scrollPane;
    }

    private Node transactions()
    {
        recentTransactions.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recentTransactions.setPrefHeight(220);
        recentTransactions.getColumns().setAll(
                column("Date", row -> row.transactionDate().toString()),
                column("Txn #", row -> Long.toString(row.transactionId())),
                column("Description", DashboardSnapshot.RecentTransaction::description),
                column("Account", DashboardSnapshot.RecentTransaction::accountSummary),
                column("Fund", DashboardSnapshot.RecentTransaction::fundSummary),
                column("Debit", row -> DashboardValueFormatter.money(row.debitTotal())),
                column("Credit", row -> DashboardValueFormatter.money(row.creditTotal())),
                column("Status", DashboardSnapshot.RecentTransaction::status));
        recentTransactions.setPlaceholder(
                new Label("No transactions through the selected date."));
        recentTransactions.setRowFactory(table ->
        {
            javafx.scene.control.TableRow<DashboardSnapshot.RecentTransaction> row =
                    new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event ->
            {
                if (event.getClickCount() == 2 && !row.isEmpty())
                {
                    DrillThroughCoordinator.openLedgerWithContext(
                            "Dashboard transaction id: " + row.getItem().transactionId());
                }
            });
            return row;
        });
        return recentTransactions;
    }

    private Node reconciliations()
    {
        reconciliations.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        reconciliations.setPrefHeight(210);
        reconciliations.getColumns().setAll(
                column("Statement Date", row -> row.statementEndingOn().toString()),
                column("Format", DashboardSnapshot.ReconciliationStatus::bankFormat),
                column("Status", DashboardSnapshot.ReconciliationStatus::status),
                column("Imported Txns", row -> Integer.toString(row.importedTransactionCount())));
        reconciliations.setPlaceholder(
                new Label("No reconciliation runs for the active organization."));
        return reconciliations;
    }

    private Node budgets()
    {
        budgetActuals.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        budgetActuals.setPrefHeight(210);
        budgetActuals.getColumns().setAll(
                column("Category", DashboardExperiment::budgetCategoryLabel),
                column("Budget", row -> optionalBudget(row.budget())),
                column("Actual", row -> DashboardValueFormatter.money(row.actual())),
                column("Variance", row -> optionalBudget(row.variance())));
        budgetActuals.setPlaceholder(
                new Label("No posted budget-category activity through the selected date."));
        return budgetActuals;
    }

    private Node budgetChart()
    {
        budgetPerformanceChart.setLabelsVisible(false);
        budgetPerformanceChart.setPrefHeight(155);
        emptyBudgetChart.setWrapText(true);
        emptyBudgetChart.getStyleClass().add("muted");
        return new StackPane(budgetPerformanceChart, emptyBudgetChart);
    }

    private Node quickLinks()
    {
        return new VBox(
                10,
                quickLink(
                        "New Transaction",
                        "Record a new transaction",
                        this::onNew),
                quickLink(
                        "Enter Journal Entry",
                        "Create a manual journal entry",
                        () -> DrillThroughCoordinator.openPanelWithContext(
                                AppPanelId.TXN_EDITOR,
                                "Dashboard quick link: journal entry")),
                quickLink(
                        "Import SCLX Workbook",
                        "Import from the workbook",
                        () -> DrillThroughCoordinator.openPanelWithContext(
                                AppPanelId.IMPORT_PREVIEW,
                                "Dashboard quick link: import")),
                quickLink(
                        "Reconcile Bank Account",
                        "Open reconciliation window",
                        () -> DrillThroughCoordinator.openPanelWithContext(
                                AppPanelId.RECONCILIATION_RUNS,
                                "Dashboard quick link: reconciliation")));
    }

    private void applyBudgetPerformance(DashboardSnapshot snapshot)
    {
        long onTrack = 0;
        long under = 0;
        long over = 0;

        for (DashboardSnapshot.BudgetActual row : snapshot.budgetActuals())
        {
            if (row.budget().isEmpty())
            {
                continue;
            }

            BigDecimal budget = row.budget().orElseThrow().abs();
            BigDecimal variance = row.actual().subtract(row.budget().orElseThrow());
            BigDecimal denominator = budget.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ONE
                    : budget;
            BigDecimal varianceRatio = variance.abs().divide(
                    denominator,
                    8,
                    RoundingMode.HALF_UP);

            if (varianceRatio.compareTo(ON_TRACK_TOLERANCE) <= 0)
            {
                onTrack++;
            }
            else if (variance.signum() < 0)
            {
                under++;
            }
            else
            {
                over++;
            }
        }

        budgetPerformanceChart.setData(FXCollections.observableArrayList(
                performanceSlice("On track", onTrack),
                performanceSlice("Under", under),
                performanceSlice("Over", over)).filtered(data -> data.getPieValue() > 0));

        boolean empty = budgetPerformanceChart.getData().isEmpty();
        emptyBudgetChart.setManaged(empty);
        emptyBudgetChart.setVisible(empty);
        budgetPerformanceChart.setManaged(!empty);
        budgetPerformanceChart.setVisible(!empty);
    }

    private static PieChart.Data performanceSlice(String label, long value)
    {
        return new PieChart.Data(label, value);
    }

    private void setLoadMessage(String message, boolean error)
    {
        loadMessage.setText(message == null ? "" : message);
        loadMessage.getStyleClass().remove("dashboard-load-error");
        if (error)
        {
            loadMessage.getStyleClass().add("dashboard-load-error");
        }
        boolean visible = message != null && !message.isBlank();
        loadMessage.setManaged(visible);
        loadMessage.setVisible(visible);
    }

    private static VBox kpi(
            String heading,
            String scope,
            Label amount,
            String detail)
    {
        return card(heading, new VBox(7, muted(scope), amount, muted(detail)));
    }

    private static VBox card(String heading, Node content)
    {
        Label title = new Label(heading);
        title.getStyleClass().add("card-title");
        VBox card = new VBox(10, title, content);
        card.getStyleClass().add("card");
        card.setMinWidth(0);
        return card;
    }

    private static GridPane keyValues(Object... values)
    {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(7);
        for (int index = 0; index < values.length; index += 2)
        {
            grid.add(new Label(values[index].toString()), 0, index / 2);
            Node value = values[index + 1] instanceof Node node
                    ? node
                    : new Label(values[index + 1].toString());
            grid.add(value, 1, index / 2);
        }
        return grid;
    }

    private static VBox quickLink(
            String heading,
            String detail,
            Runnable action)
    {
        Label title = new Label(heading);
        title.getStyleClass().add("quick-link-title");
        VBox link = new VBox(2, title, muted(detail));
        link.getStyleClass().add("quick-link");
        link.setOnMouseClicked(event -> action.run());
        return link;
    }

    private static <T> TableColumn<T, String> column(
            String heading,
            Function<T, String> getter)
    {
        TableColumn<T, String> column = new TableColumn<>(heading);
        column.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(getter.apply(cell.getValue())));
        return column;
    }

    private static Label amountLabel()
    {
        Label label = new Label();
        label.getStyleClass().add("amount");
        return label;
    }

    private static Label muted(String text)
    {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        return label;
    }

    private static String budgetCategoryLabel(DashboardSnapshot.BudgetActual row)
    {
        if (row.categoryCode() == null || row.categoryCode().isBlank())
        {
            return row.categoryName();
        }
        return row.categoryCode() + " " + row.categoryName();
    }

    private static String optionalBudget(Optional<BigDecimal> value)
    {
        return value == null || value.isEmpty()
                ? "Not configured"
                : DashboardValueFormatter.money(value.get());
    }
}
