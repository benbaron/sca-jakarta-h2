package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Production JavaFX workspace shell.
 */
public class ProductionWorkspaceWindow extends BorderPane
{
    private static final double LEFT_DIVIDER = 0.20;
    private static final double RIGHT_DIVIDER = 0.80;

    private final AppStateStore stateStore;
    private final DatabaseSessionController databaseSessionController;
    private final PanelHost panelHost = new PanelHost();
    private final InspectorPane inspectorPane = new InspectorPane();
    private final NavigationPane navigationPane;
    private final SplitPane workspace = new SplitPane();
    private final Label activePanelLabel = new Label();
    private final Label activePeriodLabel = new Label();
    private final Label activeDatabaseLabel = new Label();
    private RuntimeException databaseFailure;

    public ProductionWorkspaceWindow()
    {
        this(UserAppStateStore.create(), UiServiceRegistry::reconnectToDatabase);
    }

    ProductionWorkspaceWindow(
            AppStateStore stateStore,
            DatabaseSessionController.Connector connector)
    {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.databaseSessionController = new DatabaseSessionController(
                MainWindow.sharedSessionState(),
                stateStore,
                connector);

        try
        {
            databaseSessionController.restorePersistedSelection();
        }
        catch (RuntimeException ex)
        {
            databaseFailure = ex;
            System.err.println("[NPBK] Could not restore persisted database selection: "
                    + ex.getMessage());
        }

        navigationPane = new NavigationPane(
                this::openPanel,
                inspectorPane::show,
                this::inspectorContext);

        ActivePeriodContext.activeDateProperty().addListener(
                (observable, oldDate, newDate) -> updateActivePeriodLabel());
        MainWindow.sharedSessionState().onDatabaseSelectionChanged(
                ignored -> updateActiveDatabaseLabel());

        setTop(buildTopChrome());
        setCenter(buildWorkspace());
        setBottom(buildStatusBar());

        updateActiveDatabaseLabel();
        showDashboardOrRecovery(databaseFailure);
    }

    public void openPanel(AppPanelId panelId)
    {
        if (databaseFailure != null && panelId != AppPanelId.DASHBOARD)
        {
            inspectorPane.show(
                    "Database unavailable",
                    "Select, repair, or create a database from the Dashboard or File menu before opening accounting workspaces.");
            showRecoveryDashboard(databaseFailure);
            return;
        }

        try
        {
            panelHost.show(panelId);
            activePanelLabel.setText("Workspace: " + panelHost.getActiveTitle());
        }
        catch (RuntimeException ex)
        {
            if (panelId == AppPanelId.DASHBOARD)
            {
                showRecoveryDashboard(ex);
                return;
            }
            throw ex;
        }
    }

    public void saveActivePanel()
    {
        panelHost.saveActive();
        stateStore.saveDatabaseSelection(MainWindow.sharedSessionState().databaseSelection());
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

    public AppPanel.RunCommandResult executeCommand(AppCommand command)
    {
        Objects.requireNonNull(command, "command");
        return switch (command)
        {
            case NEW_ACTIVE ->
            {
                newItemInActivePanel();
                yield new AppPanel.RunCommandResult(true, "New command routed to active panel.");
            }
            case SAVE_ACTIVE ->
            {
                saveActivePanel();
                yield new AppPanel.RunCommandResult(true, "Save command routed to active panel.");
            }
            case COPY_ACTIVE ->
            {
                copySelection();
                yield new AppPanel.RunCommandResult(true, "Copy command routed to active panel.");
            }
            case PASTE_ACTIVE ->
            {
                paste();
                yield new AppPanel.RunCommandResult(true, "Paste command routed to active panel.");
            }
            case CLOSE_ALL_TABS ->
            {
                panelHost.closeAllClosableTabs();
                yield new AppPanel.RunCommandResult(true, "Closed all non-dashboard tabs.");
            }
            case POST_VALIDATE -> panelHost.runCommandActive(command);
        };
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

    Path activeDatabasePathForTests()
    {
        return databaseSessionController.activeDatabasePath();
    }

    boolean databaseRecoveryVisibleForTests()
    {
        return databaseFailure != null
                && panelHost.activeRoot() instanceof BorderPane;
    }

    void connectDatabaseForTests(Path databaseFile)
    {
        connectDatabase(databaseFile);
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
        MenuItem selectDatabase = new MenuItem("Select Database File…");
        selectDatabase.setOnAction(event -> selectDatabaseFile());
        MenuItem createDatabase = new MenuItem("Create New Database…");
        createDatabase.setOnAction(event -> createNewDatabase());
        MenuItem repairDatabase = new MenuItem("Retry / Repair Current Database");
        repairDatabase.setOnAction(event -> repairCurrentDatabase());
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
        file.getItems().addAll(
                selectDatabase,
                createDatabase,
                repairDatabase,
                new SeparatorMenuItem(),
                save,
                exit);

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
                activeDatabaseLabel,
                new Separator(),
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

    private void showDashboardOrRecovery(RuntimeException startupFailure)
    {
        if (startupFailure != null)
        {
            showRecoveryDashboard(startupFailure);
            return;
        }

        try
        {
            databaseFailure = null;
            openPanel(AppPanelId.DASHBOARD);
        }
        catch (RuntimeException ex)
        {
            showRecoveryDashboard(ex);
        }
    }

    private void showRecoveryDashboard(RuntimeException failure)
    {
        databaseFailure = failure;
        DatabaseRecoveryPanel recoveryPanel = new DatabaseRecoveryPanel(
                databaseSessionController.activeDatabasePath(),
                failure,
                this::repairCurrentDatabase,
                this::selectDatabaseFile,
                this::createNewDatabase);
        panelHost.showReplacement(AppPanelId.DASHBOARD, recoveryPanel);
        activePanelLabel.setText("Workspace: Dashboard — database attention required");
        inspectorPane.show(
                "Database attention required",
                DatabaseRecoveryPanel.safeMessage(failure));
    }

    private void repairCurrentDatabase()
    {
        connectDatabase(databaseSessionController.activeDatabasePath());
    }

    private void selectDatabaseFile()
    {
        chooseOpenDatabaseFile().ifPresent(this::connectDatabase);
    }

    private void createNewDatabase()
    {
        Optional<Path> selected = chooseNewDatabaseFile();
        if (selected.isEmpty())
        {
            return;
        }

        Path target = normalizeNewDatabasePath(selected.get());
        if (Files.exists(target))
        {
            inspectorPane.show(
                    "Database not created",
                    "A file already exists at " + target.toAbsolutePath()
                            + ". Choose a new file name or select the existing database instead.");
            return;
        }
        connectDatabase(target);
    }

    private void connectDatabase(Path databaseFile)
    {
        try
        {
            databaseSessionController.connect(databaseFile);
            databaseFailure = null;
            panelHost.reset();
            updateActiveDatabaseLabel();
            inspectorPane.show(
                    "Database connected",
                    "Active database: " + databaseSessionController.activeDatabasePath().toAbsolutePath());
            showDashboardOrRecovery(null);
        }
        catch (RuntimeException ex)
        {
            showRecoveryDashboard(ex);
        }
    }

    private Optional<Path> chooseOpenDatabaseFile()
    {
        if (getScene() == null || getScene().getWindow() == null)
        {
            inspectorPane.show("Database selection unavailable", "The application window is not ready.");
            return Optional.empty();
        }

        FileChooser chooser = databaseFileChooser("Select Database File");
        File selected = chooser.showOpenDialog(getScene().getWindow());
        return selected == null ? Optional.empty() : Optional.of(selected.toPath());
    }

    private Optional<Path> chooseNewDatabaseFile()
    {
        if (getScene() == null || getScene().getWindow() == null)
        {
            inspectorPane.show("Database creation unavailable", "The application window is not ready.");
            return Optional.empty();
        }

        FileChooser chooser = databaseFileChooser("Create New Database");
        chooser.setInitialFileName("sca-ledger.mv.db");
        File selected = chooser.showSaveDialog(getScene().getWindow());
        return selected == null ? Optional.empty() : Optional.of(selected.toPath());
    }

    private static FileChooser databaseFileChooser(String title)
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("H2 Database Files", "*.mv.db", "*.db"));
        return chooser;
    }

    static Path normalizeNewDatabasePath(Path path)
    {
        String raw = path.toString();
        return raw.toLowerCase().endsWith(".mv.db")
                ? path
                : Path.of(raw + ".mv.db");
    }

    private void updateActivePeriodLabel()
    {
        activePeriodLabel.setText("Active period: " + ActivePeriodContext.get());
    }

    private void updateActiveDatabaseLabel()
    {
        Path path = databaseSessionController.activeDatabasePath();
        activeDatabaseLabel.setText("DB: " + path.getFileName());
        activeDatabaseLabel.setTooltip(new javafx.scene.control.Tooltip(path.toAbsolutePath().toString()));
    }

    private NavigationPane.InspectorContext inspectorContext()
    {
        AppPanelId active = panelHost.activePanelId();
        String capabilities = databaseFailure == null
                ? (active == null ? "No active panel" : "Active panel: " + panelHost.getActiveTitle())
                : "Database unavailable: select, repair, or create a database";
        return new NavigationPane.InspectorContext(
                databaseSessionController.activeDatabasePath().toString(),
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
