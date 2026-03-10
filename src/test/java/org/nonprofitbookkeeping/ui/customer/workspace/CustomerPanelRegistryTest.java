package org.nonprofitbookkeeping.ui.customer.workspace;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.architecture.CustomerPanelId;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class CustomerPanelRegistryTest
{
    @Test
    public void all_includesEveryPanelExactlyOnce()
    {
        var all = CustomerPanelRegistry.all();

        assertEquals(CustomerPanelId.values().length, all.size());
        Set<CustomerPanelId> ids = all.stream().map(CustomerPanelDefinition::panelId).collect(Collectors.toSet());
        assertEquals(CustomerPanelId.values().length, ids.size());
    }

    @Test
    public void periodClose_hasClosePeriodAction()
    {
        CustomerPanelDefinition closePanel = CustomerPanelRegistry.get(CustomerPanelId.PERIOD_CLOSE);
        assertTrue(closePanel.actions().contains(PanelAction.CLOSE_PERIOD));
    }

    @Test
    public void find_returnsPresentForKnownPanel()
    {
        assertTrue(CustomerPanelRegistry.find(CustomerPanelId.RECONCILIATION).isPresent());
    }
}
