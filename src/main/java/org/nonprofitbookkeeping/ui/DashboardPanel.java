package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.dashboard.DashboardQueryService;
import org.nonprofitbookkeeping.service.dashboard.DashboardSnapshot;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Database-backed production dashboard.
 */
public class DashboardPanel implements AppPanel
{
    private static final int RECENT_TRANSACTION_LIMIT = 12;

    private final BorderPane root = new BorderPane();
    private final DashboardQueryService dashboardQueryService;
    private final Supplier<LocalDate> asOfDateSupplier;
    private final Label status = new Label();
    private final Label bookCash = new Label();
    private final Label reconciledCash = new Label();
    private final Label unreconciledDifference = new Label();
    private final Label yearToDateSurplus = new Label();
    private final Label fundClassTotals = new Label();
    private final TableView<DashboardSnapshot.BankAccountBalance> bankAccounts = new TableView<>();
    private final TableView<DashboardSnapshot.RecentTransaction> recentTransactions = new TableView<>();
    private final Button refresh = new Button("Refresh");

    public DashboardPanel()
    {
        this(UiServiceRegistry.dashboardQuery(), LocalDate::now);
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
        reconciledCash.setText(DashboardValueFormatter.optionalMoney(snapshot.reconciledCash()));
        unreconciledDifference.setText(DashboardValueFormatter.optionalMoney(snapshot.unreconciledDifference()));
        yearToDateSurplus.setText(DashboardValueFormatter.money(snapshot.yearToDateSurplus()));
        fundClassTotals.setText(formatFundClasses(snapshot.fundClassTotals()));
        bankAccounts.getItems().setAll(snapshot.bankAccounts());
        recentTransactions.getItems().setAll(snapshot.recentTransactions());
    }

    private void buildLayout()
    {
        root.setPadding(new Insets(12));

        Label title = new Label("Dashboard");
        title.getStyleClass().add("panel-title");

        Button addTransaction = new Button("New Transaction");
        addTransaction.setOnAction(event -> onNew());
        refresh.setOnAction(event -> reload());

        GridPane header = new GridPane();
        header.setHgap(8);
        header.setVgap(6);
        header.add(title, 0, 0);
        header.add(addTransaction, 1, 0);
        header.add(refresh, 2, 0);
        header.add(status, 0, 1, 3, 1);
        root.setTop(new VBox(8, header, new Separator()));

        GridPane cards = new GridPane();
        cards.setHgap(12);
        cards.setVgap(12);
        cards.add(card("Book cash", bookCash), 0, 0);
        cards.add(card("Reconciled cash", reconciledCash), 1, 0);
        cards.add(card("Unreconciled difference", unreconciledDifference), 2, 0);
        cards.add(card("Year-to-date surplus", yearToDateSurplus), 0, 1);
        cards.add(card("Fund classifications", fundClassTotals), 1, 1, 2, 1);

        configureBankAccountsTable();
        configureRecentTransactionsTable();

        VBox center = new VBox(
                12,
                cards,
                new Label("Bank accounts"),
                bankAccounts,
                new Label("Recent transactions"),
                recentTransactions);
        VBox.setVgrow(bankAccounts, Priority.ALWAYS);
        VBox.setVgrow(recentTransactions, Priority.ALWAYS);
        root.setCenter(center);
    }

    private VBox card(String heading, Label value)
    {
        Label title = new Label(heading);
        title.getStyleClass().add("dashboard-card-title");
        value.getStyleClass().add("dashboard-card-value");
        value.setWrapText(true);
        VBox card = new VBox(4, title, value);
        card.setPadding(new Insets(10));
        card.getStyleClass().add("dashboard-card");
        GridPane.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private void configureBankAccountsTable()
    {
        TableColumn<DashboardSnapshot.BankAccountBalance, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().code()));

        TableColumn<DashboardSnapshot.BankAccountBalance, String> name = new TableColumn<>("Account");
        name.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().name()));

        TableColumn<DashboardSnapshot.BankAccountBalance, String> balance = new TableColumn<>("Book balance");
        balance.setCellValueFactory(value -> new SimpleStringProperty(
                DashboardValueFormatter.money(value.getValue().balance())));

        bankAccounts.getColumns().setAll(code, name, balance);
        bankAccounts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bankAccounts.setPlaceholder(new Label("No bank-account activity through the selected date."));
    }

    private void configureRecentTransactionsTable()
    {
        TableColumn<DashboardSnapshot.RecentTransaction, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().transactionDate().toString()));

        TableColumn<DashboardSnapshot.RecentTransaction, String> memo = new TableColumn<>("Memo");
        memo.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().memo()));

        TableColumn<DashboardSnapshot.RecentTransaction, String> transactionStatus = new TableColumn<>("Status");
        transactionStatus.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().status()));

        recentTransactions.getColumns().setAll(date, memo, transactionStatus);
        recentTransactions.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recentTransactions.setPlaceholder(new Label("No transactions through the selected date."));
        recentTransactions.setRowFactory(table ->
        {
            javafx.scene.control.TableRow<DashboardSnapshot.RecentTransaction> row = new javafx.scene.control.TableRow<>();
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

    private static String formatFundClasses(Map<String, java.math.BigDecimal> totals)
    {
        if (totals == null || totals.isEmpty())
        {
            return "No fund activity";
        }

        return totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ": " + DashboardValueFormatter.money(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("   "));
    }
}
