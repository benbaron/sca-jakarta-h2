package org.nonprofitbookkeeping.ui.customer;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.architecture.CustomerPanelId;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CustomerPanelDesignServiceTest component.
 */
public class CustomerPanelDesignServiceTest
{
    @Test
    public void descriptors_coverAllPanelIdsExactlyOnce()
    {
        var descriptors = CustomerPanelDesignService.descriptors();

        assertEquals(CustomerPanelId.values().length, descriptors.size());
        Set<CustomerPanelId> ids = descriptors.stream().map(CustomerPanelDescriptor::panelId).collect(Collectors.toSet());
        assertEquals(CustomerPanelId.values().length, ids.size());
    }

    @Test
    public void periodCloseAndAudit_requireSupervisorRole()
    {
        var byId = CustomerPanelDesignService.descriptors().stream()
                .collect(Collectors.toMap(CustomerPanelDescriptor::panelId, d -> d));

        assertEquals(UserRole.SUPERVISOR, byId.get(CustomerPanelId.PERIOD_CLOSE).minimumRole());
        assertEquals(UserRole.SUPERVISOR, byId.get(CustomerPanelId.APPROVAL_AUDIT).minimumRole());
    }

    @Test
    public void eachPanel_hasAtLeastOneWorkflow()
    {
        for (CustomerPanelDescriptor descriptor : CustomerPanelDesignService.descriptors())
        {
            assertFalse(descriptor.workflows().isEmpty());
            assertTrue(descriptor.workflows().stream().allMatch(w -> !w.isBlank()));
        }
    }

    @Test
    public void descriptor_validation_rejectsInvalidInput()
    {
        assertThrows(IllegalArgumentException.class,
                () -> new CustomerPanelDescriptor(CustomerPanelId.GROUP_SELECTOR, "", UserRole.USER, java.util.List.of("ok")));
        assertThrows(IllegalArgumentException.class,
                () -> new CustomerPanelDescriptor(CustomerPanelId.GROUP_SELECTOR, "x", UserRole.USER, java.util.List.of()));
    }
}
