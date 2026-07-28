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
        validateAccountParents(document.chartOfAccounts(), accountIds);
        validateFundParents(document.funds(), fundIds);
        validateBudgets(document.budgets(), accountIds, fundIds);
        validateTransactions(document.transactions(), accountIds, fundIds, activityIds);
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

    private static void validateTransactions(List<SclxExportDocument.Transaction> transactions,
            Set<String> accountIds, Set<String> fundIds, Set<String> activityIds)
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
}
