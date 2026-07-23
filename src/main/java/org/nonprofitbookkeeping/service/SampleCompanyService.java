package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.CounterpartyKind;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.Merchant;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;
import java.util.Objects;

/**
 * Explicit sample-company data seeder for tester-created databases.
 */
public class SampleCompanyService
{
    public static final String SAMPLE_CHART_NAME = "SCA Sample Chart";
    private static final String SAMPLE_VERSION = "P03-S00";

    private final Jpa jpa;

    public SampleCompanyService(Jpa jpa)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
    }

    public SampleCompanySummary createOrRefresh()
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT");
                ChartOfAccounts chart = findOrCreateChart(em, company);
                seedAccounts(em, chart);
                seedFunds(em, company);
                seedBudgetCategories(em, company);
                seedActivities(em, company);
                seedMerchants(em, company);
                seedCounterparties(em, company);
                em.getTransaction().commit();
                return summarize(em, company, chart.getId());
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

    private static ChartOfAccounts findOrCreateChart(EntityManager em, Company company)
    {
        List<ChartOfAccounts> existing = em.createQuery(
                        "from ChartOfAccounts c where c.company = :company and c.name = :name order by c.id",
                        ChartOfAccounts.class)
                .setParameter("company", company)
                .setParameter("name", SAMPLE_CHART_NAME)
                .setMaxResults(1)
                .getResultList();
        ChartOfAccounts chart;
        if (existing.isEmpty())
        {
            chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName(SAMPLE_CHART_NAME);
            chart.setVersion(SAMPLE_VERSION);
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
        }
        else
        {
            chart = existing.get(0);
            chart.setCompany(company);
            chart.setVersion(SAMPLE_VERSION);
            chart.setStatus(ChartStatus.ACTIVE);
            chart.touchUpdatedAt();
        }
        return chart;
    }

    private static void seedAccounts(EntityManager em, ChartOfAccounts chart)
    {
        upsertAccount(em, chart, "1000", "Operating Checking", AccountType.BANK, NormalBalance.DEBIT, AccountSubtype.CASH);
        upsertAccount(em, chart, "1100", "Accounts Receivable", AccountType.ASSET, NormalBalance.DEBIT, AccountSubtype.RECEIVABLE);
        upsertAccount(em, chart, "1200", "Prepaid Expenses", AccountType.ASSET, NormalBalance.DEBIT, AccountSubtype.PREPAID);
        upsertAccount(em, chart, "1500", "Furniture and Equipment", AccountType.ASSET, NormalBalance.DEBIT, AccountSubtype.FIXED_ASSET);
        upsertAccount(em, chart, "2000", "Accounts Payable", AccountType.LIABILITY, NormalBalance.CREDIT, AccountSubtype.PAYABLE);
        upsertAccount(em, chart, "2400", "Deferred Revenue", AccountType.LIABILITY, NormalBalance.CREDIT, AccountSubtype.DEFERRED_REVENUE);
        upsertAccount(em, chart, "3000", "Unrestricted Net Assets", AccountType.EQUITY, NormalBalance.CREDIT, null);
        upsertAccount(em, chart, "3100", "Restricted Net Assets", AccountType.EQUITY, NormalBalance.CREDIT, null);
        upsertAccount(em, chart, "4000", "Contributions", AccountType.INCOME, NormalBalance.CREDIT, null);
        upsertAccount(em, chart, "4100", "Program Service Revenue", AccountType.INCOME, NormalBalance.CREDIT, null);
        upsertAccount(em, chart, "5000", "Program Supplies Expense", AccountType.EXPENSE, NormalBalance.DEBIT, null);
        upsertAccount(em, chart, "5100", "Administrative Expense", AccountType.EXPENSE, NormalBalance.DEBIT, null);
    }

    private static void upsertAccount(EntityManager em,
                                      ChartOfAccounts chart,
                                      String code,
                                      String name,
                                      AccountType type,
                                      NormalBalance normalBalance,
                                      AccountSubtype subtype)
    {
        Account account = em.createQuery(
                        "from Account a where a.chart = :chart and a.code = :code",
                        Account.class)
                .setParameter("chart", chart)
                .setParameter("code", code)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElseGet(Account::new);
        account.setChart(chart);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setNormalBalance(normalBalance);
        account.setSubtype(subtype);
        account.setPosting(true);
        account.setActive(true);
        if (account.getId() == null)
        {
            em.persist(account);
        }
    }

    private static void seedFunds(EntityManager em, Company company)
    {
        upsertFund(em, company, "UNREST", "Unrestricted Operating", FundType.UNRESTRICTED);
        upsertFund(em, company, "RESTRICT", "Donor Restricted", FundType.TEMP_RESTRICTED);
        upsertFund(em, company, "DESIGNATED", "Board Designated", FundType.DESIGNATED);
    }

    private static void upsertFund(EntityManager em, Company company, String code, String name, FundType type)
    {
        Fund fund = em.createQuery("from Fund f where f.company = :company and f.code = :code", Fund.class)
                .setParameter("company", company)
                .setParameter("code", code)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElseGet(Fund::new);
        fund.setCompany(company);
        fund.setCode(code);
        fund.setName(name);
        fund.setFundType(type);
        fund.setActive(true);
        fund.touchUpdatedAt();
        if (fund.getId() == null)
        {
            em.persist(fund);
        }
    }

    private static void seedBudgetCategories(EntityManager em, Company company)
    {
        upsertBudgetCategory(em, company, "PROGRAM", "Program Services");
        upsertBudgetCategory(em, company, "ADMIN", "Administration");
        upsertBudgetCategory(em, company, "FUNDRAISE", "Fundraising");
    }

    private static void upsertBudgetCategory(EntityManager em, Company company, String code, String name)
    {
        BudgetCategory category = em.createQuery("from BudgetCategory b where b.company = :company and b.code = :code", BudgetCategory.class)
                .setParameter("company", company)
                .setParameter("code", code)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElseGet(BudgetCategory::new);
        category.setCompany(company);
        category.setCode(code);
        category.setName(name);
        category.setActive(true);
        category.touchUpdatedAt();
        if (category.getId() == null)
        {
            em.persist(category);
        }
    }

    private static void seedActivities(EntityManager em, Company company)
    {
        upsertActivity(em, company, "GENERAL", "General Operations");
        upsertActivity(em, company, "OUTREACH", "Community Outreach");
    }

    private static void upsertActivity(EntityManager em, Company company, String code, String name)
    {
        Activity activity = em.createQuery("from Activity a where a.company = :company and a.code = :code", Activity.class)
                .setParameter("company", company)
                .setParameter("code", code)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElseGet(Activity::new);
        activity.setCompany(company);
        activity.setCode(code);
        activity.setName(name);
        activity.setActive(true);
        if (activity.getId() == null)
        {
            em.persist(activity);
        }
    }

    private static void seedMerchants(EntityManager em, Company company)
    {
        upsertMerchant(em, company, "SCA Office Supply");
        upsertMerchant(em, company, "Community Venue");
    }

    private static void upsertMerchant(EntityManager em, Company company, String name)
    {
        Merchant merchant = em.createQuery("from Merchant m where m.company = :company and m.name = :name", Merchant.class)
                .setParameter("company", company)
                .setParameter("name", name)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElseGet(Merchant::new);
        merchant.setCompany(company);
        merchant.setName(name);
        merchant.setActive(true);
        if (merchant.getId() == null)
        {
            em.persist(merchant);
        }
    }

    private static void seedCounterparties(EntityManager em, Company company)
    {
        upsertCounterparty(em, company, "Sample Donor", CounterpartyKind.PERSON);
        upsertCounterparty(em, company, "Sample Grantor Foundation", CounterpartyKind.ORG);
        upsertCounterparty(em, company, "Sample Program Client", CounterpartyKind.OTHER);
    }

    private static void upsertCounterparty(EntityManager em, Company company, String name, CounterpartyKind kind)
    {
        Counterparty counterparty = em.createQuery(
                        "from Counterparty c where c.company = :company and c.displayName = :name",
                        Counterparty.class)
                .setParameter("company", company)
                .setParameter("name", name)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElseGet(Counterparty::new);
        counterparty.setCompany(company);
        counterparty.setDisplayName(name);
        counterparty.setKind(kind);
        counterparty.setActive(true);
        if (counterparty.getId() == null)
        {
            em.persist(counterparty);
        }
    }

    private static SampleCompanySummary summarize(EntityManager em, Company company, Long chartId)
    {
        return new SampleCompanySummary(
                chartId,
                em.createQuery("select count(a) from Account a where a.chart.id = :chartId", Long.class)
                        .setParameter("chartId", chartId)
                        .getSingleResult(),
                em.createQuery("select count(f) from Fund f where f.company = :company and f.active = true", Long.class).setParameter("company", company).getSingleResult(),
                em.createQuery("select count(b) from BudgetCategory b where b.company = :company and b.active = true", Long.class).setParameter("company", company).getSingleResult(),
                em.createQuery("select count(a) from Activity a where a.company = :company and a.active = true", Long.class).setParameter("company", company).getSingleResult(),
                em.createQuery("select count(m) from Merchant m where m.company = :company and m.active = true", Long.class).setParameter("company", company).getSingleResult(),
                em.createQuery("select count(c) from Counterparty c where c.company = :company and c.active = true", Long.class).setParameter("company", company).getSingleResult());
    }

    public record SampleCompanySummary(
            Long chartId,
            long accountCount,
            long fundCount,
            long budgetCategoryCount,
            long activityCount,
            long merchantCount,
            long counterpartyCount)
    {
    }
}
