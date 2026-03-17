package org.nonprofitbookkeeping.ui.customer.workspace;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.architecture.CustomerPanelId;
import org.nonprofitbookkeeping.ui.customer.UserRole;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CustomerPanelDefinitionTest component.
 */
public class CustomerPanelDefinitionTest
{
    @Test
    public void supervisorPanel_isDeniedToUser_andAllowedToSupervisor()
    {
        CustomerPanelDefinition panel = new CustomerPanelDefinition(
                CustomerPanelId.PERIOD_CLOSE,
                "Period Close",
                UserRole.SUPERVISOR,
                Set.of(PanelAction.CLOSE_PERIOD));

        assertFalse(panel.isAllowedFor(UserRole.USER));
        assertTrue(panel.isAllowedFor(UserRole.SUPERVISOR));
    }

    @Test
    public void userPanel_isAllowedToBothUserAndSupervisor()
    {
        CustomerPanelDefinition panel = new CustomerPanelDefinition(
                CustomerPanelId.JOURNAL_WORKBENCH,
                "Journal Workbench",
                UserRole.USER,
                Set.of(PanelAction.POST));

        assertTrue(panel.isAllowedFor(UserRole.USER));
        assertTrue(panel.isAllowedFor(UserRole.SUPERVISOR));
    }

    @Test
    public void constructor_rejectsBlankOrEmptyValues()
    {
        assertThrows(IllegalArgumentException.class,
                () -> new CustomerPanelDefinition(CustomerPanelId.JOURNAL_WORKBENCH, " ", UserRole.USER, Set.of(PanelAction.POST)));
        assertThrows(IllegalArgumentException.class,
                () -> new CustomerPanelDefinition(CustomerPanelId.JOURNAL_WORKBENCH, "title", UserRole.USER, Set.of()));
    }
}
