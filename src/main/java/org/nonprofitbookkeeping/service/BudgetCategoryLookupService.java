package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;

@ApplicationScoped
public class BudgetCategoryLookupService
{
    @Inject
    Jpa jpa;

    public BudgetCategoryLookupService() {}

    public BudgetCategoryLookupService(Jpa jpa)
    {
        this.jpa = jpa;
    }

    public List<BudgetCategory> listActiveBudgetCategories()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                    "from BudgetCategory b where b.active = true order by b.code",
                    BudgetCategory.class)
                .getResultList();
        }
    }

    public List<BudgetCategory> listAllBudgetCategories()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                    "from BudgetCategory b order by b.code",
                    BudgetCategory.class)
                .getResultList();
        }
    }
}
