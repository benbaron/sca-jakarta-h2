package org.nonprofitbookkeeping.architecture;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CustomerUiPanelCatalogTest component.
 */
public class CustomerUiPanelCatalogTest
{
    @Test
    public void defaultPanels_coversEveryPanelIdExactlyOnce()
    {
        var panels = CustomerUiPanelCatalog.defaultPanels();
        assertEquals(CustomerPanelId.values().length, panels.size());

        Set<CustomerPanelId> uniqueIds = panels.stream()
                .map(CustomerPanelBlueprint::panelId)
                .collect(Collectors.toSet());

        assertEquals(CustomerPanelId.values().length, uniqueIds.size());
    }

    @Test
    public void defaultPanels_haveTitlesAndPurpose()
    {
        var panels = CustomerUiPanelCatalog.defaultPanels();

        for (CustomerPanelBlueprint panel : panels)
        {
            assertNotNull(panel.panelId());
            assertFalse(panel.title().isBlank());
            assertFalse(panel.purpose().isBlank());
        }
    }

    @Test
    public void panelBlueprint_rejectsBlankValues()
    {
        assertThrows(IllegalArgumentException.class,
                () -> new CustomerPanelBlueprint(CustomerPanelId.GROUP_SELECTOR, " ", "valid"));
        assertThrows(IllegalArgumentException.class,
                () -> new CustomerPanelBlueprint(CustomerPanelId.GROUP_SELECTOR, "valid", " "));
    }

    @Test
    public void panelCatalog_containsCriticalAccountingPanels()
    {
        var ids = CustomerUiPanelCatalog.defaultPanels().stream()
                .map(CustomerPanelBlueprint::panelId)
                .collect(Collectors.toSet());

        assertTrue(ids.contains(CustomerPanelId.JOURNAL_WORKBENCH));
        assertTrue(ids.contains(CustomerPanelId.OPEN_ITEMS_SCHEDULES));
        assertTrue(ids.contains(CustomerPanelId.PERIOD_CLOSE));
        assertTrue(ids.contains(CustomerPanelId.RECONCILIATION));
    }
}
