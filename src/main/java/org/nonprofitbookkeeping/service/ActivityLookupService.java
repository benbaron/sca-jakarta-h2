package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Company-scoped read boundary for Activity administration. */
@ApplicationScoped
public class ActivityLookupService
{
    @Inject
    Jpa jpa;

    private Supplier<String> companyCodeSupplier = () -> "DEFAULT";

    public ActivityLookupService()
    {
    }

    public ActivityLookupService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public ActivityLookupService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
    }

    public List<ActivityView> listActiveActivities()
    {
        return list(true);
    }

    public List<ActivityView> listAllActivities()
    {
        return list(false);
    }

    private List<ActivityView> list(boolean activeOnly)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCodeSupplier.get());
            String activeClause = activeOnly ? "and a.active = true " : "";
            return em.createQuery(
                            "from Activity a where a.company = :company "
                                    + activeClause + "order by upper(a.code), a.id",
                            Activity.class)
                    .setParameter("company", company)
                    .getResultList()
                    .stream()
                    .map(ActivityLookupService::view)
                    .toList();
        }
    }

    static ActivityView view(Activity activity)
    {
        return new ActivityView(
                activity.getId(),
                activity.getCode(),
                activity.getName(),
                activity.isActive());
    }
}
