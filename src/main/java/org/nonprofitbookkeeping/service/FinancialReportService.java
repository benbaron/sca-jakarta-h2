package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * M1/M2 reporting projections:
 * - Trial Balance
 * - General Ledger Detail
 * - Balance Sheet
 * - Income Statement
 */
public class FinancialReportService
{
    private final Jpa jpa;
    private final Supplier<String> activeCompanyCode;

    public FinancialReportService(Jpa jpa)
    {
        this(jpa, () -> null);
    }

    public FinancialReportService(Jpa jpa, Supplier<String> activeCompanyCode)
    {
        this.jpa = jpa;
        this.activeCompanyCode = activeCompanyCode == null ? () -> null : activeCompanyCode;
    }

    public TrialBalanceReport trialBalance(LocalDate asOf, String fundCode)
    {
        LocalDate cutoff = asOf == null ? LocalDate.now() : asOf;
        List<Account> accounts = listPostingAccounts();
        Map<Long, BigDecimal> activityByAccount = loadActivityUpTo(cutoff, fundCode);

        List<TrialBalanceRow> rows = new ArrayList<>();
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (Account account : accounts)
        {
            BigDecimal balance = signedBalance(account, activityByAccount.get(account.getId()));
            BigDecimal debit = BigDecimal.ZERO;
            BigDecimal credit = BigDecimal.ZERO;
            if (account.getNormalBalance() == NormalBalance.DEBIT)
            {
                debit = positiveOrZero(balance);
                credit = positiveOrZero(balance.negate());
            }
            else
            {
                credit = positiveOrZero(balance);
                debit = positiveOrZero(balance.negate());
            }

            if (debit.signum() == 0 && credit.signum() == 0)
            {
                continue;
            }

            totalDebits = totalDebits.add(debit);
            totalCredits = totalCredits.add(credit);
            rows.add(new TrialBalanceRow(account.getCode(), account.getName(), debit, credit));
        }

        rows.sort(Comparator.comparing(TrialBalanceRow::accountCode));
        return new TrialBalanceReport(cutoff, rows, totalDebits, totalCredits);
    }

    public List<GeneralLedgerRow> generalLedgerDetail(LocalDate from, LocalDate to, String fundCode, int maxRows)
    {
        return generalLedgerDetail(from, to, fundCode, maxRows, null);
    }

    public List<GeneralLedgerRow> generalLedgerDetail(
            LocalDate from,
            LocalDate to,
            String fundCode,
            int maxRows,
            Long accountId)
    {
        LocalDate start = from == null ? LocalDate.MIN : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        String companyCode = companyCode();

        try (EntityManager em = jpa.em())
        {
            var query = em.createQuery(
                            "select t.txnDate, t.id, coalesce(t.memo, ''), coalesce(p.displayName, ''), " +
                                    "a.code, a.name, f.code, f.name, a.normalBalance, s.amountSigned " +
                                    "from TxnSplit s " +
                                    "join s.txn t " +
                                    "join s.account a " +
                                    "join s.fund f " +
                                    "left join t.payee p " +
                                    "where t.txnDate >= :start and t.txnDate <= :end " +
                                    companyFilter(companyCode) +
                                    "and (:fundCode is null or f.code = :fundCode) " +
                                    "and (:accountId is null or a.id = :accountId) " +
                                    "order by t.txnDate, t.id, a.code", Object[].class)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .setParameter("fundCode", blankToNull(fundCode))
                    .setParameter("accountId", accountId)
                    .setMaxResults(maxRows <= 0 ? 500 : maxRows);
            setCompanyParameter(query, companyCode);
            List<Object[]> rows = query.getResultList();

            List<GeneralLedgerRow> out = new ArrayList<>();
            for (Object[] r : rows)
            {
                NormalBalance normal = (NormalBalance) r[8];
                BigDecimal amount = (BigDecimal) r[9];
                BigDecimal debit = BigDecimal.ZERO;
                BigDecimal credit = BigDecimal.ZERO;
                if (normal == NormalBalance.DEBIT)
                {
                    debit = positiveOrZero(amount);
                    credit = positiveOrZero(amount.negate());
                }
                else
                {
                    credit = positiveOrZero(amount);
                    debit = positiveOrZero(amount.negate());
                }

                out.add(new GeneralLedgerRow(
                        (LocalDate) r[0],
                        (Long) r[1],
                        (String) r[2],
                        (String) r[3],
                        (String) r[4],
                        (String) r[5],
                        (String) r[6],
                        (String) r[7],
                        debit,
                        credit));
            }
            return out;
        }
    }

    public BalanceSheetReport balanceSheet(LocalDate asOf, String fundCode)
    {
        LocalDate cutoff = asOf == null ? LocalDate.now() : asOf;
        List<Account> accounts = listPostingAccounts();
        Map<Long, BigDecimal> activityByAccount = loadActivityUpTo(cutoff, fundCode);

        List<StatementRow> assets = new ArrayList<>();
        List<StatementRow> liabilities = new ArrayList<>();
        List<StatementRow> equity = new ArrayList<>();

        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;

        for (Account account : accounts)
        {
            BigDecimal naturalBalance = signedBalance(account, activityByAccount.get(account.getId()));
            StatementRow row = statementRow(account, naturalBalance);

            if (account.getAccountType() == AccountType.ASSET)
            {
                assets.add(row);
                totalAssets = totalAssets.add(naturalBalance);
            }
            else if (account.getAccountType() == AccountType.LIABILITY)
            {
                liabilities.add(row);
                totalLiabilities = totalLiabilities.add(naturalBalance);
            }
            else if (account.getAccountType() == AccountType.EQUITY)
            {
                equity.add(row);
                totalEquity = totalEquity.add(naturalBalance);
            }
        }

        BigDecimal currentEarnings = loadNetIncomeUpTo(cutoff, fundCode);
        if (currentEarnings.signum() != 0)
        {
            StatementRow retainedEarnings = new StatementRow(
                    "9999",
                    "Current Period Earnings",
                    AccountType.EQUITY,
                    null,
                    null,
                    null,
                    null,
                    null,
                    currentEarnings);
            equity.add(retainedEarnings);
            totalEquity = totalEquity.add(currentEarnings);
        }

        assets.sort(Comparator.comparing(StatementRow::accountCode));
        liabilities.sort(Comparator.comparing(StatementRow::accountCode));
        equity.sort(Comparator.comparing(StatementRow::accountCode));

        return new BalanceSheetReport(cutoff, assets, liabilities, equity, totalAssets, totalLiabilities, totalEquity);
    }

    public IncomeStatementReport incomeStatement(LocalDate from, LocalDate to, String fundCode)
    {
        LocalDate start = from == null ? LocalDate.now().withDayOfYear(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        List<Account> accounts = listPostingAccounts();
        Map<Long, BigDecimal> activityByAccount = loadActivityBetween(start, end, fundCode);
        List<StatementRow> incomeRows = new ArrayList<>();
        List<StatementRow> expenseRows = new ArrayList<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (Account account : accounts)
        {
            BigDecimal amount = activityByAccount.getOrDefault(account.getId(), BigDecimal.ZERO);
            if (account.getAccountType() == AccountType.INCOME)
            {
                incomeRows.add(statementRow(account, amount));
                totalIncome = totalIncome.add(amount);
            }
            else if (account.getAccountType() == AccountType.EXPENSE)
            {
                expenseRows.add(statementRow(account, amount));
                totalExpense = totalExpense.add(amount);
            }
        }

        return new IncomeStatementReport(start, end, incomeRows, expenseRows, totalIncome, totalExpense);
    }

    private List<Account> listPostingAccounts()
    {
        String companyCode = companyCode();
        try (EntityManager em = jpa.em())
        {
            var query = em.createQuery(
                            "select distinct a from Account a " +
                                    "left join fetch a.parent p " +
                                    "left join fetch p.parent " +
                                    "where a.active = true and a.posting = true " +
                                    activeChartFilter(companyCode) +
                                    "order by a.code", Account.class);
            setCompanyParameter(query, companyCode);
            return query.getResultList();
        }
    }


    private BigDecimal loadNetIncomeUpTo(LocalDate asOf, String fundCode)
    {
        String companyCode = companyCode();
        try (EntityManager em = jpa.em())
        {
            var query = em.createQuery(
                            "select a.accountType, coalesce(sum(s.amountSigned), 0) " +
                                    "from TxnSplit s " +
                                    "join s.account a " +
                                    "join s.txn t " +
                                    "join s.fund f " +
                                    "where t.txnDate <= :asOf " +
                                    companyFilter(companyCode) +
                                    "and a.accountType in (:incomeType, :expenseType) " +
                                    "and (:fundCode is null or f.code = :fundCode) " +
                                    "group by a.accountType", Object[].class)
                    .setParameter("asOf", asOf)
                    .setParameter("incomeType", AccountType.INCOME)
                    .setParameter("expenseType", AccountType.EXPENSE)
                    .setParameter("fundCode", blankToNull(fundCode));
            setCompanyParameter(query, companyCode);
            List<Object[]> rows = query.getResultList();

            BigDecimal income = BigDecimal.ZERO;
            BigDecimal expense = BigDecimal.ZERO;
            for (Object[] row : rows)
            {
                AccountType type = (AccountType) row[0];
                BigDecimal amount = (BigDecimal) row[1];
                if (type == AccountType.INCOME)
                {
                    income = income.add(amount);
                }
                else
                {
                    expense = expense.add(amount);
                }
            }
            return income.subtract(expense);
        }
    }

    private Map<Long, BigDecimal> loadActivityUpTo(LocalDate asOf, String fundCode)
    {
        String companyCode = companyCode();
        try (EntityManager em = jpa.em())
        {
            var query = em.createQuery(
                            "select a.id, coalesce(sum(s.amountSigned), 0) " +
                                    "from TxnSplit s " +
                                    "join s.account a " +
                                    "join s.txn t " +
                                    "join s.fund f " +
                                    "where t.txnDate <= :asOf " +
                                    "and (:fundCode is null or f.code = :fundCode) " +
                                    companyFilter(companyCode) +
                                    "group by a.id", Object[].class)
                    .setParameter("asOf", asOf)
                    .setParameter("fundCode", blankToNull(fundCode));
            setCompanyParameter(query, companyCode);
            List<Object[]> rows = query.getResultList();

            Map<Long, BigDecimal> out = new HashMap<>();
            for (Object[] row : rows)
            {
                out.put((Long) row[0], (BigDecimal) row[1]);
            }
            return out;
        }
    }

    private Map<Long, BigDecimal> loadActivityBetween(
            LocalDate start,
            LocalDate end,
            String fundCode)
    {
        String companyCode = companyCode();
        try (EntityManager em = jpa.em())
        {
            var query = em.createQuery(
                            "select a.id, coalesce(sum(s.amountSigned), 0) " +
                                    "from TxnSplit s " +
                                    "join s.account a " +
                                    "join s.txn t " +
                                    "join s.fund f " +
                                    "where t.txnDate >= :start and t.txnDate <= :end " +
                                    companyFilter(companyCode) +
                                    "and (:fundCode is null or f.code = :fundCode) " +
                                    "group by a.id", Object[].class)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .setParameter("fundCode", blankToNull(fundCode));
            setCompanyParameter(query, companyCode);
            List<Object[]> rows = query.getResultList();

            Map<Long, BigDecimal> out = new HashMap<>();
            for (Object[] row : rows)
            {
                out.put((Long) row[0], (BigDecimal) row[1]);
            }
            return out;
        }
    }

    private static StatementRow statementRow(Account account, BigDecimal amount)
    {
        Account parent = account.getParent();
        Account grandparent = parent == null ? null : parent.getParent();
        return new StatementRow(
                account.getCode(),
                account.getName(),
                account.getAccountType(),
                account.getSubtype(),
                parent == null ? null : parent.getCode(),
                parent == null ? null : parent.getName(),
                grandparent == null ? null : grandparent.getCode(),
                grandparent == null ? null : grandparent.getName(),
                amount == null ? BigDecimal.ZERO : amount);
    }

    private String companyCode()
    {
        return blankToNull(activeCompanyCode.get());
    }

    private static String companyFilter(String companyCode)
    {
        return companyCode == null ? "" : "and a.chart.company.code = :companyCode ";
    }

    private static String activeChartFilter(String companyCode)
    {
        return companyCode == null ? "" :
                "and a.chart.company.code = :companyCode " +
                "and a.chart.company.activeChartOfAccounts = a.chart ";
    }

    private static void setCompanyParameter(
            TypedQuery<?> query,
            String companyCode)
    {
        if (companyCode != null)
        {
            query.setParameter("companyCode", companyCode);
        }
    }

    private static BigDecimal signedBalance(Account account, BigDecimal activity)
    {
        return account.getOpeningBalance().add(activity == null ? BigDecimal.ZERO : activity);
    }

    private static BigDecimal positiveOrZero(BigDecimal value)
    {
        return value.signum() > 0 ? value : BigDecimal.ZERO;
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value;
    }

    public record TrialBalanceRow(String accountCode, String accountName, BigDecimal debit, BigDecimal credit)
    {
    }

    public record TrialBalanceReport(LocalDate asOf,
                                     List<TrialBalanceRow> rows,
                                     BigDecimal totalDebits,
                                     BigDecimal totalCredits)
    {
        public boolean isBalanced()
        {
            return totalDebits.compareTo(totalCredits) == 0;
        }
    }

    public record GeneralLedgerRow(LocalDate txnDate,
                                   Long txnId,
                                   String memo,
                                   String payee,
                                   String accountCode,
                                   String accountName,
                                   String fundCode,
                                   String fundName,
                                   BigDecimal debit,
                                   BigDecimal credit)
    {
    }

    public record StatementRow(
            String accountCode,
            String accountName,
            AccountType accountType,
            AccountSubtype subtype,
            String parentCode,
            String parentName,
            String grandparentCode,
            String grandparentName,
            BigDecimal amount)
    {
        public StatementRow(String accountCode, String accountName, BigDecimal amount)
        {
            this(accountCode, accountName, null, null, null, null, null, null, amount);
        }
    }

    public record BalanceSheetReport(LocalDate asOf,
                                     List<StatementRow> assets,
                                     List<StatementRow> liabilities,
                                     List<StatementRow> equity,
                                     BigDecimal totalAssets,
                                     BigDecimal totalLiabilities,
                                     BigDecimal totalEquity)
    {
        public BigDecimal liabilitiesAndEquity()
        {
            return totalLiabilities.add(totalEquity);
        }

        public boolean isBalanced()
        {
            return totalAssets.compareTo(liabilitiesAndEquity()) == 0;
        }
    }

    public record IncomeStatementReport(LocalDate start,
                                        LocalDate end,
                                        List<StatementRow> income,
                                        List<StatementRow> expenses,
                                        BigDecimal totalIncome,
                                        BigDecimal totalExpense)
    {
        public BigDecimal netIncome()
        {
            return totalIncome.subtract(totalExpense);
        }
    }
}
