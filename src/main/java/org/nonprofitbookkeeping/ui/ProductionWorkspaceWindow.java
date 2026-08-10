package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.nonprofitbookkeeping.service.SampleCompanyService;
import org.nonprofitbookkeeping.service.CompanyView;

import org.nonprofitbookkeeping.model.WorkspaceDividerState;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Production JavaFX workspace shell.
 */
public class ProductionWorkspaceWindow extends BorderPane
{
    private final AppStateStore stateStore;
    private final WorkspaceServices workspaceServices;
    private final WorkspaceContext workspaceContext;
    private final DatabaseSessionController databaseSessionController;
    private final CompanySessionController companySessionController;
    private final PanelHost panelHost;
    private final InspectorPane inspectorPane = new InspectorPane();
    private final NavigationPane navigationPane;
    private final SplitPane workspace = new SplitPane();
    private final Label activePanelLabel = new Label();
    private final Label commandStatusLabel = new Label();
    private final Label activePeriodLabel = new Label();
    private final Label activeDatabaseLabel = new Label();
    private final ComboBox<CompanyView> activeCompanySelector = new ComboBox<>();
    private MenuItem newMenuItem;
    private MenuItem saveMenuItem;
    private Button newButton;
    private Button saveButton;
    private CloseAllTabsPrompt closeAllTabsPrompt = this::confirmCloseAllTabs;
    private DatabaseChangePrompt databaseChangePrompt = this::confirmDatabaseChange;
    private RuntimeException databaseFailure;
    private WorkspaceDividerState rememberedDividerState;
    private boolean restoringDividers;
    private boolean updatingCompanySelector;
    private boolean databaseSwitchInProgress;

    public ProductionWorkspaceWindow()
    {
        this(UserAppStateStore.create(), UiServiceRegistry::prepareDatabaseConnection);
    }

    ProductionWorkspaceWindow(
            AppStateStore stateStore,
            DatabaseSessionController.Connector connector)
    {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.stateStore.loadPreferences().ifPresent(
                MainWindow.sharedSessionState()::setPreferences);
        this.workspaceServices = WorkspaceServicesFactory.create(
                MainWindow.sharedSessionState(),
                stateStore,
                connector);
        this.workspaceContext = workspaceServices.context();
        this.databaseSessionController = workspaceServices.databaseSessionController();
        this.companySessionController = workspaceServices.companySessionController();
        this.panelHost = new PanelHost(workspaceServices.panelFactory());
        this.companySessionController.setChangeGuard(this::confirmCompanyChange);
        getStyleClass().add("production-workspace");
        MainWindow.sharedSessionState().onPreferencesChanged(this::applyPreferences);
        applyPreferences(MainWindow.sharedSessionState().preferences());

        try
        {
            databaseSessionController.restorePersistedSelection();
            companySessionController.restoreAuthoritativeSelection();
        }
        catch (RuntimeException ex)
        {
            databaseFailure = ex;
            workspaceContext.setDatabaseFailure(ex);
            System.err.println("[NPBK] Could not restore persisted database selection: "
                    + ex.getMessage());
        }

        navigationPane = new NavigationPane(
                this::openPanel,
                inspectorPane::show,
                this::inspectorContext);
        DrillThroughCoordinator.configureOpener(this::openPanel);

        workspaceContext.activePeriodDateProperty().addListener(
                (observable, oldDate, newDate) -> updateActivePeriodLabel());
        workspaceContext.activeDatabasePathProperty().addListener(
                (observable, oldPath, newPath) -> updateActiveDatabaseLabel());
        workspaceContext.activeCompanyCodeProperty().addListener(
                (observable, oldCode, newCode) -> activeCompanyChanged(oldCode, newCode));

        setTop(buildTopChrome());
        setCenter(buildWorkspace());
        setBottom(buildStatusBar());
        panelHost.setCommandCapabilitiesChangedListener(this::refreshGlobalCommandState);

        updateActiveDatabaseLabel();
        refreshActiveCompanySelector();
        showDashboardOrRecovery(databaseFailure);
    }

    public void openPanel(AppPanelId panelId)
    {
        UiDebug.log("workspace", "openPanel requested for " + panelId + ".");
        if (databaseFailure != null && panelId != AppPanelId.DASHBOARD)
        {
            UiDebug.log("workspace", "openPanel blocked by database failure for " + panelId + ".");
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
            UiDebug.log("workspace", "openPanel completed for " + panelId
                    + "; active panel is " + panelHost.activePanelId() + ".");
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

    public AppPanel.RunCommandResult saveActivePanel()
    {
        AppPanel.RunCommandResult result = panelHost.saveActive();
        if (result.handled())
        {
            stateStore.saveDatabaseSelection(MainWindow.sharedSessionState().databaseSelection());
        }
        presentCommandResult(result);
        return result;
    }

    public AppPanel.RunCommandResult newItemInActivePanel()
    {
        AppPanel.RunCommandResult result = panelHost.newItemActive();
        presentCommandResult(result);
        return result;
    }

    public void closeInspector()
    {
        setInspectorVisible(false);
    }

    public AppPanel.RunCommandResult closeAllWorkspaceTabs()
    {
        List<String> dirtyTitles = panelHost.dirtyClosablePanelTitles();
        if (!dirtyTitles.isEmpty() && !closeAllTabsPrompt.confirmDiscard(dirtyTitles))
        {
            return new AppPanel.RunCommandResult(false, "Close All Tabs cancelled; unsaved edits remain open.");
        }

        int closed = panelHost.closeAllClosableTabs();
        activePanelLabel.setText("Workspace: " + panelHost.getActiveTitle());
        return new AppPanel.RunCommandResult(true, "Closed " + closed + " non-dashboard tab(s). Dashboard remains open.");
    }

    void closeAllTabsPromptForTests(CloseAllTabsPrompt prompt)
    {
        closeAllTabsPrompt = Objects.requireNonNull(prompt, "prompt");
    }

    public AppPanel.RunCommandResult executeCommand(AppCommand command)
    {
        Objects.requireNonNull(command, "command");
        AppPanel.RunCommandResult result = switch (command)
        {
            case NEW_ACTIVE -> panelHost.newItemActive();
            case SAVE_ACTIVE -> panelHost.saveActive();
            case CLOSE_ALL_TABS -> closeAllWorkspaceTabs();
            case CLOSE_INSPECTOR ->
            {
                closeInspector();
                yield new AppPanel.RunCommandResult(true, "Closed the inspector.");
            }
            case POST_VALIDATE -> panelHost.executeActive(command);
        };
        if (command == AppCommand.SAVE_ACTIVE && result.handled())
        {
            stateStore.saveDatabaseSelection(MainWindow.sharedSessionState().databaseSelection());
        }
        presentCommandResult(result);
        return result;
    }

    LocalDate activePeriodDate()
    {
        return workspaceContext.activePeriodDate();
    }

    void setActivePeriodDate(LocalDate date)
    {
        ActivePeriodContext.set(date);
    }

    void setActivePeriod(YearMonth period)
    {
        setActivePeriodDate(ActivePeriodContext.periodStartFor(
                period,
                MainWindow.sharedSessionState().preferences().periodStartDayOfMonth()));
    }

    static List<YearMonth> periodChoicesFor(LocalDate anchorDate)
    {
        YearMonth anchor = YearMonth.from(Objects.requireNonNull(anchorDate, "anchorDate"));
        java.util.ArrayList<YearMonth> choices = new java.util.ArrayList<>();
        for (int offset = -12; offset <= 12; offset++)
        {
            choices.add(anchor.plusMonths(offset));
        }
        return choices;
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
        return workspaceContext.activeDatabasePath();
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

    void databaseChangePromptForTests(DatabaseChangePrompt prompt)
    {
        databaseChangePrompt = Objects.requireNonNull(prompt, "prompt");
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
        selectDatabase.setOnAction(event -> executeDatabaseRecoveryCommand(DatabaseRecoveryCommand.SELECT_EXISTING));
        MenuItem createDatabase = new MenuItem("Create New Database…");
        createDatabase.setOnAction(event -> executeDatabaseRecoveryCommand(DatabaseRecoveryCommand.CREATE_NEW));
        MenuItem repairDatabase = new MenuItem("Retry / Repair Current Database");
        repairDatabase.setOnAction(event -> executeDatabaseRecoveryCommand(DatabaseRecoveryCommand.RETRY_CURRENT));
        MenuItem sampleCompany = new MenuItem("Create / Refresh Sample Company Data");
        sampleCompany.setOnAction(event -> createOrRefreshSampleCompany());
        GlobalCommandRegistry.Definition newDefinition =
                GlobalCommandRegistry.definition(AppCommand.NEW_ACTIVE);
        newMenuItem = new MenuItem(newDefinition.label());
        newMenuItem.setAccelerator(newDefinition.accelerator());
        newMenuItem.setOnAction(event -> executeCommand(AppCommand.NEW_ACTIVE));
        GlobalCommandRegistry.Definition saveDefinition =
                GlobalCommandRegistry.definition(AppCommand.SAVE_ACTIVE);
        saveMenuItem = new MenuItem(saveDefinition.label());
        saveMenuItem.setAccelerator(saveDefinition.accelerator());
        saveMenuItem.setOnAction(event -> executeCommand(AppCommand.SAVE_ACTIVE));
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
                sampleCompany,
                new SeparatorMenuItem(),
                newMenuItem,
                saveMenuItem,
                exit);

        Menu workspaceMenu = new Menu("Workspace");
        GlobalCommandRegistry.Definition closeDefinition =
                GlobalCommandRegistry.definition(AppCommand.CLOSE_ALL_TABS);
        MenuItem closeAllTabs = new MenuItem(closeDefinition.label());
        closeAllTabs.setAccelerator(closeDefinition.accelerator());
        closeAllTabs.setOnAction(event -> executeCommand(AppCommand.CLOSE_ALL_TABS));
        workspaceMenu.getItems().add(closeAllTabs);

        Menu view = new Menu("View");
        MenuItem navigation = new MenuItem("Toggle Navigation");
        navigation.setOnAction(event -> setNavigationVisible(!workspace.getItems().contains(navigationPane)));
        MenuItem inspector = new MenuItem("Toggle Inspector");
        inspector.setOnAction(event -> setInspectorVisible(!workspace.getItems().contains(inspectorPane)));
        view.getItems().addAll(navigation, inspector);

        Menu destinationsMenu = new Menu("Destinations");
        MenuItem dashboard = new MenuItem("Dashboard");
        dashboard.setOnAction(event -> openPanel(AppPanelId.DASHBOARD));
        MenuItem journal = new MenuItem("Journal");
        journal.setOnAction(event -> openPanel(AppPanelId.JOURNAL_PANE));
        MenuItem administration = new MenuItem("Administration");
        administration.setOnAction(event -> openPanel(AppPanelId.SETTINGS));
        MenuItem help = new MenuItem("Help");
        help.setOnAction(event -> openPanel(AppPanelId.HELP));
        destinationsMenu.getItems().addAll(dashboard, journal, administration, help);

        return new MenuBar(file, workspaceMenu, destinationsMenu, view);
    }

    private ToolBar buildToolBar()
    {
        newButton = new Button(GlobalCommandRegistry.label(AppCommand.NEW_ACTIVE));
        newButton.setOnAction(event -> executeCommand(AppCommand.NEW_ACTIVE));

        saveButton = new Button(GlobalCommandRegistry.label(AppCommand.SAVE_ACTIVE));
        saveButton.setOnAction(event -> executeCommand(AppCommand.SAVE_ACTIVE));

        Button navigationButton = new Button("Navigation");
        navigationButton.setOnAction(event -> setNavigationVisible(!workspace.getItems().contains(navigationPane)));

        Button inspectorButton = new Button("Inspector");
        inspectorButton.setOnAction(event -> setInspectorVisible(!workspace.getItems().contains(inspectorPane)));

        ComboBox<YearMonth> periodSelector = new ComboBox<>();
        periodSelector.getItems().setAll(periodChoicesFor(workspaceContext.activePeriodDate()));
        periodSelector.getSelectionModel().select(YearMonth.from(workspaceContext.activePeriodDate()));
        periodSelector.setPromptText("Active period");

        Button setPeriodButton = new Button("Set Active Period");
        setPeriodButton.setOnAction(event ->
        {
            if (periodSelector.getValue() != null)
            {
                setActivePeriod(periodSelector.getValue());
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        updateActivePeriodLabel();
        activeCompanySelector.setId("activeCompanySelector");
        activeCompanySelector.setPromptText("Active company");
        activeCompanySelector.setOnShowing(event -> refreshActiveCompanySelector());
        activeCompanySelector.setOnAction(event -> selectCompanyFromToolbar());
        return new ToolBar(
                newButton,
                saveButton,
                new Separator(),
                navigationButton,
                inspectorButton,
                new Separator(),
                new Label("Period:"),
                periodSelector,
                setPeriodButton,
                spacer,
                new Label("Company:"),
                activeCompanySelector,
                new Separator(),
                activeDatabaseLabel,
                new Separator(),
                activePeriodLabel);
    }

    private SplitPane buildWorkspace()
    {
        workspace.getItems().setAll(navigationPane, panelHost, inspectorPane);
        rememberedDividerState = (MainWindow.sharedSessionState().preferences().rememberWindowState()
                ? stateStore.loadWorkspaceDividers()
                : Optional.<WorkspaceDividerState>empty())
                .orElseGet(() -> WorkspaceShellLayoutPolicy
                        .forWidth(WorkspaceShellLayoutPolicy.FALLBACK_WORKSPACE_WIDTH)
                        .dividerState());
        restoreDividerPositions();
        workspace.getDividers().forEach(divider -> divider.positionProperty().addListener(
                (observable, oldValue, newValue) -> rememberCurrentDividerPositions()));
        BorderPane.setMargin(workspace, new Insets(8));
        return workspace;
    }

    private HBox buildStatusBar()
    {
        activePanelLabel.getStyleClass().add("status-label");
        commandStatusLabel.getStyleClass().add("status-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox statusBar = new HBox(activePanelLabel, spacer, commandStatusLabel);
        statusBar.setPadding(new Insets(4, 10, 6, 10));
        statusBar.getStyleClass().add("status-bar");
        return statusBar;
    }

    private void refreshGlobalCommandState()
    {
        if (newMenuItem == null || saveMenuItem == null || newButton == null || saveButton == null)
        {
            return;
        }
        java.util.Set<AppCommand> capabilities = panelHost.activeCommandCapabilities();
        updateCommandControl(
                AppCommand.NEW_ACTIVE,
                capabilities,
                newMenuItem,
                newButton);
        updateCommandControl(
                AppCommand.SAVE_ACTIVE,
                capabilities,
                saveMenuItem,
                saveButton);
    }

    private void updateCommandControl(
            AppCommand command,
            java.util.Set<AppCommand> capabilities,
            MenuItem menuItem,
            Button button)
    {
        boolean supported = capabilities.contains(command);
        menuItem.setDisable(!supported);
        button.setDisable(!supported);
        String explanation = supported
                ? GlobalCommandRegistry.label(command) + " is available in " + panelHost.getActiveTitle() + "."
                : GlobalCommandRegistry.label(command) + " is not available in " + panelHost.getActiveTitle() + ".";
        menuItem.setText(supported
                ? GlobalCommandRegistry.label(command)
                : GlobalCommandRegistry.label(command) + " — not available in " + panelHost.getActiveTitle());
        button.setTooltip(new Tooltip(explanation));
    }

    private void presentCommandResult(AppPanel.RunCommandResult result)
    {
        commandStatusLabel.setText(result.message());
        refreshGlobalCommandState();
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
            workspaceContext.setDatabaseFailure(null);
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
        workspaceContext.setDatabaseFailure(failure);
        DatabaseRecoveryPanel recoveryPanel = new DatabaseRecoveryPanel(
                workspaceContext.activeDatabasePath(),
                failure,
                this::executeDatabaseRecoveryCommand);
        panelHost.showReplacement(AppPanelId.DASHBOARD, recoveryPanel);
        activePanelLabel.setText("Workspace: Dashboard — database attention required");
        inspectorPane.show(
                "Database attention required",
                DatabaseRecoveryPanel.safeMessage(failure));
    }

    private boolean confirmCloseAllTabs(List<String> dirtyTitles)
    {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Close All Tabs");
        alert.setHeaderText("Discard unsaved edits?");
        alert.setContentText("The following workspace tab(s) report unsaved edits: "
                + String.join(", ", dirtyTitles)
                + ". Choose OK to discard those edits and close all non-Dashboard tabs.");
        if (getScene() != null && getScene().getWindow() != null)
        {
            alert.initOwner(getScene().getWindow());
        }
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private boolean confirmCompanyChange(String currentCompanyCode, String requestedCompanyCode)
    {
        List<String> dirtyTitles = panelHost.dirtyPanelTitles();
        if (dirtyTitles.isEmpty())
        {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Change active company");
        alert.setHeaderText("Discard unsaved edits before changing companies?");
        alert.setContentText("Changing from " + currentCompanyCode + " to " + requestedCompanyCode
                + " will recreate open workspaces. Unsaved edits are present in: "
                + String.join(", ", dirtyTitles) + ".");
        if (getScene() != null && getScene().getWindow() != null)
        {
            alert.initOwner(getScene().getWindow());
        }
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private boolean confirmDatabaseChange(Path source, Path target, List<String> dirtyTitles)
    {
        if (dirtyTitles.isEmpty())
        {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Change database");
        alert.setHeaderText("Discard unsaved edits before changing databases?");
        alert.setContentText("Changing from " + source.toAbsolutePath() + " to "
                + target.toAbsolutePath() + " will recreate open workspaces. Unsaved edits are present in: "
                + String.join(", ", dirtyTitles) + ".");
        if (getScene() != null && getScene().getWindow() != null)
        {
            alert.initOwner(getScene().getWindow());
        }
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    @FunctionalInterface
    interface DatabaseChangePrompt
    {
        boolean confirmDiscard(Path source, Path target, List<String> dirtyTitles);
    }

    @FunctionalInterface
    interface CloseAllTabsPrompt
    {
        boolean confirmDiscard(List<String> dirtyTitles);
    }

    void executeDatabaseRecoveryCommand(DatabaseRecoveryCommand command)
    {
        switch (Objects.requireNonNull(command, "command"))
        {
            case RETRY_CURRENT -> repairCurrentDatabase();
            case SELECT_EXISTING -> selectDatabaseFile();
            case CREATE_NEW -> createNewDatabase();
        }
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

    private void createOrRefreshSampleCompany()
    {
        try
        {
            SampleCompanyService.SampleCompanySummary summary = UiServiceRegistry.sampleCompany().createOrRefresh();
            panelHost.refreshOpenPanels();
            inspectorPane.show(
                    "Sample company ready",
                    "Created or refreshed explicit sample data in the active database. "
                            + "Chart ID " + summary.chartId()
                            + " now has " + summary.accountCount() + " sample account(s), "
                            + summary.fundCount() + " active fund(s), and transaction editor reference choices.");
            activePanelLabel.setText("Workspace: sample company data refreshed");
        }
        catch (RuntimeException ex)
        {
            inspectorPane.show(
                    "Sample company failed",
                    UiErrors.safeMessage(ex));
        }
    }

    private void connectDatabase(Path databaseFile)
    {
        Path source = databaseSessionController.activeDatabasePath();
        Path target = org.nonprofitbookkeeping.persistence.DatabaseLocationService.resolveDatabasePath(
                Objects.requireNonNull(databaseFile, "databaseFile").toString());
        List<String> dirtyTitles = panelHost.dirtyPanelTitles();
        if (!dirtyTitles.isEmpty() && !databaseChangePrompt.confirmDiscard(source, target, dirtyTitles))
        {
            inspectorPane.show(
                    "Database switch cancelled",
                    "Still connected to " + source.toAbsolutePath()
                            + ". Target was " + target.toAbsolutePath() + ".");
            return;
        }

        RuntimeException previousFailure = databaseFailure;
        try
        {
            databaseSwitchInProgress = true;
            DatabaseSessionController.ConnectionResult result = databaseSessionController.connect(target);
            databaseFailure = null;
            workspaceContext.setDatabaseFailure(null);
            refreshActiveCompanySelector();
            panelHost.refreshOpenPanels();
            updateActiveDatabaseLabel();
            inspectorPane.show(
                    "Database connected",
                    "Connected database: " + result.activeDatabasePath().toAbsolutePath()
                            + ". Active company: " + result.activeCompanyCode() + ".");
            showDashboardOrRecovery(null);
        }
        catch (RuntimeException ex)
        {
            databaseFailure = previousFailure;
            workspaceContext.setDatabaseFailure(previousFailure);
            updateActiveDatabaseLabel();
            refreshActiveCompanySelector();
            inspectorPane.show(
                    "Database switch failed",
                    "Still connected to " + source.toAbsolutePath() + ". Target "
                            + target.toAbsolutePath() + " was not activated. " + UiErrors.safeMessage(ex));
        }
        finally
        {
            databaseSwitchInProgress = false;
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
        activePeriodLabel.setText("Active period: "
                + YearMonth.from(workspaceContext.activePeriodDate())
                + " starts " + workspaceContext.activePeriodDate());
    }

    private void updateActiveDatabaseLabel()
    {
        Path path = workspaceContext.activeDatabasePath();
        activeDatabaseLabel.setText("DB: " + path.getFileName());
        activeDatabaseLabel.setTooltip(new javafx.scene.control.Tooltip(path.toAbsolutePath().toString()));
    }

    private void refreshActiveCompanySelector()
    {
        try
        {
            updatingCompanySelector = true;
            List<CompanyView> activeCompanies = companySessionController.listActiveCompanies();
            activeCompanySelector.getItems().setAll(activeCompanies);
            activeCompanies.stream()
                    .filter(company -> company.code().equalsIgnoreCase(workspaceContext.activeCompanyCode()))
                    .findFirst()
                    .ifPresent(activeCompanySelector::setValue);
        }
        catch (RuntimeException ex)
        {
            activeCompanySelector.getItems().clear();
            activeCompanySelector.setPromptText("Company unavailable");
        }
        finally
        {
            updatingCompanySelector = false;
        }
    }

    private void selectCompanyFromToolbar()
    {
        if (updatingCompanySelector || activeCompanySelector.getValue() == null)
        {
            return;
        }
        CompanySessionController.SelectionResult result = companySessionController.select(
                activeCompanySelector.getValue().code());
        if (!result.selected())
        {
            inspectorPane.show("Company selection failed", result.message());
            refreshActiveCompanySelector();
        }
    }

    private void activeCompanyChanged(String oldCode, String newCode)
    {
        refreshActiveCompanySelector();
        if (!databaseSwitchInProgress
                && oldCode != null
                && !oldCode.equalsIgnoreCase(newCode)
                && panelHost.openPanelCount() > 0)
        {
            panelHost.refreshOpenPanels();
            activePanelLabel.setText("Workspace: " + panelHost.getActiveTitle());
        }
    }

    private NavigationPane.InspectorContext inspectorContext()
    {
        AppPanelId active = panelHost.activePanelId();
        String capabilities = databaseFailure == null
                ? (active == null ? "No active panel" : "Active panel: " + panelHost.getActiveTitle())
                : "Database unavailable: select, repair, or create a database";
        return new NavigationPane.InspectorContext(
                workspaceContext.activeCompanyCode(),
                workspaceContext.activePeriodDate().toString(),
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
        double width = workspace.getWidth() > 0.0
                ? workspace.getWidth()
                : WorkspaceShellLayoutPolicy.FALLBACK_WORKSPACE_WIDTH;
        WorkspaceDividerState safe = WorkspaceShellLayoutPolicy.safeDividerState(width, rememberedDividerState);
        restoringDividers = true;
        try
        {
            if (workspace.getItems().size() == 3)
            {
                workspace.setDividerPositions(safe.leftDividerPosition(), safe.rightDividerPosition());
            }
            else if (workspace.getItems().size() == 2)
            {
                if (workspace.getItems().contains(navigationPane))
                {
                    workspace.setDividerPositions(safe.leftDividerPosition());
                }
                else
                {
                    workspace.setDividerPositions(safe.rightDividerPosition());
                }
            }
        }
        finally
        {
            restoringDividers = false;
        }
    }

    private void rememberCurrentDividerPositions()
    {
        if (restoringDividers
                || workspace.getItems().size() != 3
                || !MainWindow.sharedSessionState().preferences().rememberWindowState())
        {
            return;
        }
        double[] positions = workspace.getDividerPositions();
        if (positions.length != 2)
        {
            return;
        }
        WorkspaceDividerState candidate;
        try
        {
            candidate = new WorkspaceDividerState(positions[0], positions[1]);
        }
        catch (IllegalArgumentException ex)
        {
            return;
        }
        double width = workspace.getWidth() > 0.0
                ? workspace.getWidth()
                : WorkspaceShellLayoutPolicy.FALLBACK_WORKSPACE_WIDTH;
        WorkspaceDividerState safe = WorkspaceShellLayoutPolicy.safeDividerState(width, candidate);
        if (safe.equals(candidate))
        {
            rememberedDividerState = candidate;
            stateStore.saveWorkspaceDividers(candidate);
        }
    }

    private void applyPreferences(org.nonprofitbookkeeping.model.AppPreferencesState preferences)
    {
        getStyleClass().removeAll("theme-light", "theme-dark", "theme-system");
        switch (preferences.themePreference())
        {
            case LIGHT -> getStyleClass().add("theme-light");
            case DARK -> getStyleClass().add("theme-dark");
            case SYSTEM_DEFAULT -> getStyleClass().add("theme-system");
        }
    }
}
