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
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Production dashboard based on the approved dashboard UI experiment and backed
 * by authoritative accounting projections.
 */
public class DashboardPanel implements AppPanel
{
    private static final int RECENT_TRANSACTION_LIMIT = 12;

    private final BorderPane root = new BorderPane();
    private final GridPane dashboardGrid = new GridPane();
    private final DashboardQueryService dashboardQueryService;
    private final Supplier<LocalDate> asOfDateSupplier;
    private final Label status = new Label();
    private final Label bookCash = amountLabel();
    private final Label yearToDateSurplus = amountLabel();
    private final Label reconciledCash = new Label();
    private final Label unreconciledDifference = new Label();
    private final Label bankAccountCount = new Label();
    private final PieChart fundChart = new PieChart();
    private final TableView<DashboardSnapshot.RecentTransaction> recentTransactions = new TableView<>();
    private final TableView<DashboardSnapshot.BankAccountBalance> bankAccounts = new TableView<>();
    private final TableView<FundTotalRow> fundTotals = new TableView<>();
    private final Button refresh = new Button("Refresh");

    public DashboardPanel()
    {
        this(UiServiceRegistry.dashboardQuery(), ActivePeriodContext::get);
        ActivePeriodContext.activeDateProperty().addListener((observable, oldDate, newDate) -> reload());
    }

    DashboardPanel(DashboardQueryService dashboardQueryService, Supplier<LocalDate> asOfDateSupplier)
    {
        this.dashboardQueryService = dashboardQueryService;
        this.asOfDateSupplier = asOfDateSupplier;
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
        DrillThroughCoordinator.openLedgerWithContext("Dashboard quick action: new transaction");
    }

    void reload()
    {
        refresh.setDisable(true);
        status.setText("Loading dashboard data...");
        LocalDate asOfDate = asOfDateSupplier.get();

        UiAsync.run(
                "dashboard-production-load",
                () -> dashboardQueryService.load(asOfDate, RECENT_TRANSACTION_LIMIT),
                snapshot ->
                {
                    applySnapshot(snapshot);
                    status.setText("Dashboard updated through " + snapshot.asOfDate() + ".");
                    refresh.setDisable(false);
                },
                ex ->
                {
                    status.setText("Failed to load dashboard: " + UiErrors.safeMessage(ex));
                    refresh.setDisable(false);
                });
    }

    void applySnapshot(DashboardSnapshot snapshot)
    {
        bookCash.setText(DashboardValueFormatter.money(snapshot.bookCash()));
        yearToDateSurplus.setText(DashboardValueFormatter.money(snapshot.yearToDateSurplus()));
        reconciledCash.setText(DashboardValueFormatter.optionalMoney(snapshot.reconciledCash()));
        unreconciledDifference.setText(DashboardValueFormatter.optionalMoney(snapshot.unreconciledDifference()));
        bankAccountCount.setText(Integer.toString(snapshot.bankAccounts().size()));
        recentTransactions.getItems().setAll(snapshot.recentTransactions());
        bankAccounts.getItems().setAll(snapshot.bankAccounts());
        fundTotals.getItems().setAll(snapshot.fundClassTotals().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new FundTotalRow(entry.getKey(), entry.getValue()))
                .toList());
        fundChart.setData(FXCollections.observableArrayList(snapshot.fundClassTotals().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().compareTo(BigDecimal.ZERO) != 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new PieChart.Data(entry.getKey(), entry.getValue().abs().doubleValue()))
                .toList()));
    }

    private void buildLayout()
    {
        root.getStyleClass().add("dashboard-experiment-root");
        root.setTop(buildHeader());
        root.setCenter(buildDashboard());
    }

    private Node buildHeader()
    {
        Label title = new Label("Dashboard");
        title.getStyleClass().add("panel-title");

        Button addTransaction = new Button("＋ New Transaction");
        addTransaction.setOnAction(event -> onNew());
        refresh.setOnAction(event -> reload());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(8, title, addTransaction, refresh, spacer, status);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(10, 14, 10, 14));
        actions.getStyleClass().add("dashboard-header");
        return new VBox(actions, new Separator());
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

        dashboardGrid.add(kpi("Cash Balances", "All Bank Accounts", bookCash,
                "Book cash through the active period"), 0, 0);
        dashboardGrid.add(kpi("YTD Surplus (Deficit)", "All Funds", yearToDateSurplus,
                "Income less expenses through the active period"), 1, 0);
        dashboardGrid.add(card("Fund Classification Mix", configureFundChart()), 2, 0);
        dashboardGrid.add(card("Open Items", openItems()), 3, 0);
        dashboardGrid.add(card("Recent Transactions", configureRecentTransactions()), 0, 1, 4, 1);
        dashboardGrid.add(card("Bank Reconciliation Status", configureBankAccounts()), 0, 2, 2, 1);
        dashboardGrid.add(card("Fund Classification Totals", configureFundTotals()), 2, 2);
        dashboardGrid.add(card("Quick Links", quickLinks()), 3, 2);

        ScrollPane scrollPane = new ScrollPane(dashboardGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("dashboard-scroll");
        scrollPane.viewportBoundsProperty().addListener(
                (observable, oldBounds, bounds) -> DashboardLayoutPolicy.apply(dashboardGrid, bounds.getWidth()));
        return scrollPane;
    }

    private Node configureFundChart()
    {
        fundChart.setLabelsVisible(false);
        fundChart.setLegendVisible(true);
        fundChart.setPrefHeight(175);
        return fundChart;
    }

    private Node openItems()
    {
        return keyValues(
                "Reconciled cash", reconciledCash,
                "Unreconciled difference", unreconciledDifference,
                "Bank accounts", bankAccountCount);
    }

    private Node configureRecentTransactions()
    {
        recentTransactions.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recentTransactions.setPrefHeight(230);
        recentTransactions.getColumns().setAll(
                column("Date", row -> row.transactionDate().toString()),
                column("Transaction ID", row -> Long.toString(row.transactionId())),
                column("Description", DashboardSnapshot.RecentTransaction::memo),
                column("Status", DashboardSnapshot.RecentTransaction::status));
        recentTransactions.setPlaceholder(new Label("No transactions through the selected date."));
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

    private Node configureBankAccounts()
    {
        bankAccounts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bankAccounts.setPrefHeight(215);
        bankAccounts.getColumns().setAll(
                column("Code", DashboardSnapshot.BankAccountBalance::code),
                column("Bank Account", DashboardSnapshot.BankAccountBalance::name),
                column("Book Balance", row -> DashboardValueFormatter.money(row.balance())));
        bankAccounts.setPlaceholder(new Label("No bank-account activity through the selected date."));
        return bankAccounts;
    }

    private Node configureFundTotals()
    {
        fundTotals.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        fundTotals.setPrefHeight(215);
        fundTotals.getColumns().setAll(
                column("Classification", FundTotalRow::classification),
                column("Balance", row -> DashboardValueFormatter.money(row.balance())));
        fundTotals.setPlaceholder(new Label("No fund activity through the selected date."));
        return fundTotals;
    }

    private Node quickLinks()
    {
        return new VBox(
                10,
                quickLink("New Transaction", "Record a new transaction", this::onNew),
                quickLink("Ledger Register", "Review entered transactions",
                        () -> DrillThroughCoordinator.openLedgerWithContext("Dashboard quick link")),
                quickLink("Reconcile Bank Account", "Open reconciliation workspace",
                        () -> DrillThroughCoordinator.openPanelWithContext(
                                AppPanelId.RECONCILIATION_RUNS,
                                "Dashboard quick link")),
                quickLink("Reports Center", "Open the report library",
                        () -> DrillThroughCoordinator.openPanelWithContext(
                                AppPanelId.REPORT_LIBRARY,
                                "Dashboard quick link")));
    }

    private static VBox kpi(String heading, String subtitle, Label value, String note)
    {
        Label title = new Label(heading);
        title.getStyleClass().add("card-title");
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("muted");
        Label detail = new Label(note);
        detail.getStyleClass().add("muted");
        detail.setWrapText(true);
        VBox box = new VBox(5, title, sub, value, detail);
        box.getStyleClass().add("dashboard-card");
        return box;
    }

    private static VBox card(String heading, Node content)
    {
        Label title = new Label(heading);
        title.getStyleClass().add("card-title");
        VBox box = new VBox(10, title, content);
        box.getStyleClass().add("dashboard-card");
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }

    private static Node keyValues(Object... values)
    {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        for (int index = 0; index < values.length; index += 2)
        {
            Label key = new Label(values[index].toString());
            key.getStyleClass().add("muted");
            Node value = values[index + 1] instanceof Node node
                    ? node
                    : new Label(values[index + 1].toString());
            grid.add(key, 0, index / 2);
            grid.add(value, 1, index / 2);
        }
        return grid;
    }

    private static Node quickLink(String heading, String detail, Runnable action)
    {
        Label title = new Label(heading);
        title.getStyleClass().add("quick-link-title");
        Label description = new Label(detail);
        description.getStyleClass().add("muted");
        description.setWrapText(true);
        VBox box = new VBox(2, title, description);
        box.getStyleClass().add("quick-link");
        box.setOnMouseClicked(event -> action.run());
        return box;
    }

    private static Label amountLabel()
    {
        Label label = new Label();
        label.getStyleClass().add("amount");
        return label;
    }

    private static <T> TableColumn<T, String> column(String title, Function<T, String> value)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    record FundTotalRow(String classification, BigDecimal balance)
    {
    }
}
