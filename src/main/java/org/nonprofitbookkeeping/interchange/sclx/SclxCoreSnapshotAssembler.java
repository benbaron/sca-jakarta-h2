package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.BudgetLine;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maps a bounded selected-company entity graph into the immutable SCLX DTO graph.
 * Querying remains outside this class so callers must supply an already company-scoped snapshot.
 */
public final class SclxCoreSnapshotAssembler
{
    private static final Comparator<BudgetLine> BUDGET_LINE_ORDER = Comparator
            .comparing((BudgetLine line) -> line.getBudgetCategory().getCode())
            .thenComparing(line -> line.getFund() == null ? "" : line.getFund().getCode())
            .thenComparing(line -> line.getPeriodMonth() == null ? "" : line.getPeriodMonth().toString())
            .thenComparing(BudgetLine::getAmount);

    private static final Comparator<TxnSplit> TRANSACTION_LINE_ORDER = Comparator
            .comparing((TxnSplit split) -> split.getAccount().getCode())
            .thenComparing(split -> split.getFund().getCode())
            .thenComparing(split -> split.getActivity() == null ? "" : split.getActivity().getCode())
            .thenComparing(split -> split.getMerchant() == null ? "" : split.getMerchant().getName())
            .thenComparing(TxnSplit::getAmountSigned)
            .thenComparing(split -> split.getNotes() == null ? "" : split.getNotes());

    private final SclxExportDocumentValidator validator = new SclxExportDocumentValidator();

    public SclxExportDocument assemble(
            Company company,
            List<Account> accounts,
            List<Fund> funds,
            Instant exportedAt)
    {
        return assemble(
                company,
                accounts,
                funds,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                exportedAt);
    }

    public SclxExportDocument assemble(
            Company company,
            List<Account> accounts,
            List<Fund> funds,
            List<BudgetPlan> budgetPlans,
            List<BudgetLine> budgetLines,
            List<Txn> transactions,
            List<TxnSplit> transactionLines,
            Instant exportedAt)
    {
        return assemble(
                company,
                accounts,
                funds,
                List.of(),
                budgetPlans,
                budgetLines,
                transactions,
                transactionLines,
                exportedAt);
    }

    public SclxExportDocument assemble(
            Company company,
            List<Account> accounts,
            List<Fund> funds,
            List<Activity> activities,
            List<BudgetPlan> budgetPlans,
            List<BudgetLine> budgetLines,
            List<Txn> transactions,
            List<TxnSplit> transactionLines,
            Instant exportedAt)
    {
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(accounts, "accounts");
        Objects.requireNonNull(funds, "funds");
        Objects.requireNonNull(activities, "activities");
        Objects.requireNonNull(budgetPlans, "budgetPlans");
        Objects.requireNonNull(budgetLines, "budgetLines");
        Objects.requireNonNull(transactions, "transactions");
        Objects.requireNonNull(transactionLines, "transactionLines");
        Objects.requireNonNull(exportedAt, "exportedAt");

        ChartOfAccounts activeChart = Objects.requireNonNull(
                company.getActiveChartOfAccounts(), "selected company has no active chart of accounts");
        if (activeChart.getCompany() != company)
        {
            throw new IllegalArgumentException("active chart does not belong to the selected company");
        }

        String companyCode = requireText(company.getCode(), "company code");
        String currency = requireText(company.getDefaultCurrency(), "company currency");
        LocalDate fiscalYearStart = LocalDate.of(
                exportedAt.atZone(ZoneOffset.UTC).getYear(),
                company.getFiscalYearStartMonth(),
                company.getFiscalYearStartDay());

        List<SclxExportDocument.Account> exportedAccounts = accounts.stream()
                .peek(account -> requireAccountOwnership(account, activeChart))
                .sorted(Comparator.comparing(Account::getCode))
                .map(account -> mapAccount(companyCode, currency, account))
                .toList();

        List<SclxExportDocument.Fund> exportedFunds = funds.stream()
                .peek(fund -> requireFundOwnership(fund, company))
                .sorted(Comparator.comparing(Fund::getCode))
                .map(fund -> mapFund(companyCode, fund))
                .toList();

        List<Map<String, Object>> exportedActivities = activities.stream()
                .peek(activity -> requireActivityOwnership(activity, company))
                .sorted(Comparator.comparing(Activity::getCode))
                .map(activity -> SclxActivityExtension.entry(
                        SclxPortableIdentity.activity(companyCode, activity.getCode()),
                        activity.getCode(),
                        activity.getName(),
                        activity.isActive()))
                .toList();

        Set<BudgetPlan> includedBudgetPlans = identitySet(budgetPlans);
        List<SclxExportDocument.Budget> exportedBudgets = budgetPlans.stream()
                .peek(plan -> requireBudgetOwnership(plan, company))
                .sorted(Comparator.comparingInt(BudgetPlan::getFiscalYear)
                        .thenComparing(BudgetPlan::getVersionCode))
                .map(plan -> mapBudget(
                        companyCode,
                        company,
                        plan,
                        budgetLines.stream()
                                .filter(line -> line.getBudgetPlan() == plan)
                                .peek(line -> requireBudgetLineOwnership(line, company, includedBudgetPlans))
                                .sorted(BUDGET_LINE_ORDER)
                                .toList()))
                .toList();
        for (BudgetLine line : budgetLines)
        {
            requireBudgetLineOwnership(line, company, includedBudgetPlans);
        }

        Set<Txn> includedTransactions = identitySet(transactions);
        List<SclxExportDocument.Transaction> exportedTransactions = transactions.stream()
                .peek(transaction -> requireTransactionOwnership(transaction, company, includedTransactions))
                .sorted(Comparator.comparing(Txn::getTxnDate)
                        .thenComparing(transaction -> transaction.getPortableId().toString()))
                .map(transaction -> mapTransaction(
                        companyCode,
                        transaction,
                        transactionLines.stream()
                                .filter(line -> line.getTxn() == transaction)
                                .peek(line -> requireTransactionLineOwnership(
                                        line, company, activeChart, includedTransactions))
                                .sorted(TRANSACTION_LINE_ORDER)
                                .toList()))
                .toList();
        for (TxnSplit line : transactionLines)
        {
            requireTransactionLineOwnership(line, company, activeChart, includedTransactions);
        }

        Map<String, Object> extensionValues = new LinkedHashMap<>();
        extensionValues.put("activeChartName", activeChart.getName());
        extensionValues.put("activeChartVersion", activeChart.getVersion());
        extensionValues.put(SclxActivityExtension.KEY, exportedActivities);

        SclxExportDocument document = SclxExportDocument.version13(
                exportedAt,
                new SclxExportDocument.Organization(
                        SclxPortableIdentity.organization(companyCode),
                        companyCode,
                        requireText(company.getDisplayName(), "company display name"),
                        currency,
                        fiscalYearStart),
                exportedAccounts,
                exportedFunds,
                exportedBudgets,
                exportedTransactions,
                new SclxExportDocument.Extensions(1, extensionValues));
        validator.validate(document);
        return document;
    }

    private static SclxExportDocument.Account mapAccount(
            String companyCode, String currency, Account account)
    {
        String accountId = SclxPortableIdentity.account(companyCode, account.getCode());
        String parentId = account.getParent() == null
                ? null
                : SclxPortableIdentity.account(companyCode, account.getParent().getCode());
        return new SclxExportDocument.Account(
                accountId,
                account.getCode(),
                account.getName(),
                account.getAccountType().name(),
                account.getSubtype() == null ? null : account.getSubtype().name(),
                account.getNormalBalance().name(),
                parentId,
                currency,
                account.getOpeningBalance(),
                account.isPosting(),
                account.isActive());
    }

    private static SclxExportDocument.Fund mapFund(String companyCode, Fund fund)
    {
        String parentId = fund.getParent() == null
                ? null
                : SclxPortableIdentity.fund(companyCode, fund.getParent().getCode());
        return new SclxExportDocument.Fund(
                SclxPortableIdentity.fund(companyCode, fund.getCode()),
                fund.getCode(),
                fund.getName(),
                fund.getFundType().name(),
                parentId,
                fund.isActive(),
                fund.getEffectiveFrom(),
                fund.getEffectiveTo(),
                fund.getRestrictionText());
    }

    private static SclxExportDocument.Budget mapBudget(
            String companyCode,
            Company company,
            BudgetPlan plan,
            List<BudgetLine> lines)
    {
        String budgetId = SclxPortableIdentity.budget(
                companyCode, plan.getFiscalYear(), plan.getVersionCode());
        List<SclxExportDocument.BudgetLine> exportedLines = lines.stream()
                .map(line -> mapBudgetLine(companyCode, company, budgetId, line))
                .toList();
        return new SclxExportDocument.Budget(
                budgetId,
                requireText(plan.getName(), "budget plan name"),
                plan.getFiscalYear(),
                requireText(plan.getVersionCode(), "budget plan version"),
                plan.getStatus() == BudgetPlan.Status.ACTIVE,
                exportedLines);
    }

    private static SclxExportDocument.BudgetLine mapBudgetLine(
            String companyCode,
            Company company,
            String budgetId,
            BudgetLine line)
    {
        String categoryCode = requireText(line.getBudgetCategory().getCode(), "budget category code");
        String fundId = line.getFund() == null
                ? null
                : SclxPortableIdentity.fund(companyCode, line.getFund().getCode());
        String periodMonth = line.getPeriodMonth() == null ? null : line.getPeriodMonth().toString();
        return new SclxExportDocument.BudgetLine(
                SclxPortableIdentity.budgetLine(budgetId, categoryCode, null, fundId, periodMonth),
                null,
                fundId,
                categoryCode,
                periodMonth,
                Objects.requireNonNull(line.getAmount(), "budget line amount"));
    }

    private static SclxExportDocument.Transaction mapTransaction(
            String companyCode,
            Txn transaction,
            List<TxnSplit> lines)
    {
        String transactionId = SclxPortableIdentity.transaction(
                companyCode,
                Objects.requireNonNull(transaction.getPortableId(), "transaction portableId").toString());

        Txn correctedTransaction = transaction.getReplacementFor() != null
                ? transaction.getReplacementFor()
                : transaction.getReversalOf();
        String correctionType = transaction.getReplacementFor() != null
                ? "REPLACEMENT"
                : transaction.getReversalOf() == null ? null : "REVERSAL";
        String correctionOfTransactionId = correctedTransaction == null
                ? null
                : SclxPortableIdentity.transaction(
                        companyCode,
                        Objects.requireNonNull(
                                correctedTransaction.getPortableId(),
                                "corrected transaction portableId").toString());

        List<SclxExportDocument.TransactionLine> exportedLines = new java.util.ArrayList<>();
        int ordinal = 1;
        for (TxnSplit line : lines)
        {
            exportedLines.add(mapTransactionLine(companyCode, transactionId, ordinal++, line));
        }

        return new SclxExportDocument.Transaction(
                transactionId,
                Objects.requireNonNull(transaction.getTxnDate(), "transaction date"),
                transactionDescription(transaction),
                null,
                requireText(transaction.getStatus(), "transaction status"),
                correctionType,
                correctionOfTransactionId,
                exportedLines);
    }

    private static SclxExportDocument.TransactionLine mapTransactionLine(
            String companyCode,
            String transactionId,
            int ordinal,
            TxnSplit line)
    {
        BigDecimal signed = Objects.requireNonNull(line.getAmountSigned(), "transaction line amount");
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        if (line.getAccount().getNormalBalance() == NormalBalance.DEBIT)
        {
            if (signed.signum() >= 0)
            {
                debit = signed;
            }
            else
            {
                credit = signed.abs();
            }
        }
        else if (signed.signum() >= 0)
        {
            credit = signed;
        }
        else
        {
            debit = signed.abs();
        }

        return new SclxExportDocument.TransactionLine(
                SclxPortableIdentity.transactionLine(transactionId, ordinal),
                SclxPortableIdentity.account(companyCode, line.getAccount().getCode()),
                SclxPortableIdentity.fund(companyCode, line.getFund().getCode()),
                line.getActivity() == null
                        ? null
                        : SclxPortableIdentity.activity(companyCode, line.getActivity().getCode()),
                null,
                debit,
                credit,
                line.getNotes());
    }

    private static String transactionDescription(Txn transaction)
    {
        if (transaction.getMemo() != null && !transaction.getMemo().isBlank())
        {
            return transaction.getMemo().strip();
        }
        if (transaction.getPayee() != null
                && transaction.getPayee().getDisplayName() != null
                && !transaction.getPayee().getDisplayName().isBlank())
        {
            return transaction.getPayee().getDisplayName().strip();
        }
        return "Accounting transaction";
    }

    private static void requireAccountOwnership(Account account, ChartOfAccounts activeChart)
    {
        Objects.requireNonNull(account, "account");
        if (account.getChart() != activeChart)
        {
            throw new IllegalArgumentException("account is outside the selected company's active chart: "
                    + account.getCode());
        }
    }

    private static void requireFundOwnership(Fund fund, Company company)
    {
        Objects.requireNonNull(fund, "fund");
        if (fund.getCompany() != company)
        {
            throw new IllegalArgumentException("fund is outside the selected company: " + fund.getCode());
        }
    }

    private static void requireActivityOwnership(Activity activity, Company company)
    {
        Objects.requireNonNull(activity, "activity");
        if (activity.getCompany() != company)
        {
            throw new IllegalArgumentException("activity is outside the selected company: " + activity.getCode());
        }
        requireText(activity.getCode(), "activity code");
        requireText(activity.getName(), "activity name");
    }

    private static void requireBudgetOwnership(BudgetPlan plan, Company company)
    {
        Objects.requireNonNull(plan, "budget plan");
        if (plan.getCompany() != company)
        {
            throw new IllegalArgumentException("budget plan is outside the selected company: " + plan.getName());
        }
    }

    private static void requireBudgetLineOwnership(
            BudgetLine line,
            Company company,
            Set<BudgetPlan> includedBudgetPlans)
    {
        Objects.requireNonNull(line, "budget line");
        if (!includedBudgetPlans.contains(line.getBudgetPlan()))
        {
            throw new IllegalArgumentException("budget line references a plan outside the exported snapshot");
        }
        requireBudgetOwnership(line.getBudgetPlan(), company);
        if (line.getBudgetCategory() == null || line.getBudgetCategory().getCompany() != company)
        {
            throw new IllegalArgumentException("budget line category is outside the selected company");
        }
        if (line.getFund() != null && line.getFund().getCompany() != company)
        {
            throw new IllegalArgumentException("budget line fund is outside the selected company");
        }
    }

    private static void requireTransactionOwnership(
            Txn transaction,
            Company company,
            Set<Txn> includedTransactions)
    {
        Objects.requireNonNull(transaction, "transaction");
        if (transaction.getCompany() != company)
        {
            throw new IllegalArgumentException("transaction is outside the selected company");
        }
        Objects.requireNonNull(transaction.getPortableId(), "transaction portableId");
        if (transaction.getPayee() != null && transaction.getPayee().getCompany() != company)
        {
            throw new IllegalArgumentException("transaction payee is outside the selected company");
        }
        if (transaction.getReplacementFor() != null && !includedTransactions.contains(transaction.getReplacementFor()))
        {
            throw new IllegalArgumentException("replacement transaction target is outside the exported snapshot");
        }
        if (transaction.getReversalOf() != null && !includedTransactions.contains(transaction.getReversalOf()))
        {
            throw new IllegalArgumentException("reversal transaction target is outside the exported snapshot");
        }
    }

    private static void requireTransactionLineOwnership(
            TxnSplit line,
            Company company,
            ChartOfAccounts activeChart,
            Set<Txn> includedTransactions)
    {
        Objects.requireNonNull(line, "transaction line");
        if (!includedTransactions.contains(line.getTxn()))
        {
            throw new IllegalArgumentException("transaction line references a transaction outside the exported snapshot");
        }
        requireTransactionOwnership(line.getTxn(), company, includedTransactions);
        requireAccountOwnership(line.getAccount(), activeChart);
        requireFundOwnership(line.getFund(), company);
        if (line.getBudgetCategory() != null && line.getBudgetCategory().getCompany() != company)
        {
            throw new IllegalArgumentException("transaction line budget category is outside the selected company");
        }
        if (line.getActivity() != null && line.getActivity().getCompany() != company)
        {
            throw new IllegalArgumentException("transaction line activity is outside the selected company");
        }
        if (line.getMerchant() != null && line.getMerchant().getCompany() != company)
        {
            throw new IllegalArgumentException("transaction line merchant is outside the selected company");
        }
    }

    private static <T> Set<T> identitySet(List<T> values)
    {
        Set<T> result = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(values);
        return result;
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
