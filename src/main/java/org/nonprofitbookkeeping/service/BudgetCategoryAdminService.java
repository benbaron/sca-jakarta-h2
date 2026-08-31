package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@ApplicationScoped
public class BudgetCategoryAdminService
{
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
     * <p>SCLX carries a budget category code on each budget line but has no
     * separate category-name master record. A new empty target therefore uses
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
