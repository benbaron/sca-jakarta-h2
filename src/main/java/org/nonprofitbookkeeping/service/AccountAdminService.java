package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;

@ApplicationScoped
/**
 * AccountAdminService component.
 */
public class AccountAdminService
{
    @Inject
    Jpa jpa;

    public AccountAdminService()
    {
    }

    public AccountAdminService(Jpa jpa)
    {
        this.jpa = jpa;
    }

    public Account upsert(String code,
                          String name,
                          AccountType accountType,
                          NormalBalance normalBalance,
                          AccountSubtype subtype,
                          String parentCode,
                          boolean active)
    {
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

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                ChartOfAccounts chart = resolveChart(em);

                List<Account> existingMatches = em.createQuery(
                                "from Account a where a.chart = :chart and a.code = :code",
                                Account.class)
                        .setParameter("chart", chart)
                        .setParameter("code", cleanCode)
                        .setMaxResults(2)
                        .getResultList();

                Account account;
                if (existingMatches.isEmpty())
                {
                    account = new Account();
                    account.setChart(chart);
                    account.setPosting(true);
                }
                else
                {
                    account = existingMatches.get(0);
                }

                Account parent = resolveParent(em, chart, cleanCode, parentCode);

                account.setCode(cleanCode);
                account.setName(cleanName);
                account.setAccountType(accountType);
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

    private ChartOfAccounts resolveChart(EntityManager em)
    {
        List<ChartOfAccounts> activeCharts = em.createQuery(
                        "from ChartOfAccounts c where c.status = :status order by c.id",
                        ChartOfAccounts.class)
                .setParameter("status", ChartStatus.ACTIVE)
                .setMaxResults(1)
                .getResultList();
        if (!activeCharts.isEmpty())
        {
            return activeCharts.get(0);
        }

        List<ChartOfAccounts> anyChart = em.createQuery(
                        "from ChartOfAccounts c order by c.id",
                        ChartOfAccounts.class)
                .setMaxResults(1)
                .getResultList();

        if (anyChart.isEmpty())
        {
            throw new IllegalStateException("No Chart of Accounts exists. Seed chart data before creating accounts.");
        }
        return anyChart.get(0);
    }
}
