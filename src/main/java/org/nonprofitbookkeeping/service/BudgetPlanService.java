package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.BudgetLine;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Supplier;

/** Application service for normalized, versioned budget plans. */
@ApplicationScoped
public class BudgetPlanService
{
    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;

    @Inject
    public BudgetPlanService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public BudgetPlanService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
    }

    public BudgetPlanView createDraft(BudgetPlanCommand command)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = ownership().requireCompany(em, companyCodeSupplier.get());
                BudgetPlan plan = new BudgetPlan();
                plan.setCompany(company);
                applyHeader(plan, command);
                plan.setStatus(BudgetPlan.Status.DRAFT);
                em.persist(plan);
                em.getTransaction().commit();
                return toView(plan, List.of());
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    /**
     * Creates one governed imported budget inside a caller-owned transaction.
     *
     * <p>The caller supplies managed company-owned category and fund IDs and
     * owns commit or rollback. This service retains budget header, scope,
     * ownership, duplicate-line, and active-version policy rather than allowing
     * an interchange service to write the normalized budget tables directly.</p>
     */
    public BudgetPlan createForImport(
            EntityManager em,
            Company company,
            BudgetPlanCommand command,
            BudgetPlan.Status status,
            List<BudgetLineCommand> commands)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(status, "status");
        List<BudgetLineCommand> safeCommands = List.copyOf(commands == null ? List.of() : commands);
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Caller-owned transaction must be active.");
        }
        if (!em.contains(company) || company.getId() == null)
        {
            throw new IllegalArgumentException("Company must be managed by the caller-owned transaction.");
        }

        if (status == BudgetPlan.Status.ACTIVE)
        {
            long active = em.createQuery("""
                    select count(p) from BudgetPlan p
                    where p.company = :company
                      and p.fiscalYear = :year
                      and p.status = :status
                    """, Long.class)
                    .setParameter("company", company)
                    .setParameter("year", command.fiscalYear())
                    .setParameter("status", BudgetPlan.Status.ACTIVE)
                    .getSingleResult();
            if (active != 0L)
            {
                throw new IllegalStateException(
                        "Only one active budget version is allowed per company and fiscal year.");
            }
        }

        BudgetPlan plan = new BudgetPlan();
        plan.setCompany(company);
        applyHeader(plan, command);
        plan.setStatus(status);
        if (status == BudgetPlan.Status.ACTIVE)
        {
            plan.setActivatedAt(Instant.now());
        }
        em.persist(plan);
        validateLineScopes(plan, safeCommands);

        for (BudgetLineCommand lineCommand : safeCommands)
        {
            BudgetCategory category = require(
                    em, BudgetCategory.class, lineCommand.budgetCategoryId(), "Budget category");
            ownership().ensureOwnedBy(em, company, category, "Budget category");
            Fund fund = lineCommand.fundId() == null
                    ? null
                    : require(em, Fund.class, lineCommand.fundId(), "Fund");
            if (fund != null)
            {
                ownership().ensureOwnedBy(em, company, fund, "Fund");
            }

            BudgetLine line = new BudgetLine();
            line.setBudgetPlan(plan);
            line.setBudgetCategory(category);
            line.setFund(fund);
            line.setPeriodMonth(lineCommand.periodMonth());
            line.setAmount(lineCommand.amount());
            line.setNotes(lineCommand.notes());
            em.persist(line);
        }
        return plan;
    }

    public BudgetPlanView replaceDraftLines(long planId, List<BudgetLineCommand> commands)
    {
        List<BudgetLineCommand> safeCommands = List.copyOf(commands == null ? List.of() : commands);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = ownership().requireCompany(em, companyCodeSupplier.get());
                BudgetPlan plan = requirePlan(em, planId);
                ownership().ensureOwnedBy(em, company, plan, "Budget plan");
                requireDraft(plan);
                validateLineScopes(plan, safeCommands);
                em.createQuery("delete from BudgetLine l where l.budgetPlan.id = :planId")
                        .setParameter("planId", planId)
                        .executeUpdate();
                em.flush();
                for (BudgetLineCommand command : safeCommands)
                {
                    BudgetLine line = new BudgetLine();
                    line.setBudgetPlan(plan);
                    BudgetCategory category = require(em, BudgetCategory.class, command.budgetCategoryId(), "Budget category");
                    ownership().ensureOwnedBy(em, company, category, "Budget category");
                    Fund fund = command.fundId() == null ? null : require(em, Fund.class, command.fundId(), "Fund");
                    if (fund != null)
                    {
                        ownership().ensureOwnedBy(em, company, fund, "Fund");
                    }
                    line.setBudgetCategory(category);
                    line.setFund(fund);
                    line.setPeriodMonth(command.periodMonth());
                    line.setAmount(command.amount());
                    line.setNotes(command.notes());
                    em.persist(line);
                }
                plan.touchUpdatedAt();
                em.getTransaction().commit();
                return load(planId).orElseThrow();
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public BudgetPlanView activate(long planId)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = ownership().requireCompany(em, companyCodeSupplier.get());
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                BudgetPlan plan = requirePlan(em, planId);
                ownership().ensureOwnedBy(em, company, plan, "Budget plan");
                requireDraft(plan);
                Instant now = Instant.now();
                em.createQuery("select p from BudgetPlan p where p.company = :company and p.fiscalYear = :year and p.status = :status and p.id <> :id", BudgetPlan.class)
                        .setParameter("company", company)
                        .setParameter("year", plan.getFiscalYear())
                        .setParameter("status", BudgetPlan.Status.ACTIVE)
                        .setParameter("id", planId)
                        .getResultList()
                        .forEach(active ->
                        {
                            active.setStatus(BudgetPlan.Status.ARCHIVED);
                            active.setArchivedAt(now);
                            active.touchUpdatedAt();
                        });
                plan.setStatus(BudgetPlan.Status.ACTIVE);
                plan.setActivatedAt(now);
                plan.setArchivedAt(null);
                plan.touchUpdatedAt();
                em.getTransaction().commit();
                return load(planId).orElseThrow();
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public BudgetPlanView archive(long planId)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = ownership().requireCompany(em, companyCodeSupplier.get());
                BudgetPlan plan = requirePlan(em, planId);
                ownership().ensureOwnedBy(em, company, plan, "Budget plan");
                plan.setStatus(BudgetPlan.Status.ARCHIVED);
                plan.setArchivedAt(Instant.now());
                plan.touchUpdatedAt();
                em.getTransaction().commit();
                return load(planId).orElseThrow();
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    /** Returns the editable drafts and active plan for one company fiscal year in deterministic order. */
    public List<BudgetPlanView> editableAndActiveForFiscalYear(int fiscalYear)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = ownership().requireCompany(em, companyCodeSupplier.get());
            return em.createQuery("""
                    select p from BudgetPlan p
                    where p.company = :company
                      and p.fiscalYear = :year
                      and p.status in :statuses
                    """, BudgetPlan.class)
                    .setParameter("company", company)
                    .setParameter("year", fiscalYear)
                    .setParameter("statuses", List.of(BudgetPlan.Status.DRAFT, BudgetPlan.Status.ACTIVE))
                    .getResultList()
                    .stream()
                    .sorted(Comparator
                            .comparing((BudgetPlan plan) -> plan.getStatus() == BudgetPlan.Status.DRAFT ? 0 : 1)
                            .thenComparing(BudgetPlan::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(BudgetPlan::getId, Comparator.reverseOrder()))
                    .map(plan -> toView(plan, lines(em, plan.getId())))
                    .toList();
        }
    }

    /** Creates an explicit empty draft for the supplied fiscal range. Reload never calls this implicitly. */
    public BudgetPlanView createDraft(FiscalPeriodRange range)
    {
        Objects.requireNonNull(range, "range");
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = ownership().requireCompany(em, companyCodeSupplier.get());
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                String version = nextVersionCode(em, company, range.fiscalYear(), "DRAFT");
                BudgetPlan plan = new BudgetPlan();
                plan.setCompany(company);
                plan.setName("FY " + range.fiscalYear() + " Budget Draft");
                plan.setFiscalYear(range.fiscalYear());
                plan.setVersionCode(version);
                plan.setPeriodStart(range.fiscalYearStart());
                plan.setPeriodEnd(range.fiscalYearEnd());
                plan.setNotes("Created explicitly from Budget Editor");
                plan.setStatus(BudgetPlan.Status.DRAFT);
                plan.touchUpdatedAt();
                em.persist(plan);
                em.getTransaction().commit();
                return toView(plan, List.of());
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    /** Creates a line-for-line editable revision from the explicitly selected active plan. */
    public BudgetPlanView createRevision(long sourcePlanId)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = ownership().requireCompany(em, companyCodeSupplier.get());
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                BudgetPlan source = requirePlan(em, sourcePlanId);
                ownership().ensureOwnedBy(em, company, source, "Budget plan");
                if (source.getStatus() != BudgetPlan.Status.ACTIVE)
                {
                    throw new IllegalStateException("A revision can be created only from the selected active budget version");
                }

                BudgetPlan revision = new BudgetPlan();
                revision.setCompany(company);
                revision.setName(source.getName() + " Revision");
                revision.setFiscalYear(source.getFiscalYear());
                revision.setVersionCode(nextVersionCode(em, company, source.getFiscalYear(), "REV"));
                revision.setPeriodStart(source.getPeriodStart());
                revision.setPeriodEnd(source.getPeriodEnd());
                revision.setNotes(source.getNotes());
                revision.setStatus(BudgetPlan.Status.DRAFT);
                revision.touchUpdatedAt();
                em.persist(revision);

                List<BudgetLine> sourceLines = em.createQuery(
                                "select l from BudgetLine l where l.budgetPlan.id = :planId order by l.id",
                                BudgetLine.class)
                        .setParameter("planId", sourcePlanId)
                        .getResultList();
                for (BudgetLine sourceLine : sourceLines)
                {
                    BudgetLine copy = new BudgetLine();
                    copy.setBudgetPlan(revision);
                    copy.setBudgetCategory(sourceLine.getBudgetCategory());
                    copy.setFund(sourceLine.getFund());
                    copy.setPeriodMonth(sourceLine.getPeriodMonth());
                    copy.setAmount(sourceLine.getAmount());
                    copy.setNotes(sourceLine.getNotes());
                    em.persist(copy);
                }
                em.flush();
                long revisionId = revision.getId();
                em.getTransaction().commit();
                return load(revisionId).orElseThrow();
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public Optional<BudgetPlanView> activeForFiscalYear(int fiscalYear)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = ownership().requireCompany(em, companyCodeSupplier.get());
            List<BudgetPlan> plans = em.createQuery("""
                    select p from BudgetPlan p
                    where p.company = :company and p.fiscalYear = :year and p.status = :status
                    order by p.activatedAt desc, p.id desc
                    """, BudgetPlan.class)
                    .setParameter("company", company)
                    .setParameter("year", fiscalYear)
                    .setParameter("status", BudgetPlan.Status.ACTIVE)
                    .setMaxResults(1)
                    .getResultList();
            return plans.stream().findFirst().map(plan -> toView(plan, lines(em, plan.getId())));
        }
    }

    public Optional<BudgetPlanView> load(long planId)
    {
        try (EntityManager em = jpa.em())
        {
            BudgetPlan plan = em.find(BudgetPlan.class, planId);
            if (plan == null)
            {
                return Optional.empty();
            }
            Company company = ownership().requireCompany(em, companyCodeSupplier.get());
            ownership().ensureOwnedBy(em, company, plan, "Budget plan");
            return Optional.of(toView(plan, lines(em, planId)));
        }
    }

    public FiscalPeriodRange fiscalRange(LocalDate selectedPeriodStart)
    {
        if (selectedPeriodStart == null)
        {
            throw new IllegalArgumentException("selectedPeriodStart is required");
        }
        try (EntityManager em = jpa.em())
        {
            Company company = ownership().requireCompany(em, companyCodeSupplier.get());
            return FiscalPeriodRange.of(
                    company.getFiscalYearStartMonth(),
                    company.getFiscalYearStartDay(),
                    selectedPeriodStart);
        }
    }

    public List<BudgetVarianceView> activeVariance(FiscalPeriodRange range)
    {
        Objects.requireNonNull(range, "range");
        try (EntityManager em = jpa.em())
        {
            Company company = ownership().requireCompany(em, companyCodeSupplier.get());
            List<BudgetPlan> active = em.createQuery("""
                    select p from BudgetPlan p
                    where p.company = :company and p.fiscalYear = :year and p.status = :status
                    order by p.activatedAt desc, p.id desc
                    """, BudgetPlan.class)
                    .setParameter("company", company)
                    .setParameter("year", range.fiscalYear())
                    .setParameter("status", BudgetPlan.Status.ACTIVE)
                    .setMaxResults(1)
                    .getResultList();
            if (active.isEmpty())
            {
                return List.of();
            }
            return variances(em, company, active.get(0).getId(), range);
        }
    }

    /** Compatibility overload that treats the supplied date as the exact as-of date while honoring fiscal-year boundaries. */
    public List<BudgetVarianceView> activeVariance(LocalDate asOfDate)
    {
        FiscalPeriodRange fiscal = fiscalRange(asOfDate);
        return activeVariance(new FiscalPeriodRange(
                fiscal.fiscalYear(),
                fiscal.fiscalYearStart(),
                fiscal.fiscalYearEnd(),
                asOfDate,
                asOfDate));
    }

    private static List<BudgetVarianceView> variances(EntityManager em, Company company, long planId, FiscalPeriodRange range)
    {
        Map<String, VarianceAccumulator> rows = new LinkedHashMap<>();
        List<Object[]> budgetRows = em.createQuery("""
                select bc.code, bc.name, f.id, f.code, f.name, coalesce(sum(l.amount), 0)
                from BudgetLine l
                join l.budgetCategory bc
                left join l.fund f
                where l.budgetPlan.id = :planId
                  and (l.periodMonth is null or l.periodMonth <= :period)
                group by bc.code, bc.name, f.id, f.code, f.name
                order by bc.code, f.code
                """, Object[].class)
                .setParameter("planId", planId)
                .setParameter("period", YearMonth.from(range.periodEnd()).toString())
                .getResultList();
        for (Object[] row : budgetRows)
        {
            accumulator(rows, row).budget = decimal(row[5]);
        }

        List<Object[]> actualRows = em.createQuery("""
                select bc.code, bc.name, f.id, f.code, f.name,
                       coalesce(sum(case
                           when a.accountType = :incomeType then -s.amountSigned
                           when a.accountType = :expenseType then s.amountSigned
                           else 0 end), 0)
                from TxnSplit s
                join s.budgetCategory bc
                join s.account a
                join s.fund f
                where (s.txn.company = :company
                       or (s.txn.company is null and (select count(c) from Company c) = 1))
                  and s.txn.txnDate between :start and :asOf
                  and s.txn.status = 'ENTERED'
                group by bc.code, bc.name, f.id, f.code, f.name
                order by bc.code, f.code
                """, Object[].class)
                .setParameter("company", company)
                .setParameter("incomeType", AccountType.INCOME)
                .setParameter("expenseType", AccountType.EXPENSE)
                .setParameter("start", range.fiscalYearStart())
                .setParameter("asOf", range.periodEnd())
                .getResultList();
        for (Object[] row : actualRows)
        {
            accumulator(rows, row).actual = decimal(row[5]);
        }
        return rows.values().stream().map(VarianceAccumulator::toView).toList();
    }

    private static VarianceAccumulator accumulator(Map<String, VarianceAccumulator> rows, Object[] row)
    {
        String categoryCode = string(row[0]);
        Long fundId = row[2] == null ? null : ((Number) row[2]).longValue();
        String key = categoryCode + "|" + fundId;
        return rows.computeIfAbsent(key, ignored -> new VarianceAccumulator(
                categoryCode,
                string(row[1]),
                fundId,
                string(row[3]),
                string(row[4])));
    }

    private static void validateLineScopes(BudgetPlan plan, List<BudgetLineCommand> commands)
    {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (BudgetLineCommand command : commands)
        {
            if (command.periodMonth() != null
                    && (command.periodMonth().atEndOfMonth().isBefore(plan.getPeriodStart())
                    || command.periodMonth().atDay(1).isAfter(plan.getPeriodEnd())))
            {
                throw new IllegalArgumentException("Budget line period is outside the plan period");
            }
            String key = command.budgetCategoryId() + "|" + command.fundId() + "|" + command.periodMonth();
            if (seen.put(key, Boolean.TRUE) != null)
            {
                throw new IllegalArgumentException("Duplicate budget line scope");
            }
        }
    }

    private static String nextVersionCode(EntityManager em, Company company, int fiscalYear, String kind)
    {
        String prefix = "FY" + fiscalYear + "-" + kind + "-";
        List<String> versions = em.createQuery("""
                select p.versionCode from BudgetPlan p
                where p.company = :company and p.fiscalYear = :year and p.versionCode like :prefix
                """, String.class)
                .setParameter("company", company)
                .setParameter("year", fiscalYear)
                .setParameter("prefix", prefix + "%")
                .getResultList();
        int next = 1;
        for (String version : versions)
        {
            if (version == null || !version.startsWith(prefix))
            {
                continue;
            }
            try
            {
                next = Math.max(next, Integer.parseInt(version.substring(prefix.length())) + 1);
            }
            catch (NumberFormatException ignored)
            {
                // Historical/custom version codes do not participate in the generated sequence.
            }
        }
        return prefix + next;
    }

    private static void applyHeader(BudgetPlan plan, BudgetPlanCommand command)
    {
        plan.setName(command.name());
        plan.setFiscalYear(command.fiscalYear());
        plan.setVersionCode(command.versionCode());
        plan.setPeriodStart(command.periodStart());
        plan.setPeriodEnd(command.periodEnd());
        plan.setNotes(command.notes());
        plan.touchUpdatedAt();
    }

    private static BudgetPlan requirePlan(EntityManager em, long planId)
    {
        BudgetPlan plan = em.find(BudgetPlan.class, planId);
        if (plan == null)
        {
            throw new IllegalArgumentException("Budget plan not found: " + planId);
        }
        return plan;
    }

    private static void requireDraft(BudgetPlan plan)
    {
        if (plan.getStatus() != BudgetPlan.Status.DRAFT)
        {
            throw new IllegalStateException("Only draft budgets can be edited");
        }
    }

    private static <T> T require(EntityManager em, Class<T> type, Long id, String label)
    {
        T entity = em.find(type, id);
        if (entity == null)
        {
            throw new IllegalArgumentException(label + " not found: " + id);
        }
        return entity;
    }

    private static List<BudgetLineView> lines(EntityManager em, long planId)
    {
        return em.createQuery("""
                select l.id, bc.id, bc.code, bc.name, f.id, f.code, f.name, l.periodMonth, l.amount, coalesce(l.notes, '')
                from BudgetLine l
                join l.budgetCategory bc
                left join l.fund f
                where l.budgetPlan.id = :planId
                order by bc.code, f.code, l.periodMonth, l.id
                """, Object[].class)
                .setParameter("planId", planId)
                .getResultList()
                .stream()
                .map(row -> new BudgetLineView(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).longValue(),
                        string(row[2]),
                        string(row[3]),
                        row[4] == null ? null : ((Number) row[4]).longValue(),
                        string(row[5]),
                        string(row[6]),
                        row[7] == null ? null : YearMonth.parse(string(row[7])),
                        decimal(row[8]),
                        string(row[9])))
                .toList();
    }

    private static BudgetPlanView toView(BudgetPlan plan, List<BudgetLineView> lines)
    {
        return new BudgetPlanView(
                plan.getId(),
                plan.getName(),
                plan.getFiscalYear(),
                plan.getVersionCode(),
                plan.getStatus(),
                plan.getPeriodStart(),
                plan.getPeriodEnd(),
                plan.getActivatedAt(),
                plan.getArchivedAt(),
                plan.getNotes() == null ? "" : plan.getNotes(),
                lines);
    }

    private CompanyOwnershipService ownership()
    {
        return new CompanyOwnershipService(jpa);
    }

    private static void rollback(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }

    private static BigDecimal decimal(Object value)
    {
        return value == null ? BigDecimal.ZERO : (BigDecimal) value;
    }

    private static String string(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private static final class VarianceAccumulator
    {
        private final String categoryCode;
        private final String categoryName;
        private final Long fundId;
        private final String fundCode;
        private final String fundName;
        private BigDecimal budget = BigDecimal.ZERO;
        private BigDecimal actual = BigDecimal.ZERO;

        private VarianceAccumulator(String categoryCode, String categoryName, Long fundId, String fundCode, String fundName)
        {
            this.categoryCode = categoryCode;
            this.categoryName = categoryName;
            this.fundId = fundId;
            this.fundCode = fundCode;
            this.fundName = fundName;
        }

        private BudgetVarianceView toView()
        {
            return new BudgetVarianceView(
                    categoryCode,
                    categoryName,
                    Optional.ofNullable(fundId),
                    optionalText(fundCode),
                    optionalText(fundName),
                    budget,
                    actual,
                    actual.subtract(budget));
        }

        private static Optional<String> optionalText(String text)
        {
            return text == null || text.isBlank() ? Optional.empty() : Optional.of(text);
        }
    }
}
