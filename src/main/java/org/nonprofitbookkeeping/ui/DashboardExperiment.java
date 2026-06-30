package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.dashboard.DashboardQueryService;
import org.nonprofitbookkeeping.service.dashboard.DashboardSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Production dashboard promoted from the standalone dashboard experiment.
 *
 * <p>The visual hierarchy, responsive card grid, tables, and quick links come
 * from the experiment. All displayed values are supplied by production
 * services; no fictional values remain in this class.</p>
 */
public final class DashboardExperiment implements AppPanel
{
    private static final int RECENT_TRANSACTION_LIMIT = 12;

    private final BorderPane root = new BorderPane();
    private final GridPane dashboardGrid = new GridPane();
    private final DashboardQueryService dashboardQueryService;
    private final Supplier<LocalDate> asOfDateSupplier;
    private final Supplier<String> groupCodeSupplier;

    private final Label status = new Label();
    private final Label bookCash = amountLabel();
    private final Label yearToDateSurplus = amountLabel();
    private final Label outstandingBankItems = new Label("0");
    private final Label receivables = new Label("0");
    private final Label payables = new Label("0");
    private final Label timingItems = new Label("0");
    private final Label totalOpenItems = new Label("0");
    private final PieChart fundChart = new PieChart();
    private final Label emptyFundChart = new Label("No posted fund activity through the selected date.");
    private final TableView<DashboardSnapshot.RecentTransaction> recentTransactions = new TableView<>();
    private final TableView<DashboardSnapshot.ReconciliationStatus> reconciliations = new TableView<>();
    private final TableView<DashboardSnapshot.BudgetActual> budgetActuals = new TableView<>();
    private final Button refreshButton = new Button("Refresh");

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
        refreshButton.setDisable(true);
        status.setText("Loading dashboard data...");

        LocalDate asOfDate = asOfDateSupplier.get();
        String groupCode = groupCodeSupplier.get();
        UiAsync.run(
                "dashboard-experiment-load",
                () -> dashboardQueryService.load(groupCode, asOfDate, RECENT_TRANSACTION_LIMIT),
                snapshot ->
                {
                    applySnapshot(snapshot);
                    status.setText("Dashboard updated through " + snapshot.asOfDate() + ".");
                    refreshButton.setDisable(false);
                },
                ex ->
                {
                    status.setText("Failed to load dashboard: " + UiErrors.safeMessage(ex));
                    refreshButton.setDisable(false);
                });
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

        fundChart.setData(FXCollections.observableArrayList(
                snapshot.fundClassTotals().entrySet().stream()
                        .filter(entry -> entry.getValue() != null
                                && entry.getValue().compareTo(BigDecimal.ZERO) != 0)
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new PieChart.Data(
                                entry.getKey(),
                                entry.getValue().abs().doubleValue()))
                        .toList()));
        boolean chartEmpty = fundChart.getData().isEmpty();
        emptyFundChart.setVisible(chartEmpty);
        emptyFundChart.setManaged(chartEmpty);
        fundChart.setVisible(!chartEmpty);
        fundChart.setManaged(!chartEmpty);

        recentTransactions.getItems().setAll(snapshot.recentTransactions());
        reconciliations.getItems().setAll(snapshot.reconciliations());
        budgetActuals.getItems().setAll(snapshot.budgetActuals());
    }

    private void buildLayout()
    {
        root.getStyleClass().add("dashboard-experiment-root");
        root.setCenter(buildDashboard());
        root.setBottom(buildLocalStatusBar());
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
        dashboardGrid.add(card("Fund Classification Mix", fundChartContent()), 2, 0);
        dashboardGrid.add(card("Open Items", openItems()), 3, 0);
        dashboardGrid.add(card("Recent Transactions", configureRecentTransactions()), 0, 1, 4, 1);
        dashboardGrid.add(card("Bank Reconciliation Status", configureReconciliations()), 0, 2, 2, 1);
        dashboardGrid.add(card("Budget vs Actual (YTD)", configureBudgetActuals()), 2, 2);
        dashboardGrid.add(card("Quick Links", quickLinks()), 3, 2);

        ScrollPane scrollPane = new ScrollPane(dashboardGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("dashboard-scroll");
        scrollPane.viewportBoundsProperty().addListener(
                (observable, oldBounds, bounds) ->
                        DashboardLayoutPolicy.apply(dashboardGrid, bounds.getWidth()));
        return scrollPane;
    }

    private Node buildLocalStatusBar()
    {
        status.getStyleClass().add("muted");
        refreshButton.setOnAction(event -> reload());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(10, status, spacer, refreshButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(7, 12, 7, 12));
        bar.getStyleClass().add("dashboard-local-status");
        return bar;
    }

    private Node fundChartContent()
    {
        fundChart.setLabelsVisible(false);
        fundChart.setLegendVisible(true);
        fundChart.setPrefHeight(155);
        emptyFundChart.setWrapText(true);
        emptyFundChart.getStyleClass().add("muted");
        return new StackPane(fundChart, emptyFundChart);
    }

    private Node openItems()
    {
        return keyValues(
                "Outstanding bank items", outstandingBankItems,
                "Receivables", receivables,
                "Payables", payables,
                "Prepaids / deferred", timingItems,
                "Total open items", totalOpenItems);
    }

    private Node configureRecentTransactions()
    {
        recentTransactions.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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

    private Node configureReconciliations()
    {
        reconciliations.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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

    private Node configureBudgetActuals()
    {
        budgetActuals.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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
                        "Open import preview and validation",
                        () -> DrillThroughCoordinator.openPanelWithContext(
                                AppPanelId.IMPORT_PREVIEW,
                                "Dashboard quick link: import")),
                quickLink(
                        "Reconcile Bank Account",
                        "Open reconciliation workspace",
                        () -> DrillThroughCoordinator.openPanelWithContext(
                                AppPanelId.RECONCILIATION_RUNS,
                                "Dashboard quick link: reconciliation")));
    }

    private static VBox kpi(
            String heading,
            String scope,
            Label amount,
            String detail)
    {
        Label scopeLabel = muted(scope);
        Label detailLabel = muted(detail);
        VBox content = new VBox(7, scopeLabel, amount, detailLabel);
        return card(heading, content);
    }

    private static VBox card(String heading, Node content)
    {
        Label title = new Label(heading);
        title.getStyleClass().add("card-title");
        VBox card = new VBox(10, title, content);
        card.getStyleClass().add("card");
        card.setMinWidth(0);
        VBox.setVgrow(content, Priority.ALWAYS);
        return card;
    }

    private static GridPane keyValues(Object... values)
    {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(7);
        for (int index = 0; index < values.length; index += 2)
        {
            Label key = new Label(values[index].toString());
            Node value = values[index + 1] instanceof Node node
                    ? node
                    : new Label(values[index + 1].toString());
            grid.add(key, 0, index / 2);
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

    private static <T> TableColumn<T, String> column(
            String heading,
            Function<T, String> getter)
    {
        TableColumn<T, String> column = new TableColumn<>(heading);
        column.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(getter.apply(cell.getValue())));
        return column;
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
