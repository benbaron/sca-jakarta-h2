package org.nonprofitbookkeeping.interchange.sclx;

/** Immutable selected-company entity, warning, and exclusion counts for one SCLX export. */
public record SclxExportCounts(
        long organizations,
        long accounts,
        long funds,
        long activities,
        long counterparties,
        long merchants,
        long budgets,
        long budgetLines,
        long transactions,
        long transactionLines,
        long supplementalDetails,
        long warnings,
        long exclusions,
        long totalEntities)
{
    public SclxExportCounts
    {
        if (organizations < 0L || accounts < 0L || funds < 0L || activities < 0L
                || counterparties < 0L || merchants < 0L || budgets < 0L
                || budgetLines < 0L || transactions < 0L || transactionLines < 0L
                || supplementalDetails < 0L || warnings < 0L || exclusions < 0L
                || totalEntities < 0L)
        {
            throw new IllegalArgumentException("SCLX export counts must not be negative");
        }
    }

    public SclxExportCounts(
            long organizations,
            long accounts,
            long funds,
            long activities,
            long counterparties,
            long merchants,
            long budgets,
            long budgetLines,
            long transactions,
            long transactionLines,
            long warnings,
            long exclusions,
            long totalEntities)
    {
        this(
                organizations,
                accounts,
                funds,
                activities,
                counterparties,
                merchants,
                budgets,
                budgetLines,
                transactions,
                transactionLines,
                0L,
                warnings,
                exclusions,
                totalEntities);
    }

    public SclxExportCounts(
            long organizations,
            long accounts,
            long funds,
            long activities,
            long budgets,
            long budgetLines,
            long transactions,
            long transactionLines,
            long warnings,
            long exclusions,
            long totalEntities)
    {
        this(
                organizations,
                accounts,
                funds,
                activities,
                0L,
                0L,
                budgets,
                budgetLines,
                transactions,
                transactionLines,
                0L,
                warnings,
                exclusions,
                totalEntities);
    }

    public SclxExportCounts(
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
        this(
                organizations,
                accounts,
                funds,
                0L,
                0L,
                0L,
                budgets,
                budgetLines,
                transactions,
                transactionLines,
                0L,
                warnings,
                exclusions,
                totalEntities);
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
        long activityCount = SclxActivityExtension.entries(document.extensions()).size();
        SclxPartyExtension.Data partyData = SclxPartyExtension.data(document.extensions());
        long counterpartyCount = partyData.counterparties().size();
        long merchantCount = partyData.merchants().size();
        long supplementalDetailCount = SclxSupplementalDetailExtension.entries(document.extensions()).size();
        long entityCount = 1L
                + document.chartOfAccounts().size()
                + document.funds().size()
                + activityCount
                + counterpartyCount
                + merchantCount
                + document.budgets().size()
                + budgetLineCount
                + document.transactions().size()
                + transactionLineCount
                + supplementalDetailCount;
        return new SclxExportCounts(
                1L,
                document.chartOfAccounts().size(),
                document.funds().size(),
                activityCount,
                counterpartyCount,
                merchantCount,
                document.budgets().size(),
                budgetLineCount,
                document.transactions().size(),
                transactionLineCount,
                supplementalDetailCount,
                warningCount,
                exclusionCount,
                entityCount);
    }
}
