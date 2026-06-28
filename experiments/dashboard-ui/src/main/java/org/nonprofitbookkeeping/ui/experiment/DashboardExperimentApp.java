package org.nonprofitbookkeeping.ui.experiment;

import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.Function;

/**
 * Standalone visual experiment for the proposed SCA-Jakarta application shell.
 *
 * <p>The experiment uses fictional in-memory data and deliberately does not initialize
 * CDI, JPA, Flyway, or the production H2 database.</p>
 */
public final class DashboardExperimentApp extends Application
{
    private final SplitPane outerSplit = new SplitPane();
    private final SplitPane workspaceSplit = new SplitPane();
    private final TabPane workspaceTabs = new TabPane();

    @Override
    public void start(Stage stage)
    {
        BorderPane root = new BorderPane();
        root.setTop(buildTop());
        root.setCenter(buildWorkspace());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1440, 900);
        scene.getStylesheets().add(getClass().getResource("dashboard-experiment.css").toExternalForm());

        stage.setTitle("Nonprofit Accounting (SCA-Jakarta) — UI Experiment");
        stage.setMinWidth(1024);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.show();

        outerSplit.setDividerPositions(0.15);
        workspaceSplit.setDividerPositions(0.80);
    }

    private Node buildTop()
    {
        MenuBar menuBar = new MenuBar(
                menu("File", "New", "Open", "Backup", "Exit"),
                menu("Edit", "Undo", "Redo", "Preferences"),
                menu("View", "Dashboard", "Ledger Register", "Inspector"),
                menu("Transactions", "New Transaction", "Journal Entry"),
                menu("Reports", "Reports Center"),
                menu("Tools", "Import", "Reconcile", "Close Period"),
                menu("Window", "Reset Layout"),
                menu("Help", "About"));

        ToolBar toolBar = new ToolBar(
                action("＋ New Transaction"),
                action("▣ New Item"),
                new MenuButton("⇩ Import", null, new MenuItem("SCLX Workbook"), new MenuItem("Bank Statement")),
                action("✓ Reconcile"),
                action("▤ Close Period"),
                new MenuButton("▧ Reports", null, new MenuItem("Reports Center")),
                spacer(),
                new Label("Organization:"), selector("Barony of the Example", "Demo Branch"),
                new Label("Period:"), selector("May 2026", "April 2026", "FY 2026"),
                action("● Treasurer"));
        toolBar.getStyleClass().add("main-toolbar");
        return new VBox(menuBar, toolBar);
    }

    private Node buildWorkspace()
    {
        VBox navigation = new VBox(8);
        navigation.getStyleClass().add("navigation-pane");
        navigation.getChildren().addAll(new Label("☰  Workspace"), buildNavigationTree(), collapseButton());
        navigation.setMinWidth(0);

        Tab dashboard = new Tab("Dashboard", buildDashboard());
        dashboard.setClosable(false);
        workspaceTabs.getTabs().add(dashboard);

        VBox inspector = new VBox(10,
                title("Inspector"),
                infoCard("Organization", "Barony of the Example", "Kingdom: Kingdom of the Stag", "Fiscal year: Jan–Dec", "Currency: USD"),
                infoCard("Period Information", "Current period: May 2026", "05/01/2026 – 05/31/2026", "Status: Open", "Days remaining: 16"),
                infoCard("Balances (All Funds)", "Total assets      $245,678.90", "Total liabilities  $38,765.21", "Net assets         $206,913.69", "YTD surplus         $32,145.67"),
                infoCard("Notes", "No notes for this period."),
                new Button("Add Note"));
        inspector.getStyleClass().add("inspector-pane");
        inspector.setMinWidth(0);

        workspaceSplit.getItems().addAll(workspaceTabs, inspector);
        workspaceSplit.setMinWidth(0);
        outerSplit.getItems().addAll(navigation, workspaceSplit);
        outerSplit.setMinWidth(0);
        return outerSplit;
    }

    private TreeView<String> buildNavigationTree()
    {
        TreeItem<String> root = new TreeItem<>("Navigation");
        root.setExpanded(true);
        root.getChildren().addAll(
                leaf("Dashboard"),
                group("Accounting", "Ledger Register", "Transaction Editor", "Journal Entry", "Banking & Reconciliation", "Schedules"),
                group("Planning", "Budget Editor", "Budget vs Actual"),
                group("Assets & Inventory", "Fixed Assets", "Inventory", "Supplies"),
                group("Liabilities & Receivables", "Receivables", "Payables", "Prepaids & Deferred"),
                group("Period Close", "Period Close"),
                group("Reports", "Reports Center"),
                group("Administration", "Chart of Accounts", "Funds", "Settings"));

        TreeView<String> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.setMinWidth(0);
        VBox.setVgrow(tree, Priority.ALWAYS);
        tree.setOnMouseClicked(event ->
        {
            TreeItem<String> selected = tree.getSelectionModel().getSelectedItem();
            if (selected != null && selected.isLeaf() && !"Dashboard".equals(selected.getValue()))
            {
                openPlaceholderTab(selected.getValue());
            }
        });
        return tree;
    }

    private Node buildDashboard()
    {
        GridPane grid = new GridPane();
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

        grid.add(kpi("Cash Balances", "All Bank Accounts", "$125,430.12", "Cleared balance as of May 15, 2026"), 0, 0);
        grid.add(kpi("YTD Surplus (Deficit)", "All Funds", "$32,145.67", "+14.8% vs budget"), 1, 0);
        grid.add(card("Budget Performance", budgetChart()), 2, 0);
        grid.add(card("Open Items", keyValues(
                "Outstanding checks", "12", "Deposits in transit", "6", "Receivables", "9", "Payables", "7", "Total open items", "34")), 3, 0);
        grid.add(card("Recent Transactions", transactions()), 0, 1, 4, 1);
        grid.add(card("Bank Reconciliation Status", reconciliations()), 0, 2, 2, 1);
        grid.add(card("Budget vs Actual (YTD)", budgets()), 2, 2);
        grid.add(card("Quick Links", quickLinks()), 3, 2);

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.viewportBoundsProperty().addListener((observable, oldBounds, bounds) -> DashboardLayoutPolicy.apply(grid, bounds.getWidth()));
        return scrollPane;
    }

    private Node transactions()
    {
        TableView<TransactionRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(220);
        table.getColumns().addAll(
                column("Date", TransactionRow::date), column("Txn #", TransactionRow::number),
                column("Description", TransactionRow::description), column("Account", TransactionRow::account),
                column("Fund", TransactionRow::fund), column("Debit", TransactionRow::debit),
                column("Credit", TransactionRow::credit), column("Status", TransactionRow::status));
        table.getItems().addAll(
                new TransactionRow("05/15/2026", "1258", "Donation — General Fund", "4000 Donations", "10 General", "$500.00", "", "Posted"),
                new TransactionRow("05/14/2026", "1257", "Office supplies", "6200 Office Supplies", "10 General", "", "$125.43", "Posted"),
                new TransactionRow("05/14/2026", "1256", "Youth retreat expense", "6100 Program Expenses", "20 Youth", "", "$1,250.00", "Posted"),
                new TransactionRow("05/13/2026", "1255", "Grant — Community Fund", "4300 Grants", "30 Community", "$2,000.00", "", "Posted"),
                new TransactionRow("05/12/2026", "1254", "Rent expense", "6000 Rent", "10 General", "", "$1,800.00", "Posted"));
        return table;
    }

    private Node reconciliations()
    {
        TableView<FourColumnRow> table = fourColumnTable("Bank Account", "Statement Date", "Status", "Balance");
        table.getItems().addAll(
                new FourColumnRow("Operating Checking", "05/15/2026", "● Reconciled", "$98,765.43"),
                new FourColumnRow("Savings Account", "04/30/2026", "● Reconciled", "$25,432.11"),
                new FourColumnRow("Building Fund", "05/10/2026", "◐ In progress", "$15,000.00"),
                new FourColumnRow("Petty Cash", "05/01/2026", "○ Not started", "$125.00"));
        return table;
    }

    private Node budgets()
    {
        TableView<FourColumnRow> table = fourColumnTable("Category", "Budget", "Actual", "Variance");
        table.getItems().addAll(
                new FourColumnRow("Revenue", "$250,000.00", "$272,145.67", "$22,145.67"),
                new FourColumnRow("Program Expenses", "$120,000.00", "$98,765.23", "$21,234.77"),
                new FourColumnRow("Admin Expenses", "$60,000.00", "$52,430.11", "$7,569.89"),
                new FourColumnRow("Fundraising", "$20,000.00", "$18,234.66", "$1,765.34"));
        return table;
    }

    private TableView<FourColumnRow> fourColumnTable(String first, String second, String third, String fourth)
    {
        TableView<FourColumnRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(210);
        table.getColumns().addAll(column(first, FourColumnRow::first), column(second, FourColumnRow::second), column(third, FourColumnRow::third), column(fourth, FourColumnRow::fourth));
        return table;
    }

    private Node budgetChart()
    {
        PieChart chart = new PieChart(FXCollections.observableArrayList(
                new PieChart.Data("On track", 68), new PieChart.Data("Under", 22), new PieChart.Data("Over", 10)));
        chart.setLabelsVisible(false);
        chart.setPrefHeight(155);
        return chart;
    }

    private Node quickLinks()
    {
        return new VBox(10,
                quickLink("New Transaction", "Record a new transaction"),
                quickLink("Enter Journal Entry", "Create a manual journal entry"),
                quickLink("Import SCLX Workbook", "Import from the workbook"),
                quickLink("Reconcile Bank Account", "Open reconciliation window"));
    }

    private Node buildStatusBar()
    {
        HBox bar = new HBox(12,
                new Label("Database: experiment (in memory)"), separator(),
                new Label("User: Treasurer"), separator(),
                new Label("Organization: Barony of the Example"), separator(),
                new Label("Period: May 2026"), spacer(), new Label("● Database open"));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private Button collapseButton()
    {
        Button button = new Button("← Collapse");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event ->
        {
            double position = outerSplit.getDividerPositions()[0];
            outerSplit.setDividerPositions(position < 0.04 ? 0.15 : 0.0);
            button.setText(position < 0.04 ? "← Collapse" : "→ Expand");
        });
        return button;
    }

    private void openPlaceholderTab(String name)
    {
        for (Tab tab : workspaceTabs.getTabs())
        {
            if (name.equals(tab.getText()))
            {
                workspaceTabs.getSelectionModel().select(tab);
                return;
            }
        }
        Label message = new Label(name + "\n\nThis experiment validates shell navigation and tab behavior.\nThe production panel would be inserted here.");
        message.setWrapText(true);
        StackPane content = new StackPane(message);
        content.setPadding(new Insets(30));
        Tab tab = new Tab(name, content);
        workspaceTabs.getTabs().add(tab);
        workspaceTabs.getSelectionModel().select(tab);
    }

    private static VBox kpi(String heading, String scope, String amount, String detail)
    {
        Label amountLabel = new Label(amount);
        amountLabel.getStyleClass().add("amount");
        return card(heading, new VBox(7, muted(scope), amountLabel, muted(detail)));
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

    private static GridPane keyValues(String... values)
    {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(7);
        for (int index = 0; index < values.length; index += 2)
        {
            grid.add(new Label(values[index]), 0, index / 2);
            grid.add(new Label(values[index + 1]), 1, index / 2);
        }
        return grid;
    }

    private static VBox infoCard(String heading, String... lines)
    {
        VBox box = new VBox(6);
        box.getStyleClass().add("inspector-card");
        Label title = new Label(heading);
        title.getStyleClass().add("inspector-card-title");
        box.getChildren().add(title);
        for (String line : lines)
        {
            Label label = new Label(line);
            label.setWrapText(true);
            box.getChildren().add(label);
        }
        return box;
    }

    private static VBox quickLink(String heading, String detail)
    {
        Label title = new Label(heading);
        title.getStyleClass().add("quick-link-title");
        VBox link = new VBox(2, title, muted(detail));
        link.getStyleClass().add("quick-link");
        return link;
    }

    private static <T> TableColumn<T, String> column(String heading, Function<T, String> getter)
    {
        TableColumn<T, String> column = new TableColumn<>(heading);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(getter.apply(cell.getValue())));
        return column;
    }

    private static TreeItem<String> leaf(String name)
    {
        return new TreeItem<>(name);
    }

    private static TreeItem<String> group(String name, String... children)
    {
        TreeItem<String> item = new TreeItem<>(name);
        item.setExpanded(true);
        for (String child : children)
        {
            item.getChildren().add(leaf(child));
        }
        return item;
    }

    private static Menu menu(String heading, String... items)
    {
        Menu menu = new Menu(heading);
        for (String item : items)
        {
            menu.getItems().add(new MenuItem(item));
        }
        return menu;
    }

    private static Button action(String text)
    {
        return new Button(text);
    }

    private static ComboBox<String> selector(String... values)
    {
        ComboBox<String> selector = new ComboBox<>(FXCollections.observableArrayList(values));
        selector.getSelectionModel().selectFirst();
        return selector;
    }

    private static Label title(String text)
    {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Label muted(String text)
    {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        return label;
    }

    private static Separator separator()
    {
        return new Separator(Orientation.VERTICAL);
    }

    private static Region spacer()
    {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    public static void main(String[] args)
    {
        launch(args);
    }

    private record TransactionRow(String date, String number, String description, String account, String fund, String debit, String credit, String status)
    {
    }

    private record FourColumnRow(String first, String second, String third, String fourth)
    {
    }
}
