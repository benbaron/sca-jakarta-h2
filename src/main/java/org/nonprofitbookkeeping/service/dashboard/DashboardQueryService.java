package org.nonprofitbookkeeping.service.dashboard;

import java.time.LocalDate;

/**
 * Loads one consistent dashboard projection from the active organization database.
 */
public interface DashboardQueryService
{
    DashboardSnapshot load(LocalDate asOfDate, int recentTransactionLimit);
}
