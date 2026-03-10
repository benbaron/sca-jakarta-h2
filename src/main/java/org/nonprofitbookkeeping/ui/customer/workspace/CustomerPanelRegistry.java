package org.nonprofitbookkeeping.ui.customer.workspace;

import org.nonprofitbookkeeping.architecture.CustomerPanelId;
import org.nonprofitbookkeeping.ui.customer.UserRole;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Canonical registry of panels available in the customer workspace.
 */
public final class CustomerPanelRegistry
{
    private static final Map<CustomerPanelId, CustomerPanelDefinition> DEFINITIONS = initialize();

    private CustomerPanelRegistry()
    {
    }

    public static List<CustomerPanelDefinition> all()
    {
        return List.copyOf(DEFINITIONS.values());
    }

    public static CustomerPanelDefinition get(CustomerPanelId panelId)
    {
        CustomerPanelDefinition panel = DEFINITIONS.get(panelId);
        if (panel == null)
        {
            throw new IllegalArgumentException("Unknown panel: " + panelId);
        }
        return panel;
    }

    public static Optional<CustomerPanelDefinition> find(CustomerPanelId panelId)
    {
        return Optional.ofNullable(DEFINITIONS.get(panelId));
    }

    private static Map<CustomerPanelId, CustomerPanelDefinition> initialize()
    {
        Map<CustomerPanelId, CustomerPanelDefinition> definitions = new EnumMap<>(CustomerPanelId.class);

        register(definitions, new CustomerPanelDefinition(CustomerPanelId.GROUP_SELECTOR, "Group Selector", UserRole.USER,
                CustomerPanelDefinition.actions(PanelAction.SELECT_GROUP)));
        register(definitions, new CustomerPanelDefinition(CustomerPanelId.JOURNAL_WORKBENCH, "Journal Workbench", UserRole.USER,
                CustomerPanelDefinition.actions(PanelAction.CREATE, PanelAction.EDIT, PanelAction.POST)));
        register(definitions, new CustomerPanelDefinition(CustomerPanelId.OPEN_ITEMS_SCHEDULES, "Open Item Schedules", UserRole.USER,
                CustomerPanelDefinition.actions(PanelAction.RESOLVE, PanelAction.REVIEW_AUDIT)));
        register(definitions, new CustomerPanelDefinition(CustomerPanelId.EVENT_LIFECYCLE, "Event Lifecycle", UserRole.USER,
                CustomerPanelDefinition.actions(PanelAction.CREATE, PanelAction.EDIT, PanelAction.RESOLVE)));
        register(definitions, new CustomerPanelDefinition(CustomerPanelId.RECONCILIATION, "Bank Reconciliation", UserRole.USER,
                CustomerPanelDefinition.actions(PanelAction.RECONCILE, PanelAction.RESOLVE)));
        register(definitions, new CustomerPanelDefinition(CustomerPanelId.PERIOD_CLOSE, "Period Close", UserRole.SUPERVISOR,
                CustomerPanelDefinition.actions(PanelAction.CLOSE_PERIOD, PanelAction.REVIEW_AUDIT)));
        register(definitions, new CustomerPanelDefinition(CustomerPanelId.IMPORT_EXPORT, "Import / Export", UserRole.SUPERVISOR,
                CustomerPanelDefinition.actions(PanelAction.IMPORT, PanelAction.EXPORT)));
        register(definitions, new CustomerPanelDefinition(CustomerPanelId.APPROVAL_AUDIT, "Approval & Audit", UserRole.SUPERVISOR,
                CustomerPanelDefinition.actions(PanelAction.APPROVE, PanelAction.REVIEW_AUDIT)));

        return Map.copyOf(definitions);
    }

    private static void register(Map<CustomerPanelId, CustomerPanelDefinition> definitions, CustomerPanelDefinition panel)
    {
        if (definitions.put(panel.panelId(), panel) != null)
        {
            throw new IllegalStateException("Duplicate panel definition: " + panel.panelId());
        }
    }
}
