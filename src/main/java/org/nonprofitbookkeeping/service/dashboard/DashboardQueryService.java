package org.nonprofitbookkeeping.service.dashboard;

import java.time.LocalDate;

/** Loads one consistent dashboard projection from the active organization database. */
public interface DashboardQueryService
{
    /**
     * Loads Dashboard data for the accounting period that starts on {@code selectedPeriodStart}.
     * The returned snapshot projects through the calculated end of that selected period.
     */
    DashboardSnapshot load(LocalDate selectedPeriodStart, int recentTransactionLimit);

    /**
     * Loads a company-scoped Dashboard projection for the accounting period that starts on
     * {@code selectedPeriodStart}.
     */
    default DashboardSnapshot load(
            String groupCode,
            LocalDate selectedPeriodStart,
            int recentTransactionLimit)
    {
        return load(selectedPeriodStart, recentTransactionLimit);
    }
}
