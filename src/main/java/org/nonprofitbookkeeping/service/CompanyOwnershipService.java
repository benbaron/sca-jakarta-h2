package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountingPeriod;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyOwnershipIssue;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.Merchant;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Central company-ownership validation used by all P15-capable services. */
@ApplicationScoped
public class CompanyOwnershipService
{
    private final Jpa jpa;

    @Inject
    public CompanyOwnershipService(Jpa jpa)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
    }

    public Company requireCompany(EntityManager em, String companyCode)
    {
        String code = requireText(companyCode, "Company code").toUpperCase(Locale.ROOT);
        List<Company> matches = em.createQuery(
                        "from Company c where upper(c.code) = :code",
                        Company.class)
                .setParameter("code", code)
                .setMaxResults(2)
                .getResultList();
        if (matches.isEmpty())
        {
            throw new CompanyOwnershipException("Company does not exist: " + code + ".");
        }
        if (matches.size() != 1)
        {
            throw new CompanyOwnershipException("Company code is ambiguous: " + code + ".");
        }
        return matches.get(0);
    }

    public Company requireCompany(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            return requireCompany(em, companyCode);
        }
    }

    public void requireOwnedBy(Company expected, Company actual, String label)
    {
        if (expected == null || expected.getId() == null)
        {
            throw new CompanyOwnershipException("Expected company is not persisted.");
        }
        if (actual == null || actual.getId() == null)
        {
            throw new CompanyOwnershipException(label + " has no authoritative company owner.");
        }
        if (!expected.getId().equals(actual.getId()))
        {
            throw new CompanyOwnershipException(label + " belongs to company " + actual.getCode()
                    + ", not " + expected.getCode() + ".");
        }
    }

    public void requireOwnedBy(Company expected, ChartOfAccounts chart, String label)
    {
        requireOwnedBy(expected, chart == null ? null : chart.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, Account account, String label)
    {
        requireOwnedBy(expected, account == null ? null : account.getChart(), label);
    }

    public void requireOwnedBy(Company expected, Fund fund, String label)
    {
        requireOwnedBy(expected, fund == null ? null : fund.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, BudgetCategory category, String label)
    {
        requireOwnedBy(expected, category == null ? null : category.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, BudgetPlan plan, String label)
    {
        requireOwnedBy(expected, plan == null ? null : plan.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, Activity activity, String label)
    {
        requireOwnedBy(expected, activity == null ? null : activity.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, Counterparty counterparty, String label)
    {
        requireOwnedBy(expected, counterparty == null ? null : counterparty.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, Merchant merchant, String label)
    {
        requireOwnedBy(expected, merchant == null ? null : merchant.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, Txn transaction, String label)
    {
        requireOwnedBy(expected, transaction == null ? null : transaction.getCompany(), label);
    }

    public void requireOwnedBy(Company expected, AccountingPeriod period, String label)
    {
        requireOwnedBy(expected, period == null ? null : period.getCompany(), label);
    }


    /**
     * Adopts an unowned legacy row only when the database contains exactly one
     * company. That is deterministic database evidence, not the selected UI
     * company. Multi-company ambiguity is always rejected.
     */
    public void ensureOwnedBy(EntityManager em, Company expected, ChartOfAccounts chart, String label)
    {
        if (chart == null)
        {
            throw new CompanyOwnershipException(label + " is required.");
        }
        if (chart.getCompany() == null && isOnlyCompany(em, expected))
        {
            chart.setCompany(expected);
        }
        requireOwnedBy(expected, chart, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, Fund fund, String label)
    {
        if (fund == null)
        {
            throw new CompanyOwnershipException(label + " is required.");
        }
        if (fund.getCompany() == null && isOnlyCompany(em, expected))
        {
            fund.setCompany(expected);
        }
        requireOwnedBy(expected, fund, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, BudgetCategory category, String label)
    {
        if (category == null)
        {
            return;
        }
        if (category.getCompany() == null && isOnlyCompany(em, expected))
        {
            category.setCompany(expected);
        }
        requireOwnedBy(expected, category, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, BudgetPlan plan, String label)
    {
        if (plan == null)
        {
            throw new CompanyOwnershipException(label + " is required.");
        }
        if (plan.getCompany() == null && isOnlyCompany(em, expected))
        {
            plan.setCompany(expected);
        }
        requireOwnedBy(expected, plan, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, Activity activity, String label)
    {
        if (activity == null)
        {
            return;
        }
        if (activity.getCompany() == null && isOnlyCompany(em, expected))
        {
            activity.setCompany(expected);
        }
        requireOwnedBy(expected, activity, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, Counterparty counterparty, String label)
    {
        if (counterparty == null)
        {
            return;
        }
        if (counterparty.getCompany() == null && isOnlyCompany(em, expected))
        {
            counterparty.setCompany(expected);
        }
        requireOwnedBy(expected, counterparty, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, Merchant merchant, String label)
    {
        if (merchant == null)
        {
            return;
        }
        if (merchant.getCompany() == null && isOnlyCompany(em, expected))
        {
            merchant.setCompany(expected);
        }
        requireOwnedBy(expected, merchant, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, Account account, String label)
    {
        if (account == null)
        {
            throw new CompanyOwnershipException(label + " is required.");
        }
        ensureOwnedBy(em, expected, account.getChart(), label + " chart");
    }

    public void ensureOwnedBy(EntityManager em, Company expected, Txn transaction, String label)
    {
        if (transaction == null)
        {
            throw new CompanyOwnershipException(label + " is required.");
        }
        if (transaction.getCompany() == null && isOnlyCompany(em, expected))
        {
            transaction.setCompany(expected);
        }
        requireOwnedBy(expected, transaction, label);
    }

    public void ensureOwnedBy(EntityManager em, Company expected, AccountingPeriod period, String label)
    {
        if (period == null)
        {
            throw new CompanyOwnershipException(label + " is required.");
        }
        if (period.getCompany() == null && isOnlyCompany(em, expected))
        {
            period.setCompany(expected);
        }
        requireOwnedBy(expected, period, label);
    }

    private static boolean isOnlyCompany(EntityManager em, Company expected)
    {
        Long count = em.createQuery("select count(c) from Company c", Long.class).getSingleResult();
        if (count != 1L)
        {
            return false;
        }
        Long onlyId = em.createQuery("select min(c.id) from Company c", Long.class).getSingleResult();
        return expected != null && expected.getId() != null && expected.getId().equals(onlyId);
    }

    public List<CompanyOwnershipIssueView> listOpenIssues()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "from CompanyOwnershipIssue i where i.resolvedAt is null order by i.entityType, i.entityId",
                            CompanyOwnershipIssue.class)
                    .getResultList()
                    .stream()
                    .map(CompanyOwnershipService::toView)
                    .toList();
        }
    }

    public void requireNoOpenOwnershipIssues()
    {
        List<CompanyOwnershipIssueView> issues = listOpenIssues();
        if (!issues.isEmpty())
        {
            CompanyOwnershipIssueView first = issues.get(0);
            throw new CompanyOwnershipException("Company ownership has " + issues.size()
                    + " unresolved diagnostic(s); first is " + first.entityType() + " "
                    + first.entityId() + ": " + first.details());
        }
    }

    private static CompanyOwnershipIssueView toView(CompanyOwnershipIssue issue)
    {
        return new CompanyOwnershipIssueView(
                issue.getId(),
                issue.getEntityType(),
                issue.getEntityId(),
                issue.getIssueCode(),
                issue.getCandidateCompanyCount(),
                issue.getDetails(),
                issue.getDetectedAt());
    }

    private static String requireText(String value, String label)
    {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty())
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        return text;
    }
}
