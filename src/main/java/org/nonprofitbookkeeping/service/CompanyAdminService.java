package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.CompanyTaxProfile;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.time.DateTimeException;
import java.time.MonthDay;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

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

    public List<CompanyView> listCompanyViews()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("from Company c order by c.code", Company.class)
                    .getResultList()
                    .stream()
                    .map(CompanyAdminService::toView)
                    .toList();
        }
    }

    public List<CompanyView> listActiveCompanyViews()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("from Company c where c.active = true order by c.code", Company.class)
                    .getResultList()
                    .stream()
                    .map(CompanyAdminService::toView)
                    .toList();
        }
    }

    public Optional<CompanyView> findCompany(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            return findByCode(em, companyCode).map(CompanyAdminService::toView);
        }
    }

    public CompanyView requireActiveCompany(String companyCode)
    {
        CompanyView company = findCompany(companyCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Company does not exist in the active database: " + normalizeCode(companyCode)));
        if (!company.active())
        {
            throw new IllegalStateException("Company is inactive and cannot be selected: " + company.code());
        }
        return company;
    }

    /**
     * Resolves a persisted recent-company preference to an authoritative active
     * company. A missing or inactive preference falls back to the first active
     * H2 company and never creates a company row.
     */
    public CompanyView resolveActiveCompany(String preferredCompanyCode)
    {
        if (preferredCompanyCode != null && !preferredCompanyCode.isBlank())
        {
            Optional<CompanyView> preferred = findCompany(preferredCompanyCode);
            if (preferred.isPresent() && preferred.get().active())
            {
                return preferred.get();
            }
        }
        return listActiveCompanyViews().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "The active database has no active company. Reactivate a company before continuing."));
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
        CompanyView existing = findCompany(code).orElse(null);
        CompanyView saved = save(new CompanyCommand(
                existing == null ? null : existing.id(),
                code,
                displayName,
                legalName,
                branchType,
                parentOrganization,
                existing == null || existing.active(),
                existing == null ? 1 : existing.fiscalYearStartMonth(),
                existing == null ? 1 : existing.fiscalYearStartDay(),
                existing == null ? "USD" : existing.defaultCurrency()),
                null);
        try (EntityManager em = jpa.em())
        {
            return em.find(Company.class, saved.id());
        }
    }

    public CompanyView createCompany(String code, String displayName)
    {
        return save(new CompanyCommand(
                null,
                code,
                displayName,
                displayName,
                null,
                null,
                true,
                1,
                1,
                "USD"),
                null);
    }

    /**
     * Creates or updates a company by stable database ID in one transaction.
     * The current active company cannot be deactivated, and at least one active
     * company must remain.
     */
    public CompanyView save(CompanyCommand command, String currentActiveCompanyCode)
    {
        CompanyCommand clean = validate(command);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company;
                if (clean.id() == null)
                {
                    company = new Company();
                }
                else
                {
                    company = em.find(Company.class, clean.id());
                    if (company == null)
                    {
                        throw new IllegalArgumentException("Company not found: ID " + clean.id());
                    }
                }

                ensureUniqueCode(em, clean.code(), clean.id());
                enforceActiveLifecycle(em, company, clean.active(), currentActiveCompanyCode);

                if (company.getId() != null && !company.getCode().equals(clean.code()))
                {
                    renameCompanyCodeReferences(em, company.getCode(), clean.code());
                }

                company.setCode(clean.code());
                company.setDisplayName(clean.displayName());
                company.setLegalName(blankToNull(clean.legalName()));
                company.setBranchType(blankToNull(clean.branchType()));
                company.setParentOrganization(blankToNull(clean.parentOrganization()));
                company.setActive(clean.active());
                company.setFiscalYearStartMonth(clean.fiscalYearStartMonth());
                company.setFiscalYearStartDay(clean.fiscalYearStartDay());
                company.setDefaultCurrency(clean.defaultCurrency());
                company.touchUpdatedAt();
                if (company.getId() == null)
                {
                    em.persist(company);
                }
                em.flush();
                CompanyView result = toView(company);
                em.getTransaction().commit();
                return result;
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

    private static void enforceActiveLifecycle(
            EntityManager em,
            Company company,
            boolean requestedActive,
            String currentActiveCompanyCode)
    {
        if (requestedActive)
        {
            return;
        }

        if (company.getId() != null && currentActiveCompanyCode != null && !currentActiveCompanyCode.isBlank())
        {
            Optional<Company> current = findByCode(em, currentActiveCompanyCode);
            if (current.isPresent() && Objects.equals(current.get().getId(), company.getId()))
            {
                throw new IllegalStateException(
                        "Select another active company before deactivating the current company.");
            }
        }

        String jpql = company.getId() == null
                ? "select count(c) from Company c where c.active = true"
                : "select count(c) from Company c where c.active = true and c.id <> :id";
        var query = em.createQuery(jpql, Long.class);
        if (company.getId() != null)
        {
            query.setParameter("id", company.getId());
        }
        if (query.getSingleResult() == 0L)
        {
            throw new IllegalStateException("At least one company must remain active.");
        }
    }

    private static void ensureUniqueCode(EntityManager em, String code, Long ignoredId)
    {
        String jpql = ignoredId == null
                ? "select count(c) from Company c where lower(c.code) = :code"
                : "select count(c) from Company c where lower(c.code) = :code and c.id <> :id";
        var query = em.createQuery(jpql, Long.class).setParameter("code", code.toLowerCase(Locale.ROOT));
        if (ignoredId != null)
        {
            query.setParameter("id", ignoredId);
        }
        if (query.getSingleResult() > 0L)
        {
            throw new IllegalArgumentException("Company code already exists: " + code);
        }
    }

    /**
     * Keeps the remaining code-keyed company records attached to the same
     * stable Company row. Most company-owned tables use company_id foreign
     * keys; these UI-state and period-close tables intentionally use the
     * business code and therefore must move in the same transaction.
     */
    private static void renameCompanyCodeReferences(EntityManager em, String previousCode, String nextCode)
    {
        em.createNativeQuery("delete from company_ui_preference where company_code = :nextCode")
                .setParameter("nextCode", nextCode)
                .executeUpdate();
        em.createNativeQuery("delete from company_ui_state where company_code = :nextCode")
                .setParameter("nextCode", nextCode)
                .executeUpdate();
        for (String table : List.of(
                "company_ui_preference",
                "company_ui_state",
                "period_close_range",
                "period_close_event"))
        {
            em.createNativeQuery("update " + table + " set company_code = :nextCode where company_code = :previousCode")
                    .setParameter("nextCode", nextCode)
                    .setParameter("previousCode", previousCode)
                    .executeUpdate();
        }
    }

    private static Optional<Company> findByCode(EntityManager em, String companyCode)
    {
        String code = normalizeCode(companyCode);
        return em.createQuery("from Company c where lower(c.code) = :code", Company.class)
                .setParameter("code", code.toLowerCase(Locale.ROOT))
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    private static CompanyCommand validate(CompanyCommand command)
    {
        Objects.requireNonNull(command, "command");
        String code = normalizeCode(command.code());
        String displayName = requireText(command.displayName(), "Display name");
        requireLength(code, 64, "Company code");
        requireLength(displayName, 200, "Display name");
        requireLength(command.legalName(), 250, "Legal name");
        requireLength(command.branchType(), 80, "Branch type");
        requireLength(command.parentOrganization(), 200, "Parent organization");
        try
        {
            MonthDay.of(command.fiscalYearStartMonth(), command.fiscalYearStartDay());
        }
        catch (DateTimeException ex)
        {
            throw new IllegalArgumentException("Fiscal-year start must be a valid month and day.", ex);
        }
        String currency = requireText(command.defaultCurrency(), "Default currency").toUpperCase(Locale.ROOT);
        try
        {
            Currency.getInstance(currency);
        }
        catch (IllegalArgumentException ex)
        {
            throw new IllegalArgumentException("Default currency must be a valid ISO-4217 code.", ex);
        }
        return new CompanyCommand(
                command.id(),
                code,
                displayName,
                blankToNull(command.legalName()),
                blankToNull(command.branchType()),
                blankToNull(command.parentOrganization()),
                command.active(),
                command.fiscalYearStartMonth(),
                command.fiscalYearStartDay(),
                currency);
    }

    private static CompanyView toView(Company company)
    {
        return new CompanyView(
                company.getId(),
                company.getCode(),
                company.getDisplayName(),
                company.getLegalName(),
                company.getBranchType(),
                company.getParentOrganization(),
                company.isActive(),
                company.getFiscalYearStartMonth(),
                company.getFiscalYearStartDay(),
                company.getDefaultCurrency());
    }

    private static String normalizeCode(String value)
    {
        return requireText(value, "Company code").toUpperCase(Locale.ROOT);
    }

    private static void requireLength(String value, int maximum, String label)
    {
        if (value != null && value.trim().length() > maximum)
        {
            throw new IllegalArgumentException(label + " must not exceed " + maximum + " characters.");
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
