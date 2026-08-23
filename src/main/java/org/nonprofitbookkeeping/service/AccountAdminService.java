package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@ApplicationScoped
/** Company-scoped account create/update service for the selected chart. */
public class AccountAdminService
{
    @Inject
    Jpa jpa;

    private Supplier<String> companyCodeSupplier = () -> "DEFAULT";

    public AccountAdminService()
    {
    }

    public AccountAdminService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public AccountAdminService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
    }

    public Account upsert(String code,
                          String name,
                          AccountType accountType,
                          NormalBalance normalBalance,
                          AccountSubtype subtype,
                          String parentCode,
                          boolean active)
    {
        return upsert(code, name, accountType, null, normalBalance, subtype, parentCode, active);
    }

    public Account upsert(String code,
                          String name,
                          AccountType accountType,
                          AccountFunction accountFunction,
                          NormalBalance normalBalance,
                          AccountSubtype subtype,
                          String parentCode,
                          boolean active)
    {
        // Validate the public command contract before opening persistence. In particular,
        // do not allow an uninitialized/default-constructed service to mask invalid
        // arguments with a NullPointerException from the JPA dependency.
        String cleanCode = requireText(code, "Account code");
        String cleanName = requireText(name, "Account name");
        if (accountType == null)
        {
            throw new IllegalArgumentException("Account type is required.");
        }
        if (normalBalance == null)
        {
            throw new IllegalArgumentException("Normal balance is required.");
        }
        validateClassification(accountType, accountFunction, normalBalance);

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, companyCodeSupplier.get());
                ChartOfAccounts chart = resolveChart(em, company, ownership);
                Account account = upsert(
                        em,
                        company,
                        chart,
                        cleanCode,
                        cleanName,
                        accountType,
                        accountFunction,
                        normalBalance,
                        subtype,
                        parentCode,
                        active);
                em.getTransaction().commit();
                return account;
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
     * Caller-owned transaction seam for governed batch operations. This method never commits or rolls back.
     */
    public Account upsert(EntityManager em,
                          Company company,
                          ChartOfAccounts chart,
                          String code,
                          String name,
                          AccountType accountType,
                          NormalBalance normalBalance,
                          AccountSubtype subtype,
                          String parentCode,
                          boolean active)
    {
        return upsert(em, company, chart, code, name, accountType, null, normalBalance,
                subtype, parentCode, active);
    }

    public Account upsert(EntityManager em,
                          Company company,
                          ChartOfAccounts chart,
                          String code,
                          String name,
                          AccountType accountType,
                          AccountFunction accountFunction,
                          NormalBalance normalBalance,
                          AccountSubtype subtype,
                          String parentCode,
                          boolean active)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(chart, "chart");
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Caller-owned account upsert requires an active transaction.");
        }

        String cleanCode = requireText(code, "Account code");
        String cleanName = requireText(name, "Account name");
        if (accountType == null)
        {
            throw new IllegalArgumentException("Account type is required.");
        }
        if (normalBalance == null)
        {
            throw new IllegalArgumentException("Normal balance is required.");
        }
        validateClassification(accountType, accountFunction, normalBalance);

        Company managedCompany = em.find(Company.class, company.getId());
        ChartOfAccounts managedChart = em.find(ChartOfAccounts.class, chart.getId());
        if (managedCompany == null)
        {
            throw new CompanyOwnershipException("Company no longer exists.");
        }
        if (managedChart == null)
        {
            throw new CompanyOwnershipException("Chart of Accounts no longer exists.");
        }
        CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
        ownership.requireOwnedBy(managedCompany, managedChart, "Chart of Accounts");

        List<Account> existingMatches = em.createQuery(
                        "from Account a where a.chart = :chart and a.code = :code",
                        Account.class)
                .setParameter("chart", managedChart)
                .setParameter("code", cleanCode)
                .setMaxResults(2)
                .getResultList();
        if (existingMatches.size() > 1)
        {
            throw new IllegalStateException("Account code is ambiguous in active chart: " + cleanCode + ".");
        }

        Account account;
        if (existingMatches.isEmpty())
        {
            account = new Account();
            account.setChart(managedChart);
            account.setPosting(true);
        }
        else
        {
            account = existingMatches.get(0);
        }

        Account parent = resolveParent(em, managedChart, cleanCode, parentCode);
        if (parent != null)
        {
            ownership.requireOwnedBy(managedCompany, parent, "Parent account");
        }

        account.setCode(cleanCode);
        account.setName(cleanName);
        account.setAccountType(accountType);
        account.setAccountFunction(accountFunction);
        account.setNormalBalance(normalBalance);
        account.setSubtype(subtype);
        account.setParent(parent);
        account.setActive(active);

        if (account.getId() == null)
        {
            em.persist(account);
        }
        else
        {
            account = em.merge(account);
        }
        return account;
    }

    private static void validateClassification(
            AccountType accountType,
            AccountFunction accountFunction,
            NormalBalance normalBalance)
    {
        if (accountFunction == AccountFunction.BANK
                && (accountType != AccountType.ASSET || normalBalance != NormalBalance.DEBIT))
        {
            throw new IllegalArgumentException(
                    "BANK function requires an ASSET account with a DEBIT normal balance.");
        }
    }

    private static RuntimeException mapPersistenceError(RuntimeException ex, String code)
    {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("uq_account_code") || message.contains("unique") || message.contains("constraint"))
        {
            return new IllegalArgumentException("Account code already exists: " + code + ".", ex);
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

    private static String normalizeOptional(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Account resolveParent(EntityManager em,
                                  ChartOfAccounts chart,
                                  String currentCode,
                                  String requestedParentCode)
    {
        String parentCode = normalizeOptional(requestedParentCode);
        if (parentCode == null)
        {
            return null;
        }
        if (parentCode.equals(currentCode))
        {
            throw new IllegalArgumentException("Parent account cannot be the same as account code.");
        }

        List<Account> parentMatches = em.createQuery(
                        "from Account a where a.chart = :chart and a.code = :code",
                        Account.class)
                .setParameter("chart", chart)
                .setParameter("code", parentCode)
                .setMaxResults(1)
                .getResultList();

        if (parentMatches.isEmpty())
        {
            throw new IllegalArgumentException("Parent account code does not exist in active chart: " + parentCode + ".");
        }

        return parentMatches.get(0);
    }

    private ChartOfAccounts resolveChart(
            EntityManager em,
            Company company,
            CompanyOwnershipService ownership)
    {
        Company managedCompany = em.find(Company.class, company.getId());
        if (managedCompany.getActiveChartOfAccounts() != null)
        {
            ChartOfAccounts active = managedCompany.getActiveChartOfAccounts();
            ownership.ensureOwnedBy(em, managedCompany, active, "Active Chart of Accounts");
            return active;
        }

        List<ChartOfAccounts> ownedActive = em.createQuery(
                        "from ChartOfAccounts c where c.company = :company and c.status = :status order by c.id",
                        ChartOfAccounts.class)
                .setParameter("company", managedCompany)
                .setParameter("status", ChartStatus.ACTIVE)
                .setMaxResults(2)
                .getResultList();
        if (ownedActive.size() == 1)
        {
            managedCompany.setActiveChartOfAccounts(ownedActive.get(0));
            return ownedActive.get(0);
        }
        if (ownedActive.size() > 1)
        {
            throw new CompanyOwnershipException("Company has multiple active Charts of Accounts.");
        }

        List<ChartOfAccounts> unowned = em.createQuery(
                        "from ChartOfAccounts c where c.company is null order by c.id",
                        ChartOfAccounts.class)
                .setMaxResults(2)
                .getResultList();
        if (unowned.size() == 1)
        {
            ChartOfAccounts chart = unowned.get(0);
            ownership.ensureOwnedBy(em, managedCompany, chart, "Chart of Accounts");
            managedCompany.setActiveChartOfAccounts(chart);
            return chart;
        }

        throw new IllegalStateException("No unambiguous Chart of Accounts exists for company "
                + managedCompany.getCode() + ".");
    }
}
