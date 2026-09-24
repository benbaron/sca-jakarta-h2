package org.nonprofitbookkeeping.service.dashboard;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.FiscalPeriodRange;
import org.nonprofitbookkeeping.service.SupplementalOpenItemQueryService;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
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
    public DashboardSnapshot load(LocalDate selectedPeriodStart, int recentTransactionLimit)
    {
        return load("", selectedPeriodStart, recentTransactionLimit);
    }

    @Override
    public DashboardSnapshot load(
            String groupCode,
            LocalDate selectedPeriodStart,
            int recentTransactionLimit)
    {
        if (selectedPeriodStart == null)
        {
            throw new IllegalArgumentException("selectedPeriodStart is required");
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
                Optional<Company> company = findCompany(em, normalizedGroupCode);
                if (company.isEmpty())
                {
                    em.getTransaction().commit();
                    return emptySnapshot(normalizedGroupCode, selectedPeriodStart);
                }

                Company owner = company.orElseThrow();
                FiscalPeriodRange fiscalRange = FiscalPeriodRange.of(
                        owner.getFiscalYearStartMonth(),
                        owner.getFiscalYearStartDay(),
                        selectedPeriodStart);
                LocalDate projectionDate = fiscalRange.periodEnd();
                BigDecimal bookCash = loadBookCash(em, owner, projectionDate);
                Optional<BigDecimal> reconciledCash = loadReconciledCash(em, owner, projectionDate);
                Optional<BigDecimal> unreconciledDifference = reconciledCash.map(bookCash::subtract);
                BigDecimal yearToDate = loadYearToDateSurplus(em, owner, fiscalRange.fiscalYearStart(), projectionDate);
                Map<String, BigDecimal> fundClassTotals = loadFundClassTotals(em, owner, projectionDate);
                List<DashboardSnapshot.BankAccountBalance> bankAccounts = loadBankAccounts(em, owner, projectionDate);
                List<DashboardSnapshot.RecentTransaction> recentTransactions =
                        loadRecentTransactions(em, owner, projectionDate, recentTransactionLimit);
                DashboardSnapshot.OpenItemSummary openItems = loadOpenItems(owner, projectionDate);
                List<DashboardSnapshot.ReconciliationStatus> reconciliations =
                        loadReconciliations(em, owner, projectionDate);
                List<DashboardSnapshot.BudgetActual> budgetActuals =
                        loadBudgetActuals(em, owner, fiscalRange, projectionDate);
                DashboardSnapshot.OrganizationSummary organization = loadOrganization(owner);
                DashboardSnapshot.PeriodSummary period = loadPeriod(em, owner, fiscalRange);
                List<DashboardSnapshot.MonthlyResult> monthlyResults =
                        loadMonthlyResults(em, owner, fiscalRange.fiscalYearStart(), projectionDate);

                em.getTransaction().commit();
                return new DashboardSnapshot(
                        projectionDate,
                        bookCash,
                        reconciledCash,
                        unreconciledDifference,
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

    private DashboardSnapshot.OpenItemSummary loadOpenItems(Company company, LocalDate asOfDate)
    {
        Map<SupplementalOpenItemQueryService.Kind, SupplementalOpenItemQueryService.Result> projections =
                new SupplementalOpenItemQueryService(jpa, company::getCode).queryAll(asOfDate);
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        long totalCount = 0L;
        BigDecimal totalAmount = BigDecimal.ZERO;
        boolean available = false;
        for (SupplementalOpenItemQueryService.Kind kind : SupplementalOpenItemQueryService.Kind.values())
        {
            SupplementalOpenItemQueryService.Result result = projections.get(kind);
            if (result == null)
            {
                continue;
            }
            available |= result.authorityAvailable();
            long count = result.openCount();
            BigDecimal amount = result.openAmount();
            counts.put(kind.name(), count);
            amounts.put(kind.name(), amount);
            totalCount += count;
            totalAmount = totalAmount.add(amount);
        }
        if (!available)
        {
            return DashboardSnapshot.OpenItemSummary.unavailable();
        }
        return new DashboardSnapshot.OpenItemSummary(
                Map.copyOf(counts), Map.copyOf(amounts), totalCount, totalAmount, true);
    }

    private static DashboardSnapshot emptySnapshot(String companyCode, LocalDate asOfDate)
    {
        return new DashboardSnapshot(
                asOfDate,
                BigDecimal.ZERO,
                Optional.empty(),
                Optional.empty(),
                BigDecimal.ZERO,
                Map.of(),
                List.of(),
                List.of(),
                DashboardSnapshot.OpenItemSummary.unavailable(),
                List.of(),
                List.of(),
                DashboardSnapshot.OrganizationSummary.unavailable(companyCode),
                DashboardSnapshot.PeriodSummary.unavailable(),
                List.of());
    }

    private static Optional<Company> findCompany(EntityManager em, String companyCode)
    {
        if (companyCode == null || companyCode.isBlank())
        {
            return Optional.empty();
        }
        List<Company> matches = em.createQuery(
                        "from Company c where upper(c.code) = :code",
                        Company.class)
                .setParameter("code", companyCode.trim().toUpperCase(java.util.Locale.ROOT))
                .setMaxResults(2)
                .getResultList();
        if (matches.size() > 1)
        {
            throw new IllegalStateException("Company code is ambiguous: " + companyCode);
        }
        return matches.stream().findFirst();
    }

    private static BigDecimal loadBookCash(EntityManager em, Company company, LocalDate asOfDate)
    {
        return decimal(em.createQuery("""
                select coalesce(sum(s.amountSigned), 0)
                from TxnSplit s
                where s.txn.company = :company
                  and s.txn.txnDate <= :asOf
                  and s.txn.status = 'ENTERED'
                  and s.account.accountType = :assetType
                  and s.account.subtype = :cashSubtype
                """, BigDecimal.class)
                .setParameter("company", company)
                .setParameter("asOf", asOfDate)
                .setParameter("assetType", AccountType.ASSET)
                .setParameter("cashSubtype", AccountSubtype.CASH)
                .getSingleResult());
    }

    /**
     * Cash not classified as a bank account is always included. Bank-function cash is included only after the
     * authoritative reconciliation-owned cleared flag is set, so Book Cash minus this value is the uncleared bank-cash delta.
     */
    private static Optional<BigDecimal> loadReconciledCash(EntityManager em, Company company, LocalDate asOfDate)
    {
        long bankCashAccounts = em.createQuery("""
                select count(a)
                from Account a
                where a.chart.company = :company
                  and a.accountType = :assetType
                  and a.subtype = :cashSubtype
                  and a.accountFunction = :bankFunction
                """, Long.class)
                .setParameter("company", company)
                .setParameter("assetType", AccountType.ASSET)
                .setParameter("cashSubtype", AccountSubtype.CASH)
                .setParameter("bankFunction", AccountFunction.BANK)
                .getSingleResult();
        if (bankCashAccounts == 0L)
        {
            return Optional.empty();
        }

        BigDecimal reconciled = decimal(em.createQuery("""
                select coalesce(sum(case
                    when s.account.accountFunction <> :bankFunction or s.account.accountFunction is null
                        then s.amountSigned
                    when s.bankCleared = true then s.amountSigned
                    else 0 end), 0)
                from TxnSplit s
                where s.txn.company = :company
                  and s.txn.txnDate <= :asOf
                  and s.txn.status = 'ENTERED'
                  and s.account.accountType = :assetType
                  and s.account.subtype = :cashSubtype
                """, BigDecimal.class)
                .setParameter("company", company)
                .setParameter("asOf", asOfDate)
                .setParameter("assetType", AccountType.ASSET)
                .setParameter("cashSubtype", AccountSubtype.CASH)
                .setParameter("bankFunction", AccountFunction.BANK)
                .getSingleResult());
        return Optional.of(reconciled);
    }

    private static BigDecimal loadYearToDateSurplus(
            EntityManager em,
            Company company,
            LocalDate fiscalYearStart,
            LocalDate asOfDate)
    {
        return decimal(em.createQuery("""
                select coalesce(sum(case
                    when s.account.accountType = :incomeType then -s.amountSigned
                    when s.account.accountType = :expenseType then -s.amountSigned
                    else 0 end), 0)
                from TxnSplit s
                where s.txn.company = :company
                  and s.txn.txnDate between :start and :asOf
                  and s.txn.status = 'ENTERED'
                """, BigDecimal.class)
                .setParameter("company", company)
                .setParameter("incomeType", AccountType.INCOME)
                .setParameter("expenseType", AccountType.EXPENSE)
                .setParameter("start", fiscalYearStart)
                .setParameter("asOf", asOfDate)
                .getSingleResult());
    }

    private static Map<String, BigDecimal> loadFundClassTotals(
            EntityManager em,
            Company company,
            LocalDate asOfDate)
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
                where s.txn.company = :company
                  and s.txn.txnDate <= :asOf
                  and s.txn.status = 'ENTERED'
                group by f.fundType
                order by f.fundType
                """, Object[].class)
                .setParameter("company", company)
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
            Company company,
            LocalDate asOfDate)
    {
        return em.createQuery("""
                select new org.nonprofitbookkeeping.service.dashboard.DashboardSnapshot$BankAccountBalance(
                    a.id, a.code, a.name, coalesce(sum(s.amountSigned), 0))
                from TxnSplit s join s.account a
                where s.txn.company = :company
                  and s.txn.txnDate <= :asOf
                  and s.txn.status = 'ENTERED'
                  and a.accountFunction = :bankFunction
                group by a.id, a.code, a.name
                order by abs(sum(s.amountSigned)) desc, a.code
                """, DashboardSnapshot.BankAccountBalance.class)
                .setParameter("company", company)
                .setParameter("asOf", asOfDate)
                .setParameter("bankFunction", AccountFunction.BANK)
                .getResultList();
    }

    private static List<DashboardSnapshot.RecentTransaction> loadRecentTransactions(
            EntityManager em,
            Company company,
            LocalDate asOfDate,
            int limit)
    {
        List<Object[]> headers = em.createQuery("""
                select t.id, t.txnDate, coalesce(t.memo, ''), t.status,
                       coalesce(p.displayName, '')
                from Txn t left join t.payee p
                where t.company = :company
                  and t.txnDate <= :asOf
                order by t.txnDate desc, t.id desc
                """, Object[].class)
                .setParameter("company", company)
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

        assignRunningBankBalances(em, company, accumulators);
        return accumulators.values().stream()
                .map(RecentAccumulator::toSnapshot)
                .toList();
    }

    private static void assignRunningBankBalances(
            EntityManager em,
            Company company,
            Map<Long, RecentAccumulator> accumulators)
    {
        long bankAccountCount = em.createQuery("""
                select count(a)
                from Account a
                where a.chart.company = :company
                  and a.accountFunction = :bankFunction
                """, Long.class)
                .setParameter("company", company)
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
                where s.txn.company = :company
                  and s.txn.status = 'ENTERED'
                  and s.account.accountFunction = :bankFunction
                  and (s.txn.txnDate < :firstDate
                       or (s.txn.txnDate = :firstDate and s.txn.id < :firstId))
                """, BigDecimal.class)
                .setParameter("company", company)
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

    private static List<DashboardSnapshot.ReconciliationStatus> loadReconciliations(
            EntityManager em,
            Company company,
            LocalDate asOfDate)
    {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT s.statement_end_date, cba.name, s.status, s.difference_amount
                FROM bank_reconciliation_session s
                JOIN company_bank_account cba ON cba.id = s.bank_account_id
                WHERE s.company_id = ?1
                  AND s.statement_end_date <= ?2
                ORDER BY s.statement_end_date DESC, s.id DESC
                """)
                .setParameter(1, company.getId())
                .setParameter(2, Date.valueOf(asOfDate))
                .setMaxResults(4)
                .getResultList();

        return rows.stream()
                .map(row -> new DashboardSnapshot.ReconciliationStatus(
                        localDate(row[0]),
                        string(row[1]),
                        string(row[2]),
                        decimal(row[3])))
                .toList();
    }

    private static List<DashboardSnapshot.BudgetActual> loadBudgetActuals(
            EntityManager em,
            Company company,
            FiscalPeriodRange fiscalRange,
            LocalDate asOfDate)
    {
        Map<String, BudgetActualAccumulator> accumulators = new LinkedHashMap<>();
        List<Long> activePlanIds = em.createQuery("""
                select p.id from BudgetPlan p
                where p.company = :company
                  and p.fiscalYear = :year
                  and p.status = :status
                order by p.activatedAt desc, p.id desc
                """, Long.class)
                .setParameter("company", company)
                .setParameter("year", fiscalRange.fiscalYear())
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
                    .setParameter("period", YearMonth.from(asOfDate).toString())
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
                where s.txn.company = :company
                  and s.txn.txnDate between :start and :asOf
                  and s.txn.status = 'ENTERED'
                group by bc.code, bc.name
                order by bc.code
                """, Object[].class)
                .setParameter("company", company)
                .setParameter("incomeType", AccountType.INCOME)
                .setParameter("expenseType", AccountType.EXPENSE)
                .setParameter("start", fiscalRange.fiscalYearStart())
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

    private static DashboardSnapshot.OrganizationSummary loadOrganization(Company company)
    {
        return new DashboardSnapshot.OrganizationSummary(
                string(company.getCode()),
                string(company.getDisplayName()),
                string(company.getBranchType()),
                string(company.getParentOrganization()),
                company.isActive(),
                string(company.getDefaultCurrency()));
    }

    private static DashboardSnapshot.PeriodSummary loadPeriod(
            EntityManager em,
            Company company,
            FiscalPeriodRange fiscalRange)
    {
        Number covering = (Number) em.createNativeQuery("""
                SELECT COUNT(*)
                FROM period_close_range
                WHERE company_id = ?1
                  AND status = 'CLOSED'
                  AND start_date <= ?2
                  AND end_date >= ?3
                """)
                .setParameter(1, company.getId())
                .setParameter(2, Date.valueOf(fiscalRange.periodStart()))
                .setParameter(3, Date.valueOf(fiscalRange.periodEnd()))
                .getSingleResult();
        Number overlapping = (Number) em.createNativeQuery("""
                SELECT COUNT(*)
                FROM period_close_range
                WHERE company_id = ?1
                  AND status = 'CLOSED'
                  AND start_date <= ?3
                  AND end_date >= ?2
                """)
                .setParameter(1, company.getId())
                .setParameter(2, Date.valueOf(fiscalRange.periodStart()))
                .setParameter(3, Date.valueOf(fiscalRange.periodEnd()))
                .getSingleResult();
        int periodNumber = Math.toIntExact(
                ChronoUnit.MONTHS.between(
                        YearMonth.from(fiscalRange.fiscalYearStart()),
                        YearMonth.from(fiscalRange.periodStart())) + 1L);
        String status = covering.longValue() > 0L
                ? "CLOSED"
                : overlapping.longValue() > 0L ? "PARTIALLY_CLOSED" : "OPEN";
        return new DashboardSnapshot.PeriodSummary(
                Optional.of(fiscalRange.fiscalYear()),
                Optional.of(periodNumber),
                Optional.of(fiscalRange.periodStart()),
                Optional.of(fiscalRange.periodEnd()),
                status);
    }

    private static List<DashboardSnapshot.MonthlyResult> loadMonthlyResults(
            EntityManager em,
            Company company,
            LocalDate fiscalYearStart,
            LocalDate asOfDate)
    {
        List<Object[]> rows = em.createQuery("""
                select year(s.txn.txnDate), month(s.txn.txnDate),
                       coalesce(sum(case
                           when s.account.accountType = :incomeType then -s.amountSigned
                           when s.account.accountType = :expenseType then -s.amountSigned
                           else 0 end), 0)
                from TxnSplit s
                where s.txn.company = :company
                  and s.txn.txnDate between :start and :asOf
                  and s.txn.status = 'ENTERED'
                group by year(s.txn.txnDate), month(s.txn.txnDate)
                order by year(s.txn.txnDate), month(s.txn.txnDate)
                """, Object[].class)
                .setParameter("company", company)
                .setParameter("incomeType", AccountType.INCOME)
                .setParameter("expenseType", AccountType.EXPENSE)
                .setParameter("start", fiscalYearStart)
                .setParameter("asOf", asOfDate)
                .getResultList();

        Map<YearMonth, BigDecimal> byMonth = new LinkedHashMap<>();
        for (Object[] row : rows)
        {
            byMonth.put(
                    YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()),
                    decimal(row[2]));
        }

        List<DashboardSnapshot.MonthlyResult> results = new ArrayList<>();
        YearMonth month = YearMonth.from(fiscalYearStart);
        YearMonth through = YearMonth.from(asOfDate);
        while (!month.isAfter(through))
        {
            results.add(new DashboardSnapshot.MonthlyResult(
                    month.getMonthValue(),
                    byMonth.getOrDefault(month, BigDecimal.ZERO)));
            month = month.plusMonths(1);
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
