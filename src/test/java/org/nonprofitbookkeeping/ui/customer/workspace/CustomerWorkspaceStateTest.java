package org.nonprofitbookkeeping.ui.customer.workspace;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.architecture.CustomerPanelId;
import org.nonprofitbookkeeping.ui.customer.UserRole;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CustomerWorkspaceStateTest component.
 */
public class CustomerWorkspaceStateTest
{
    @Test
    public void requiredLogin_rejectsOpeningPanelBeforeLogin()
    {
        CustomerWorkspaceState state = new CustomerWorkspaceState(LoginMode.REQUIRED);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> state.openPanel(CustomerPanelId.JOURNAL_WORKBENCH));

        assertTrue(ex.getMessage().contains("Login required"));
    }


    @Test
    public void requiredLogin_canAccessReturnsFalseBeforeLogin()
    {
        CustomerWorkspaceState state = new CustomerWorkspaceState(LoginMode.REQUIRED);

        assertFalse(state.canAccess(CustomerPanelId.JOURNAL_WORKBENCH));
        assertFalse(state.canAccess(CustomerPanelId.PERIOD_CLOSE));
    }

    @Test
    public void optionalLogin_defaultsToUserRole()
    {
        CustomerWorkspaceState state = new CustomerWorkspaceState(LoginMode.OPTIONAL);

        assertTrue(state.canAccess(CustomerPanelId.JOURNAL_WORKBENCH));
        assertFalse(state.canAccess(CustomerPanelId.PERIOD_CLOSE));
    }

    @Test
    public void userRole_cannotOpenSupervisorPanel()
    {
        CustomerWorkspaceState state = new CustomerWorkspaceState(LoginMode.REQUIRED);
        state.login("clerk", UserRole.USER);

        assertThrows(IllegalStateException.class,
                () -> state.openPanel(CustomerPanelId.APPROVAL_AUDIT));
    }

    @Test
    public void supervisor_canOpenSupervisorPanel_andHistoryTracksNavigation()
    {
        CustomerWorkspaceState state = new CustomerWorkspaceState(LoginMode.REQUIRED);
        state.login("exchequer", UserRole.SUPERVISOR);
        state.selectGroup("BARONY-DRAGON");

        state.openPanel(CustomerPanelId.JOURNAL_WORKBENCH);
        state.openPanel(CustomerPanelId.PERIOD_CLOSE);

        assertEquals("BARONY-DRAGON", state.activeGroupCode());
        assertEquals(CustomerPanelId.PERIOD_CLOSE, state.activePanelId());
        assertEquals(2, state.openHistory().size());
        assertEquals(CustomerPanelId.JOURNAL_WORKBENCH, state.openHistory().get(0));
    }

    @Test
    public void logout_clearsSessionAndHistory()
    {
        CustomerWorkspaceState state = new CustomerWorkspaceState(LoginMode.REQUIRED);
        state.login("exchequer", UserRole.SUPERVISOR);
        state.openPanel(CustomerPanelId.APPROVAL_AUDIT);

        state.logout();

        assertNull(state.currentUser());
        assertNull(state.currentRole());
        assertNull(state.activePanelId());
        assertTrue(state.openHistory().isEmpty());
    }
}
