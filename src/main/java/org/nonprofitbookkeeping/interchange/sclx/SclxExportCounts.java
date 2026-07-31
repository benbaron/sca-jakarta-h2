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
        long banks,
        long bankAccounts,
        long importBatches,
        long statementLines,
        long importIssues,
        long reconciliationSessions,
        long reconciliationMatches,
        long fixedAssets,
        long depreciationRuns,
        long inventoryItems,
        long inventoryMovements,
        long periodCloseRanges,
        long periodCloseEvents,
        long auditEvents,
        long warnings,
        long exclusions,
        long totalEntities)
{
    public SclxExportCounts
    {
        if (organizations < 0L || accounts < 0L || funds < 0L || activities < 0L
                || counterparties < 0L || merchants < 0L || budgets < 0L
                || budgetLines < 0L || transactions < 0L || transactionLines < 0L
                || supplementalDetails < 0L || banks < 0L || bankAccounts < 0L
                || importBatches < 0L || statementLines < 0L || importIssues < 0L
                || reconciliationSessions < 0L || reconciliationMatches < 0L
                || fixedAssets < 0L || depreciationRuns < 0L
                || inventoryItems < 0L || inventoryMovements < 0L
                || periodCloseRanges < 0L || periodCloseEvents < 0L || auditEvents < 0L
                || warnings < 0L || exclusions < 0L || totalEntities < 0L)
        {
            throw new IllegalArgumentException("SCLX export counts must not be negative");
        }
    }

    /** Backward-compatible constructor used before factual audit-history export. */
    public SclxExportCounts(
            long organizations, long accounts, long funds, long activities, long counterparties, long merchants,
            long budgets, long budgetLines, long transactions, long transactionLines, long supplementalDetails,
            long banks, long bankAccounts, long importBatches, long statementLines, long importIssues,
            long reconciliationSessions, long reconciliationMatches, long fixedAssets, long depreciationRuns,
            long inventoryItems, long inventoryMovements, long periodCloseRanges, long periodCloseEvents,
            long warnings, long exclusions, long totalEntities)
    {
        this(organizations, accounts, funds, activities, counterparties, merchants, budgets, budgetLines,
                transactions, transactionLines, supplementalDetails, banks, bankAccounts, importBatches,
                statementLines, importIssues, reconciliationSessions, reconciliationMatches, fixedAssets,
                depreciationRuns, inventoryItems, inventoryMovements, periodCloseRanges, periodCloseEvents,
                0L, warnings, exclusions, totalEntities);
    }

    /** Backward-compatible constructor used before inventory export. */
    public SclxExportCounts(
            long organizations, long accounts, long funds, long activities, long counterparties, long merchants,
            long budgets, long budgetLines, long transactions, long transactionLines, long supplementalDetails,
            long banks, long bankAccounts, long importBatches, long statementLines, long importIssues,
            long reconciliationSessions, long reconciliationMatches, long fixedAssets, long depreciationRuns,
            long warnings, long exclusions, long totalEntities)
    {
        this(organizations, accounts, funds, activities, counterparties, merchants, budgets, budgetLines,
                transactions, transactionLines, supplementalDetails, banks, bankAccounts, importBatches,
                statementLines, importIssues, reconciliationSessions, reconciliationMatches, fixedAssets,
                depreciationRuns, 0L, 0L, 0L, 0L, warnings, exclusions, totalEntities);
    }

    /** Backward-compatible constructor used by banking-complete summaries before fixed assets. */
    public SclxExportCounts(
            long organizations, long accounts, long funds, long activities, long counterparties, long merchants,
            long budgets, long budgetLines, long transactions, long transactionLines, long supplementalDetails,
            long banks, long bankAccounts, long importBatches, long statementLines, long importIssues,
            long reconciliationSessions, long reconciliationMatches, long warnings, long exclusions,
            long totalEntities)
    {
        this(organizations, accounts, funds, activities, counterparties, merchants, budgets, budgetLines,
                transactions, transactionLines, supplementalDetails, banks, bankAccounts, importBatches,
                statementLines, importIssues, reconciliationSessions, reconciliationMatches, 0L, 0L, 0L, 0L,
                0L, 0L, warnings, exclusions, totalEntities);
    }

    /** Backward-compatible constructor used by pre-banking completion summaries. */
    public SclxExportCounts(long organizations, long accounts, long funds, long activities, long counterparties,
            long merchants, long budgets, long budgetLines, long transactions, long transactionLines,
            long supplementalDetails, long warnings, long exclusions, long totalEntities)
    {
        this(organizations, accounts, funds, activities, counterparties, merchants, budgets, budgetLines,
                transactions, transactionLines, supplementalDetails, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, warnings, exclusions, totalEntities);
    }

    public SclxExportCounts(long organizations, long accounts, long funds, long activities, long counterparties,
            long merchants, long budgets, long budgetLines, long transactions, long transactionLines,
            long warnings, long exclusions, long totalEntities)
    {
        this(organizations, accounts, funds, activities, counterparties, merchants, budgets, budgetLines,
                transactions, transactionLines, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                warnings, exclusions, totalEntities);
    }

    public SclxExportCounts(long organizations, long accounts, long funds, long activities, long budgets,
            long budgetLines, long transactions, long transactionLines, long warnings, long exclusions,
            long totalEntities)
    {
        this(organizations, accounts, funds, activities, 0L, 0L, budgets, budgetLines, transactions,
                transactionLines, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                warnings, exclusions, totalEntities);
    }

    public SclxExportCounts(long organizations, long accounts, long funds, long budgets, long budgetLines,
            long transactions, long transactionLines, long warnings, long exclusions, long totalEntities)
    {
        this(organizations, accounts, funds, 0L, 0L, 0L, budgets, budgetLines, transactions,
                transactionLines, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                warnings, exclusions, totalEntities);
    }

    static SclxExportCounts from(SclxExportDocument document, long warningCount, long exclusionCount)
    {
        long budgetLineCount = document.budgets().stream().mapToLong(budget -> budget.lines().size()).sum();
        long transactionLineCount = document.transactions().stream()
                .mapToLong(transaction -> transaction.lines().size()).sum();
        long activityCount = SclxActivityExtension.entries(document.extensions()).size();
        SclxPartyExtension.Data partyData = SclxPartyExtension.data(document.extensions());
        long counterpartyCount = partyData.counterparties().size();
        long merchantCount = partyData.merchants().size();
        long supplementalDetailCount = SclxSupplementalDetailExtension.entries(document.extensions()).size();
        SclxBankConfigurationExtension.Data bankConfiguration =
                SclxBankConfigurationExtension.data(document.extensions());
        SclxBankStatementFactsExtension.Data bankStatementFacts =
                SclxBankStatementFactsExtension.data(document.extensions());
        SclxReconciliationExtension.Data reconciliation =
                SclxReconciliationExtension.data(document.extensions());
        SclxFixedAssetsExtension.Data fixedAssetData =
                SclxFixedAssetsExtension.data(document.extensions());
        SclxInventoryExtension.Data inventoryData = SclxInventoryExtension.data(document.extensions());
        SclxPeriodCloseExtension.Data periodCloseData = SclxPeriodCloseExtension.data(document.extensions());
        SclxAuditHistoryExtension.Data auditHistoryData = SclxAuditHistoryExtension.data(document.extensions());

        long bankCount = bankConfiguration.banks().size();
        long bankAccountCount = bankConfiguration.accounts().size();
        long importBatchCount = bankStatementFacts.importBatches().size();
        long statementLineCount = bankStatementFacts.statementLines().size();
        long importIssueCount = bankStatementFacts.issues().size();
        long reconciliationSessionCount = reconciliation.sessions().size();
        long reconciliationMatchCount = reconciliation.matches().size();
        long fixedAssetCount = fixedAssetData.assets().size();
        long depreciationRunCount = fixedAssetData.depreciationRuns().size();
        long inventoryItemCount = inventoryData.items().size();
        long inventoryMovementCount = inventoryData.movements().size();
        long periodCloseRangeCount = periodCloseData.ranges().size();
        long periodCloseEventCount = periodCloseData.events().size();
        long auditEventCount = auditHistoryData.events().size();
        long entityCount = 1L + document.chartOfAccounts().size() + document.funds().size() + activityCount
                + counterpartyCount + merchantCount + document.budgets().size() + budgetLineCount
                + document.transactions().size() + transactionLineCount + supplementalDetailCount + bankCount
                + bankAccountCount + importBatchCount + statementLineCount + importIssueCount
                + reconciliationSessionCount + reconciliationMatchCount + fixedAssetCount
                + depreciationRunCount + inventoryItemCount + inventoryMovementCount
                + periodCloseRangeCount + periodCloseEventCount + auditEventCount;
        return new SclxExportCounts(1L, document.chartOfAccounts().size(), document.funds().size(), activityCount,
                counterpartyCount, merchantCount, document.budgets().size(), budgetLineCount,
                document.transactions().size(), transactionLineCount, supplementalDetailCount, bankCount,
                bankAccountCount, importBatchCount, statementLineCount, importIssueCount,
                reconciliationSessionCount, reconciliationMatchCount, fixedAssetCount, depreciationRunCount,
                inventoryItemCount, inventoryMovementCount, periodCloseRangeCount, periodCloseEventCount,
                auditEventCount, warningCount, exclusionCount, entityCount);
    }
}
