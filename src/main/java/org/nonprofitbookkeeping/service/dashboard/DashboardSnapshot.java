package org.nonprofitbookkeeping.service.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Immutable database-backed projection for the production dashboard.
 */
public record DashboardSnapshot(LocalDate asOfDate,
                                BigDecimal bookCash,
                                BigDecimal reconciledCash,
                                BigDecimal unreconciledDifference,
                                BigDecimal yearToDateSurplus,
                                Map<String, BigDecimal> fundClassTotals,
                                List<BankAccountBalance> bankAccounts,
                                List<RecentTransaction> recentTransactions)
{
    public record BankAccountBalance(long accountId, String code, String name, BigDecimal balance)
    {
    }

    public record RecentTransaction(long transactionId, LocalDate transactionDate, String memo, String status)
    {
    }
}
