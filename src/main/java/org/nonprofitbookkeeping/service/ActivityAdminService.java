package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/** Service-owned stable-ID Activity create, edit, lifecycle, usage, and protected-delete boundary. */
@ApplicationScoped
public class ActivityAdminService
{
    @Inject
    Jpa jpa;

    private Supplier<String> companyCodeSupplier = () -> "DEFAULT";
    private AuthorizationGuard authorizationGuard;

    public ActivityAdminService()
    {
    }

    public ActivityAdminService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public ActivityAdminService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, companyCodeSupplier, null);
    }

    public ActivityAdminService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            AuthorizationGuard authorizationGuard)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.authorizationGuard = authorizationGuard;
    }

    /** Creates when command.id is null; otherwise updates exactly that stable Activity row. */
    public ActivityView save(ActivityCommand command)
    {
        if (command == null)
        {
            throw new IllegalArgumentException("Activity details are required.");
        }
        String companyCode = companyCodeSupplier.get();
        String actor = ServiceAuthorization.actor(
                authorizationGuard,
                ApplicationPermission.BOOKKEEPING_WRITE,
                companyCode,
                "save activity",
                "SYSTEM");

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, companyCode);
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);

                Activity activity = command.id() == null
                        ? new Activity()
                        : requireActivity(em, command.id());
                if (activity.getCompany() == null)
                {
                    activity.setCompany(company);
                }
                ownership.ensureOwnedBy(em, company, activity, "Activity");

                String before = activity.getId() == null ? null : describe(activity);
                boolean previouslyActive = activity.isActive();
                apply(em, company, activity, command);
                boolean created = activity.getId() == null;
                if (created)
                {
                    em.persist(activity);
                    em.flush();
                }

                em.persist(audit(
                        company,
                        actor,
                        action(created, previouslyActive, activity.isActive()),
                        activity,
                        before,
                        describe(activity)));
                em.getTransaction().commit();
                return ActivityLookupService.view(activity);
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw mapPersistenceError(ex, command.code());
            }
        }
    }

    /** Returns factual references used by the protected-delete decision. */
    public ActivityUsage usage(long activityId)
    {
        try (EntityManager em = jpa.em())
        {
            Activity activity = requireActivity(em, activityId);
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCodeSupplier.get());
            new CompanyOwnershipService(jpa).ensureOwnedBy(em, company, activity, "Activity");
            return usage(em, company, activityId);
        }
    }

    /**
     * Physically removes only an Activity with neither Journal references nor
     * durable interchange identity. Referenced Activities remain historical
     * master data and are deactivated instead.
     */
    public void deleteUnused(long activityId)
    {
        String companyCode = companyCodeSupplier.get();
        String actor = ServiceAuthorization.actor(
                authorizationGuard,
                ApplicationPermission.BOOKKEEPING_WRITE,
                companyCode,
                "delete unused activity",
                "SYSTEM");

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, companyCode);
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                Activity activity = requireActivity(em, activityId);
                ownership.ensureOwnedBy(em, company, activity, "Activity");
                ActivityUsage usage = usage(em, company, activityId);
                if (!usage.canDelete())
                {
                    throw new IllegalStateException(
                            "Activity " + activity.getCode() + " is referenced by "
                                    + usage.describeReferences()
                                    + ". Clear Active and Save to preserve history.");
                }

                String before = describe(activity);
                em.persist(audit(
                        company,
                        actor,
                        "ACTIVITY_DELETED",
                        activity,
                        before,
                        null));
                em.remove(activity);
                em.getTransaction().commit();
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    private static void apply(
            EntityManager em,
            Company company,
            Activity activity,
            ActivityCommand command)
    {
        String code = requireText(command.code(), "Activity code", 64);
        String name = requireText(command.name(), "Activity name", 200);
        requireUniqueCode(em, company, command.id(), code);
        activity.setCode(code);
        activity.setName(name);
        activity.setActive(command.active());
    }

    private static void requireUniqueCode(
            EntityManager em,
            Company company,
            Long activityId,
            String code)
    {
        String jpql = activityId == null
                ? "select a.id from Activity a where a.company = :company and upper(a.code) = :code"
                : "select a.id from Activity a where a.company = :company and upper(a.code) = :code and a.id <> :id";
        var query = em.createQuery(jpql, Long.class)
                .setParameter("company", company)
                .setParameter("code", code.toUpperCase(Locale.ROOT))
                .setMaxResults(1);
        if (activityId != null)
        {
            query.setParameter("id", activityId);
        }
        if (query.getResultStream().findAny().isPresent())
        {
            throw new IllegalArgumentException("Activity code already exists: " + code + ".");
        }
    }

    private static ActivityUsage usage(EntityManager em, Company company, long activityId)
    {
        long transactionSplits = em.createQuery(
                        "select count(s) from TxnSplit s where s.activity.id = :id",
                        Long.class)
                .setParameter("id", activityId)
                .getSingleResult();
        long identities = em.createQuery(
                        "select count(i) from InterchangeIdentity i where i.company = :company "
                                + "and upper(i.entityType) = 'ACTIVITY' and i.localEntityId = :localId",
                        Long.class)
                .setParameter("company", company)
                .setParameter("localId", Long.toString(activityId))
                .getSingleResult();
        return new ActivityUsage(transactionSplits, identities);
    }

    private static Activity requireActivity(EntityManager em, long activityId)
    {
        Activity activity = em.find(Activity.class, activityId);
        if (activity == null)
        {
            throw new IllegalArgumentException("Unknown Activity ID: " + activityId + ".");
        }
        return activity;
    }

    private static AuditEvent audit(
            Company company,
            String actor,
            String action,
            Activity activity,
            String before,
            String after)
    {
        AuditEvent event = new AuditEvent();
        event.setCompany(company);
        event.setActor(requireText(actor, "Audit actor", 200));
        event.setActionType(action);
        event.setEntityType("ACTIVITY");
        event.setEntityId(Long.toString(activity.getId()));
        event.setSummary(summary(action, activity));
        event.setBeforeValue(before);
        event.setAfterValue(after);
        return event;
    }

    private static String action(boolean created, boolean beforeActive, boolean afterActive)
    {
        if (created)
        {
            return "ACTIVITY_CREATED";
        }
        if (beforeActive && !afterActive)
        {
            return "ACTIVITY_DEACTIVATED";
        }
        if (!beforeActive && afterActive)
        {
            return "ACTIVITY_REACTIVATED";
        }
        return "ACTIVITY_UPDATED";
    }

    private static String summary(String action, Activity activity)
    {
        return switch (action)
        {
            case "ACTIVITY_CREATED" -> "Created Activity " + activity.getCode() + ".";
            case "ACTIVITY_DEACTIVATED" -> "Deactivated Activity " + activity.getCode() + ".";
            case "ACTIVITY_REACTIVATED" -> "Reactivated Activity " + activity.getCode() + ".";
            case "ACTIVITY_DELETED" -> "Deleted unused Activity " + activity.getCode() + ".";
            default -> "Updated Activity " + activity.getCode() + ".";
        };
    }

    private static String describe(Activity activity)
    {
        return "code=" + activity.getCode()
                + "; name=" + activity.getName()
                + "; active=" + activity.isActive();
    }

    private static String requireText(String value, String label, int maxLength)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        String clean = value.trim();
        if (clean.length() > maxLength)
        {
            throw new IllegalArgumentException(label + " must be at most " + maxLength + " characters.");
        }
        return clean;
    }

    private static RuntimeException mapPersistenceError(RuntimeException ex, String code)
    {
        if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException)
        {
            return ex;
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("uq_activity_company_code") || message.contains("unique") || message.contains("constraint"))
        {
            String cleanCode = code == null ? "" : code.trim();
            return new IllegalArgumentException("Activity code already exists: " + cleanCode + ".", ex);
        }
        return ex;
    }

    private static void rollback(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }
}
