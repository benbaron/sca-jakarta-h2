package org.nonprofitbookkeeping.service.dashboard;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** JPA implementation of the production dashboard projection. */
@ApplicationScoped
public class JpaDashboardQueryService implements DashboardQueryService
{
    private final Jpa jpa;

    @Inject
    public JpaDashboardQueryService(Jpa jpa)
    {
        this.jpa = jpa;
    }

    @Override
    public DashboardSnapshot load(LocalDate asOfDate, int recentTransactionLimit)
    {
        return load("", asOfDate, recentTransactionLimit);
    }

    @Override
    public DashboardSnapshot load(
            String groupCode,
            LocalDate asOfDate,
            int recentTransactionLimit)
    {
        if (asOfDate == null)
        {
            throw new IllegalArgumentException("asOfDate is required");
        }
        if (recentTransactionLimit <= 0)
        {
            throw new IllegalArgumentException("recentTransactionLimit must be positive");
        }

        String normalizedGroupCode = groupCode == null ? "" : groupCode.trim();

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                BigDecimal bookCash = loadBookCash(em, asOfDate);
                BigDecimal yearToDate = loadYearToDateSurplus(em, asOfDate);
                Map<String, BigDecimal> fundClassTotals = loadFundClassTotals(em, asOfDate);
                List<DashboardSnapshot.BankAccountBalance> bankAccounts = loadBankAccounts(em, asOfDate);
                List<DashboardSnapshot.RecentTransaction> recentTransactions =
                        loadRecentTransactions(em, asOfDate, recentTransactionLimit);
                DashboardSnapshot.OpenItemSummary openItems =
                        loadOpenItems(em, normalizedGroupCode, asOfDate);
                List<DashboardSnapshot.ReconciliationStatus> reconciliations =
                        loadReconciliations(em, normalizedGroupCode, asOfDate);
                List<DashboardSnapshot.BudgetActual> budgetActuals =
                        loadBudgetActuals(em, asOfDate);
                DashboardSnapshot.OrganizationSummary organization =
                        loadOrganization(em, normalizedGroupCode);
                DashboardSnapshot.PeriodSummary period = loadPeriod(em, asOfDate);
                List<DashboardSnapshot.MonthlyResult> monthlyResults =
                        loadMonthlyResults(em, asOfDate);

                em.getTransaction().commit();
                return new DashboardSnapshot(
                        asOfDate,
                        bookCash,
                        Optional.empty(),
                        Optional.empty(),
                        yearToDate,
                        Map.copyOf(fundClassTotals),
                        List.copyOf(bankAccounts),
                        List.copyOf(recentTransactions),
                        openItems,
                        List.copyOf(reconciliations),
                        List.copyOf(budgetActuals),
                        organization,
                        period,
                        List.copyOf(monthlyResults));
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive())
                {
                    em.getTransaction().rollback();
                }
                throw ex;
            }
        }
    }

    private static BigDecimal loadBookCash(EntityManager em, LocalDate asOfDate)
    {
        return decimal(em.createQuery("""
                select coalesce(sum(s.amountSigned), 0)
                from TxnSplit s
                where s.txn.txnDate <= :asOf
                  and s.txn.status = 'ENTERED'
                  and s.account.accountType = :assetType
                  and s.account.subtype = :cashSubtype
                """, BigDecimal.class)
                .setParameter("asOf", asOfDate)
                .setParameter("assetType", AccountType.ASSET)
                .setParameter("cashSubtype", AccountSubtype.CASH)
                .getSingleResult());
    }

    private static BigDecimal loadYearToDateSurplus(EntityManager em, LocalDate asOfDate)
    {
        return decimal(em.createQuery("""
                select coalesce(sum(case
                    when s.account.accountType = :incomeType then -s.amountSigned
                    when s.account.accountType = :expenseType then -s.amountSigned
                    else 0 end), 0)
                from TxnSplit s
                where s.txn.txnDate between :start and :asOf
                  and s.txn.status = 'ENTERED'
                """, BigDecimal.class)
                .setParameter("incomeType", AccountType.INCOME)
                .setParameter("expenseType", AccountType.EXPENSE)
                .setParameter("start", LocalDate.of(asOfDate.getYear(), 1, 1))
                .setParameter("asOf", asOfDate)
                .getSingleResult());
    }

    private static Map<String, BigDecimal> loadFundClassTotals(EntityManager em, LocalDate asOfDate)
    {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        List<Object[]> rows = em.createQuery("""
                select f.fundType, coalesce(sum(case
                    when a.accountType = :equityType then -s.amountSigned
                    when a.accountType = :incomeType then -s.amountSigned
                    when a.accountType = :expenseType then -s.amountSigned
                    else 0 end), 0)
                from TxnSplit s
                join s.fund f
                join s.account a
                where s.txn.txnDate <= :asOf
                  and s.txn.status = 'ENTERED'
                group by f.fundType
                order by f.fundType
                """, Object[].class)
                .setParameter("equityType", AccountType.EQUITY)
                .setParameter("incomeType", AccountType.INCOME)
                .setParameter("expenseType", AccountType.EXPENSE)
                .setParameter("asOf", asOfDate)
                .getResultList();
        for (Object[] row : rows)
        {
            totals.put(String.valueOf(row[0]), decimal(row[1]));
        }
        return totals;
    }

    private static List<DashboardSnapshot.BankAccountBalance> loadBankAccounts(
            EntityManager em,
            LocalDate asOfDate)
    {
        return em.createQuery("""
                select new org.nonprofitbookkeeping.service.dashboard.DashboardSnapshot$BankAccountBalance(
                    a.id, a.code, a.name, coalesce(sum(s.amountSigned), 0))
                from TxnSplit s join s.account a
                where s.txn.txnDate <= :asOf
                  and s.txn.status = 'ENTERED'
                  and a.accountFunction = :bankFunction
                group by a.id, a.code, a.name
                order by abs(sum(s.amountSigned)) desc, a.code
                """, DashboardSnapshot.BankAccountBalance.class)
                .setParameter("asOf", asOfDate)
                .setParameter("bankFunction", AccountFunction.BANK)
                .getResultList();
    }

    private static List<DashboardSnapshot.RecentTransaction> loadRecentTransactions(
            EntityManager em,
            LocalDate asOfDate,
            int limit)
    {
        List<Object[]> headers = em.createQuery("""
                select t.id, t.txnDate, coalesce(t.memo, ''), t.status,
                       coalesce(p.displayName, '')
                from Txn t left join t.payee p
                where t.txnDate <= :asOf
                order by t.txnDate desc, t.id desc
                """, Object[].class)
                .setParameter("asOf", asOfDate)
                .setMaxResults(limit)
                .getResultList();

        Map<Long, RecentAccumulator> accumulators = new LinkedHashMap<>();
        for (Object[] row : headers)
        {
            long transactionId = ((Number) row[0]).longValue();
            String memo = string(row[2]);
            String payee = string(row[4]);
            accumulators.put(transactionId, new RecentAccumulator(
                    transactionId,
                    localDate(row[1]),
                    description(payee, memo),
                    string(row[3])));
        }

        if (accumulators.isEmpty())
        {
            return List.of();
        }

        List<Object[]> splitRows = em.createQuery("""
                select s.txn.id, a.code, a.name, a.normalBalance, a.accountType, a.accountFunction,
                       f.code, f.name, s.amountSigned, bc.id
                from TxnSplit s
                join s.account a
                join s.fund f
                left join s.budgetCategory bc
                where s.txn.id in :transactionIds
                order by s.txn.txnDate, s.txn.id, s.id
                """, Object[].class)
                .setParameter("transactionIds", new ArrayList<>(accumulators.keySet()))
                .getResultList();

        for (Object[] row : splitRows)
        {
            long transactionId = ((Number) row[0]).longValue();
            RecentAccumulator accumulator = accumulators.get(transactionId);
            if (accumulator != null)
            {
                accumulator.addSplit(
                        string(row[1]),
                        string(row[2]),
                        (NormalBalance) row[3],
                        (AccountType) row[4],
                        (AccountFunction) row[5],
                        string(row[6]),
                        string(row[7]),
                        decimal(row[8]),
                        row[9] != null);
            }
        }

        assignRunningBankBalances(em, accumulators);
        return accumulators.values().stream()
                .map(RecentAccumulator::toSnapshot)
                .toList();
    }

    private static void assignRunningBankBalances(
            EntityManager em,
            Map<Long, RecentAccumulator> accumulators)
    {
        long bankAccountCount = em.createQuery("""
                select count(a)
                from Account a
                where a.accountFunction = :bankFunction
                """, Long.class)
                .setParameter("bankFunction", AccountFunction.BANK)
                .getSingleResult();
        if (bankAccountCount == 0)
        {
            return;
        }

        List<RecentAccumulator> chronological = accumulators.values().stream()
                .sorted(Comparator
                        .comparing(RecentAccumulator::transactionDate)
                        .thenComparingLong(RecentAccumulator::transactionId))
                .toList();
        RecentAccumulator first = chronological.get(0);

        BigDecimal runningBalance = decimal(em.createQuery("""
                select coalesce(sum(s.amountSigned), 0)
                from TxnSplit s
                where s.txn.status = 'ENTERED'
                  and s.account.accountFunction = :bankFunction
                  and (s.txn.txnDate < :firstDate
                       or (s.txn.txnDate = :firstDate and s.txn.id < :firstId))
                """, BigDecimal.class)
                .setParameter("bankFunction", AccountFunction.BANK)
                .setParameter("firstDate", first.transactionDate())
                .setParameter("firstId", first.transactionId())
                .getSingleResult());

        for (RecentAccumulator accumulator : chronological)
        {
            runningBalance = runningBalance.add(accumulator.bankDelta());
            if (accumulator.posted())
            {
                accumulator.setRunningBankBalance(runningBalance);
            }
        }
    }

    private static DashboardSnapshot.OpenItemSummary loadOpenItems(
            EntityManager em,
            String groupCode,
            LocalDate asOfDate)
    {
        if (groupCode.isBlank())
        {
            return new DashboardSnapshot.OpenItemSummary(
                    Map.of(),
                    Map.of(),
                    0L,
                    BigDecimal.ZERO);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT item_kind, COUNT(*), COALESCE(SUM(open_amount), 0)
                FROM open_item_snapshot
                WHERE group_code = ?1
                  AND last_updated_on <= ?2
                  AND open_amount <> 0
                GROUP BY item_kind
                ORDER BY item_kind
                """)
                .setParameter(1, groupCode)
                .setParameter(2, Date.valueOf(asOfDate))
                .getResultList();

        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        long totalCount = 0L;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Object[] row : rows)
        {
            String itemKind = string(row[0]);
            long count = ((Number) row[1]).longValue();
            BigDecimal amount = decimal(row[2]);
            counts.put(itemKind, count);
            amounts.put(itemKind, amount);
            totalCount += count;
            totalAmount = totalAmount.add(amount);
        }
        return new DashboardSnapshot.OpenItemSummary(
                Map.copyOf(counts),
                Map.copyOf(amounts),
                totalCount,
                totalAmount);
    }

    private static List<DashboardSnapshot.ReconciliationStatus> loadReconciliations(
            EntityManager em,
            String groupCode,
            LocalDate asOfDate)
    {
        if (groupCode.isBlank())
        {
            return List.of();
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT statement_ending_on, bank_format, status, imported_transaction_count
                FROM reconciliation_run
                WHERE group_code = ?1
                  AND statement_ending_on <= ?2
                ORDER BY statement_ending_on DESC, created_at DESC
                """)
                .setParameter(1, groupCode)
                .setParameter(2, Date.valueOf(asOfDate))
                .setMaxResults(4)
                .getResultList();

        return rows.stream()
                .map(row -> new DashboardSnapshot.ReconciliationStatus(
                        localDate(row[0]),
                        string(row[1]),
                        string(row[2]),
                        ((Number) row[3]).intValue()))
                .toList();
    }

    private static List<DashboardSnapshot.BudgetActual> loadBudgetActuals(
            EntityManager em,
            LocalDate asOfDate)
    {
        Map<String, BudgetActualAccumulator> accumulators = new LinkedHashMap<>();
        List<Long> activePlanIds = em.createQuery("""
                select p.id from BudgetPlan p
                where p.fiscalYear = :year and p.status = :status
                order by p.activatedAt desc, p.id desc
                """, Long.class)
                .setParameter("year", asOfDate.getYear())
                .setParameter("status", BudgetPlan.Status.ACTIVE)
                .setMaxResults(1)
                .getResultList();
        if (!activePlanIds.isEmpty())
        {
            List<Object[]> budgetRows = em.createQuery("""
                    select bc.code, bc.name, coalesce(sum(l.amount), 0)
                    from BudgetLine l
                    join l.budgetCategory bc
                    where l.budgetPlan.id = :planId
                      and (l.periodMonth is null or l.periodMonth <= :period)
                    group by bc.code, bc.name
                    order by bc.code
                    """, Object[].class)
                    .setParameter("planId", activePlanIds.get(0))
                    .setParameter("period", java.time.YearMonth.from(asOfDate).toString())
                    .getResultList();
            for (Object[] row : budgetRows)
            {
                accumulator(accumulators, row).budget = Optional.of(decimal(row[2]));
            }
        }

        List<Object[]> actualRows = em.createQuery("""
                select bc.code, bc.name,
                       coalesce(sum(case
                           when a.accountType = :incomeType then -s.amountSigned
                           when a.accountType = :expenseType then s.amountSigned
                           else 0 end), 0)
                from TxnSplit s
                join s.budgetCategory bc
                join s.account a
                where s.txn.txnDate between :start and :asOf
                  and s.txn.status = 'ENTERED'
                group by bc.code, bc.name
                order by bc.code
                """, Object[].class)
                .setParameter("incomeType", AccountType.INCOME)
                .setParameter("expenseType", AccountType.EXPENSE)
                .setParameter("start", LocalDate.of(asOfDate.getYear(), 1, 1))
                .setParameter("asOf", asOfDate)
                .getResultList();
        for (Object[] row : actualRows)
        {
            accumulator(accumulators, row).actual = decimal(row[2]);
        }

        return accumulators.values().stream()
                .map(BudgetActualAccumulator::toSnapshot)
                .toList();
    }

    private static BudgetActualAccumulator accumulator(Map<String, BudgetActualAccumulator> rows, Object[] row)
    {
        String code = string(row[0]);
        return rows.computeIfAbsent(code, ignored -> new BudgetActualAccumulator(code, string(row[1])));
    }

    private static DashboardSnapshot.OrganizationSummary loadOrganization(
            EntityManager em,
            String groupCode)
    {
        if (groupCode.isBlank())
        {
            return DashboardSnapshot.OrganizationSummary.unavailable(groupCode);
        }

        List<Object[]> rows = em.createQuery("""
                select c.code, c.displayName, coalesce(c.branchType, ''),
                       coalesce(c.parentOrganization, ''), c.active, c.defaultCurrency
                from Company c
                where c.code = :code
                """, Object[].class)
                .setParameter("code", groupCode)
                .setMaxResults(1)
                .getResultList();
        if (rows.isEmpty())
        {
            return DashboardSnapshot.OrganizationSummary.unavailable(groupCode);
        }

        Object[] row = rows.get(0);
        return new DashboardSnapshot.OrganizationSummary(
                string(row[0]),
                string(row[1]),
                string(row[2]),
                string(row[3]),
                Boolean.TRUE.equals(row[4]),
                string(row[5]));
    }

    private static DashboardSnapshot.PeriodSummary loadPeriod(
            EntityManager em,
            LocalDate asOfDate)
    {
        List<Object[]> rows = em.createQuery("""
                select p.fiscalYear, p.periodNumber, p.startDate, p.endDate, p.status
                from AccountingPeriod p
                where p.startDate <= :asOf
                  and p.endDate >= :asOf
                order by p.startDate desc
                """, Object[].class)
                .setParameter("asOf", asOfDate)
                .setMaxResults(1)
                .getResultList();
        if (rows.isEmpty())
        {
            return DashboardSnapshot.PeriodSummary.unavailable();
        }

        Object[] row = rows.get(0);
        return new DashboardSnapshot.PeriodSummary(
                Optional.of(((Number) row[0]).intValue()),
                Optional.of(((Number) row[1]).intValue()),
                Optional.of(localDate(row[2])),
                Optional.of(localDate(row[3])),
                string(row[4]));
    }

    private static List<DashboardSnapshot.MonthlyResult> loadMonthlyResults(
            EntityManager em,
            LocalDate asOfDate)
    {
        List<Object[]> rows = em.createQuery("""
                select month(s.txn.txnDate),
                       coalesce(sum(case
                           when s.account.accountType = :incomeType then -s.amountSigned
                           when s.account.accountType = :expenseType then -s.amountSigned
                           else 0 end), 0)
                from TxnSplit s
                where s.txn.txnDate between :start and :asOf
                  and s.txn.status = 'ENTERED'
                group by month(s.txn.txnDate)
                order by month(s.txn.txnDate)
                """, Object[].class)
                .setParameter("incomeType", AccountType.INCOME)
                .setParameter("expenseType", AccountType.EXPENSE)
                .setParameter("start", LocalDate.of(asOfDate.getYear(), 1, 1))
                .setParameter("asOf", asOfDate)
                .getResultList();

        Map<Integer, BigDecimal> byMonth = new LinkedHashMap<>();
        for (Object[] row : rows)
        {
            byMonth.put(((Number) row[0]).intValue(), decimal(row[1]));
        }

        List<DashboardSnapshot.MonthlyResult> results = new ArrayList<>();
        for (int month = 1; month <= asOfDate.getMonthValue(); month++)
        {
            results.add(new DashboardSnapshot.MonthlyResult(
                    month,
                    byMonth.getOrDefault(month, BigDecimal.ZERO)));
        }
        return results;
    }

    private static BigDecimal decimal(Object value)
    {
        return value == null ? BigDecimal.ZERO : (BigDecimal) value;
    }

    private static String string(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private static LocalDate localDate(Object value)
    {
        if (value instanceof LocalDate localDate)
        {
            return localDate;
        }
        if (value instanceof Date date)
        {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp)
        {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        throw new IllegalArgumentException("Unsupported date value: " + value);
    }

    private static String description(String payee, String memo)
    {
        if (payee.isBlank())
        {
            return memo;
        }
        if (memo.isBlank())
        {
            return payee;
        }
        return payee + " — " + memo;
    }

    private static final class BudgetActualAccumulator
    {
        private final String categoryCode;
        private final String categoryName;
        private Optional<BigDecimal> budget = Optional.empty();
        private BigDecimal actual = BigDecimal.ZERO;

        private BudgetActualAccumulator(String categoryCode, String categoryName)
        {
            this.categoryCode = categoryCode;
            this.categoryName = categoryName;
        }

        private DashboardSnapshot.BudgetActual toSnapshot()
        {
            return new DashboardSnapshot.BudgetActual(categoryCode, categoryName, budget, actual);
        }
    }

    private static final class RecentAccumulator
    {
        private final long transactionId;
        private final LocalDate transactionDate;
        private final String description;
        private final String status;
        private final Set<String> accounts = new LinkedHashSet<>();
        private final Set<String> funds = new LinkedHashSet<>();
        private BigDecimal debitTotal = BigDecimal.ZERO;
        private BigDecimal creditTotal = BigDecimal.ZERO;
        private BigDecimal bankDelta = BigDecimal.ZERO;
        private Optional<BigDecimal> runningBankBalance = Optional.empty();
        private boolean affectsBank;
        private boolean affectsBudget;

        private RecentAccumulator(
                long transactionId,
                LocalDate transactionDate,
                String description,
                String status)
        {
            this.transactionId = transactionId;
            this.transactionDate = transactionDate;
            this.description = description;
            this.status = status;
        }

        private long transactionId()
        {
            return transactionId;
        }

        private LocalDate transactionDate()
        {
            return transactionDate;
        }

        private BigDecimal bankDelta()
        {
            return bankDelta;
        }

        private boolean posted()
        {
            return "ENTERED".equals(status);
        }

        private void setRunningBankBalance(BigDecimal value)
        {
            runningBankBalance = Optional.of(value);
        }

        private void addSplit(
                String accountCode,
                String accountName,
                NormalBalance normalBalance,
                AccountType accountType,
                AccountFunction accountFunction,
                String fundCode,
                String fundName,
                BigDecimal amountSigned,
                boolean hasBudgetCategory)
        {
            accounts.add(accountCode + " " + accountName);
            funds.add(fundCode + " " + fundName);

            boolean debit = normalBalance == NormalBalance.DEBIT
                    ? amountSigned.signum() >= 0
                    : amountSigned.signum() < 0;
            if (debit)
            {
                debitTotal = debitTotal.add(amountSigned.abs());
            }
            else
            {
                creditTotal = creditTotal.add(amountSigned.abs());
            }

            if (posted() && accountFunction == AccountFunction.BANK)
            {
                affectsBank = true;
                bankDelta = bankDelta.add(amountSigned);
            }
            if (posted()
                    && hasBudgetCategory
                    && (accountType == AccountType.INCOME || accountType == AccountType.EXPENSE))
            {
                affectsBudget = true;
            }
        }

        private DashboardSnapshot.RecentTransaction toSnapshot()
        {
            return new DashboardSnapshot.RecentTransaction(
                    transactionId,
                    transactionDate,
                    description,
                    String.join(", ", accounts),
                    String.join(", ", funds),
                    debitTotal,
                    creditTotal,
                    runningBankBalance,
                    affectsBank,
                    affectsBudget,
                    status);
        }
    }
}
