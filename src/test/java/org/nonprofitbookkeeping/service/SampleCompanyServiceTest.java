package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.ui.TransactionLineEditorModel;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SampleCompanyServiceTest
{
    @Test
    public void createOrRefresh_isIdempotentAndProvidesReferenceChoices(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("sample-company")))
        {
            SampleCompanyService service = new SampleCompanyService(jpa);
            SampleCompanyService.SampleCompanySummary first = service.createOrRefresh();
            SampleCompanyService.SampleCompanySummary second = service.createOrRefresh();

            assertEquals(first.chartId(), second.chartId());
            assertEquals(first.accountCount(), second.accountCount());
            assertEquals(first.fundCount(), second.fundCount());
            assertEquals(12L, second.accountCount());
            assertTrue(second.fundCount() >= 3L);
            assertTrue(second.budgetCategoryCount() >= 3L);
            assertTrue(second.activityCount() >= 2L);
            assertTrue(second.merchantCount() >= 2L);
            assertTrue(second.counterpartyCount() >= 3L);

            ChartOfAccounts chart = activeSampleChart(jpa);
            assertEquals(SampleCompanyService.SAMPLE_CHART_NAME, chart.getName());
            assertEquals(ChartStatus.ACTIVE, chart.getStatus());
            try (EntityManager em = jpa.em())
            {
                assertEquals(chart.getId(), em.createQuery("from Company c where c.code='DEFAULT'",
                        org.nonprofitbookkeeping.model.Company.class).getSingleResult().getActiveChartOfAccounts().getId());
            }

            TransactionLineEditorModel.ReferenceData references = new TransactionReferenceDataService(jpa).loadActiveReferenceData();
            assertFalse(references.accounts().isEmpty());
            assertFalse(references.funds().isEmpty());
            assertFalse(references.budgetCategories().isEmpty());
            assertFalse(references.activities().isEmpty());
            assertFalse(references.merchants().isEmpty());
            assertFalse(references.counterparties().isEmpty());
            assertTrue(references.accounts().stream().anyMatch(option -> option.code().equals("1000")));
            assertTrue(references.funds().stream().anyMatch(option -> option.code().equals("UNREST")));
        }
    }

    @Test
    void refreshPreservesExistingSelectionAndDoesNotChooseAmongMultipleOwnedCharts(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("sample-existing-chart")))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            var service = new SampleCompanyService(jpa);
            service.createOrRefresh();
            try (EntityManager em = jpa.em())
            {
                var company = em.createQuery("from Company c where c.code='DEFAULT'",
                        org.nonprofitbookkeeping.model.Company.class).getSingleResult();
                assertEquals(em.find(org.nonprofitbookkeeping.model.Account.class, fixture.bank()).getChart().getId(),
                        company.getActiveChartOfAccounts().getId());
                em.getTransaction().begin();
                company.setActiveChartOfAccounts(null);
                em.getTransaction().commit();
            }
            service.createOrRefresh();
            try (EntityManager em = jpa.em())
            {
                org.junit.jupiter.api.Assertions.assertNull(em.createQuery("from Company c where c.code='DEFAULT'",
                        org.nonprofitbookkeeping.model.Company.class).getSingleResult().getActiveChartOfAccounts());
            }
        }
    }

    private static ChartOfAccounts activeSampleChart(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "from ChartOfAccounts c where c.name = :name and c.status = :status",
                            ChartOfAccounts.class)
                    .setParameter("name", SampleCompanyService.SAMPLE_CHART_NAME)
                    .setParameter("status", ChartStatus.ACTIVE)
                    .getSingleResult();
        }
    }
}
