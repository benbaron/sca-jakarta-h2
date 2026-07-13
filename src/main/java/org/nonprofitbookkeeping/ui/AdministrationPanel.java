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
    private final SettingsPanel settings = new SettingsPanel();
    private final CompanyAdminPanel companies = new CompanyAdminPanel();
    private final UserAdminPanel users = new UserAdminPanel();

    public AdministrationPanel()
    {
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
        AppPanel selected = selectedPanel();
        return selected != null && selected.hasUnsavedChanges();
    }

    private AppPanel selectedPanel()
    {
        Tab selected = tabs.getSelectionModel().getSelectedItem();
        return selected != null && selected.getUserData() instanceof AppPanel panel ? panel : null;
    }
}
