package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import org.nonprofitbookkeeping.persistence.DatabaseLocationService;

import java.util.Objects;
import java.util.Set;

/**
 * Production Administration workspace that makes preferences, database transfer,
 * company, and user administration reachable through one stable shell destination.
 */
public final class AdministrationPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TabPane tabs = new TabPane();
    private final SettingsPanel settings;
    private final DatabaseTransferPanel transfers;
    private final CompanyAdminPanel companies;
    private final UserAdminPanel users;
    private Runnable commandCapabilitiesChangedListener = () -> { };

    public AdministrationPanel()
    {
        this(
                new CompanySessionController(
                        MainWindow.sharedSessionState(),
                        UserAppStateStore.create(),
                        UiServiceRegistry::companyAdmin),
                DatabaseTransferActions.unavailable(() -> DatabaseLocationService.resolveDatabasePath(
                        MainWindow.sharedSessionState().databaseSelection().activeDatabasePath())),
                UserAppStateStore.create());
    }

    AdministrationPanel(CompanySessionController companyController)
    {
        this(
                companyController,
                DatabaseTransferActions.unavailable(() -> DatabaseLocationService.resolveDatabasePath(
                        MainWindow.sharedSessionState().databaseSelection().activeDatabasePath())),
                UserAppStateStore.create());
    }

    AdministrationPanel(
            CompanySessionController companyController,
            DatabaseTransferActions databaseTransferActions)
    {
        this(companyController, databaseTransferActions, UserAppStateStore.create());
    }

    AdministrationPanel(
            CompanySessionController companyController,
            DatabaseTransferActions databaseTransferActions,
            AppStateStore stateStore)
    {
        settings = new SettingsPanel(MainWindow.sharedSessionState(), companyController, stateStore);
        transfers = new DatabaseTransferPanel(databaseTransferActions);
        companies = new CompanyAdminPanel(companyController);
        users = new UserAdminPanel();
        tabs.setId("administrationTabs");
        tabs.getTabs().setAll(
                tab("Preferences", settings),
                tab("Database Transfer", transfers),
                tab("Company Admin", companies),
                tab("User Admin", users));
        tabs.getTabs().stream()
                .map(Tab::getUserData)
                .filter(AppPanel.class::isInstance)
                .map(AppPanel.class::cast)
                .forEach(panel -> panel.setCommandCapabilitiesChangedListener(
                        this::notifyCommandCapabilitiesChanged));
        tabs.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldTab, newTab) -> notifyCommandCapabilitiesChanged());
        root.setCenter(tabs);
    }

    private static Tab tab(String title, AppPanel panel)
    {
        Tab tab = new Tab(title, panel.root());
        tab.setClosable(false);
        tab.setUserData(panel);
        return tab;
    }

    @Override
    public String title()
    {
        return "Administration";
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public Set<AppCommand> commandCapabilities()
    {
        AppPanel selected = selectedPanel();
        return selected == null ? Set.of() : Set.copyOf(selected.commandCapabilities());
    }

    @Override
    public RunCommandResult executeCommand(AppCommand command)
    {
        AppPanel selected = selectedPanel();
        return selected == null
                ? new RunCommandResult(false, "Administration has no selected tab.")
                : selected.executeCommand(command);
    }

    @Override
    public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        commandCapabilitiesChangedListener = Objects.requireNonNull(listener, "listener");
        commandCapabilitiesChangedListener.run();
    }

    @Override
    public void onSave()
    {
        AppPanel selected = selectedPanel();
        if (selected != null)
        {
            selected.onSave();
        }
    }

    @Override
    public void onNew()
    {
        AppPanel selected = selectedPanel();
        if (selected != null)
        {
            selected.onNew();
        }
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return tabs.getTabs().stream()
                .map(Tab::getUserData)
                .filter(AppPanel.class::isInstance)
                .map(AppPanel.class::cast)
                .anyMatch(AppPanel::hasUnsavedChanges);
    }

    @Override
    public void onPanelShown()
    {
        AppPanel selected = selectedPanel();
        if (selected != null)
        {
            selected.onPanelShown();
        }
    }

    private AppPanel selectedPanel()
    {
        Tab selected = tabs.getSelectionModel().getSelectedItem();
        return selected != null && selected.getUserData() instanceof AppPanel panel ? panel : null;
    }

    private void notifyCommandCapabilitiesChanged()
    {
        commandCapabilitiesChangedListener.run();
    }

    SettingsPanel settingsForTests()
    {
        return settings;
    }

    DatabaseTransferPanel transfersForTests()
    {
        return transfers;
    }

    TabPane tabsForTests()
    {
        return tabs;
    }

    UserAdminPanel usersForTests()
    {
        return users;
    }
}
