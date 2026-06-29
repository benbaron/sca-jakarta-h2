package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AccountingPeriod;
import org.nonprofitbookkeeping.model.AccountingPeriodStatus;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.PeriodReopenEvent;
import org.nonprofitbookkeeping.model.ReopenScope;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Creates, closes, and reopens accounting periods with audit history.
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
            return em.createQuery("from AccountingPeriod p order by p.fiscalYear, p.periodNumber", AccountingPeriod.class)
                    .getResultList();
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
        String cleanActor = requireText(actor, "actor");
        if (scope == null)
        {
            throw new IllegalArgumentException("scope is required");
        }

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                AccountingPeriod period = requirePeriod(em, periodId);
                AccountingPeriodStatus priorStatus = period.getStatus();

                PeriodReopenEvent event = new PeriodReopenEvent();
                event.setAccountingPeriod(period);
                event.setReopenedBy(cleanActor);
                event.setReason(blankToNull(reason));
                event.setReopenScope(scope);
                event.setPriorStatus(priorStatus);
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

    private static AccountingPeriod requirePeriod(EntityManager em, long periodId)
    {
        AccountingPeriod period = em.find(AccountingPeriod.class, periodId);
        if (period == null)
        {
            throw new IllegalArgumentException("Unknown accounting period: " + periodId);
        }
        return period;
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
