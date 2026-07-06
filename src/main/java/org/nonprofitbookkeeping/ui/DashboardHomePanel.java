package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** Database-backed dashboard home rebuilt from the supplied visual reference. */
public final class DashboardHomePanel implements AppPanel
{
    private static final int RECENT_TRANSACTION_LIMIT = 25;
    private static final BigDecimal ON_TRACK_TOLERANCE = new BigDecimal("0.05");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final DashboardQueryService dashboardQueryService;
    private final Supplier<LocalDate> asOfDateSupplier;
    private final Supplier<String> groupCodeSupplier;
    private final BorderPane root = new BorderPane();
    private final GridPane dashboardGrid = new GridPane();
    private final Label loadMessage = new Label();
    private final Label bookCash = moneyLabel();
    private final Label clearedCash = new Label("Cleared balance not available");
    private final Label cashAsOf = new Label();
    private final Label yearToDateSurplus = moneyLabel();
    private final Label surplusBudget = new Label("Budget not configured");
    private final Label surplusComparison = new Label();
    private final Label outstandingChecks = valueLabel();
    private final Label depositsInTransit = valueLabel();
    private final Label receivables = valueLabel();
    private final Label payables = valueLabel();
    private final Label totalOpenItems = linkValueLabel();
    private final LineChart<Number, Number> cashTrend = createCashTrend();
    private final BarChart<String, Number> surplusBars = createSurplusBars();
    private final PieChart budgetPerformance = new PieChart();
    private final Label budgetPerformanceEmpty = new Label("No authoritative budget targets are configured.");
    private final TableView<DashboardSnapshot.RecentTransaction> recentTransactions = new TableView<>();
    private final TableView<DashboardSnapshot.ReconciliationStatus> reconciliations = new TableView<>();
    private final TableView<DashboardSnapshot.BudgetActual> budgetActuals = new TableView<>();

    public DashboardHomePanel()
    {
        this(
                UiServiceRegistry.dashboardQuery(),
                ActivePeriodContext::get,
                () -> MainWindow.sharedSessionState().multiCompany().activeCompanyCode());
        ActivePeriodContext.activeDateProperty().addListener(
                (observable, oldDate, newDate) -> reload());
    }

    DashboardHomePanel(
            DashboardQueryService dashboardQueryService,
            WorkspaceContext workspaceContext)
    {
        this(
                dashboardQueryService,
                workspaceContext::activePeriodDate,
                workspaceContext::activeCompanyCode);
        workspaceContext.activePeriodDateProperty().addListener(
                (observable, oldDate, newDate) -> reload());
        workspaceContext.activeCompanyCodeProperty().addListener(
                (observable, oldCode, newCode) -> reload());
    }

    DashboardHomePanel(
            DashboardQueryService dashboardQueryService,
            Supplier<LocalDate> asOfDateSupplier,
            Supplier<String> groupCodeSupplier)
    {
        this.dashboardQueryService = dashboardQueryService;
        this.asOfDateSupplier = asOfDateSupplier;
        this.groupCodeSupplier = groupCodeSupplier;
        buildView();
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
        UiDebug.log("dashboard", "New action requested; opening Transaction Editor.");
        open(AppPanelId.TXN_EDITOR, "Dashboard: new transaction");
    }

    void reload()
    {
        showLoadMessage("Loading dashboard data…", false);
        LocalDate asOfDate = asOfDateSupplier.get();
        String groupCode = groupCodeSupplier.get();
        UiDebug.log("dashboard", "Reload requested for group '" + groupCode
                + "' as of " + asOfDate + " with limit " + RECENT_TRANSACTION_LIMIT + ".");
        UiAsync.run(
                "dashboard-home-load",
                () -> dashboardQueryService.load(groupCode, asOfDate, RECENT_TRANSACTION_LIMIT),
                snapshot ->
                {
                    UiDebug.log("dashboard", "Reload succeeded for group '"
                            + snapshot.organization().code() + "' as of " + snapshot.asOfDate() + ".");
                    applySnapshot(snapshot);
                    showLoadMessage("", false);
                },
                ex -> {
                    UiDebug.log("dashboard", "Reload failed: " + UiErrors.safeMessage(ex));
                    showLoadMessage(
                            "Dashboard data could not be loaded: " + UiErrors.safeMessage(ex),
                            true);
                });
    }

    void applySnapshot(DashboardSnapshot snapshot)
    {
        UiDebug.log("dashboard", "Applying snapshot with "
                + snapshot.recentTransactions().size() + " recent transaction(s), "
                + snapshot.reconciliations().size() + " reconciliation row(s), "
                + snapshot.budgetActuals().size() + " budget row(s), and "
                + snapshot.openItems().totalOpenItems() + " open item(s).");
        bookCash.setText(DashboardValueFormatter.money(snapshot.bookCash()));
        clearedCash.setText(snapshot.reconciledCash()
                .map(value -> "Cleared " + DashboardValueFormatter.money(value))
                .orElse("Cleared balance not available"));
        cashAsOf.setText("as of " + DISPLAY_DATE.format(snapshot.asOfDate()));
        yearToDateSurplus.setText(DashboardValueFormatter.money(snapshot.yearToDateSurplus()));

        Map<String, Long> counts = snapshot.openItems().countsByKind();
        long genericBankItems = counts.getOrDefault("OUTSTANDING_BANK_ITEM", 0L);
        outstandingChecks.setText(Long.toString(
                counts.getOrDefault("OUTSTANDING_CHECK", genericBankItems)));
        depositsInTransit.setText(Long.toString(
                counts.getOrDefault("DEPOSIT_IN_TRANSIT", 0L)));
        receivables.setText(Long.toString(counts.getOrDefault("RECEIVABLE", 0L)));
        payables.setText(Long.toString(counts.getOrDefault("PAYABLE", 0L)));
        totalOpenItems.setText(Long.toString(snapshot.openItems().totalOpenItems()));

        applyCashTrend(snapshot.recentTransactions());
        applySurplusBars(snapshot.budgetActuals());
        applyBudgetPerformance(snapshot.budgetActuals());
        applyBudgetComparison(snapshot.budgetActuals(), snapshot.yearToDateSurplus());
        recentTransactions.getItems().setAll(snapshot.recentTransactions());
        reconciliations.getItems().setAll(snapshot.reconciliations());
        budgetActuals.getItems().setAll(snapshot.budgetActuals());
        DashboardSnapshotPublisher.publish(snapshot);
    }

    private void buildView()
    {
        root.getStyleClass().add("dashboard-home");
        loadMessage.getStyleClass().add("dashboard-message");
        loadMessage.setWrapText(true);
        loadMessage.setManaged(false);
        loadMessage.setVisible(false);
        root.setTop(loadMessage);
        root.setCenter(buildDashboardScrollPane());
    }

    private ScrollPane buildDashboardScrollPane()
    {
        dashboardGrid.getStyleClass().add("dashboard-grid");
        dashboardGrid.setHgap(10);
        dashboardGrid.setVgap(10);
        dashboardGrid.setPadding(new Insets(12));
        for (int index = 0; index < 4; index++)
        {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25.0);
            column.setHgrow(Priority.ALWAYS);
            column.setMinWidth(0.0);
            dashboardGrid.getColumnConstraints().add(column);
        }

        dashboardGrid.add(cashCard(), 0, 0);
        dashboardGrid.add(surplusCard(), 1, 0);
        dashboardGrid.add(budgetPerformanceCard(), 2, 0);
        dashboardGrid.add(openItemsCard(), 3, 0);
        dashboardGrid.add(recentTransactionsCard(), 0, 1, 4, 1);
        dashboardGrid.add(reconciliationCard(), 0, 2, 2, 1);
        dashboardGrid.add(budgetActualCard(), 2, 2);
        dashboardGrid.add(quickLinksCard(), 3, 2);

        ScrollPane scrollPane = new ScrollPane(dashboardGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("dashboard-scroll");
        scrollPane.viewportBoundsProperty().addListener(
                (observable, oldBounds, bounds) ->
                        DashboardLayoutPolicy.apply(dashboardGrid, bounds.getWidth()));
        return scrollPane;
    }

    private Node cashCard()
    {
        HBox amountRow = new HBox(12, bookCash, cashTrend);
        amountRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cashTrend, Priority.ALWAYS);
        VBox body = new VBox(
                8,
                muted("All Bank Accounts"),
                amountRow,
                clearedCash,
                mutedNode(cashAsOf));
        return card("Cash Balances", body, null);
    }

    private Node surplusCard()
    {
        HBox amountRow = new HBox(12, yearToDateSurplus, surplusBars);
        amountRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(surplusBars, Priority.ALWAYS);
        surplusComparison.getStyleClass().add("dashboard-emphasis");
        VBox body = new VBox(
                8,
                muted("All Funds"),
                amountRow,
                surplusBudget,
                surplusComparison);
        return card("YTD Surplus (Deficit)", body, null);
    }

    private Node budgetPerformanceCard()
    {
        budgetPerformance.setLabelsVisible(false);
        budgetPerformance.setLegendVisible(true);
        budgetPerformance.setPrefHeight(145);
        budgetPerformanceEmpty.setWrapText(true);
        budgetPerformanceEmpty.getStyleClass().add("muted");
        StackPane body = new StackPane(budgetPerformance, budgetPerformanceEmpty);
        return card(
                "Budget Performance",
                new VBox(5, muted("YTD"), body, muted("Based on Budget Categories")),
                null);
    }

    private Node openItemsCard()
    {
        GridPane values = new GridPane();
        values.setHgap(16);
        values.setVgap(8);
        addKeyValue(values, 0, "Outstanding Checks", outstandingChecks);
        addKeyValue(values, 1, "Deposits in Transit", depositsInTransit);
        addKeyValue(values, 2, "Receivables", receivables);
        addKeyValue(values, 3, "Payables", payables);
        Region line = new Region();
        line.getStyleClass().add("dashboard-rule");
        VBox body = new VBox(
                10,
                values,
                line,
                keyValueRow("Total Open Items", totalOpenItems));
        return card("Open Items", body, null);
    }

    private Node recentTransactionsCard()
    {
        configureRecentTransactions();
        Label showing = muted("Showing up to " + RECENT_TRANSACTION_LIMIT + " transactions");
        Hyperlink viewLedger = link("View Ledger Register  →", AppPanelId.LEDGER_REGISTER);
        return card(
                "Recent Transactions",
                recentTransactions,
                footer(showing, viewLedger));
    }

    private Node reconciliationCard()
    {
        configureReconciliations();
        Hyperlink go = link(
                "Go to Banking & Reconciliation  →",
                AppPanelId.RECONCILIATION_RUNS);
        return card(
                "Bank Reconciliation Status",
                reconciliations,
                footer(new Label(), go));
    }

    private Node budgetActualCard()
    {
        configureBudgetActuals();
        Hyperlink go = link(
                "View Budget vs Actual Report  →",
                AppPanelId.BUDGET_VS_ACTUAL);
        return card(
                "Budget vs Actual (YTD)",
                budgetActuals,
                footer(new Label(), go));
    }

    private Node quickLinksCard()
    {
        VBox links = new VBox(
                9,
                quickLink("▣", "New Transaction", "Record a new transaction", this::onNew),
                quickLink(
                        "▤",
                        "Enter Journal Entry",
                        "Create a manual journal entry",
                        () -> open(AppPanelId.TXN_EDITOR, "Dashboard: journal entry")),
                quickLink(
                        "⇩",
                        "Import SCLX Workbook",
                        "Import from the bookkeeping workbook",
                        () -> open(AppPanelId.IMPORT_PREVIEW, "Dashboard: import")),
                quickLink(
                        "✓",
                        "Reconcile Bank Account",
                        "Open reconciliation window",
                        () -> open(AppPanelId.RECONCILIATION_RUNS, "Dashboard: reconciliation")));
        Hyperlink all = link("All Quick Links  →", AppPanelId.DASHBOARD);
        return card("Quick Links", links, footer(new Label(), all));
    }

    private void configureRecentTransactions()
    {
        recentTransactions.getStyleClass().add("dashboard-table");
        recentTransactions.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recentTransactions.setPrefHeight(210);
        recentTransactions.setFixedCellSize(28);
        recentTransactions.getColumns().setAll(
                column("Date", row -> DISPLAY_DATE.format(row.transactionDate()), 85),
                column("Txn #", row -> Long.toString(row.transactionId()), 62),
                column("Description", DashboardSnapshot.RecentTransaction::description, 150),
                column("Account", DashboardSnapshot.RecentTransaction::accountSummary, 150),
                column("Fund", DashboardSnapshot.RecentTransaction::fundSummary, 105),
                column("Debit", row -> amountText(row.debitTotal()), 82),
                column("Credit", row -> amountText(row.creditTotal()), 82),
                column("Balance", row -> optionalAmountText(row.runningBankBalance()), 90),
                column("Affects Bank", row -> mark(row.affectsBank()), 82),
                column("Affects Budget", row -> mark(row.affectsBudget()), 92),
                column("Status", row -> displayStatus(row.status()), 82));
        recentTransactions.setPlaceholder(
                new Label("No transactions through the selected date."));
        recentTransactions.setRowFactory(table ->
        {
            TableRow<DashboardSnapshot.RecentTransaction> row = new TableRow<>();
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
    }

    private void configureReconciliations()
    {
        reconciliations.getStyleClass().add("dashboard-table");
        reconciliations.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        reconciliations.setPrefHeight(155);
        reconciliations.setFixedCellSize(28);
        reconciliations.getColumns().setAll(
                column(
                        "Bank / Format",
                        DashboardSnapshot.ReconciliationStatus::bankFormat,
                        150),
                column(
                        "Statement Date",
                        row -> DISPLAY_DATE.format(row.statementEndingOn()),
                        100),
                column(
                        "Status",
                        row -> displayStatus(row.status()),
                        100),
                column(
                        "Imported",
                        row -> Integer.toString(row.importedTransactionCount()),
                        70));
        reconciliations.setPlaceholder(
                new Label("No reconciliation runs for the active organization."));
    }

    private void configureBudgetActuals()
    {
        budgetActuals.getStyleClass().add("dashboard-table");
        budgetActuals.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        budgetActuals.setPrefHeight(155);
        budgetActuals.setFixedCellSize(28);
        budgetActuals.getColumns().setAll(
                column("Category", DashboardHomePanel::budgetCategoryLabel, 145),
                column("Budget", row -> optionalAmountText(row.budget()), 90),
                column("Actual", row -> DashboardValueFormatter.money(row.actual()), 90),
                column("Variance", row -> optionalAmountText(row.variance()), 90),
                column("%", row -> percentText(row.performancePercent()), 55));
        budgetActuals.setPlaceholder(
                new Label("No posted budget-category activity through the selected date."));
    }

    private void applyCashTrend(List<DashboardSnapshot.RecentTransaction> transactions)
    {
        List<DashboardSnapshot.RecentTransaction> chronological = new ArrayList<>(transactions);
        Collections.reverse(chronological);
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        int index = 0;
        for (DashboardSnapshot.RecentTransaction transaction : chronological)
        {
            if (transaction.runningBankBalance().isPresent())
            {
                series.getData().add(new XYChart.Data<>(
                        index++,
                        transaction.runningBankBalance().orElseThrow()));
            }
        }
        cashTrend.getData().setAll(series);
        cashTrend.setVisible(!series.getData().isEmpty());
        cashTrend.setManaged(!series.getData().isEmpty());
    }

    private void applySurplusBars(List<DashboardSnapshot.BudgetActual> actuals)
    {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        actuals.stream().limit(6).forEach(row -> series.getData().add(
                new XYChart.Data<>(row.categoryCode(), row.actual())));
        surplusBars.getData().setAll(series);
        surplusBars.setVisible(!series.getData().isEmpty());
        surplusBars.setManaged(!series.getData().isEmpty());
    }

    private void applyBudgetPerformance(List<DashboardSnapshot.BudgetActual> actuals)
    {
        long onTrack = 0;
        long under = 0;
        long over = 0;
        for (DashboardSnapshot.BudgetActual row : actuals)
        {
            if (row.budget().isEmpty())
            {
                continue;
            }
            BigDecimal budget = row.budget().orElseThrow();
            BigDecimal denominator = budget.abs().compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ONE
                    : budget.abs();
            BigDecimal variance = row.actual().subtract(budget);
            BigDecimal ratio = variance.abs().divide(
                    denominator,
                    8,
                    RoundingMode.HALF_UP);
            if (ratio.compareTo(ON_TRACK_TOLERANCE) <= 0)
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

        List<PieChart.Data> slices = new ArrayList<>();
        if (onTrack > 0)
        {
            slices.add(new PieChart.Data("On Track", onTrack));
        }
        if (under > 0)
        {
            slices.add(new PieChart.Data("Under", under));
        }
        if (over > 0)
        {
            slices.add(new PieChart.Data("Over", over));
        }
        budgetPerformance.setData(FXCollections.observableArrayList(slices));
        boolean empty = slices.isEmpty();
        budgetPerformance.setManaged(!empty);
        budgetPerformance.setVisible(!empty);
        budgetPerformanceEmpty.setManaged(empty);
        budgetPerformanceEmpty.setVisible(empty);
    }

    private void applyBudgetComparison(
            List<DashboardSnapshot.BudgetActual> actuals,
            BigDecimal surplus)
    {
        BigDecimal totalBudget = actuals.stream()
                .map(DashboardSnapshot.BudgetActual::budget)
                .flatMap(Optional::stream)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalBudget.compareTo(BigDecimal.ZERO) == 0)
        {
            surplusBudget.setText("Budget not configured");
            surplusComparison.setText("");
            return;
        }
        surplusBudget.setText("Budget " + DashboardValueFormatter.money(totalBudget));
        BigDecimal percent = surplus
                .divide(totalBudget.abs(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        surplusComparison.setText(
                percent.setScale(1, RoundingMode.HALF_UP) + "% vs Budget");
    }

    private static LineChart<Number, Number> createCashTrend()
    {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        yAxis.setForceZeroInRange(false);
        xAxis.setOpacity(0.0);
        yAxis.setOpacity(0.0);
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(false);
        chart.setPrefSize(115, 70);
        chart.setMinSize(80, 55);
        chart.getStyleClass().add("dashboard-sparkline");
        return chart;
    }

    private static BarChart<String, Number> createSurplusBars()
    {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setOpacity(0.0);
        yAxis.setOpacity(0.0);
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCategoryGap(2.0);
        chart.setBarGap(1.0);
        chart.setPrefSize(115, 70);
        chart.setMinSize(80, 55);
        chart.getStyleClass().add("dashboard-mini-bars");
        return chart;
    }

    private static VBox card(String titleText, Node content, Node footer)
    {
        Label title = new Label(titleText);
        title.getStyleClass().add("dashboard-card-title");
        VBox box = new VBox(8, title, content);
        box.getStyleClass().add("dashboard-card");
        box.setMinWidth(0);
        VBox.setVgrow(content, Priority.ALWAYS);
        if (footer != null)
        {
            box.getChildren().add(footer);
        }
        return box;
    }

    private static HBox footer(Node left, Node right)
    {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, left, spacer, right);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("dashboard-card-footer");
        return footer;
    }

    private static HBox quickLink(
            String glyph,
            String title,
            String detail,
            Runnable action)
    {
        Label icon = new Label(glyph);
        icon.getStyleClass().add("dashboard-quick-icon");
        Label heading = new Label(title);
        heading.getStyleClass().add("dashboard-link-title");
        VBox text = new VBox(1, heading, muted(detail));
        HBox link = new HBox(10, icon, text);
        link.setAlignment(Pos.CENTER_LEFT);
        link.getStyleClass().add("dashboard-quick-link");
        link.setOnMouseClicked(event -> action.run());
        return link;
    }

    private static void addKeyValue(
            GridPane grid,
            int row,
            String key,
            Label value)
    {
        grid.add(new Label(key), 0, row);
        grid.add(value, 1, row);
        GridPane.setHalignment(value, javafx.geometry.HPos.RIGHT);
    }

    private static HBox keyValueRow(String key, Label value)
    {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("dashboard-link-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new HBox(8, keyLabel, spacer, value);
    }

    private static Hyperlink link(String text, AppPanelId panelId)
    {
        Hyperlink link = new Hyperlink(text);
        link.getStyleClass().add("dashboard-footer-link");
        link.setOnAction(event -> open(panelId, "Dashboard link: " + text));
        return link;
    }

    private static void open(AppPanelId panelId, String context)
    {
        UiDebug.log("dashboard", "Opening " + panelId + " with context '" + context + "'.");
        DrillThroughCoordinator.openPanelWithContext(panelId, context);
    }

    private static <T> TableColumn<T, String> column(
            String title,
            Function<T, String> value,
            double prefWidth)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(prefWidth);
        return column;
    }

    private static Label moneyLabel()
    {
        Label label = new Label("$0.00");
        label.getStyleClass().add("dashboard-money");
        return label;
    }

    private static Label valueLabel()
    {
        Label label = new Label("0");
        label.getStyleClass().add("dashboard-value");
        return label;
    }

    private static Label linkValueLabel()
    {
        Label label = valueLabel();
        label.getStyleClass().add("dashboard-link-value");
        return label;
    }

    private static Label muted(String text)
    {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        return label;
    }

    private static Node mutedNode(Label label)
    {
        label.getStyleClass().add("muted");
        return label;
    }

    private static String budgetCategoryLabel(DashboardSnapshot.BudgetActual row)
    {
        return row.categoryCode() == null || row.categoryCode().isBlank()
                ? row.categoryName()
                : row.categoryCode() + " " + row.categoryName();
    }

    private static String amountText(BigDecimal value)
    {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0
                ? ""
                : DashboardValueFormatter.money(value);
    }

    private static String optionalAmountText(Optional<BigDecimal> value)
    {
        return value == null || value.isEmpty()
                ? "—"
                : DashboardValueFormatter.money(value.orElseThrow());
    }

    private static String percentText(Optional<BigDecimal> value)
    {
        return value == null || value.isEmpty()
                ? "—"
                : value.orElseThrow().setScale(1, RoundingMode.HALF_UP) + "%";
    }

    private static String mark(boolean value)
    {
        return value ? "✓" : "";
    }

    private static String displayStatus(String status)
    {
        if (status == null || status.isBlank())
        {
            return "";
        }
        if (status.equalsIgnoreCase("ENTERED"))
        {
            return "● Posted";
        }
        String lower = status.toLowerCase().replace('_', ' ');
        String display = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        return "● " + display;
    }

    private void showLoadMessage(String message, boolean error)
    {
        loadMessage.setText(message == null ? "" : message);
        loadMessage.getStyleClass().remove("dashboard-message-error");
        if (error)
        {
            loadMessage.getStyleClass().add("dashboard-message-error");
        }
        boolean visible = message != null && !message.isBlank();
        loadMessage.setManaged(visible);
        loadMessage.setVisible(visible);
    }
}
