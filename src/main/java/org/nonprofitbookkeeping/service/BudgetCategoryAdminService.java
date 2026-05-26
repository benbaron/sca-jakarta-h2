package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;

@ApplicationScoped
public class BudgetCategoryAdminService
{
    @Inject
    Jpa jpa;

    public BudgetCategoryAdminService() {}

    public BudgetCategoryAdminService(Jpa jpa)
    {
        this.jpa = jpa;
    }

    public BudgetCategory upsert(String code, String name, boolean active)
    {
        String cleanCode = requireText(code, "Budget category code");
        String cleanName = requireText(name, "Budget category name");

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                List<BudgetCategory> existingMatches = em.createQuery(
                                "from BudgetCategory b where b.code = :code",
                                BudgetCategory.class)
                        .setParameter("code", cleanCode)
                        .setMaxResults(2)
                        .getResultList();

                BudgetCategory category = existingMatches.isEmpty() ? new BudgetCategory() : existingMatches.get(0);
                category.setCode(cleanCode);
                category.setName(cleanName);
                category.setActive(active);
                category.touchUpdatedAt();

                if (category.getId() == null)
                {
                    em.persist(category);
                }
                else
                {
                    category = em.merge(category);
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

    private static RuntimeException mapPersistenceError(RuntimeException ex, String code)
    {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("uq_budget_category_code") || message.contains("unique") || message.contains("constraint"))
        {
            return new IllegalArgumentException("Budget category code already exists: " + code + ".", ex);
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
