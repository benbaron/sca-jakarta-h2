package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Company-scoped fund lookup service. */
@ApplicationScoped
public class FundLookupService
{
    @Inject
    Jpa jpa;

    private Supplier<String> companyCodeSupplier = () -> "DEFAULT";

    public FundLookupService()
    {
    }

    public FundLookupService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public FundLookupService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
    }

    public List<Fund> listActiveFunds()
    {
        return list(true);
    }

    public List<Fund> listAllFunds()
    {
        return list(false);
    }

    private List<Fund> list(boolean activeOnly)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCodeSupplier.get());
            String activeClause = activeOnly ? "and f.active = true " : "";
            return em.createQuery(
                            "select f from Fund f left join fetch f.parent "
                                    + "where f.company = :company "
                                    + activeClause
                                    + "order by f.code",
                            Fund.class)
                    .setParameter("company", company)
                    .getResultList();
        }
    }
}
