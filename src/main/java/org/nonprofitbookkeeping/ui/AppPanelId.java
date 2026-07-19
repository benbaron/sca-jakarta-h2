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

    BANKING,

    /**
     * Retired compatibility identifier.
     * The former Schedules panel has no factory route and is not exposed in navigation.
     */
    SCHEDULES,

    BUDGET_EDITOR,
    BUDGET_VS_ACTUAL,

    ASSETS_REGISTER,
    DEPRECIATION_RUNS,
    INVENTORY,

    RECONCILIATION_RUNS,
    PERIOD_CLOSE_RUNS,
    IMPORT_PREVIEW,
    APPROVAL_AUDIT,
    BANK_TRANSACTIONS,

    REPORT_LIBRARY,

    CHART_OF_ACCOUNTS,
    FUNDS,
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
        return id;
    }
}
