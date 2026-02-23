package org.nonprofitbookkeeping.bridge.dashboard;

import org.nonprofitbookkeeping.service.FundBalanceRow;
import org.nonprofitbookkeeping.ui.UiServiceRegistry;

import java.time.LocalDate;
import java.util.List;

/**
 * Local bridge layer for imported dashboard package wiring.
 */
public class DashboardDataBridge
{
    public DashboardSnapshot load()
    {
        List<FundBalanceRow> rows = UiServiceRegistry.fundBalance().balancesAsOf(LocalDate.now());
        int accountCount = UiServiceRegistry.accountLookup().listActivePostingAccounts().size();
        int fundCount = UiServiceRegistry.fundLookup().listActiveFunds().size();
        return new DashboardSnapshot(rows, accountCount, fundCount);
    }

    public record DashboardSnapshot(List<FundBalanceRow> rows, int accountCount, int fundCount) {}
}
