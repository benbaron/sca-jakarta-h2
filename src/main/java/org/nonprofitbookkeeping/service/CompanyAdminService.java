package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.CompanyTaxProfile;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;

@ApplicationScoped
public class CompanyAdminService
{
    @Inject
    Jpa jpa;

    public CompanyAdminService() {}

    public CompanyAdminService(Jpa jpa)
    {
        this.jpa = jpa;
    }

    public List<Company> listCompanies()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("from Company c order by c.code", Company.class).getResultList();
        }
    }

    public List<CompanyBankAccount> listBankAccounts(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("""
                    from CompanyBankAccount b
                    where b.company.code = :code
                    order by b.name
                    """, CompanyBankAccount.class)
                    .setParameter("code", requireText(companyCode, "Company code"))
                    .getResultList();
        }
    }

    public CompanyTaxProfile taxProfile(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("""
                    from CompanyTaxProfile t
                    where t.company.code = :code
                    """, CompanyTaxProfile.class)
                    .setParameter("code", requireText(companyCode, "Company code"))
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
        }
    }

    public Company upsertCompany(String code, String displayName, String legalName, String branchType, String parentOrganization)
    {
        String cleanCode = requireText(code, "Company code").toUpperCase();
        String cleanName = requireText(displayName, "Display name");
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = em.createQuery("from Company c where c.code = :code", Company.class)
                        .setParameter("code", cleanCode)
                        .setMaxResults(1)
                        .getResultStream()
                        .findFirst()
                        .orElseGet(Company::new);
                company.setCode(cleanCode);
                company.setDisplayName(cleanName);
                company.setLegalName(blankToNull(legalName));
                company.setBranchType(blankToNull(branchType));
                company.setParentOrganization(blankToNull(parentOrganization));
                company.touchUpdatedAt();
                if (company.getId() == null)
                {
                    em.persist(company);
                }
                else
                {
                    company = em.merge(company);
                }
                em.getTransaction().commit();
                return company;
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw ex;
            }
        }
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
