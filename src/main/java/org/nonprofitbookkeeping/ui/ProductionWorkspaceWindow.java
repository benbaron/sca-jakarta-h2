package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

/**
 * Production JavaFX workspace shell.
 */
public class ProductionWorkspaceWindow extends BorderPane
{
    private static final double LEFT_DIVIDER = 0.20;
    private static final double RIGHT_DIVIDER = 0.80;

    private final PanelHost panelHost = new PanelHost();
    private final InspectorPane inspectorPane = new InspectorPane();
    private final NavigationPane navigationPane;
    private final SplitPane workspace = new SplitPane();
    private final Label activePanelLabel = new Label();
    private final Label activePeriodLabel = new Label();

    public ProductionWorkspaceWindow()
    {
        navigationPane = new NavigationPane(
                this::openPanel,
                inspectorPane::show,
                this::inspectorContext);

        ActivePeriodContext.activeDateProperty().addListener(
                (observable, oldDate, newDate) -> updateActivePeriodLabel());

        setTop(buildTopChrome());
        setCenter(buildWorkspace());
        setBottom(buildStatusBar());

        openPanel(AppPanelId.DASHBOARD);
    }

    public void openPanel(AppPanelId panelId)
    {
        panelHost.show(panelId);
        activePanelLabel.setText("Workspace: " + panelHost.getActiveTitle());
    }

    public void saveActivePanel()
    {
        panelHost.saveActive();
    }

    public void newItemInActivePanel()
    {
        panelHost.newItemActive();
    }

    public void copySelection()
    {
        panelHost.copySelectionActive();
    }

    public void paste()
    {
        panelHost.pasteActive();
    }

    public void closeInspector()
    {
        setInspectorVisible(false);
    }

    LocalDate activePeriodDate()
    {
        return ActivePeriodContext.get();
    }

    void setActivePeriodDate(LocalDate date)
    {
        ActivePeriodContext.set(date);
    }

    PanelHost panelHost()
    {
        return panelHost;
    }

    SplitPane workspaceForTests()
    {
        return workspace;
    }

    private VBox buildTopChrome()
    {
        VBox top = new VBox(buildMenuBar(), buildToolBar());
        top.getStyleClass().add("top-chrome");
        return top;
    }

    private MenuBar buildMenuBar()
    {
        Menu file = new Menu("File");
        MenuItem save = new MenuItem("Save");
        save.setOnAction(event -> saveActivePanel());
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(event ->
        {
            if (getScene() != null && getScene().getWindow() != null)
            {
                getScene().getWindow().hide();
            }
        });
        file.getItems().addAll(save, exit);

        Menu view = new Menu("View");
        MenuItem navigation = new MenuItem("Toggle Navigation");
        navigation.setOnAction(event -> setNavigationVisible(!workspace.getItems().contains(navigationPane)));
        MenuItem inspector = new MenuItem("Toggle Inspector");
        inspector.setOnAction(event -> setInspectorVisible(!workspace.getItems().contains(inspectorPane)));
        view.getItems().addAll(navigation, inspector);

        Menu workspaceMenu = new Menu("Workspace");
        MenuItem dashboard = new MenuItem("Dashboard");
        dashboard.setOnAction(event -> openPanel(AppPanelId.DASHBOARD));
        MenuItem ledger = new MenuItem("Ledger Register");
        ledger.setOnAction(event -> openPanel(AppPanelId.LEDGER_REGISTER));
        MenuItem transaction = new MenuItem("Transaction Editor");
        transaction.setOnAction(event -> openPanel(AppPanelId.TXN_EDITOR));
        workspaceMenu.getItems().addAll(dashboard, ledger, transaction);

        return new MenuBar(file, workspaceMenu, view);
    }

    private ToolBar buildToolBar()
    {
        Button newButton = new Button("New");
        newButton.setOnAction(event -> newItemInActivePanel());

        Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> saveActivePanel());

        Button navigationButton = new Button("Navigation");
        navigationButton.setOnAction(event -> setNavigationVisible(!workspace.getItems().contains(navigationPane)));

        Button inspectorButton = new Button("Inspector");
        inspectorButton.setOnAction(event -> setInspectorVisible(!workspace.getItems().contains(inspectorPane)));

        DatePicker periodPicker = new DatePicker(ActivePeriodContext.get());
        periodPicker.setPromptText("Active period date");

        Button setPeriodButton = new Button("Set Active Period");
        setPeriodButton.setOnAction(event ->
        {
            if (periodPicker.getValue() != null)
            {
                setActivePeriodDate(periodPicker.getValue());
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        updateActivePeriodLabel();
        return new ToolBar(
                newButton,
                saveButton,
                new Separator(),
                navigationButton,
                inspectorButton,
                new Separator(),
                new Label("Period:"),
                periodPicker,
                setPeriodButton,
                spacer,
                activePeriodLabel);
    }

    private SplitPane buildWorkspace()
    {
        workspace.getItems().setAll(navigationPane, panelHost, inspectorPane);
        workspace.setDividerPositions(LEFT_DIVIDER, RIGHT_DIVIDER);
        BorderPane.setMargin(workspace, new Insets(8));
        return workspace;
    }

    private HBox buildStatusBar()
    {
        activePanelLabel.getStyleClass().add("status-label");
        HBox statusBar = new HBox(activePanelLabel);
        statusBar.setPadding(new Insets(4, 10, 6, 10));
        statusBar.getStyleClass().add("status-bar");
        return statusBar;
    }

    private void updateActivePeriodLabel()
    {
        activePeriodLabel.setText("Active period: " + ActivePeriodContext.get());
    }

    private NavigationPane.InspectorContext inspectorContext()
    {
        AppPanelId active = panelHost.activePanelId();
        String capabilities = active == null ? "No active panel" : "Active panel: " + panelHost.getActiveTitle();
        return new NavigationPane.InspectorContext(
                "Active database",
                ActivePeriodContext.get().toString(),
                capabilities);
    }

    private void setNavigationVisible(boolean visible)
    {
        boolean present = workspace.getItems().contains(navigationPane);
        if (visible && !present)
        {
            workspace.getItems().add(0, navigationPane);
            restoreDividerPositions();
        }
        else if (!visible && present)
        {
            workspace.getItems().remove(navigationPane);
            restoreDividerPositions();
        }
    }

    private void setInspectorVisible(boolean visible)
    {
        boolean present = workspace.getItems().contains(inspectorPane);
        if (visible && !present)
        {
            workspace.getItems().add(inspectorPane);
            restoreDividerPositions();
        }
        else if (!visible && present)
        {
            workspace.getItems().remove(inspectorPane);
            restoreDividerPositions();
        }
    }

    private void restoreDividerPositions()
    {
        if (workspace.getItems().size() == 3)
        {
            workspace.setDividerPositions(LEFT_DIVIDER, RIGHT_DIVIDER);
        }
        else if (workspace.getItems().size() == 2)
        {
            workspace.setDividerPositions(0.25);
        }
    }
}
