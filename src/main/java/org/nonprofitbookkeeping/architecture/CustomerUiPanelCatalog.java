package org.nonprofitbookkeeping.architecture;

import java.util.List;

/**
 * Architecture-level customer panel design for the stateful accounting workflow.
 */
public final class CustomerUiPanelCatalog
{
    private CustomerUiPanelCatalog()
    {
    }

    public static List<CustomerPanelBlueprint> defaultPanels()
    {
        return List.of(
                new CustomerPanelBlueprint(CustomerPanelId.GROUP_SELECTOR, "Group Selector", "Choose branch identity and active books."),
                new CustomerPanelBlueprint(CustomerPanelId.JOURNAL_WORKBENCH, "Journal Workbench", "Create immutable journal transactions with dual timing."),
                new CustomerPanelBlueprint(CustomerPanelId.OPEN_ITEMS_SCHEDULES, "Open Item Schedules", "Track receivables, prepaids, deferred revenue, payables, and outstanding bank items."),
                new CustomerPanelBlueprint(CustomerPanelId.EVENT_LIFECYCLE, "Event Lifecycle", "Plan, run, settle, and close SCA events with linked postings."),
                new CustomerPanelBlueprint(CustomerPanelId.RECONCILIATION, "Bank Reconciliation", "Resolve cleared/uncleared bank items and record corrections."),
                new CustomerPanelBlueprint(CustomerPanelId.PERIOD_CLOSE, "Period Close", "Run deterministic close and open-item roll-forward."),
                new CustomerPanelBlueprint(CustomerPanelId.IMPORT_EXPORT, "Import / Export", "Import OFX/QFX and export portable database images and chart templates."),
                new CustomerPanelBlueprint(CustomerPanelId.APPROVAL_AUDIT, "Approval & Audit", "Review supervisory actions, reversals, deletions, and evidence trail.")
        );
    }
}
