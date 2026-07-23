package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Company-scoped account lookup for UI reference data. */
@ApplicationScoped
public class AccountLookupService
{
    @Inject
    Jpa jpa;

    private Supplier<String> companyCodeSupplier = () -> "DEFAULT";

    public AccountLookupService()
    {
    }

    public AccountLookupService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public AccountLookupService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
    }

    public List<Account> listActivePostingAccounts()
    {
        return list(true);
    }

    public List<Account> listPostingAccountsIncludingInactive()
    {
        return list(false);
    }

    private List<Account> list(boolean activeOnly)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCodeSupplier.get());
            String activeClause = activeOnly ? "and a.active = true " : "";
            return em.createQuery(
                            "select a from Account a left join fetch a.parent "
                                    + "where a.chart.company = :company "
                                    + activeClause
                                    + "and a.posting = true order by a.code",
                            Account.class)
                    .setParameter("company", company)
                    .getResultList();
        }
    }
}
