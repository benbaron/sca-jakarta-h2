package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyObjectWrapper;
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
import javafx.scene.control.Tooltip;
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
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Greenfield production dashboard built from the approved visual reference.
 * The panel owns no SQL and displays only values supplied by dashboard services.
 */
public final class DashboardWorkspacePanel implements AppPanel
{
    private static final int RECENT_TRANSACTION_LIMIT = 20;
    private static final BigDecimal ON_TRACK_TOLERANCE = new BigDecimal("0.05");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final DashboardQueryService dashboardQueryService;
    private final Supplier<LocalDate> asOfDateSupplier;
    private final Supplier<String> groupCodeSupplier;
    private final Consumer<DashboardSnapshot> snapshotConsumer;

    private final BorderPane root = new BorderPane();
    private final GridPane dashboardGrid = new GridPane();
    private final Label loadMessage = new Label();

    private final Label cashTotal = amountLabel();
    private final VBox bankAccountRows = new VBox(6);
    private final Label surplusAmount = amountLabel();
    private final Label surplusStatus = new Label();
    private final Label surplusBudget = new Label();
    private final Label surplusVariance = new Label();
    private final DashboardSparkline surplusSparkline = new DashboardSparkline();

    private final DashboardDonutChart budgetDonut = new DashboardDonutChart();
    private final Label budgetOnTrackCount = new Label("0");
    private final Label budgetUnderCount = new Label("0");
    private final Label budgetOverCount = new Label("0");

    private final Label receivableCount = new Label("0");
    private final Label receivableAmount = new Label();
    private final Label payableCount = new Label("0");
    private final Label payableAmount = new Label();
    private final Label timingCount = new Label("0");
    private final Label timingAmount = new Label();
    private final Label outstandingBankCount = new Label("0");
    private final Label outstandingBankAmount = new Label();

    private final TableView<DashboardSnapshot.RecentTransaction> recentTransactions = new TableView<>();
    private final VBox reconciliationRows = new VBox(8);
    private final TableView<DashboardSnapshot.BudgetActual> budgetActuals = new TableView<>();

    public DashboardWorkspacePanel()
    {
        this(
                UiServiceRegistry.dashboardQuery(),
                ActivePeriodContext::get,
                () -> MainWindow.sharedSessionState().multiCompany().activeCompanyCode(),
                snapshot -> { });
        ActivePeriodContext.activeDateProperty().addListener(
                (observable, oldDate, newDate) -> reload());
    }

    DashboardWorkspacePanel(
            DashboardQueryService dashboardQueryService,
            Supplier<LocalDate> asOfDateSupplier,
            Supplier<String> groupCodeSupplier,
            Consumer<DashboardSnapshot> snapshotConsumer)
    {
        this.dashboardQueryService = dashboardQueryService;
        this.asOfDateSupplier = asOfDateSupplier;
        this.groupCodeSupplier = groupCodeSupplier;
        this.snapshotConsumer = snapshotConsumer;
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
                "dashboard-workspace-load",
                () -> dashboardQueryService.load(groupCode, asOfDate, RECENT_TRANSACTION_LIMIT),
                snapshot ->
                {
                    applySnapshot(snapshot);
                    snapshotConsumer.accept(snapshot);
                    setLoadMessage("", false);
                },
                ex -> setLoadMessage(
                        "Dashboard data could not be loaded: " + UiErrors.safeMessage(ex),
                        true));
    }

    void applySnapshot(DashboardSnapshot snapshot)
    {
        cashTotal.setText(DashboardValueFormatter.money(snapshot.bookCash()));
        applyBankAccounts(snapshot.bankAccounts());
        applySurplus(snapshot);
        applyBudgetPerformance(snapshot.budgetActuals());
        applyOpenItems(snapshot.openItems());
        recentTransactions.getItems().setAll(snapshot.recentTransactions());
        applyReconciliations(snapshot.reconciliations());
        budgetActuals.getItems().setAll(snapshot.budgetActuals());
    }

    private void buildLayout()
    {
        root.getStyleClass().add("dashboard-workspace");
        loadMessage.getStyleClass().add("dashboard-load-message");
        loadMessage.setWrapText(true);
        loadMessage.setManaged(false);
        loadMessage.setVisible(false);
        root.setTop(loadMessage);
        root.setCenter(buildDashboard());
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

        dashboardGrid.add(buildCashCard(), 0, 0);
        dashboardGrid.add(buildSurplusCard(), 1, 0);
        dashboardGrid.add(buildBudgetPerformanceCard(), 2, 0);
        dashboardGrid.add(buildOpenItemsCard(), 3, 0);
        dashboardGrid.add(buildRecentTransactionsCard(), 0, 1, 4, 1);
        dashboardGrid.add(buildReconciliationCard(), 0, 2, 2, 1);
        dashboardGrid.add(buildBudgetActualCard(), 2, 2);
        dashboardGrid.add(buildQuickLinksCard(), 3, 2);

        ScrollPane scrollPane = new ScrollPane(dashboardGrid);
        scrollPane.getStyleClass().add("dashboard-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.viewportBoundsProperty().addListener(
                (observable, oldBounds, bounds) ->
                        DashboardWorkspaceLayoutPolicy.apply(dashboardGrid, bounds.getWidth()));
        return scrollPane;
    }

    private Node buildCashCard()
    {
        cashTotal.getStyleClass().add("dashboard-positive-amount");
        bankAccountRows.getStyleClass().add("dashboard-key-values");
        VBox content = new VBox(8, muted("All bank accounts"), cashTotal, bankAccountRows);
        return card("Cash Balances", UiIcons.Glyph.BANK, "accent-blue", content);
    }

    private Node buildSurplusCard()
    {
        surplusStatus.getStyleClass().add("status-pill");
        HBox amountRow = new HBox(8, surplusAmount, surplusStatus);
        amountRow.setAlignment(Pos.CENTER_LEFT);

        GridPane values = new GridPane();
        values.setHgap(18);
        values.setVgap(4);
        values.add(muted("Budget"), 0, 0);
        values.add(surplusBudget, 1, 0);
        values.add(muted("Variance"), 0, 1);
        values.add(surplusVariance, 1, 1);

        VBox content = new VBox(5, muted("All funds"), amountRow, values, surplusSparkline);
        VBox.setVgrow(surplusSparkline, Priority.ALWAYS);
        return card("YTD Surplus (Deficit)", UiIcons.Glyph.TREND_UP, "accent-green", content);
    }

    private Node buildBudgetPerformanceCard()
    {
        VBox legend = new VBox(
                5,
                legendRow("On track", "legend-green", budgetOnTrackCount),
                legendRow("Under", "legend-amber", budgetUnderCount),
                legendRow("Over", "legend-red", budgetOverCount));
        HBox content = new HBox(12, budgetDonut, legend);
        content.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(legend, Priority.ALWAYS);
        return card("Budget Performance", UiIcons.Glyph.CHART, "accent-purple", content);
    }

    private Node buildOpenItemsCard()
    {
        VBox content = new VBox(
                4,
                openItemRow(
                        UiIcons.Glyph.REPORT,
                        "accent-blue",
                        "Receivables",
                        receivableCount,
                        receivableAmount),
                openItemRow(
                        UiIcons.Glyph.CREDIT_CARD,
                        "accent-red",
                        "Payables",
                        payableCount,
                        payableAmount),
                openItemRow(
                        UiIcons.Glyph.CALENDAR,
                        "accent-purple",
                        "Prepaids & deferred",
                        timingCount,
                        timingAmount),
                openItemRow(
                        UiIcons.Glyph.BANK,
                        "accent-amber",
                        "Outstanding bank items",
                        outstandingBankCount,
                        outstandingBankAmount));
        return card("Open Items", UiIcons.Glyph.CLOCK, "accent-amber", content);
    }

    private Node buildRecentTransactionsCard()
    {
        configureRecentTransactions();
        Button viewAll = textButton("View All", UiIcons.Glyph.CHEVRON_RIGHT, () ->
                DrillThroughCoordinator.openLedgerWithContext("Dashboard recent transactions"));
        return card(
                "Recent Transactions",
                UiIcons.Glyph.LEDGER,
                "accent-blue",
                recentTransactions,
                viewAll);
    }

    private Node buildReconciliationCard()
    {
        reconciliationRows.getStyleClass().add("dashboard-reconciliation-list");
        Button reconcile = actionButton(
                "Reconcile Account",
                UiIcons.Glyph.BANK,
                () -> DrillThroughCoordinator.openPanelWithContext(
                        AppPanelId.RECONCILIATION_RUNS,
                        "Dashboard reconciliation action"));
        VBox content = new VBox(10, reconciliationRows, reconcile);
        VBox.setVgrow(reconciliationRows, Priority.ALWAYS);
        return card(
                "Bank Reconciliation Status",
                UiIcons.Glyph.BANK,
                "accent-green",
                content);
    }

    private Node buildBudgetActualCard()
    {
        configureBudgetActuals();
        Button details = textButton("View Budget", UiIcons.Glyph.CHEVRON_RIGHT, () ->
                DrillThroughCoordinator.openPanelWithContext(
                        AppPanelId.BUDGET_VS_ACTUAL,
                        "Dashboard budget action"));
        return card(
                "Budget vs Actual (YTD)",
                UiIcons.Glyph.BUDGET,
                "accent-purple",
                budgetActuals,
                details);
    }

    private Node buildQuickLinksCard()
    {
        VBox links = new VBox(
                4,
                quickLink(
                        "New Transaction",
                        "Record a transaction",
                        UiIcons.Glyph.ADD,
                        "accent-blue",
                        this::onNew),
                quickLink(
                        "Enter Journal Entry",
                        "Post a manual journal",
                        UiIcons.Glyph.LEDGER,
                        "accent-purple",
                        () -> DrillThroughCoordinator.openPanelWithContext(
                                AppPanelId.TXN_EDITOR,
                                "Dashboard journal action")),
                quickLink(
                        "Import SCLX Workbook",
                        "Preview and validate import",
                        UiIcons.Glyph.IMPORT,
                        "accent-amber",
                        () -> DrillThroughCoordinator.openPanelWithContext(
                                AppPanelId.IMPORT_PREVIEW,
                                "Dashboard import action")),
                quickLink(
                        "Reconcile Bank Account",
                        "Open reconciliation",
                        UiIcons.Glyph.BANK,
                        "accent-green",
                        () -> DrillThroughCoordinator.openPanelWithContext(
                                AppPanelId.RECONCILIATION_RUNS,
                                "Dashboard reconcile action")));
        return card("Quick Links", UiIcons.Glyph.DASHBOARD, "accent-blue", links);
    }

    private void configureRecentTransactions()
    {
        recentTransactions.getStyleClass().add("dashboard-table");
        recentTransactions.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        recentTransactions.setPrefHeight(224);
        recentTransactions.setFixedCellSize(30);
        recentTransactions.getColumns().setAll(
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
        recentTransactions.setPlaceholder(new Label("No transactions through the selected date."));
        recentTransactions.setRowFactory(table ->
        {
            TableRow<DashboardSnapshot.RecentTransaction> row = new TableRow<>()
            {
                @Override
                protected void updateItem(
                        DashboardSnapshot.RecentTransaction item,
                        boolean empty)
                {
                    super.updateItem(item, empty);
                    getStyleClass().remove("dashboard-row-inactive");
                    if (!empty && item != null && !"ENTERED".equals(item.status()))
                    {
                        getStyleClass().add("dashboard-row-inactive");
                    }
                }
            };
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

    private void configureBudgetActuals()
    {
        budgetActuals.getStyleClass().add("dashboard-table");
        budgetActuals.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        budgetActuals.setPrefHeight(205);
        budgetActuals.setFixedCellSize(30);
        budgetActuals.getColumns().setAll(
                textColumn("Category", DashboardWorkspacePanel::budgetCategoryLabel, 145),
                optionalMoneyColumn("Budget", DashboardSnapshot.BudgetActual::budget, 88),
                moneyColumn("Actual", DashboardSnapshot.BudgetActual::actual, 88),
                optionalMoneyColumn("Variance", DashboardSnapshot.BudgetActual::variance, 90),
                budgetStatusColumn());
        budgetActuals.setPlaceholder(
                new Label("No posted budget-category activity through the selected date."));
    }

    private void applyBankAccounts(List<DashboardSnapshot.BankAccountBalance> bankAccounts)
    {
        bankAccountRows.getChildren().clear();
        if (bankAccounts.isEmpty())
        {
            bankAccountRows.getChildren().add(muted("No bank-account activity"));
            return;
        }

        int visibleRows = Math.min(bankAccounts.size(), 3);
        for (int index = 0; index < visibleRows; index++)
        {
            DashboardSnapshot.BankAccountBalance account = bankAccounts.get(index);
            bankAccountRows.getChildren().add(valueRow(
                    account.name(),
                    DashboardValueFormatter.money(account.balance())));
        }
        if (bankAccounts.size() > visibleRows)
        {
            bankAccountRows.getChildren().add(muted(
                    "+ " + (bankAccounts.size() - visibleRows) + " more bank accounts"));
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

        Optional<BigDecimal> totalBudget = snapshot.budgetActuals().stream()
                .map(DashboardSnapshot.BudgetActual::budget)
                .filter(Optional::isPresent)
                .map(Optional::orElseThrow)
                .reduce(BigDecimal::add);
        surplusBudget.setText(totalBudget.map(DashboardValueFormatter::money).orElse(""));
        surplusVariance.setText(totalBudget
                .map(surplus::subtract)
                .map(DashboardValueFormatter::money)
                .orElse(""));
        surplusSparkline.setValues(snapshot.monthlyResults().stream()
                .map(DashboardSnapshot.MonthlyResult::surplus)
                .toList());
    }

    private void applyBudgetPerformance(List<DashboardSnapshot.BudgetActual> rows)
    {
        long onTrack = 0;
        long under = 0;
        long over = 0;

        for (DashboardSnapshot.BudgetActual row : rows)
        {
            BudgetStatus status = budgetStatus(row);
            switch (status)
            {
                case ON_TRACK -> onTrack++;
                case UNDER -> under++;
                case OVER -> over++;
                case UNAVAILABLE ->
                {
                }
            }
        }

        budgetDonut.setValues(onTrack, under, over);
        budgetOnTrackCount.setText(Long.toString(onTrack));
        budgetUnderCount.setText(Long.toString(under));
        budgetOverCount.setText(Long.toString(over));
    }

    private void applyOpenItems(DashboardSnapshot.OpenItemSummary openItems)
    {
        setOpenItem(
                receivableCount,
                receivableAmount,
                openItems.countFor("RECEIVABLE"),
                openItems.amountFor("RECEIVABLE"));
        setOpenItem(
                payableCount,
                payableAmount,
                openItems.countFor("PAYABLE"),
                openItems.amountFor("PAYABLE"));

        long combinedTimingCount = openItems.countFor("PREPAID_EXPENSE")
                + openItems.countFor("DEFERRED_REVENUE");
        BigDecimal combinedTimingAmount = openItems.amountFor("PREPAID_EXPENSE")
                .add(openItems.amountFor("DEFERRED_REVENUE"));
        setOpenItem(timingCount, timingAmount, combinedTimingCount, combinedTimingAmount);

        setOpenItem(
                outstandingBankCount,
                outstandingBankAmount,
                openItems.countFor("OUTSTANDING_BANK_ITEM"),
                openItems.amountFor("OUTSTANDING_BANK_ITEM"));
    }

    private void applyReconciliations(List<DashboardSnapshot.ReconciliationStatus> rows)
    {
        reconciliationRows.getChildren().clear();
        if (rows.isEmpty())
        {
            reconciliationRows.getChildren().add(muted(
                    "No reconciliation runs for the active organization."));
            return;
        }

        for (DashboardSnapshot.ReconciliationStatus row : rows)
        {
            Label date = new Label(DATE_FORMAT.format(row.statementEndingOn()));
            date.getStyleClass().add("dashboard-list-primary");
            Label details = muted(row.bankFormat() + " · "
                    + row.importedTransactionCount() + " imported transactions");
            VBox text = new VBox(1, date, details);
            HBox.setHgrow(text, Priority.ALWAYS);

            Label status = statusPill(row.status());
            HBox reconciliation = new HBox(
                    9,
                    iconBadge(UiIcons.Glyph.BANK, "accent-green"),
                    text,
                    status);
            reconciliation.setAlignment(Pos.CENTER_LEFT);
            reconciliation.getStyleClass().add("dashboard-list-row");
            reconciliationRows.getChildren().add(reconciliation);
        }
    }

    private static void setOpenItem(
            Label countLabel,
            Label amountLabel,
            long count,
            BigDecimal amount)
    {
        countLabel.setText(Long.toString(count));
        amountLabel.setText(DashboardValueFormatter.money(amount));
    }

    private static VBox card(
            String title,
            UiIcons.Glyph glyph,
            String accentClass,
            Node content)
    {
        return card(title, glyph, accentClass, content, null);
    }

    private static VBox card(
            String title,
            UiIcons.Glyph glyph,
            String accentClass,
            Node content,
            Node trailingAction)
    {
        Label heading = new Label(title);
        heading.getStyleClass().add("dashboard-card-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8, iconBadge(glyph, accentClass), heading, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        if (trailingAction != null)
        {
            header.getChildren().add(trailingAction);
        }

        VBox card = new VBox(10, header, content);
        card.getStyleClass().add("dashboard-card");
        card.setMinWidth(0);
        VBox.setVgrow(content, Priority.ALWAYS);
        return card;
    }

    private static Node iconBadge(UiIcons.Glyph glyph, String accentClass)
    {
        HBox badge = new HBox(UiIcons.icon(glyph, 15, accentClass));
        badge.setAlignment(Pos.CENTER);
        badge.getStyleClass().addAll("dashboard-icon-badge", accentClass);
        return badge;
    }

    private static Node legendRow(String text, String cueClass, Label count)
    {
        Region cue = new Region();
        cue.getStyleClass().addAll("dashboard-legend-dot", cueClass);
        Label label = new Label(text);
        HBox.setHgrow(label, Priority.ALWAYS);
        HBox row = new HBox(7, cue, label, count);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Node openItemRow(
            UiIcons.Glyph glyph,
            String accentClass,
            String text,
            Label count,
            Label amount)
    {
        Label name = new Label(text);
        name.getStyleClass().add("dashboard-list-primary");
        HBox.setHgrow(name, Priority.ALWAYS);
        count.getStyleClass().add("dashboard-count-pill");
        amount.getStyleClass().add("dashboard-open-item-amount");
        HBox row = new HBox(8, iconBadge(glyph, accentClass), name, count, amount);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("dashboard-open-item-row");
        return row;
    }

    private static Node quickLink(
            String title,
            String detail,
            UiIcons.Glyph glyph,
            String accentClass,
            Runnable action)
    {
        Label heading = new Label(title);
        heading.getStyleClass().add("quick-link-title");
        Label description = muted(detail);
        VBox text = new VBox(1, heading, description);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox link = new HBox(
                9,
                iconBadge(glyph, accentClass),
                text,
                UiIcons.icon(UiIcons.Glyph.CHEVRON_RIGHT, 13, "icon-muted"));
        link.setAlignment(Pos.CENTER_LEFT);
        link.getStyleClass().add("quick-link");
        link.setOnMouseClicked(event -> action.run());
        return link;
    }

    private static Node valueRow(String labelText, String valueText)
    {
        Label label = new Label(labelText);
        label.getStyleClass().add("dashboard-list-primary");
        Label value = new Label(valueText);
        value.getStyleClass().add("dashboard-key-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, label, spacer, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Button actionButton(
            String text,
            UiIcons.Glyph glyph,
            Runnable action)
    {
        Button button = new Button(text, UiIcons.icon(glyph, 14, "icon-white"));
        button.getStyleClass().add("dashboard-primary-button");
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Button textButton(
            String text,
            UiIcons.Glyph glyph,
            Runnable action)
    {
        Button button = new Button(text, UiIcons.icon(glyph, 12, "icon-blue"));
        button.getStyleClass().add("dashboard-text-button");
        button.setContentDisplay(ContentDisplay.RIGHT);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static <T> TableColumn<T, String> textColumn(
            String title,
            Function<T, String> value,
            double preferredWidth)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(preferredWidth);
        column.setMinWidth(Math.min(preferredWidth, 64));
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private static <T> TableColumn<T, String> moneyColumn(
            String title,
            Function<T, BigDecimal> value,
            double preferredWidth)
    {
        TableColumn<T, String> column = textColumn(
                title,
                row -> DashboardValueFormatter.money(value.apply(row)),
                preferredWidth);
        column.setCellFactory(ignored -> new RightAlignedCell());
        return column;
    }

    private static <T> TableColumn<T, String> optionalMoneyColumn(
            String title,
            Function<T, Optional<BigDecimal>> value,
            double preferredWidth)
    {
        TableColumn<T, String> column = textColumn(
                title,
                row -> value.apply(row).map(DashboardValueFormatter::money).orElse(""),
                preferredWidth);
        column.setCellFactory(ignored -> new RightAlignedCell());
        return column;
    }

    private static <T> TableColumn<T, Boolean> checkColumn(
            String title,
            Function<T, Boolean> value,
            double preferredWidth)
    {
        TableColumn<T, Boolean> column = new TableColumn<>(title);
        column.setPrefWidth(preferredWidth);
        column.setMinWidth(82);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setCellFactory(ignored -> new CheckCell<>());
        return column;
    }

    private static TableColumn<DashboardSnapshot.BudgetActual, String> budgetStatusColumn()
    {
        TableColumn<DashboardSnapshot.BudgetActual, String> column = new TableColumn<>("Status");
        column.setMinWidth(80);
        column.setPrefWidth(90);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                budgetStatus(cell.getValue()).label));
        column.setCellFactory(ignored -> new BudgetStatusCell());
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
        BigDecimal denominator = budget.abs().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ONE
                : budget.abs();
        BigDecimal ratio = variance.abs().divide(
                denominator,
                8,
                RoundingMode.HALF_UP);
        if (ratio.compareTo(ON_TRACK_TOLERANCE) <= 0)
        {
            return BudgetStatus.ON_TRACK;
        }
        return variance.signum() < 0 ? BudgetStatus.UNDER : BudgetStatus.OVER;
    }

    private static Label statusPill(String rawStatus)
    {
        String statusText = rawStatus == null || rawStatus.isBlank() ? "Unknown" : titleCase(rawStatus);
        Label label = new Label(statusText);
        label.getStyleClass().addAll("status-pill", statusStyle(rawStatus));
        return label;
    }

    private static String statusStyle(String status)
    {
        String normalized = status == null ? "" : status.toUpperCase();
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
        String normalized = value.toLowerCase().replace('_', ' ');
        if (normalized.isBlank())
        {
            return normalized;
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String budgetCategoryLabel(DashboardSnapshot.BudgetActual row)
    {
        if (row.categoryCode() == null || row.categoryCode().isBlank())
        {
            return row.categoryName();
        }
        return row.categoryCode() + " " + row.categoryName();
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

    private static final class RightAlignedCell extends TableCell<Object, String>
    {
        private RightAlignedCell()
        {
            setAlignment(Pos.CENTER_RIGHT);
        }

        @Override
        protected void updateItem(String item, boolean empty)
        {
            super.updateItem(item, empty);
            setText(empty ? null : item);
        }
    }

    private static final class CheckCell<T> extends TableCell<T, Boolean>
    {
        private final Node check = UiIcons.icon(UiIcons.Glyph.CHECK, 13, "icon-white");
        private final HBox badge = new HBox(check);

        private CheckCell()
        {
            badge.setAlignment(Pos.CENTER);
            badge.getStyleClass().add("dashboard-check-badge");
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(Boolean item, boolean empty)
        {
            super.updateItem(item, empty);
            setText(null);
            setGraphic(!empty && Boolean.TRUE.equals(item) ? badge : null);
        }
    }

    private static final class BudgetStatusCell
            extends TableCell<DashboardSnapshot.BudgetActual, String>
    {
        private final Label pill = new Label();

        private BudgetStatusCell()
        {
            setAlignment(Pos.CENTER);
            pill.getStyleClass().add("status-pill");
        }

        @Override
        protected void updateItem(String item, boolean empty)
        {
            super.updateItem(item, empty);
            pill.getStyleClass().removeAll(
                    "status-success",
                    "status-warning",
                    "status-danger",
                    "status-neutral");
            if (empty || item == null || item.isBlank() || getTableRow().getItem() == null)
            {
                setGraphic(null);
                return;
            }

            BudgetStatus status = budgetStatus(getTableRow().getItem());
            pill.setText(status.label);
            pill.getStyleClass().add(status.styleClass);
            setGraphic(pill);
        }
    }
}
