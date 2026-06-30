package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AccountingPeriod;
import org.nonprofitbookkeeping.model.AccountingPeriodStatus;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.model.PeriodReopenEvent;
import org.nonprofitbookkeeping.model.ReopenScope;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Creates, locates, closes, and reopens accounting periods with audit history.
 */
@ApplicationScoped
public class AccountingPeriodService
{
    private final Jpa jpa;

    @Inject
    public AccountingPeriodService(Jpa jpa)
    {
        this.jpa = jpa;
    }

    public List<AccountingPeriod> listPeriods()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "from AccountingPeriod p order by p.fiscalYear, p.periodNumber",
                            AccountingPeriod.class)
                    .getResultList();
        }
    }

    /**
     * Finds the single accounting period containing the supplied date.
     *
     * @param date date to locate
     * @return matching period, or empty when no period covers the date
     */
    public Optional<AccountingPeriod> findPeriodContaining(LocalDate date)
    {
        if (date == null)
        {
            throw new IllegalArgumentException("date is required");
        }

        try (EntityManager em = jpa.em())
        {
            List<AccountingPeriod> matches = em.createQuery("""
                    from AccountingPeriod p
                    where p.startDate <= :date
                      and p.endDate >= :date
                    order by p.fiscalYear, p.periodNumber
                    """, AccountingPeriod.class)
                    .setParameter("date", date)
                    .setMaxResults(2)
                    .getResultList();

            if (matches.size() > 1)
            {
                throw new IllegalStateException("Multiple accounting periods contain date " + date);
            }
            return matches.stream().findFirst();
        }
    }

    public AccountingPeriod createPeriod(int fiscalYear, int periodNumber, LocalDate startDate, LocalDate endDate)
    {
        if (periodNumber <= 0)
        {
            throw new IllegalArgumentException("periodNumber must be positive");
        }
        if (startDate == null || endDate == null || endDate.isBefore(startDate))
        {
            throw new IllegalArgumentException("A valid accounting-period date range is required");
        }

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                long overlaps = em.createQuery("""
                        select count(p)
                        from AccountingPeriod p
                        where p.startDate <= :endDate
                          and p.endDate >= :startDate
                        """, Long.class)
                        .setParameter("startDate", startDate)
                        .setParameter("endDate", endDate)
                        .getSingleResult();
                if (overlaps > 0)
                {
                    throw new IllegalArgumentException(
                            "Accounting period overlaps an existing period: " + startDate + " through " + endDate);
                }

                AccountingPeriod period = new AccountingPeriod();
                period.setFiscalYear(fiscalYear);
                period.setPeriodNumber(periodNumber);
                period.setStartDate(startDate);
                period.setEndDate(endDate);
                em.persist(period);
                em.getTransaction().commit();
                return period;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public AccountingPeriod closePeriod(long periodId, String actor)
    {
        String cleanActor = requireText(actor, "actor");
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                AccountingPeriod period = requirePeriod(em, periodId);
                requireStatus(period, AccountingPeriodStatus.OPEN, "close");

                period.setStatus(AccountingPeriodStatus.CLOSED);
                period.setClosedAt(Instant.now());
                period.setClosedBy(cleanActor);
                period.touchUpdatedAt();
                em.persist(audit(cleanActor, "PERIOD_CLOSED", periodId, "Accounting period closed", null));
                em.getTransaction().commit();
                return period;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public AccountingPeriod reopenPeriod(long periodId, String actor, ReopenScope scope, String reason)
    {
        return reopenPeriod(
                periodId,
                actor,
                scope,
                reason,
                ClosedPeriodPolicy.WARN_AND_REOPEN,
                false);
    }

    /**
     * Reopens a closed period after enforcing the configured reopening policy.
     *
     * @param periodId accounting period identifier
     * @param actor user performing the action
     * @param scope reopening scope
     * @param reason optional or required reason according to policy
     * @param policy configured closed-period policy
     * @param requireReason organization-level reason requirement
     * @return reopened period
     */
    public AccountingPeriod reopenPeriod(
            long periodId,
            String actor,
            ReopenScope scope,
            String reason,
            ClosedPeriodPolicy policy,
            boolean requireReason)
    {
        String cleanActor = requireText(actor, "actor");
        if (scope == null)
        {
            throw new IllegalArgumentException("scope is required");
        }
        if (policy == null)
        {
            throw new IllegalArgumentException("policy is required");
        }

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                AccountingPeriod period = requirePeriod(em, periodId);
                requireStatus(period, AccountingPeriodStatus.CLOSED, "reopen");
                enforceReopenPolicy(periodId, policy, requireReason, reason);

                PeriodReopenEvent event = new PeriodReopenEvent();
                event.setAccountingPeriod(period);
                event.setReopenedBy(cleanActor);
                event.setReason(blankToNull(reason));
                event.setReopenScope(scope);
                event.setPriorStatus(AccountingPeriodStatus.CLOSED);
                em.persist(event);

                period.setStatus(AccountingPeriodStatus.OPEN);
                period.setClosedAt(null);
                period.setClosedBy(null);
                period.touchUpdatedAt();

                em.persist(audit(cleanActor, "PERIOD_REOPENED", periodId, "Accounting period reopened", reason));
                em.getTransaction().commit();
                return period;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    private static void enforceReopenPolicy(
            long periodId,
            ClosedPeriodPolicy policy,
            boolean requireReason,
            String reason)
    {
        if (policy == ClosedPeriodPolicy.REQUIRE_FORMAL_ADJUSTMENT)
        {
            throw new FormalAdjustmentRequiredException(periodId);
        }

        boolean reasonRequired = requireReason || policy == ClosedPeriodPolicy.REQUIRE_REASON;
        if (reasonRequired && (reason == null || reason.isBlank()))
        {
            throw new IllegalArgumentException("A reopening reason is required");
        }
    }

    private static AccountingPeriod requirePeriod(EntityManager em, long periodId)
    {
        AccountingPeriod period = em.find(AccountingPeriod.class, periodId);
        if (period == null)
        {
            throw new IllegalArgumentException("Unknown accounting period: " + periodId);
        }
        return period;
    }

    private static void requireStatus(
            AccountingPeriod period,
            AccountingPeriodStatus expectedStatus,
            String action)
    {
        if (period.getStatus() != expectedStatus)
        {
            throw new IllegalStateException(
                    "Cannot " + action + " accounting period " + period.getId()
                            + " while status is " + period.getStatus());
        }
    }

    private static AuditEvent audit(String actor, String action, long periodId, String summary, String reason)
    {
        AuditEvent event = new AuditEvent();
        event.setActor(actor);
        event.setActionType(action);
        event.setEntityType("AccountingPeriod");
        event.setEntityId(Long.toString(periodId));
        event.setSummary(summary);
        event.setReason(blankToNull(reason));
        return event;
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void rollback(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }
}
