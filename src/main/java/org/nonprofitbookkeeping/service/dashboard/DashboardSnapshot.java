package org.nonprofitbookkeeping.service.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable database-backed projection for the production dashboard workspace.
 */
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
        List<BudgetActual> budgetActuals,
        OrganizationSummary organization,
        PeriodSummary period,
        List<MonthlyResult> monthlyResults)
{
    public record BankAccountBalance(
            long accountId,
            String code,
            String name,
            BigDecimal balance)
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
            Optional<BigDecimal> runningBankBalance,
            boolean affectsBank,
            boolean affectsBudget,
            String status)
    {
    }

    public record OpenItemSummary(
            Map<String, Long> countsByKind,
            Map<String, BigDecimal> amountsByKind,
            long totalOpenItems,
            BigDecimal totalOpenAmount)
    {
        public long countFor(String itemKind)
        {
            return countsByKind.getOrDefault(itemKind, 0L);
        }

        public BigDecimal amountFor(String itemKind)
        {
            return amountsByKind.getOrDefault(itemKind, BigDecimal.ZERO);
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

    public record OrganizationSummary(
            String code,
            String displayName,
            String branchType,
            String parentOrganization,
            boolean active,
            String currency)
    {
        public static OrganizationSummary unavailable(String code)
        {
            String normalizedCode = code == null || code.isBlank() ? "DEFAULT" : code;
            return new OrganizationSummary(
                    normalizedCode,
                    normalizedCode,
                    "",
                    "",
                    false,
                    "USD");
        }
    }

    public record PeriodSummary(
            Optional<Integer> fiscalYear,
            Optional<Integer> periodNumber,
            Optional<LocalDate> startDate,
            Optional<LocalDate> endDate,
            String status)
    {
        public static PeriodSummary unavailable()
        {
            return new PeriodSummary(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    "UNCONFIGURED");
        }
    }

    public record MonthlyResult(int month, BigDecimal surplus)
    {
    }
}
