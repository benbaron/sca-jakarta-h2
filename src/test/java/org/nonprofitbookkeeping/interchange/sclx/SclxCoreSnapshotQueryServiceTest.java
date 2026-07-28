package org.nonprofitbookkeeping.interchange.sclx;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.CounterpartyKind;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.Merchant;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SclxCoreSnapshotQueryServiceTest
{
    @TempDir
    Path tempDir;

    @Test
    void loadsOnlySelectedCompanyActiveChartAndCompanyFunds()
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("selected-company-snapshot")))
        {
            seedTwoCompanies(jpa);

            SclxExportDocument document = new SclxCoreSnapshotQueryService(jpa, () -> "alpha")
                    .query(Instant.parse("2026-07-26T21:45:00Z"));

            assertEquals("ALPHA", document.organization().code());
            assertEquals(List.of("1010", "6100", "6200"),
                    document.chartOfAccounts().stream()
                            .map(SclxExportDocument.Account::code)
                            .toList());
            assertFalse(document.chartOfAccounts().get(2).active());
            assertEquals(List.of("GENERAL", "RESERVE"),
                    document.funds().stream().map(SclxExportDocument.Fund::code).toList());
            assertFalse(document.funds().get(1).active());
            List<SclxActivityExtension.Entry> activities = SclxActivityExtension.entries(document.extensions());
            assertEquals(List.of("EVENT", "OLD"), activities.stream()
                    .map(SclxActivityExtension.Entry::code)
                    .toList());
            assertFalse(activities.get(1).active());
            SclxPartyExtension.Data parties = SclxPartyExtension.data(document.extensions());
            assertEquals(List.of("Alpha Former", "Alpha Vendor"), parties.counterparties().stream()
                    .map(SclxPartyExtension.CounterpartyEntry::displayName)
                    .sorted()
                    .toList());
            assertFalse(parties.counterparties().stream()
                    .filter(entry -> entry.displayName().equals("Alpha Former"))
                    .findFirst()
                    .orElseThrow()
                    .active());
            assertEquals(List.of("Alpha Merchant", "Old Merchant"), parties.merchants().stream()
                    .map(SclxPartyExtension.MerchantEntry::name)
                    .sorted()
                    .toList());
        }
    }

    @Test
    void rejectsSelectedCompanyWithoutActiveChart()
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("missing-active-chart")))
        {
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.persist(company("NOCHART"));
                em.getTransaction().commit();
            }

            SclxCoreSnapshotQueryService service = new SclxCoreSnapshotQueryService(jpa, () -> "NOCHART");
            assertThrows(NullPointerException.class, () -> service.query(Instant.EPOCH));
        }
    }

    private static void seedTwoCompanies(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();

            Company alpha = company("ALPHA");
            Company beta = company("BETA");
            em.persist(alpha);
            em.persist(beta);

            ChartOfAccounts alphaActive = chart(alpha, "Alpha Active", "1", ChartStatus.ACTIVE);
            ChartOfAccounts alphaRetired = chart(alpha, "Alpha Retired", "0", ChartStatus.RETIRED);
            ChartOfAccounts betaActive = chart(beta, "Beta Active", "1", ChartStatus.ACTIVE);
            em.persist(alphaActive);
            em.persist(alphaRetired);
            em.persist(betaActive);

            alpha.setActiveChartOfAccounts(alphaActive);
            beta.setActiveChartOfAccounts(betaActive);

            em.persist(account(alphaActive, "1010", "Cash", AccountType.ASSET, true, true));
            em.persist(account(alphaActive, "6100", "Operations", AccountType.EXPENSE, true, true));
            em.persist(account(alphaActive, "6200", "Inactive expense", AccountType.EXPENSE, true, false));
            em.persist(account(alphaRetired, "9998", "Retired chart account", AccountType.ASSET, true, true));
            em.persist(account(betaActive, "9999", "Other company account", AccountType.ASSET, true, true));

            em.persist(fund(alpha, "GENERAL", true));
            em.persist(fund(alpha, "RESERVE", false));
            em.persist(fund(beta, "FOREIGN", true));

            em.persist(activity(alpha, "EVENT", true));
            em.persist(activity(alpha, "OLD", false));
            em.persist(activity(beta, "FOREIGN", true));

            em.persist(counterparty(alpha, "Alpha Vendor", true));
            em.persist(counterparty(alpha, "Alpha Former", false));
            em.persist(counterparty(beta, "Beta Vendor", true));
            em.persist(merchant(alpha, "Alpha Merchant", true));
            em.persist(merchant(alpha, "Old Merchant", false));
            em.persist(merchant(beta, "Beta Merchant", true));

            em.getTransaction().commit();
        }
    }

    private static Company company(String code)
    {
        Company company = new Company();
        company.setCode(code);
        company.setDisplayName(code + " Company");
        company.setDefaultCurrency("USD");
        company.setFiscalYearStartMonth(1);
        company.setFiscalYearStartDay(1);
        return company;
    }

    private static ChartOfAccounts chart(
            Company company,
            String name,
            String version,
            ChartStatus status)
    {
        ChartOfAccounts chart = new ChartOfAccounts();
        chart.setCompany(company);
        chart.setName(name);
        chart.setVersion(version);
        chart.setStatus(status);
        return chart;
    }

    private static Account account(
            ChartOfAccounts chart,
            String code,
            String name,
            AccountType type,
            boolean posting,
            boolean active)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setNormalBalance(NormalBalance.DEBIT);
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setPosting(posting);
        account.setActive(active);
        return account;
    }


    private static Activity activity(Company company, String code, boolean active)
    {
        Activity activity = new Activity();
        activity.setCompany(company);
        activity.setCode(code);
        activity.setName(code + " Activity");
        activity.setActive(active);
        return activity;
    }

    private static Counterparty counterparty(Company company, String name, boolean active)
    {
        Counterparty counterparty = new Counterparty();
        counterparty.setCompany(company);
        counterparty.setDisplayName(name);
        counterparty.setKind(CounterpartyKind.ORG);
        counterparty.setActive(active);
        return counterparty;
    }

    private static Merchant merchant(Company company, String name, boolean active)
    {
        Merchant merchant = new Merchant();
        merchant.setCompany(company);
        merchant.setName(name);
        merchant.setActive(active);
        return merchant;
    }

    private static Fund fund(Company company, String code, boolean active)
    {
        Fund fund = new Fund();
        fund.setCompany(company);
        fund.setCode(code);
        fund.setName(code + " Fund");
        fund.setFundType(FundType.UNRESTRICTED);
        fund.setActive(active);
        return fund;
    }
}
