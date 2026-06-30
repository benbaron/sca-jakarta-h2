package org.nonprofitbookkeeping.service.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable database-backed projection for the production dashboard. */
public record DashboardSnapshot(
        LocalDate asOfDate,
        BigDecimal bookCash,
        Optional<BigDecimal> reconciledCash,
        Optional<BigDecimal> unreconciledDifference,
        BigDecimal yearToDateSurplus,
        Map<String, BigDecimal> fundClassTotals,
        List<BankAccountBalance> bankAccounts,
        List<RecentTransaction> recentTransactions,
        OpenItemSummary openItems,
        List<ReconciliationStatus> reconciliations,
        List<BudgetActual> budgetActuals)
{
    public record BankAccountBalance(long accountId, String code, String name, BigDecimal balance)
    {
    }

    public record RecentTransaction(
            long transactionId,
            LocalDate transactionDate,
            String description,
            String accountSummary,
            String fundSummary,
            BigDecimal debitTotal,
            BigDecimal creditTotal,
            String status)
    {
    }

    public record OpenItemSummary(Map<String, Long> countsByKind, long totalOpenItems)
    {
        public long countFor(String itemKind)
        {
            return countsByKind.getOrDefault(itemKind, 0L);
        }
    }

    public record ReconciliationStatus(
            LocalDate statementEndingOn,
            String bankFormat,
            String status,
            int importedTransactionCount)
    {
    }

    public record BudgetActual(
            String categoryCode,
            String categoryName,
            Optional<BigDecimal> budget,
            BigDecimal actual)
    {
        public Optional<BigDecimal> variance()
        {
            return budget.map(actual::subtract);
        }
    }
}
