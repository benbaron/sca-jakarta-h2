package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MainWindowPrivilegeGatingTest component.
 */
class MainWindowPrivilegeGatingTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    void requiredPrivilegeForPanel_mapsExpectedSensitivePanels()
    {
        assertEquals(UserPrivilegeLevel.MANAGER, MainWindow.requiredPrivilegeForPanel(AppPanelId.APPROVAL_AUDIT));
        assertEquals(UserPrivilegeLevel.ADMIN, MainWindow.requiredPrivilegeForPanel(AppPanelId.SETTINGS));
        assertEquals(UserPrivilegeLevel.ACCOUNTANT, MainWindow.requiredPrivilegeForPanel(AppPanelId.TXN_EDITOR));
    }

    @Test
    void canAccessPanelForPrivilege_enforcesMinimumRole()
    {
        assertFalse(MainWindow.canAccessPanelForPrivilege(AppPanelId.DIAGNOSTICS, UserPrivilegeLevel.MANAGER));
        assertTrue(MainWindow.canAccessPanelForPrivilege(AppPanelId.DIAGNOSTICS, UserPrivilegeLevel.ADMIN));
        assertFalse(MainWindow.canAccessPanelForPrivilege(AppPanelId.PERIOD_CLOSE_RUNS, UserPrivilegeLevel.ACCOUNTANT));
        assertTrue(MainWindow.canAccessPanelForPrivilege(AppPanelId.PERIOD_CLOSE_RUNS, UserPrivilegeLevel.MANAGER));
    }

    @Test
    void gatedMenuAndToolbarControls_toggleByPrivilege()
    {
        MainWindow.resetSessionForTests(
                new AppPreferencesState(UiThemePreference.SYSTEM_DEFAULT, false, true, UserPrivilegeLevel.VIEWER),
                new MultiCompanyState("BARONY-RED", List.of("BARONY-RED")));

        MainWindow window = FxTestSupport.onFx(MainWindow::new);
        Map<String, Boolean> viewerMenu = FxTestSupport.onFx(window::gatedToolItemDisabledStatesForTests);
        Map<String, Boolean> viewerToolbar = FxTestSupport.onFx(window::gatedToolbarDisabledStatesForTests);

        assertTrue(viewerMenu.get("Approval Audit…"));
        assertTrue(viewerMenu.get("Diagnostics…"));
        assertTrue(viewerMenu.get("Preferences…"));
        assertTrue(viewerToolbar.get("New"));
        assertTrue(viewerToolbar.get("Save"));

        FxTestSupport.onFx(() -> {
            window.applyPreferences(new AppPreferencesState(
                    UiThemePreference.SYSTEM_DEFAULT, false, true, UserPrivilegeLevel.ADMIN));
            return null;
        });

        Map<String, Boolean> adminMenu = FxTestSupport.onFx(window::gatedToolItemDisabledStatesForTests);
        Map<String, Boolean> adminToolbar = FxTestSupport.onFx(window::gatedToolbarDisabledStatesForTests);

        assertFalse(adminMenu.get("Approval Audit…"));
        assertFalse(adminMenu.get("Diagnostics…"));
        assertFalse(adminMenu.get("Preferences…"));
        assertFalse(adminToolbar.get("New"));
        assertFalse(adminToolbar.get("Save"));
    }
}
