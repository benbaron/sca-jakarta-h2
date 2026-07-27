package org.nonprofitbookkeeping.interchange.sclx;

/** Immutable selected-company entity, warning, and exclusion counts for one SCLX export. */
public record SclxExportCounts(
        long organizations,
        long accounts,
        long funds,
        long budgets,
        long budgetLines,
        long transactions,
        long transactionLines,
        long warnings,
        long exclusions,
        long totalEntities)
{
    public SclxExportCounts
    {
        if (organizations < 0L || accounts < 0L || funds < 0L || budgets < 0L
                || budgetLines < 0L || transactions < 0L || transactionLines < 0L
                || warnings < 0L || exclusions < 0L || totalEntities < 0L)
        {
            throw new IllegalArgumentException("SCLX export counts must not be negative");
        }
    }

    static SclxExportCounts from(
            SclxExportDocument document,
            long warningCount,
            long exclusionCount)
    {
        long budgetLineCount = document.budgets().stream()
                .mapToLong(budget -> budget.lines().size())
                .sum();
        long transactionLineCount = document.transactions().stream()
                .mapToLong(transaction -> transaction.lines().size())
                .sum();
        long entityCount = 1L
                + document.chartOfAccounts().size()
                + document.funds().size()
                + document.budgets().size()
                + budgetLineCount
                + document.transactions().size()
                + transactionLineCount;
        return new SclxExportCounts(
                1L,
                document.chartOfAccounts().size(),
                document.funds().size(),
                document.budgets().size(),
                budgetLineCount,
                document.transactions().size(),
                transactionLineCount,
                warningCount,
                exclusionCount,
                entityCount);
    }
}
