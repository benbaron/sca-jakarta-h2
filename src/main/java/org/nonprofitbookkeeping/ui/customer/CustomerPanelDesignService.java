package org.nonprofitbookkeeping.ui.customer;

import org.nonprofitbookkeeping.architecture.CustomerPanelId;

import java.util.List;

/**
 * Canonical customer panel design for stateful accounting operations.
 */
public final class CustomerPanelDesignService
{
    private CustomerPanelDesignService()
    {
    }

    public static List<CustomerPanelDescriptor> descriptors()
    {
        return List.of(
                new CustomerPanelDescriptor(CustomerPanelId.GROUP_SELECTOR, "Group Selector", UserRole.USER,
                        List.of("Switch active branch identity", "Load branch-specific chart and funds")),
                new CustomerPanelDescriptor(CustomerPanelId.JOURNAL_WORKBENCH, "Journal Workbench", UserRole.USER,
                        List.of("Post immutable journal transaction", "Tag bank and budget timing", "Create supervisory reversal request")),
                new CustomerPanelDescriptor(CustomerPanelId.OPEN_ITEMS_SCHEDULES, "Open Item Schedules", UserRole.USER,
                        List.of("Track receivables and prepaids", "Apply settlements and recognitions", "Inspect schedule derivation from journal")),
                new CustomerPanelDescriptor(CustomerPanelId.EVENT_LIFECYCLE, "Event Lifecycle", UserRole.USER,
                        List.of("Progress event state machine", "Link event activity to journal transactions")),
                new CustomerPanelDescriptor(CustomerPanelId.RECONCILIATION, "Bank Reconciliation", UserRole.USER,
                        List.of("Match bank statement activity", "Resolve outstanding bank items")),
                new CustomerPanelDescriptor(CustomerPanelId.PERIOD_CLOSE, "Period Close", UserRole.SUPERVISOR,
                        List.of("Run deterministic close", "Roll open items forward")),
                new CustomerPanelDescriptor(CustomerPanelId.IMPORT_EXPORT, "Import / Export", UserRole.SUPERVISOR,
                        List.of("Import OFX/QFX data", "Export portable database image", "Import/export chart of accounts")),
                new CustomerPanelDescriptor(CustomerPanelId.APPROVAL_AUDIT, "Approval & Audit", UserRole.SUPERVISOR,
                        List.of("Approve/reject supervisory operations", "Review audit trail"))
        );
    }
}
