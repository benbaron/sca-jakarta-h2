package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainWindowPrivilegeGatingTest
{
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
}
