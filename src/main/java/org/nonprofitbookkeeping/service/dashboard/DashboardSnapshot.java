package org.nonprofitbookkeeping.service.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        public Optional<BigDecimal> runningBankBalance()
        {
            return Optional.empty();
        }

        public boolean affectsBank()
        {
            return false;
        }

        public boolean affectsBudget()
        {
            return false;
        }
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

        public Optional<BigDecimal> performancePercent()
        {
            if (budget.isEmpty() || budget.orElseThrow().compareTo(BigDecimal.ZERO) == 0)
            {
                return Optional.empty();
            }
            return Optional.of(actual
                    .divide(budget.orElseThrow(), 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")));
        }
    }
}
