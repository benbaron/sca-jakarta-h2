package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.AuditEvent;
import java.util.Locale;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@ApplicationScoped
public class BudgetCategoryAdminService
{
    /** Exact retained master facts, independent from interactive maintenance normalization. */
    public record ImportFact(java.util.UUID portableId, String code, String name, boolean active,
            java.time.LocalDate effectiveFrom, java.time.LocalDate effectiveTo, String description,
            java.time.Instant createdAt, java.time.Instant updatedAt)
    {
        public ImportFact
        {
            Objects.requireNonNull(portableId, "Category identity");
            if (code == null || code.isBlank() || code.length() > 64
                    || name == null || name.isBlank() || name.length() > 200)
                throw new IllegalArgumentException("Invalid category code/name bounds");
            if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo))
                throw new IllegalArgumentException("Category effective dates are inverted");
            for (var date : new java.time.LocalDate[]{effectiveFrom, effectiveTo})
                if (date != null && (date.getYear() < 1900 || date.getYear() > 9999))
                    throw new IllegalArgumentException("Category date outside supported range");
            for (var time : new java.time.Instant[]{createdAt, updatedAt})
                if (time == null || time.getNano() % 1000 != 0
                        || time.isBefore(java.time.Instant.parse("1900-01-01T00:00:00Z"))
                        || time.isAfter(java.time.Instant.parse("9999-12-31T23:59:59.999999Z")))
                    throw new IllegalArgumentException("Invalid category timestamp or persisted precision");
            if (description != null && description.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1_048_576)
                throw new IllegalArgumentException("Category description exceeds one MiB UTF-8");
        }
    }

    public static ImportFact importFact(BudgetCategory category)
    {
        return new ImportFact(category.getPortableId(), category.getCode(), category.getName(), category.isActive(),
                category.getEffectiveFrom(), category.getEffectiveTo(), category.getDescription(),
                category.getCreatedAt(), category.getUpdatedAt());
    }

    /** Restores a new master in the caller's atomic import, without replaying Save or audit creation. */
    public BudgetCategory importForInterchange(EntityManager em, Company company, ImportFact fact)
    {
        Objects.requireNonNull(fact);
        if (!em.getTransaction().isActive() || !em.contains(company) || company.getId() == null)
            throw new IllegalArgumentException("Category import requires an active transaction and managed company");
        long collisions = em.createQuery("select count(c) from BudgetCategory c where c.portableId=:uuid "
                        + "or (c.company=:company and upper(c.code)=:code)", Long.class)
                .setParameter("uuid", fact.portableId()).setParameter("company", company)
                .setParameter("code", fact.code().toUpperCase(Locale.ROOT)).getSingleResult();
        if (collisions != 0) throw new IllegalStateException("Category UUID or company code already exists");
        var category = new BudgetCategory(); category.setCompany(company); category.setCode(fact.code());
        category.setName(fact.name()); category.setActive(fact.active()); category.setEffectiveFrom(fact.effectiveFrom());
        category.setEffectiveTo(fact.effectiveTo()); category.setDescription(fact.description());
        category.initializeImportMetadata(fact.portableId(), fact.createdAt(), fact.updatedAt());
        em.persist(category);
        return category;
    }
    @Inject
    Jpa jpa;

    private Supplier<String> companyCodeSupplier = () -> "DEFAULT";
    private AuthorizationGuard authorizationGuard;

    public BudgetCategoryAdminService() {}

    public BudgetCategoryAdminService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public BudgetCategoryAdminService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, companyCodeSupplier, null);
    }

    public BudgetCategoryAdminService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            AuthorizationGuard authorizationGuard)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.authorizationGuard = authorizationGuard;
    }

    /** Saves the exact selected category and its audit atomically; categories are retained, not deleted. */
    public BudgetCategoryView save(BudgetCategoryCommand command)
    {
        Objects.requireNonNull(command, "Category details are required");
        String companyCode = companyCodeSupplier.get();
        String actor = ServiceAuthorization.actor(authorizationGuard, ApplicationPermission.BOOKKEEPING_WRITE,
                companyCode, "save budget category", "SYSTEM");
        String code = bounded(command.code(), "Budget category code", 64);
        String name = bounded(command.name(), "Budget category name", 200);
        if (command.effectiveFrom() != null && command.effectiveTo() != null
                && command.effectiveFrom().isAfter(command.effectiveTo()))
        {
            throw new IllegalArgumentException("Effective from must not follow effective to.");
        }
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, companyCode);
                em.lock(company, LockModeType.PESSIMISTIC_WRITE);
                BudgetCategory category;
                if (command.id() == null)
                {
                    category = new BudgetCategory();
                    category.setCompany(company);
                }
                else
                {
                    category = em.find(BudgetCategory.class, command.id(), LockModeType.PESSIMISTIC_WRITE);
                    if (category == null)
                    {
                        throw new IllegalArgumentException("Unknown Budget Category ID: " + command.id());
                    }
                    ownership.requireOwnedBy(company, category, "Budget category");
                }
                var duplicates = em.createQuery("select b.id from BudgetCategory b where b.company = :company "
                                + "and upper(b.code) = :code", Long.class)
                        .setParameter("company", company).setParameter("code", code.toUpperCase(Locale.ROOT))
                        .getResultList();
                if (duplicates.stream().anyMatch(id -> !id.equals(command.id())))
                {
                    throw new IllegalArgumentException("Budget category code already exists: " + code);
                }
                String before = category.getId() == null ? null : describe(category);
                category.setCode(code);
                category.setName(name);
                category.setActive(command.active());
                category.setEffectiveFrom(command.effectiveFrom());
                category.setEffectiveTo(command.effectiveTo());
                category.setDescription(command.description() == null ? null : command.description().trim());
                category.touchUpdatedAt();
                if (category.getId() == null)
                {
                    em.persist(category);
                }
                em.flush();
                AuditEvent audit = new AuditEvent();
                audit.setCompany(company);
                audit.setActor(actor);
                audit.setActionType(before == null ? "BUDGET_CATEGORY_CREATED" : "BUDGET_CATEGORY_UPDATED");
                audit.setEntityType("BUDGET_CATEGORY");
                audit.setEntityId(category.getId().toString());
                audit.setSummary((before == null ? "Created " : "Updated ") + "Budget category " + code);
                audit.setBeforeValue(before);
                audit.setAfterValue(describe(category));
                em.persist(audit);
                em.getTransaction().commit();
                return BudgetCategoryLookupService.view(category);
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

    private static String describe(BudgetCategory category)
    {
        return "code=" + category.getCode() + "; name=" + category.getName() + "; active=" + category.isActive()
                + "; from=" + category.getEffectiveFrom() + "; to=" + category.getEffectiveTo()
                + "; description=" + Objects.toString(category.getDescription(), "");
    }

    private static String bounded(String value, String label, int limit)
    {
        String text = requireText(value, label);
        if (text.length() > limit)
        {
            throw new IllegalArgumentException(label + " must be at most " + limit + " characters.");
        }
        return text;
    }

    public BudgetCategory upsert(String code, String name, boolean active)
    {
        ServiceAuthorization.require(authorizationGuard, ApplicationPermission.BOOKKEEPING_WRITE,
                companyCodeSupplier.get(), "save budget category");
        String cleanCode = requireText(code, "Budget category code");
        String cleanName = requireText(name, "Budget category name");

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, companyCodeSupplier.get());
                List<BudgetCategory> existingMatches = em.createQuery(
                                "from BudgetCategory b where (b.company = :company or b.company is null) and b.code = :code",
                                BudgetCategory.class)
                        .setParameter("company", company)
                        .setParameter("code", cleanCode)
                        .setMaxResults(2)
                        .getResultList();

                BudgetCategory category = existingMatches.isEmpty() ? new BudgetCategory() : existingMatches.get(0);
                if (category.getCompany() == null)
                {
                    category.setCompany(company);
                }
                ownership.ensureOwnedBy(em, company, category, "Budget category");
                category.setCode(cleanCode);
                category.setName(cleanName);
                category.setActive(active);
                category.touchUpdatedAt();

                if (category.getId() == null)
                {
                    em.persist(category);
                }

                em.getTransaction().commit();
                return category;
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive())
                {
                    em.getTransaction().rollback();
                }
                throw mapPersistenceError(ex, cleanCode);
            }
        }
    }

    /**
     * Caller-owned transaction variant used by governed interchange imports.
     *
     * <p>Legacy SCLX without complete category masters carries only category codes.
     * A new empty target in that compatibility path therefore uses
     * the portable code as the initial display name. The caller owns commit or
     * rollback so this supporting master remains atomic with the imported
     * budget graph.</p>
     */
    public BudgetCategory createForImport(
            EntityManager em,
            Company company,
            String code)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        String cleanCode = requireText(code, "Budget category code");
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Caller-owned transaction must be active.");
        }
        if (!em.contains(company) || company.getId() == null)
        {
            throw new IllegalArgumentException("Company must be managed by the caller-owned transaction.");
        }
        List<BudgetCategory> existing = em.createQuery(
                        "from BudgetCategory b where b.company = :company and b.code = :code",
                        BudgetCategory.class)
                .setParameter("company", company)
                .setParameter("code", cleanCode)
                .setMaxResults(1)
                .getResultList();
        if (!existing.isEmpty())
        {
            throw new IllegalStateException(
                    "SCLX budget category already exists in the empty target: " + cleanCode + ".");
        }

        BudgetCategory category = new BudgetCategory();
        category.setCompany(company);
        category.setCode(cleanCode);
        category.setName(cleanCode);
        category.setActive(true);
        category.touchUpdatedAt();
        em.persist(category);
        return category;
    }

    private static RuntimeException mapPersistenceError(RuntimeException ex, String code)
    {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("uq_budget_category_company_code") || message.contains("unique") || message.contains("constraint"))
        {
            return new IllegalArgumentException("Budget category code already exists for the company: " + code + ".", ex);
        }
        return ex;
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }
}
