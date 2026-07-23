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

/** Company-scoped budget-category lookup service. */
@ApplicationScoped
public class BudgetCategoryLookupService
{
    @Inject
    Jpa jpa;

    private Supplier<String> companyCodeSupplier = () -> "DEFAULT";

    public BudgetCategoryLookupService()
    {
    }

    public BudgetCategoryLookupService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public BudgetCategoryLookupService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
    }

    public List<BudgetCategory> listActiveBudgetCategories()
    {
        return list(true);
    }

    public List<BudgetCategory> listAllBudgetCategories()
    {
        return list(false);
    }

    private List<BudgetCategory> list(boolean activeOnly)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCodeSupplier.get());
            String activeClause = activeOnly ? "and b.active = true " : "";
            return em.createQuery(
                            "from BudgetCategory b where b.company = :company "
                                    + activeClause + "order by b.code",
                            BudgetCategory.class)
                    .setParameter("company", company)
                    .getResultList();
        }
    }
}
