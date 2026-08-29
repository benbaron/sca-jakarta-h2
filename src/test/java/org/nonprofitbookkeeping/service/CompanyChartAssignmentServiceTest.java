package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyChartAssignmentServiceTest
{
    @Test
    void assignmentPromotesOwnedDraftWithoutMovingHistoryOrRetiringPriorChart(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("company-chart-assignment")))
        {
            CompanyAdminService service = new CompanyAdminService(jpa);
            CompanyView company = service.requireActiveCompany("DEFAULT");
            CompanyView otherCompany = service.createCompany("OTHER", "Other Company");

            Long priorChartId;
            Long draftChartId;
            Long retiredChartId;
            Long otherChartId;
            Long priorAccountId;
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                Company managed = em.find(Company.class, company.id());
                Company other = em.find(Company.class, otherCompany.id());

                ChartOfAccounts prior = chart(managed, "Prior Chart", "2025", ChartStatus.ACTIVE);
                ChartOfAccounts draft = chart(managed, "Imported Draft", "2026", ChartStatus.DRAFT);
                ChartOfAccounts retired = chart(managed, "Retired Chart", "2024", ChartStatus.RETIRED);
                ChartOfAccounts foreign = chart(other, "Other Chart", "2026", ChartStatus.ACTIVE);
                em.persist(prior);
                em.persist(draft);
                em.persist(retired);
                em.persist(foreign);
                em.flush();

                Account account = new Account();
                account.setChart(prior);
                account.setCode("1000");
                account.setName("Historical Cash");
                account.setAccountType(AccountType.ASSET);
                account.setNormalBalance(NormalBalance.DEBIT);
                em.persist(account);

                managed.setActiveChartOfAccounts(prior);
                em.flush();
                priorChartId = prior.getId();
                draftChartId = draft.getId();
                retiredChartId = retired.getId();
                otherChartId = foreign.getId();
                priorAccountId = account.getId();
                em.getTransaction().commit();
            }

            assertEquals(3, service.listCompanyCharts(company.id()).size());
            assertTrue(service.listCompanyCharts(company.id()).stream()
                    .noneMatch(chart -> chart.id().equals(otherChartId)));

            CompanyChartView assigned = service.assignActiveChart(company.id(), draftChartId);
            assertEquals(draftChartId, assigned.id());
            assertEquals(ChartStatus.ACTIVE, assigned.status());
            assertTrue(assigned.activeForCompany());

            try (var em = jpa.em())
            {
                Company managed = em.find(Company.class, company.id());
                ChartOfAccounts prior = em.find(ChartOfAccounts.class, priorChartId);
                ChartOfAccounts draft = em.find(ChartOfAccounts.class, draftChartId);
                Account historical = em.find(Account.class, priorAccountId);

                assertEquals(draftChartId, managed.getActiveChartOfAccounts().getId());
                assertEquals(ChartStatus.ACTIVE, draft.getStatus());
                assertEquals(ChartStatus.ACTIVE, prior.getStatus());
                assertEquals(priorChartId, historical.getChart().getId());
            }

            assertThrows(
                    CompanyOwnershipException.class,
                    () -> service.assignActiveChart(company.id(), otherChartId));
            assertThrows(
                    IllegalStateException.class,
                    () -> service.assignActiveChart(company.id(), retiredChartId));
        }
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
}
