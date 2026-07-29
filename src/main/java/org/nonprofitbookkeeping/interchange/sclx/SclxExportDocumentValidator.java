package org.nonprofitbookkeeping.interchange.sclx;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Validates the immutable export snapshot before deterministic serialization or file creation. */
public final class SclxExportDocumentValidator
{
    public void validate(SclxExportDocument document)
    {
        Objects.requireNonNull(document, "document");

        Set<String> accountIds = uniqueAccountIds(document.chartOfAccounts());
        Set<String> fundIds = uniqueFundIds(document.funds());
        Set<String> activityIds = SclxActivityExtension.uniqueIds(document.extensions());
        SclxPartyExtension.Data partyData = SclxPartyExtension.data(document.extensions());
        Set<String> counterpartyIds = SclxPartyExtension.uniqueCounterpartyIds(partyData);
        Set<String> merchantIds = SclxPartyExtension.uniqueMerchantIds(partyData);
        validateAccountParents(document.chartOfAccounts(), accountIds);
        validateFundParents(document.funds(), fundIds);
        validateBudgets(document.budgets(), accountIds, fundIds);
        TransactionReferences transactionReferences = validateTransactions(
                document.transactions(), accountIds, fundIds, activityIds, counterpartyIds);
        validateTransactionLineMerchants(
                partyData, transactionReferences.transactionLineIds(), merchantIds);
        validateSupplementalDetails(
                SclxSupplementalDetailExtension.entries(document.extensions()),
                transactionReferences.transactionIds());
        validateFixedAssets(
                SclxFixedAssetsExtension.data(document.extensions()),
                accountIds,
                fundIds,
                transactionReferences.transactionIds());
        SclxBankConfigurationExtension.Data bankConfiguration =
                SclxBankConfigurationExtension.data(document.extensions());
        Set<String> bankIds = SclxBankConfigurationExtension.uniqueBankIds(bankConfiguration);
        Set<String> bankAccountIds = SclxBankConfigurationExtension.uniqueBankAccountIds(bankConfiguration);
        validateBankConfiguration(bankConfiguration, bankIds, accountIds);
        SclxBankStatementFactsExtension.Data statementFacts =
                SclxBankStatementFactsExtension.data(document.extensions());
        BankingReferences bankingReferences = validateBankStatementFacts(
                statementFacts,
                bankAccountIds,
                transactionReferences.transactionIds(),
                transactionReferences.transactionLineIds());
        validateReconciliation(
                SclxReconciliationExtension.data(document.extensions()),
                bankAccountIds,
                bankingReferences.statementLineIds(),
                transactionReferences.transactionLineIds());
        validateFixedAssets(
                SclxFixedAssetsExtension.data(document.extensions()),
                accountIds,
                fundIds,
                transactionReferences.transactionIds());
    }

    private static Set<String> uniqueAccountIds(List<SclxExportDocument.Account> accounts)
    {
        Set<String> ids = new HashSet<>();
        for (SclxExportDocument.Account account : accounts)
        {
            requireUnique(ids, account.accountId(), "account");
        }
        return ids;
    }

    private static Set<String> uniqueFundIds(List<SclxExportDocument.Fund> funds)
    {
        Set<String> ids = new HashSet<>();
        for (SclxExportDocument.Fund fund : funds)
        {
            requireUnique(ids, fund.fundId(), "fund");
        }
        return ids;
    }

    private static void validateAccountParents(List<SclxExportDocument.Account> accounts, Set<String> accountIds)
    {
        for (SclxExportDocument.Account account : accounts)
        {
            requireOptionalReference(account.parentAccountId(), accountIds,
                    "account " + account.accountId() + " parentAccountId");
        }
    }

    private static void validateFundParents(List<SclxExportDocument.Fund> funds, Set<String> fundIds)
    {
        for (SclxExportDocument.Fund fund : funds)
        {
            requireOptionalReference(fund.parentFundId(), fundIds,
                    "fund " + fund.fundId() + " parentFundId");
        }
    }

    private static void validateBudgets(List<SclxExportDocument.Budget> budgets,
            Set<String> accountIds, Set<String> fundIds)
    {
        Set<String> budgetIds = new HashSet<>();
        Set<String> lineIds = new HashSet<>();
        for (SclxExportDocument.Budget budget : budgets)
        {
            requireUnique(budgetIds, budget.budgetId(), "budget");
            for (SclxExportDocument.BudgetLine line : budget.lines())
            {
                requireUnique(lineIds, line.lineId(), "budget line");
                requireOptionalReference(line.accountId(), accountIds,
                        "budget line " + line.lineId() + " accountId");
                requireOptionalReference(line.fundId(), fundIds,
                        "budget line " + line.lineId() + " fundId");
            }
        }
    }

    private static TransactionReferences validateTransactions(
            List<SclxExportDocument.Transaction> transactions,
            Set<String> accountIds,
            Set<String> fundIds,
            Set<String> activityIds,
            Set<String> counterpartyIds)
    {
        Set<String> transactionIds = new HashSet<>();
        Set<String> lineIds = new HashSet<>();
        for (SclxExportDocument.Transaction transaction : transactions)
        {
            requireUnique(transactionIds, transaction.transactionId(), "transaction");
            if (transaction.lines().size() < 2)
            {
                throw new IllegalArgumentException(
                        "transaction " + transaction.transactionId() + " must contain at least two posting lines");
            }

            BigDecimal debits = BigDecimal.ZERO;
            BigDecimal credits = BigDecimal.ZERO;
            for (SclxExportDocument.TransactionLine line : transaction.lines())
            {
                requireUnique(lineIds, line.lineId(), "transaction line");
                requireReference(line.accountId(), accountIds,
                        "transaction line " + line.lineId() + " accountId");
                requireOptionalReference(line.fundId(), fundIds,
                        "transaction line " + line.lineId() + " fundId");
                requireOptionalReference(line.activityId(), activityIds,
                        "transaction line " + line.lineId() + " activityId");
                requireOptionalReference(line.counterpartyId(), counterpartyIds,
                        "transaction line " + line.lineId() + " counterpartyId");
                debits = debits.add(line.debit());
                credits = credits.add(line.credit());
            }
            if (debits.compareTo(credits) != 0)
            {
                throw new IllegalArgumentException(
                        "transaction " + transaction.transactionId() + " is not balanced: debits="
                                + debits.toPlainString() + ", credits=" + credits.toPlainString());
            }
        }

        for (SclxExportDocument.Transaction transaction : transactions)
        {
            requireOptionalReference(transaction.correctionOfTransactionId(), transactionIds,
                    "transaction " + transaction.transactionId() + " correctionOfTransactionId");
        }
        return new TransactionReferences(Set.copyOf(transactionIds), Set.copyOf(lineIds));
    }

    private static void validateTransactionLineMerchants(
            SclxPartyExtension.Data partyData,
            Set<String> transactionLineIds,
            Set<String> merchantIds)
    {
        Set<String> linkedLineIds = new HashSet<>();
        for (SclxPartyExtension.TransactionLineMerchant link : partyData.transactionLineMerchants())
        {
            requireUnique(linkedLineIds, link.lineId(), "transaction-line merchant reference");
            requireReference(link.lineId(), transactionLineIds,
                    "transaction-line merchant lineId");
            requireReference(link.merchantId(), merchantIds,
                    "transaction-line merchant merchantId");
        }
    }

    private static void validateSupplementalDetails(
            List<SclxSupplementalDetailExtension.Entry> details,
            Set<String> transactionIds)
    {
        SclxSupplementalDetailExtension.uniqueIds(details);
        for (SclxSupplementalDetailExtension.Entry detail : details)
        {
            requireReference(
                    detail.transactionId(),
                    transactionIds,
                    "supplemental detail " + detail.supplementalDetailId() + " transactionId");
        }
    }

    private static void validateFixedAssets(
            SclxFixedAssetsExtension.Data data,
            Set<String> accountIds,
            Set<String> fundIds,
            Set<String> transactionIds)
    {
        Set<String> assetIds = SclxFixedAssetsExtension.uniqueAssetIds(data);
        SclxFixedAssetsExtension.requireUniqueRunIds(data);
        for (SclxFixedAssetsExtension.AssetEntry asset : data.assets())
        {
            requireReference(asset.assetAccountId(), accountIds,
                    "fixed asset " + asset.assetId() + " assetAccountId");
            requireReference(asset.accumulatedDepreciationAccountId(), accountIds,
                    "fixed asset " + asset.assetId() + " accumulatedDepreciationAccountId");
            requireReference(asset.depreciationExpenseAccountId(), accountIds,
                    "fixed asset " + asset.assetId() + " depreciationExpenseAccountId");
            requireReference(asset.fundId(), fundIds,
                    "fixed asset " + asset.assetId() + " fundId");
            if (asset.acquisitionCost().signum() < 0 || asset.salvageValue().signum() < 0
                    || asset.openingAccumulatedDepreciation().signum() < 0)
            {
                throw new IllegalArgumentException("fixed asset amounts must not be negative: " + asset.assetId());
            }
            if (asset.usefulLifeMonths() < 1)
            {
                throw new IllegalArgumentException("fixed asset usefulLifeMonths must be positive: " + asset.assetId());
            }
        }
        for (SclxFixedAssetsExtension.DepreciationRunEntry run : data.depreciationRuns())
        {
            requireReference(run.assetId(), assetIds,
                    "depreciation run " + run.depreciationRunId() + " assetId");
            requireReference(run.transactionId(), transactionIds,
                    "depreciation run " + run.depreciationRunId() + " transactionId");
            if (run.depreciationAmount().signum() <= 0)
            {
                throw new IllegalArgumentException(
                        "depreciation run amount must be positive: " + run.depreciationRunId());
            }
        }
    }

    private static void validateBankConfiguration(
            SclxBankConfigurationExtension.Data data,
            Set<String> bankIds,
            Set<String> ledgerAccountIds)
    {
        for (SclxBankConfigurationExtension.AccountEntry account : data.accounts())
        {
            requireOptionalReference(
                    account.bankId(), bankIds,
                    "bank account " + account.bankAccountId() + " bankId");
            requireOptionalReference(
                    account.ledgerAccountId(), ledgerAccountIds,
                    "bank account " + account.bankAccountId() + " ledgerAccountId");
        }
    }

    private static BankingReferences validateBankStatementFacts(
            SclxBankStatementFactsExtension.Data data,
            Set<String> bankAccountIds,
            Set<String> transactionIds,
            Set<String> transactionLineIds)
    {
        Set<String> batchIds = SclxBankStatementFactsExtension.uniqueImportBatchIds(data);
        Set<String> statementLineIds = SclxBankStatementFactsExtension.uniqueStatementLineIds(data);
        SclxBankStatementFactsExtension.uniqueIssueIds(data);
        for (SclxBankStatementFactsExtension.ImportBatchEntry batch : data.importBatches())
        {
            requireOptionalReference(
                    batch.bankAccountId(), bankAccountIds,
                    "bank import batch " + batch.importBatchId() + " bankAccountId");
        }
        for (SclxBankStatementFactsExtension.StatementLineEntry line : data.statementLines())
        {
            requireReference(
                    line.importBatchId(), batchIds,
                    "bank statement line " + line.statementLineId() + " importBatchId");
            requireOptionalReference(
                    line.bankAccountId(), bankAccountIds,
                    "bank statement line " + line.statementLineId() + " bankAccountId");
            requireOptionalReference(
                    line.acceptedTransactionId(), transactionIds,
                    "bank statement line " + line.statementLineId() + " acceptedTransactionId");
            requireOptionalReference(
                    line.matchedTransactionId(), transactionIds,
                    "bank statement line " + line.statementLineId() + " matchedTransactionId");
            if ("ACCEPTED".equals(line.status()) && line.acceptedTransactionId() == null)
            {
                throw new IllegalArgumentException(
                        "accepted bank statement line " + line.statementLineId()
                                + " must reference acceptedTransactionId");
            }
            if ("MATCHED".equals(line.status()) && line.matchedTransactionId() == null)
            {
                throw new IllegalArgumentException(
                        "matched bank statement line " + line.statementLineId()
                                + " must reference matchedTransactionId");
            }
        }
        for (SclxBankStatementFactsExtension.IssueEntry issue : data.issues())
        {
            requireReference(
                    issue.importBatchId(), batchIds,
                    "bank import issue " + issue.issueId() + " importBatchId");
            requireOptionalReference(
                    issue.statementLineId(), statementLineIds,
                    "bank import issue " + issue.issueId() + " statementLineId");
        }
        Set<String> clearedLineIds = new HashSet<>();
        for (SclxBankStatementFactsExtension.TransactionLineClearance clearance
                : data.transactionLineClearance())
        {
            requireUnique(clearedLineIds, clearance.lineId(), "transaction-line clearance");
            requireReference(
                    clearance.lineId(), transactionLineIds,
                    "transaction-line clearance lineId");
            requireOptionalReference(
                    clearance.statementLineId(), statementLineIds,
                    "transaction-line clearance " + clearance.lineId() + " statementLineId");
        }
        return new BankingReferences(Set.copyOf(batchIds), Set.copyOf(statementLineIds));
    }

    private static void validateReconciliation(
            SclxReconciliationExtension.Data data,
            Set<String> bankAccountIds,
            Set<String> statementLineIds,
            Set<String> transactionLineIds)
    {
        Set<String> sessionIds = SclxReconciliationExtension.uniqueSessionIds(data);
        SclxReconciliationExtension.uniqueMatchIds(data);
        for (SclxReconciliationExtension.SessionEntry session : data.sessions())
        {
            requireReference(
                    session.bankAccountId(), bankAccountIds,
                    "reconciliation session " + session.reconciliationSessionId() + " bankAccountId");
        }
        for (SclxReconciliationExtension.MatchEntry match : data.matches())
        {
            requireReference(
                    match.reconciliationSessionId(), sessionIds,
                    "reconciliation match " + match.reconciliationMatchId()
                            + " reconciliationSessionId");
            requireOptionalReference(
                    match.statementLineId(), statementLineIds,
                    "reconciliation match " + match.reconciliationMatchId() + " statementLineId");
            requireOptionalReference(
                    match.lineId(), transactionLineIds,
                    "reconciliation match " + match.reconciliationMatchId() + " lineId");
        }
    }

    private static void requireUnique(Set<String> identities, String identity, String type)
    {
        if (!identities.add(identity))
        {
            throw new IllegalArgumentException("duplicate " + type + " portable identity: " + identity);
        }
    }

    private static void requireReference(String identity, Set<String> identities, String field)
    {
        if (!identities.contains(identity))
        {
            throw new IllegalArgumentException(field + " does not resolve: " + identity);
        }
    }

    private static void requireOptionalReference(String identity, Set<String> identities, String field)
    {
        if (identity != null && !identity.isBlank())
        {
            requireReference(identity, identities, field);
        }
    }
    private record TransactionReferences(Set<String> transactionIds, Set<String> transactionLineIds)
    {
    }

    private record BankingReferences(Set<String> importBatchIds, Set<String> statementLineIds)
    {
    }

}
