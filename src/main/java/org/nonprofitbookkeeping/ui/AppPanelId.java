package org.nonprofitbookkeeping.ui;

/** Stable identifiers for workspace panels. */
public enum AppPanelId
{
    DASHBOARD,

    /** Retired compatibility alias normalized to {@link #JOURNAL_PANE}. */
    LEDGER_REGISTER,

    /** Retired compatibility alias normalized to {@link #JOURNAL_PANE}. */
    TXN_EDITOR,

    /** Canonical identifier for the unified Journal workspace. */
    JOURNAL_PANE,

    /** Compatibility route to the Accounting tab in Events / Activities. */
    EVENT_ACCOUNTING,

    BANKING,

    BUDGET_EDITOR,
    BUDGET_VS_ACTUAL,
    BUDGET_CATEGORIES,
    RECOGNITION_ASSISTANT,
    COUNTERPARTIES,
    MERCHANTS,
    MERCHANT_SETTLEMENT_ASSISTANT,

    ASSETS_REGISTER,
    DEPRECIATION_RUNS,
    INVENTORY,
    PROPERTY_CUSTODY,

    RECONCILIATION_RUNS,
    PERIOD_CLOSE_RUNS,
    IMPORT_PREVIEW,
    OPENING_CONVERSION,
    APPROVAL_AUDIT,
    BANK_TRANSACTIONS,

    REPORT_LIBRARY,

    CHART_OF_ACCOUNTS,
    FUNDS,
    FUND_TRANSFERS,
    ACTIVITIES,
    SETTINGS,
    DIAGNOSTICS,
    HELP;

    /** Returns the production destination for a stable or retired panel id. */
    public static AppPanelId canonical(AppPanelId id)
    {
        if (id == LEDGER_REGISTER || id == TXN_EDITOR)
        {
            return JOURNAL_PANE;
        }
        if (id == RECONCILIATION_RUNS || id == BANK_TRANSACTIONS)
        {
            return BANKING;
        }
        return id == EVENT_ACCOUNTING ? ACTIVITIES : id;
    }
}
