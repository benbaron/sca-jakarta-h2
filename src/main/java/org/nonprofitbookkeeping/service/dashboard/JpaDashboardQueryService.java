package org.nonprofitbookkeeping.service.dashboard;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
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
    public DashboardSnapshot load(String groupCode, LocalDate asOfDate, int recentTransactionLimit)
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
                DashboardSnapshot snapshot = new DashboardSnapshot(
                        asOfDate,
                        loadBookCash(em, asOfDate),
                        Optional.empty(),
                        Optional.empty(),
                        loadYearToDateSurplus(em, asOfDate),
                        Map.copyOf(loadFundClassTotals(em, asOfDate)),
                        List.copyOf(loadBankAccounts(em, asOfDate)),
                        List.copyOf(loadRecentTransactions(em, asOfDate, recentTransactionLimit)),
                        loadOpenItems(em, normalizedGroupCode, asOfDate),
                        List.copyOf(loadReconciliations(em, normalizedGroupCode, asOfDate)),
                        List.copyOf(loadBudgetActuals(em, asOfDate)));
                em.getTransaction().commit();
                return snapshot;
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
                  and s.account.accountType = :bankType
                """, BigDecimal.class)
                .setParameter("asOf", asOfDate)
                .setParameter("bankType", AccountType.BANK)
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
                select f.fundType, coalesce(sum(s.amountSigned), 0)
                from TxnSplit s join s.fund f
                where s.txn.txnDate <= :asOf
                  and s.txn.status = 'ENTERED'
                group by f.fundType
                order by f.fundType
                """, Object[].class)
                .setParameter("asOf", asOfDate)
                .getResultList();
        for (Object[] row : rows)
        {
            totals.put(String.valueOf(row[0]), decimal(row[1]));
        }
        return totals;
    }

    private static List<DashboardSnapshot.BankAccountBalance> loadBankAccounts(EntityManager em, LocalDate asOfDate)
    {
        return em.createQuery("""
                select new org.nonprofitbookkeeping.service.dashboard.DashboardSnapshot$BankAccountBalance(
                    a.id, a.code, a.name, coalesce(sum(s.amountSigned), 0))
                from TxnSplit s join s.account a
                where s.txn.txnDate <= :asOf
                  and s.txn.status = 'ENTERED'
                  and a.accountType = :bankType
                group by a.id, a.code, a.name
                order by abs(sum(s.amountSigned)) desc, a.code
                """, DashboardSnapshot.BankAccountBalance.class)
                .setParameter("asOf", asOfDate)
                .setParameter("bankType", AccountType.BANK)
                .getResultList();
    }

    private static List<DashboardSnapshot.RecentTransaction> loadRecentTransactions(EntityManager em, LocalDate asOfDate, int limit)
    {
        List<Object[]> headers = em.createQuery("""
                select t.id, t.txnDate, coalesce(t.memo, ''), t.status, coalesce(p.displayName, '')
                from Txn t left join t.payee p
                where t.txnDate <= :asOf
                order by t.txnDate desc, t.id desc
                """, Object[].class)
                .setParameter("asOf", asOfDate)
                .setMaxResults(limit)
                .getResultList();

        Map<Long, BigDecimal> runningBalances = loadRunningBankBalances(em, asOfDate);
        Map<Long, RecentAccumulator> accumulators = new LinkedHashMap<>();
        for (Object[] row : headers)
        {
            long transactionId = ((Number) row[0]).longValue();
            accumulators.put(transactionId, new RecentAccumulator(
                    transactionId,
                    localDate(row[1]),
                    description(string(row[4]), string(row[2])),
                    string(row[3]),
                    Optional.ofNullable(runningBalances.get(transactionId))));
        }

        if (!accumulators.isEmpty())
        {
            List<Object[]> splitRows = em.createQuery("""
                    select s.txn.id, a.code, a.name, a.normalBalance, a.accountType,
                           f.code, f.name, s.amountSigned, s.budgetCategory
                    from TxnSplit s join s.account a join s.fund f
                    where s.txn.id in :transactionIds
                    order by s.txn.id desc, s.id
                    """, Object[].class)
                    .setParameter("transactionIds", new ArrayList<>(accumulators.keySet()))
                    .getResultList();
            for (Object[] row : splitRows)
            {
                RecentAccumulator accumulator = accumulators.get(((Number) row[0]).longValue());
                if (accumulator != null)
                {
                    accumulator.addSplit(
                            string(row[1]),
                            string(row[2]),
                            (NormalBalance) row[3],
                            (AccountType) row[4],
                            string(row[5]),
                            string(row[6]),
                            decimal(row[7]),
                            row[8] != null);
                }
            }
        }
        return accumulators.values().stream().map(RecentAccumulator::toSnapshot).toList();
    }

    private static Map<Long, BigDecimal> loadRunningBankBalances(EntityManager em, LocalDate asOfDate)
    {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT t.id,
                       COALESCE(SUM(CASE WHEN a.account_type = 'BANK'
                                         THEN s.amount_signed ELSE 0 END), 0)
                FROM txn t
                LEFT JOIN txn_split s ON s.txn_id = t.id
                LEFT JOIN account a ON a.id = s.account_id
                WHERE t.txn_date <= ?1 AND t.status = 'ENTERED'
                GROUP BY t.id, t.txn_date
                ORDER BY t.txn_date, t.id
                """)
                .setParameter(1, Date.valueOf(asOfDate))
                .getResultList();
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        BigDecimal running = BigDecimal.ZERO;
        for (Object[] row : rows)
        {
            running = running.add(decimal(row[1]));
            balances.put(((Number) row[0]).longValue(), running);
        }
        return balances;
    }

    private static DashboardSnapshot.OpenItemSummary loadOpenItems(EntityManager em, String groupCode, LocalDate asOfDate)
    {
        if (groupCode.isBlank())
        {
            return new DashboardSnapshot.OpenItemSummary(Map.of(), 0L);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT item_kind, COUNT(*)
                FROM open_item_snapshot
                WHERE group_code = ?1 AND last_updated_on <= ?2 AND open_amount <> 0
                GROUP BY item_kind
                ORDER BY item_kind
                """)
                .setParameter(1, groupCode)
                .setParameter(2, Date.valueOf(asOfDate))
                .getResultList();
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0L;
        for (Object[] row : rows)
        {
            long count = ((Number) row[1]).longValue();
            counts.put(string(row[0]), count);
            total += count;
        }
        return new DashboardSnapshot.OpenItemSummary(Map.copyOf(counts), total);
    }

    private static List<DashboardSnapshot.ReconciliationStatus> loadReconciliations(EntityManager em, String groupCode, LocalDate asOfDate)
    {
        if (groupCode.isBlank())
        {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT statement_ending_on, bank_format, status, imported_transaction_count
                FROM reconciliation_run
                WHERE group_code = ?1 AND statement_ending_on <= ?2
                ORDER BY statement_ending_on DESC, created_at DESC
                """)
                .setParameter(1, groupCode)
                .setParameter(2, Date.valueOf(asOfDate))
                .setMaxResults(4)
                .getResultList();
        return rows.stream()
                .map(row -> new DashboardSnapshot.ReconciliationStatus(
                        localDate(row[0]), string(row[1]), string(row[2]), ((Number) row[3]).intValue()))
                .toList();
    }

    private static List<DashboardSnapshot.BudgetActual> loadBudgetActuals(EntityManager em, LocalDate asOfDate)
    {
        List<Object[]> rows = em.createQuery("""
                select bc.code, bc.name,
                       coalesce(sum(case
                           when a.accountType = :incomeType then -s.amountSigned
                           when a.accountType = :expenseType then s.amountSigned
                           else 0 end), 0)
                from TxnSplit s join s.budgetCategory bc join s.account a
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
        return rows.stream()
                .map(row -> new DashboardSnapshot.BudgetActual(
                        string(row[0]), string(row[1]), Optional.empty(), decimal(row[2])))
                .toList();
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
        return memo.isBlank() ? payee : payee + " — " + memo;
    }

    private static final class RecentAccumulator
    {
        private final long transactionId;
        private final LocalDate transactionDate;
        private final String description;
        private final String status;
        private final Optional<BigDecimal> runningBankBalance;
        private final Set<String> accounts = new LinkedHashSet<>();
        private final Set<String> funds = new LinkedHashSet<>();
        private BigDecimal debitTotal = BigDecimal.ZERO;
        private BigDecimal creditTotal = BigDecimal.ZERO;
        private boolean affectsBank;
        private boolean affectsBudget;

        private RecentAccumulator(long transactionId, LocalDate transactionDate, String description,
                String status, Optional<BigDecimal> runningBankBalance)
        {
            this.transactionId = transactionId;
            this.transactionDate = transactionDate;
            this.description = description;
            this.status = status;
            this.runningBankBalance = runningBankBalance;
        }

        private void addSplit(String accountCode, String accountName, NormalBalance normalBalance,
                AccountType accountType, String fundCode, String fundName, BigDecimal amountSigned,
                boolean hasBudgetCategory)
        {
            accounts.add(accountCode + " " + accountName);
            funds.add(fundCode + " " + fundName);
            affectsBank = affectsBank || accountType == AccountType.BANK;
            affectsBudget = affectsBudget || hasBudgetCategory;
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
