package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/** Applies the approved reference chrome to the production workspace shell. */
final class ReferenceWorkspaceWindow extends ProductionWorkspaceWindow
{
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy");

    private final Label databaseLabel = new Label();
    private final Label companyLabel = new Label();
    private final Label periodLabel = new Label();
    private final Label panelLabel = new Label("Dashboard");
    private final Label connectionLabel = new Label();
    private final Region connectionDot = new Region();

    public ReferenceWorkspaceWindow()
    {
        super();
        getStyleClass().addAll("reference-workspace", "production-workspace");
        configureWorkspaceGeometry();
        installMenuBar();
        installToolbarIcons();
        setBottom(buildReferenceStatusBar());

        ActivePeriodContext.activeDateProperty().addListener(
                (observable, oldDate, newDate) -> updatePeriod());
        MainWindow.sharedSessionState().onDatabaseSelectionChanged(ignored ->
        {
            updateDatabase();
            updateConnection();
        });
        MainWindow.sharedSessionState().onMultiCompanyChanged(ignored -> updateCompany());
        panelHost().getSelectionModel().selectedItemProperty().addListener(
                (observable, oldTab, newTab) ->
                        panelLabel.setText(newTab == null ? "" : newTab.getText()));

        updateDatabase();
        updateCompany();
        updatePeriod();
        updateConnection();
    }

    /** Closes all user-opened workspace tabs while leaving Dashboard open. */
    public void closeAllWorkspaceTabs()
    {
        panelHost().closeAllClosableTabs();
        openPanel(AppPanelId.DASHBOARD);
    }

    private void configureWorkspaceGeometry()
    {
        panelHost().setMinWidth(0.0);
        panelHost().getStyleClass().add("workspace-tabs");

        SplitPane workspace = workspaceForTests();
        workspace.setMinWidth(0.0);
        workspace.getStyleClass().add("workspace-split");
        workspace.getItems().forEach(item ->
        {
            if (item instanceof Region region)
            {
                region.setMinWidth(0.0);
            }
        });
        Platform.runLater(() ->
        {
            double width = workspace.getWidth() > 0.0
                    ? workspace.getWidth()
                    : WorkspaceShellLayoutPolicy.FALLBACK_WORKSPACE_WIDTH;
            WorkspaceShellLayoutPolicy.ShellGeometry geometry =
                    WorkspaceShellLayoutPolicy.forWidth(width);
            workspace.setDividerPositions(
                    geometry.leftDividerPosition(),
                    geometry.rightDividerPosition());
        });
    }

    private void installMenuBar()
    {
        if (!(getTop() instanceof VBox top)
                || top.getChildren().isEmpty()
                || !(top.getChildren().get(0) instanceof MenuBar existing))
        {
            return;
        }

        Menu file = existing.getMenus().isEmpty() ? new Menu("File") : existing.getMenus().remove(0);
        Menu edit = menu("Edit",
                item("Copy", this::copySelection),
                item("Paste", this::paste));
        Menu transactions = menu("Transactions",
                item("New Transaction", () -> openPanel(AppPanelId.TXN_EDITOR)),
                item("Ledger Register", () -> openPanel(AppPanelId.LEDGER_REGISTER)),
                item("Scheduled Transactions", () -> openPanel(AppPanelId.SCHEDULES)));

        MenuItem closeAllTabs = item("Close All Tabs", this::closeAllWorkspaceTabs);
        closeAllTabs.setAccelerator(new KeyCodeCombination(
                KeyCode.W,
                KeyCombination.CONTROL_DOWN,
                KeyCombination.SHIFT_DOWN));
        Menu workspace = menu("Workspace", closeAllTabs);

        Menu reports = menu("Reports",
                item("Report Library", () -> openPanel(AppPanelId.REPORT_LIBRARY)),
                item("Budget vs Actual", () -> openPanel(AppPanelId.BUDGET_VS_ACTUAL)));
        Menu tools = menu("Tools",
                item("Chart of Accounts", () -> openPanel(AppPanelId.CHART_OF_ACCOUNTS)),
                item("Funds", () -> openPanel(AppPanelId.FUNDS)),
                item("Settings", () -> openPanel(AppPanelId.SETTINGS)),
                item("Diagnostics", () -> openPanel(AppPanelId.DIAGNOSTICS)));
        Menu help = menu("Help", item("Help", () -> openPanel(AppPanelId.HELP)));

        MenuBar replacement = new MenuBar(
                file,
                edit,
                transactions,
                workspace,
                reports,
                tools,
                help);
        replacement.getStyleClass().add("reference-menu-bar");
        top.getChildren().set(0, replacement);
    }

    private void installToolbarIcons()
    {
        if (!(getTop() instanceof VBox top) || top.getChildren().size() < 2
                || !(top.getChildren().get(1) instanceof ToolBar toolbar))
        {
            return;
        }

        toolbar.getStyleClass().add("reference-toolbar");
        for (Node node : toolbar.getItems())
        {
            if (node instanceof Button button)
            {
                UiIcons.Glyph glyph = switch (button.getText())
                {
                    case "New" -> UiIcons.Glyph.ADD;
                    case "Save" -> UiIcons.Glyph.SAVE;
                    case "Navigation" -> UiIcons.Glyph.MORE;
                    case "Inspector" -> UiIcons.Glyph.NOTE;
                    case "Set Active Period" -> UiIcons.Glyph.CALENDAR;
                    default -> null;
                };
                if (glyph != null)
                {
                    button.setGraphic(UiIcons.icon(glyph, 15, "toolbar-icon"));
                    button.setContentDisplay(ContentDisplay.LEFT);
                    button.getStyleClass().add("toolbar-icon-button");
                }
            }
            else if (node instanceof DatePicker picker)
            {
                picker.getStyleClass().add("toolbar-period-picker");
            }
        }
    }

    private HBox buildReferenceStatusBar()
    {
        connectionDot.getStyleClass().add("connection-dot");
        panelLabel.getStyleClass().add("status-active-panel");
        HBox bar = new HBox(
                12,
                statusItem(UiIcons.Glyph.DATABASE, databaseLabel),
                separator(),
                statusItem(UiIcons.Glyph.USER, new Label(
                        MainWindow.sharedSessionState().preferences().defaultPrivilege().name())),
                separator(),
                statusItem(UiIcons.Glyph.ACCOUNTS, companyLabel),
                separator(),
                statusItem(UiIcons.Glyph.CALENDAR, periodLabel),
                separator(),
                panelLabel,
                spacer(),
                connectionDot,
                connectionLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void updateDatabase()
    {
        Path path = activeDatabasePathForTests();
        databaseLabel.setText(path.getFileName().toString());
        databaseLabel.setTooltip(new Tooltip(path.toAbsolutePath().toString()));
    }

    private void updateCompany()
    {
        companyLabel.setText(MainWindow.sharedSessionState().multiCompany().activeCompanyCode());
    }

    private void updatePeriod()
    {
        periodLabel.setText(PERIOD_FORMAT.format(activePeriodDate()));
    }

    private void updateConnection()
    {
        boolean connected = !databaseRecoveryVisibleForTests();
        connectionLabel.setText(connected ? "Connected" : "Database attention required");
        connectionDot.getStyleClass().removeAll("connection-ok", "connection-error");
        connectionDot.getStyleClass().add(connected ? "connection-ok" : "connection-error");
        connectionLabel.getStyleClass().removeAll("connection-ok-text", "connection-error-text");
        connectionLabel.getStyleClass().add(connected ? "connection-ok-text" : "connection-error-text");
    }

    private static Menu menu(String text, MenuItem... items)
    {
        Menu menu = new Menu(text);
        menu.getItems().addAll(items);
        return menu;
    }

    private static MenuItem item(String text, Runnable action)
    {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }

    private static Node statusItem(UiIcons.Glyph glyph, Label label)
    {
        label.getStyleClass().add("status-item-text");
        HBox item = new HBox(5, UiIcons.icon(glyph, 13, "status-icon"), label);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private static Region separator()
    {
        Region separator = new Region();
        separator.getStyleClass().add("status-separator");
        separator.setMinSize(1, 15);
        separator.setPrefSize(1, 15);
        separator.setMaxSize(1, 15);
        return separator;
    }

    private static Region spacer()
    {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
