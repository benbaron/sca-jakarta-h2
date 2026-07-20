package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;

/**
 * Production Administration workspace that makes preferences, company, and user
 * administration reachable through one stable shell destination.
 */
public final class AdministrationPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TabPane tabs = new TabPane();
    private final SettingsPanel settings;
    private final CompanyAdminPanel companies;
    private final UserAdminPanel users;

    public AdministrationPanel()
    {
        this(new CompanySessionController(
                MainWindow.sharedSessionState(),
                UserAppStateStore.create(),
                UiServiceRegistry::companyAdmin));
    }

    AdministrationPanel(CompanySessionController companyController)
    {
        settings = new SettingsPanel(MainWindow.sharedSessionState(), companyController);
        companies = new CompanyAdminPanel(companyController);
        users = new UserAdminPanel();
        tabs.setId("administrationTabs");
        tabs.getTabs().setAll(
                tab("Preferences", settings),
                tab("Company Admin", companies),
                tab("User Admin", users));
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

    SettingsPanel settingsForTests()
    {
        return settings;
    }
}
