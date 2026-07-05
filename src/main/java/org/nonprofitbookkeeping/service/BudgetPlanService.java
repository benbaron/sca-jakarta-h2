package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.BudgetLine;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Application service for normalized, versioned budget plans. */
@ApplicationScoped
public class BudgetPlanService
{
    private final Jpa jpa;

    @Inject
    public BudgetPlanService(Jpa jpa)
    {
        this.jpa = jpa;
    }

    public BudgetPlanView createDraft(BudgetPlanCommand command)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                BudgetPlan plan = new BudgetPlan();
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

    public BudgetPlanView replaceDraftLines(long planId, List<BudgetLineCommand> commands)
    {
        List<BudgetLineCommand> safeCommands = List.copyOf(commands == null ? List.of() : commands);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                BudgetPlan plan = requirePlan(em, planId);
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
                    line.setBudgetCategory(require(em, BudgetCategory.class, command.budgetCategoryId(), "Budget category"));
                    line.setFund(command.fundId() == null ? null : require(em, Fund.class, command.fundId(), "Fund"));
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
                BudgetPlan plan = requirePlan(em, planId);
                if (plan.getStatus() == BudgetPlan.Status.ARCHIVED)
                {
                    throw new IllegalStateException("Archived budgets cannot be activated");
                }
                Instant now = Instant.now();
                em.createQuery("select p from BudgetPlan p where p.fiscalYear = :year and p.status = :status and p.id <> :id", BudgetPlan.class)
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
                BudgetPlan plan = requirePlan(em, planId);
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

    public Optional<BudgetPlanView> activeForFiscalYear(int fiscalYear)
    {
        try (EntityManager em = jpa.em())
        {
            List<BudgetPlan> plans = em.createQuery("""
                    select p from BudgetPlan p
                    where p.fiscalYear = :year and p.status = :status
                    order by p.activatedAt desc, p.id desc
                    """, BudgetPlan.class)
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
            return plan == null ? Optional.empty() : Optional.of(toView(plan, lines(em, planId)));
        }
    }

    public List<BudgetVarianceView> activeVariance(LocalDate asOfDate)
    {
        if (asOfDate == null)
        {
            throw new IllegalArgumentException("asOfDate is required");
        }
        try (EntityManager em = jpa.em())
        {
            Optional<BudgetPlanView> active = activeForFiscalYear(asOfDate.getYear());
            if (active.isEmpty())
            {
                return List.of();
            }
            return variances(em, active.orElseThrow().id(), asOfDate);
        }
    }

    private static List<BudgetVarianceView> variances(EntityManager em, long planId, LocalDate asOfDate)
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
                .setParameter("period", YearMonth.from(asOfDate).toString())
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
                where s.txn.txnDate between :start and :asOf
                  and s.txn.status = 'ENTERED'
                group by bc.code, bc.name, f.id, f.code, f.name
                order by bc.code, f.code
                """, Object[].class)
                .setParameter("incomeType", AccountType.INCOME)
                .setParameter("expenseType", AccountType.EXPENSE)
                .setParameter("start", LocalDate.of(asOfDate.getYear(), 1, 1))
                .setParameter("asOf", asOfDate)
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
                    && (command.periodMonth().atDay(1).isBefore(plan.getPeriodStart())
                    || command.periodMonth().atEndOfMonth().isAfter(plan.getPeriodEnd())))
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
