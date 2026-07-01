package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.dashboard.DashboardQueryService;
import org.nonprofitbookkeeping.service.dashboard.DashboardSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Greenfield production dashboard built from the approved visual reference.
 * The panel owns no SQL and displays only database-backed service projections.
 */
public final class DashboardWorkspacePanel implements AppPanel
{
    private static final int RECENT_TRANSACTION_LIMIT = 20;
    private static final BigDecimal ON_TRACK_TOLERANCE = new BigDecimal("0.05");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final DashboardQueryService dashboardQueryService;
    private final Supplier<LocalDate> asOfDateSupplier;
    private final Supplier<String> groupCodeSupplier;

    private final BorderPane root = new BorderPane();
    private final GridPane grid = new GridPane();
    private final Label loadMessage = new Label();

    private final Label cashAmount = amountLabel();
    private final VBox bankRows = new VBox(5);
    private final Label surplusAmount = amountLabel();
    private final Label surplusStatus = new Label();
    private final Label budgetValue = new Label();
    private final Label varianceValue = new Label();
    private final DashboardSparkline sparkline = new DashboardSparkline();
    private final DashboardDonutChart donut = new DashboardDonutChart();
    private final Label onTrackCount = new Label("0");
    private final Label underCount = new Label("0");
    private final Label overCount = new Label("0");

    private final Label receivableCount = new Label("0");
    private final Label receivableAmount = new Label();
    private final Label payableCount = new Label("0");
    private final Label payableAmount = new Label();
    private final Label timingCount = new Label("0");
    private final Label timingAmount = new Label();
    private final Label bankItemCount = new Label("0");
    private final Label bankItemAmount = new Label();

    private final TableView<DashboardSnapshot.RecentTransaction> transactions = new TableView<>();
    private final VBox reconciliationRows = new VBox(7);
    private final TableView<DashboardSnapshot.BudgetActual> budgetActuals = new TableView<>();

    public DashboardWorkspacePanel()
    {
        this(
                UiServiceRegistry.dashboardQuery(),
                ActivePeriodContext::get,
                () -> MainWindow.sharedSessionState().multiCompany().activeCompanyCode());
        ActivePeriodContext.activeDateProperty().addListener(
                (observable, oldDate, newDate) -> reload());
    }

    DashboardWorkspacePanel(
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
        showMessage("Loading dashboard data...", false);
        UiAsync.run(
                "dashboard-reference-load",
                () -> dashboardQueryService.load(
                        groupCodeSupplier.get(),
                        asOfDateSupplier.get(),
                        RECENT_TRANSACTION_LIMIT),
                snapshot ->
                {
                    applySnapshot(snapshot);
                    showMessage("", false);
                },
                ex -> showMessage(
                        "Dashboard data could not be loaded: " + UiErrors.safeMessage(ex),
                        true));
    }

    void applySnapshot(DashboardSnapshot snapshot)
    {
        cashAmount.setText(DashboardValueFormatter.money(snapshot.bookCash()));
        applyBankRows(snapshot.bankAccounts());
        applySurplus(snapshot);
        applyBudgetPerformance(snapshot.budgetActuals());
        applyOpenItems(snapshot.openItems());
        transactions.getItems().setAll(snapshot.recentTransactions());
        applyReconciliations(snapshot.reconciliations());
        budgetActuals.getItems().setAll(snapshot.budgetActuals());
    }

    private void buildLayout()
    {
        root.getStyleClass().add("dashboard-workspace");
        loadMessage.getStyleClass().add("dashboard-load-message");
        loadMessage.setManaged(false);
        loadMessage.setVisible(false);
        loadMessage.setWrapText(true);
        root.setTop(loadMessage);
        root.setCenter(buildDashboard());
    }

    private Node buildDashboard()
    {
        grid.getStyleClass().add("dashboard-grid");
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(14));
        for (int index = 0; index < 4; index++)
        {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25);
            column.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(column);
        }

        grid.add(cashCard(), 0, 0);
        grid.add(surplusCard(), 1, 0);
        grid.add(performanceCard(), 2, 0);
        grid.add(openItemsCard(), 3, 0);
        grid.add(transactionCard(), 0, 1, 4, 1);
        grid.add(reconciliationCard(), 0, 2, 2, 1);
        grid.add(budgetCard(), 2, 2);
        grid.add(quickLinksCard(), 3, 2);

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.getStyleClass().add("dashboard-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.viewportBoundsProperty().addListener(
                (observable, oldBounds, bounds) ->
                        DashboardWorkspaceLayoutPolicy.apply(grid, bounds.getWidth()));
        return scrollPane;
    }

    private Node cashCard()
    {
        cashAmount.getStyleClass().add("dashboard-positive-amount");
        VBox body = new VBox(7, muted("All bank accounts"), cashAmount, bankRows);
        return card("Cash Balances", UiIcons.Glyph.BANK, "accent-blue", body, null);
    }

    private Node surplusCard()
    {
        surplusStatus.getStyleClass().add("status-pill");
        HBox amountRow = new HBox(8, surplusAmount, surplusStatus);
        amountRow.setAlignment(Pos.CENTER_LEFT);

        GridPane values = new GridPane();
        values.setHgap(16);
        values.setVgap(3);
        values.add(muted("Budget"), 0, 0);
        values.add(budgetValue, 1, 0);
        values.add(muted("Variance"), 0, 1);
        values.add(varianceValue, 1, 1);

        VBox body = new VBox(4, muted("All funds"), amountRow, values, sparkline);
        return card("YTD Surplus (Deficit)", UiIcons.Glyph.TREND_UP, "accent-green", body, null);
    }

    private Node performanceCard()
    {
        VBox legend = new VBox(
                5,
                legend("On track", "legend-green", onTrackCount),
                legend("Under", "legend-amber", underCount),
                legend("Over", "legend-red", overCount));
        HBox body = new HBox(10, donut, legend);
        body.setAlignment(Pos.CENTER_LEFT);
        return card("Budget Performance", UiIcons.Glyph.CHART, "accent-purple", body, null);
    }

    private Node openItemsCard()
    {
        VBox body = new VBox(
                3,
                openItem(UiIcons.Glyph.REPORT, "accent-blue", "Receivables", receivableCount, receivableAmount),
                openItem(UiIcons.Glyph.CREDIT_CARD, "accent-red", "Payables", payableCount, payableAmount),
                openItem(UiIcons.Glyph.CALENDAR, "accent-purple", "Prepaids & deferred", timingCount, timingAmount),
                openItem(UiIcons.Glyph.BANK, "accent-amber", "Outstanding bank items", bankItemCount, bankItemAmount));
        return card("Open Items", UiIcons.Glyph.CLOCK, "accent-amber", body, null);
    }

    private Node transactionCard()
    {
        configureTransactions();
        Button viewAll = textButton("View All", () ->
                DrillThroughCoordinator.openLedgerWithContext("Dashboard recent transactions"));
        return card("Recent Transactions", UiIcons.Glyph.LEDGER, "accent-blue", transactions, viewAll);
    }

    private Node reconciliationCard()
    {
        Button action = primaryButton(
                "Reconcile Account",
                UiIcons.Glyph.BANK,
                () -> DrillThroughCoordinator.openPanelWithContext(
                        AppPanelId.RECONCILIATION_RUNS,
                        "Dashboard reconciliation action"));
        VBox body = new VBox(9, reconciliationRows, action);
        return card("Bank Reconciliation Status", UiIcons.Glyph.BANK, "accent-green", body, null);
    }

    private Node budgetCard()
    {
        configureBudgetTable();
        Button view = textButton("View Budget", () ->
                DrillThroughCoordinator.openPanelWithContext(
                        AppPanelId.BUDGET_VS_ACTUAL,
                        "Dashboard budget action"));
        return card("Budget vs Actual (YTD)", UiIcons.Glyph.BUDGET, "accent-purple", budgetActuals, view);
    }

    private Node quickLinksCard()
    {
        VBox links = new VBox(
                3,
                quickLink("New Transaction", "Record a transaction", UiIcons.Glyph.ADD, "accent-blue", this::onNew),
                quickLink("Enter Journal Entry", "Post a manual journal", UiIcons.Glyph.LEDGER, "accent-purple", () ->
                        DrillThroughCoordinator.openPanelWithContext(AppPanelId.TXN_EDITOR, "Dashboard journal action")),
                quickLink("Import SCLX Workbook", "Preview and validate import", UiIcons.Glyph.IMPORT, "accent-amber", () ->
                        DrillThroughCoordinator.openPanelWithContext(AppPanelId.IMPORT_PREVIEW, "Dashboard import action")),
                quickLink("Reconcile Bank Account", "Open reconciliation", UiIcons.Glyph.BANK, "accent-green", () ->
                        DrillThroughCoordinator.openPanelWithContext(AppPanelId.RECONCILIATION_RUNS, "Dashboard reconcile action")));
        return card("Quick Links", UiIcons.Glyph.DASHBOARD, "accent-blue", links, null);
    }

    private void configureTransactions()
    {
        transactions.getStyleClass().add("dashboard-table");
        transactions.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        transactions.setPrefHeight(224);
        transactions.setFixedCellSize(30);
        transactions.getColumns().setAll(
                textColumn("Date", row -> row.transactionDate().toString(), 92),
                textColumn("Txn #", row -> Long.toString(row.transactionId()), 72),
                textColumn("Description", DashboardSnapshot.RecentTransaction::description, 180),
                textColumn("Account", DashboardSnapshot.RecentTransaction::accountSummary, 150),
                textColumn("Fund", DashboardSnapshot.RecentTransaction::fundSummary, 130),
                moneyColumn("Debit", DashboardSnapshot.RecentTransaction::debitTotal, 94),
                moneyColumn("Credit", DashboardSnapshot.RecentTransaction::creditTotal, 94),
                optionalMoneyColumn("Balance", DashboardSnapshot.RecentTransaction::runningBankBalance, 100),
                checkColumn("Affects Bank", DashboardSnapshot.RecentTransaction::affectsBank, 94),
                checkColumn("Affects Budget", DashboardSnapshot.RecentTransaction::affectsBudget, 104));
        transactions.setPlaceholder(new Label("No transactions through the selected date."));
        transactions.setRowFactory(table ->
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

    private void configureBudgetTable()
    {
        budgetActuals.getStyleClass().add("dashboard-table");
        budgetActuals.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        budgetActuals.setPrefHeight(205);
        budgetActuals.setFixedCellSize(30);
        budgetActuals.getColumns().setAll(
                textColumn("Category", DashboardWorkspacePanel::categoryLabel, 145),
                optionalMoneyColumn("Budget", DashboardSnapshot.BudgetActual::budget, 88),
                moneyColumn("Actual", DashboardSnapshot.BudgetActual::actual, 88),
                optionalMoneyColumn("Variance", DashboardSnapshot.BudgetActual::variance, 90),
                budgetStatusColumn());
        budgetActuals.setPlaceholder(new Label("No posted budget-category activity."));
    }

    private void applyBankRows(List<DashboardSnapshot.BankAccountBalance> rows)
    {
        bankRows.getChildren().clear();
        if (rows.isEmpty())
        {
            bankRows.getChildren().add(muted("No bank-account activity"));
            return;
        }
        rows.stream().limit(3).forEach(account -> bankRows.getChildren().add(
                valueRow(account.name(), DashboardValueFormatter.money(account.balance()))));
        if (rows.size() > 3)
        {
            bankRows.getChildren().add(muted("+ " + (rows.size() - 3) + " more bank accounts"));
        }
    }

    private void applySurplus(DashboardSnapshot snapshot)
    {
        BigDecimal surplus = snapshot.yearToDateSurplus();
        surplusAmount.setText(DashboardValueFormatter.money(surplus));
        surplusAmount.getStyleClass().removeAll(
                "dashboard-positive-amount",
                "dashboard-negative-amount",
                "dashboard-neutral-amount");
        surplusStatus.getStyleClass().removeAll(
                "status-success",
                "status-danger",
                "status-neutral");
        if (surplus.signum() > 0)
        {
            surplusAmount.getStyleClass().add("dashboard-positive-amount");
            surplusStatus.getStyleClass().add("status-success");
            surplusStatus.setText("Surplus");
        }
        else if (surplus.signum() < 0)
        {
            surplusAmount.getStyleClass().add("dashboard-negative-amount");
            surplusStatus.getStyleClass().add("status-danger");
            surplusStatus.setText("Deficit");
        }
        else
        {
            surplusAmount.getStyleClass().add("dashboard-neutral-amount");
            surplusStatus.getStyleClass().add("status-neutral");
            surplusStatus.setText("Break even");
        }

        Optional<BigDecimal> budget = snapshot.budgetActuals().stream()
                .map(DashboardSnapshot.BudgetActual::budget)
                .flatMap(Optional::stream)
                .reduce(BigDecimal::add);
        budgetValue.setText(budget.map(DashboardValueFormatter::money).orElse(""));
        varianceValue.setText(budget.map(surplus::subtract)
                .map(DashboardValueFormatter::money)
                .orElse(""));
        sparkline.setValues(snapshot.monthlyResults().stream()
                .map(DashboardSnapshot.MonthlyResult::surplus)
                .toList());
    }

    private void applyBudgetPerformance(List<DashboardSnapshot.BudgetActual> rows)
    {
        long onTrack = rows.stream().filter(row -> budgetStatus(row) == BudgetStatus.ON_TRACK).count();
        long under = rows.stream().filter(row -> budgetStatus(row) == BudgetStatus.UNDER).count();
        long over = rows.stream().filter(row -> budgetStatus(row) == BudgetStatus.OVER).count();
        donut.setValues(onTrack, under, over);
        onTrackCount.setText(Long.toString(onTrack));
        underCount.setText(Long.toString(under));
        overCount.setText(Long.toString(over));
    }

    private void applyOpenItems(DashboardSnapshot.OpenItemSummary items)
    {
        setOpenItem(receivableCount, receivableAmount, items, "RECEIVABLE");
        setOpenItem(payableCount, payableAmount, items, "PAYABLE");
        timingCount.setText(Long.toString(
                items.countFor("PREPAID_EXPENSE") + items.countFor("DEFERRED_REVENUE")));
        timingAmount.setText(DashboardValueFormatter.money(
                items.amountFor("PREPAID_EXPENSE").add(items.amountFor("DEFERRED_REVENUE"))));
        setOpenItem(bankItemCount, bankItemAmount, items, "OUTSTANDING_BANK_ITEM");
    }

    private void applyReconciliations(List<DashboardSnapshot.ReconciliationStatus> rows)
    {
        reconciliationRows.getChildren().clear();
        if (rows.isEmpty())
        {
            reconciliationRows.getChildren().add(muted("No reconciliation runs for the active organization."));
            return;
        }
        rows.forEach(row -> reconciliationRows.getChildren().add(reconciliationRow(row)));
    }

    private static Node reconciliationRow(DashboardSnapshot.ReconciliationStatus row)
    {
        Label date = new Label(DATE_FORMAT.format(row.statementEndingOn()));
        date.getStyleClass().add("dashboard-list-primary");
        VBox text = new VBox(1, date, muted(row.bankFormat() + " · "
                + row.importedTransactionCount() + " imported transactions"));
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox line = new HBox(8, iconBadge(UiIcons.Glyph.BANK, "accent-green"), text, statusPill(row.status()));
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("dashboard-list-row");
        return line;
    }

    private static void setOpenItem(
            Label count,
            Label amount,
            DashboardSnapshot.OpenItemSummary items,
            String kind)
    {
        count.setText(Long.toString(items.countFor(kind)));
        amount.setText(DashboardValueFormatter.money(items.amountFor(kind)));
    }

    private static VBox card(
            String title,
            UiIcons.Glyph glyph,
            String accent,
            Node body,
            Node action)
    {
        Label heading = new Label(title);
        heading.getStyleClass().add("dashboard-card-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(7, iconBadge(glyph, accent), heading, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        if (action != null)
        {
            header.getChildren().add(action);
        }
        VBox card = new VBox(9, header, body);
        card.getStyleClass().add("dashboard-card");
        card.setMinWidth(0);
        VBox.setVgrow(body, Priority.ALWAYS);
        return card;
    }

    private static Node iconBadge(UiIcons.Glyph glyph, String accent)
    {
        HBox badge = new HBox(UiIcons.icon(glyph, 15, accent));
        badge.setAlignment(Pos.CENTER);
        badge.getStyleClass().addAll("dashboard-icon-badge", accent);
        return badge;
    }

    private static Node legend(String labelText, String cueClass, Label count)
    {
        Region cue = new Region();
        cue.getStyleClass().addAll("dashboard-legend-dot", cueClass);
        Label label = new Label(labelText);
        HBox.setHgrow(label, Priority.ALWAYS);
        HBox row = new HBox(7, cue, label, count);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Node openItem(
            UiIcons.Glyph glyph,
            String accent,
            String title,
            Label count,
            Label amount)
    {
        Label name = new Label(title);
        name.getStyleClass().add("dashboard-list-primary");
        HBox.setHgrow(name, Priority.ALWAYS);
        count.getStyleClass().add("dashboard-count-pill");
        amount.getStyleClass().add("dashboard-open-item-amount");
        HBox row = new HBox(7, iconBadge(glyph, accent), name, count, amount);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("dashboard-open-item-row");
        return row;
    }

    private static Node quickLink(
            String title,
            String detail,
            UiIcons.Glyph glyph,
            String accent,
            Runnable action)
    {
        Label heading = new Label(title);
        heading.getStyleClass().add("quick-link-title");
        VBox text = new VBox(1, heading, muted(detail));
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(
                8,
                iconBadge(glyph, accent),
                text,
                UiIcons.icon(UiIcons.Glyph.CHEVRON_RIGHT, 12, "icon-muted"));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("quick-link");
        row.setOnMouseClicked(event -> action.run());
        return row;
    }

    private static Node valueRow(String title, String value)
    {
        Label name = new Label(title);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label amount = new Label(value);
        amount.getStyleClass().add("dashboard-key-value");
        HBox row = new HBox(7, name, spacer, amount);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Button primaryButton(String text, UiIcons.Glyph glyph, Runnable action)
    {
        Button button = new Button(text, UiIcons.icon(glyph, 14, "icon-white"));
        button.getStyleClass().add("dashboard-primary-button");
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Button textButton(String text, Runnable action)
    {
        Button button = new Button(text, UiIcons.icon(UiIcons.Glyph.CHEVRON_RIGHT, 12, "icon-blue"));
        button.getStyleClass().add("dashboard-text-button");
        button.setContentDisplay(ContentDisplay.RIGHT);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static <T> TableColumn<T, String> textColumn(
            String title,
            Function<T, String> value,
            double width)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setMinWidth(Math.min(width, 64));
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private static <T> TableColumn<T, String> moneyColumn(
            String title,
            Function<T, BigDecimal> value,
            double width)
    {
        TableColumn<T, String> column = textColumn(
                title,
                row -> DashboardValueFormatter.money(value.apply(row)),
                width);
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
        return column;
    }

    private static <T> TableColumn<T, String> optionalMoneyColumn(
            String title,
            Function<T, Optional<BigDecimal>> value,
            double width)
    {
        TableColumn<T, String> column = textColumn(
                title,
                row -> value.apply(row).map(DashboardValueFormatter::money).orElse(""),
                width);
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
        return column;
    }

    private static <T> TableColumn<T, String> checkColumn(
            String title,
            Function<T, Boolean> value,
            double width)
    {
        TableColumn<T, String> column = textColumn(
                title,
                row -> Boolean.TRUE.equals(value.apply(row)) ? "check" : "",
                width);
        column.setCellFactory(ignored -> new TableCell<>()
        {
            private final Label check = checkLabel();

            @Override
            protected void updateItem(String item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(null);
                setAlignment(Pos.CENTER);
                setGraphic(!empty && "check".equals(item) ? check : null);
            }
        });
        return column;
    }

    private static TableColumn<DashboardSnapshot.BudgetActual, String> budgetStatusColumn()
    {
        TableColumn<DashboardSnapshot.BudgetActual, String> column = textColumn(
                "Status",
                row -> budgetStatus(row).label,
                88);
        column.setCellFactory(ignored -> new TableCell<>()
        {
            private final Label pill = new Label();

            @Override
            protected void updateItem(String item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(null);
                setAlignment(Pos.CENTER);
                pill.getStyleClass().setAll("status-pill");
                if (empty || getTableRow() == null || getTableRow().getItem() == null)
                {
                    setGraphic(null);
                    return;
                }
                BudgetStatus status = budgetStatus(getTableRow().getItem());
                if (status == BudgetStatus.UNAVAILABLE)
                {
                    setGraphic(null);
                    return;
                }
                pill.setText(status.label);
                pill.getStyleClass().add(status.styleClass);
                setGraphic(pill);
            }
        });
        return column;
    }

    private static BudgetStatus budgetStatus(DashboardSnapshot.BudgetActual row)
    {
        if (row.budget().isEmpty())
        {
            return BudgetStatus.UNAVAILABLE;
        }
        BigDecimal budget = row.budget().orElseThrow();
        BigDecimal variance = row.actual().subtract(budget);
        BigDecimal denominator = budget.abs().signum() == 0 ? BigDecimal.ONE : budget.abs();
        BigDecimal ratio = variance.abs().divide(denominator, 8, RoundingMode.HALF_UP);
        if (ratio.compareTo(ON_TRACK_TOLERANCE) <= 0)
        {
            return BudgetStatus.ON_TRACK;
        }
        return variance.signum() < 0 ? BudgetStatus.UNDER : BudgetStatus.OVER;
    }

    private static Label checkLabel()
    {
        Label label = new Label("✓");
        label.getStyleClass().add("dashboard-check-badge");
        return label;
    }

    private static Label statusPill(String status)
    {
        String normalized = status == null ? "" : status.toUpperCase();
        Label pill = new Label(titleCase(status));
        pill.getStyleClass().addAll("status-pill", statusClass(normalized));
        return pill;
    }

    private static String statusClass(String normalized)
    {
        if (normalized.contains("COMPLETE") || normalized.contains("OPEN") || normalized.contains("ACTIVE"))
        {
            return "status-success";
        }
        if (normalized.contains("FAIL") || normalized.contains("CLOSED") || normalized.contains("VOID"))
        {
            return "status-danger";
        }
        if (normalized.contains("RUN") || normalized.contains("PEND") || normalized.contains("REVIEW"))
        {
            return "status-warning";
        }
        return "status-neutral";
    }

    private static String titleCase(String value)
    {
        if (value == null || value.isBlank())
        {
            return "Unknown";
        }
        String normalized = value.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String categoryLabel(DashboardSnapshot.BudgetActual row)
    {
        return row.categoryCode() == null || row.categoryCode().isBlank()
                ? row.categoryName()
                : row.categoryCode() + " " + row.categoryName();
    }

    private void showMessage(String message, boolean error)
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

    private static Label amountLabel()
    {
        Label label = new Label();
        label.getStyleClass().add("dashboard-amount");
        return label;
    }

    private static Label muted(String text)
    {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        return label;
    }

    private enum BudgetStatus
    {
        ON_TRACK("On track", "status-success"),
        UNDER("Under", "status-warning"),
        OVER("Over", "status-danger"),
        UNAVAILABLE("", "status-neutral");

        private final String label;
        private final String styleClass;

        BudgetStatus(String label, String styleClass)
        {
            this.label = label;
            this.styleClass = styleClass;
        }
    }
}
