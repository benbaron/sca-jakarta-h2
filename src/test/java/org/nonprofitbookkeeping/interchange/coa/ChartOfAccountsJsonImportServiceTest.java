package org.nonprofitbookkeeping.interchange.coa;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartOfAccountsJsonImportServiceTest
{
    @Test
    void createNewChartCommitsParentBeforeChildWithoutActivation(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("create-new");
        try (Jpa jpa = new Jpa(database))
        {
            seedCompanyAndActiveChart(jpa);
            Path source = writeSource(tempDir.resolve("create.json"));
            ChartOfAccountsJsonService previewService = new ChartOfAccountsJsonService(jpa, () -> "DEFAULT");
            CoaImportRequest request = new CoaImportRequest(
                    source,
                    CoaImportMode.CREATE_NEW_CHART,
                    "Imported Chart",
                    "2027",
                    Map.of(),
                    true);
            CoaImportPreview preview = previewService.preview(request);

            CoaImportResult result = new ChartOfAccountsJsonImportService(jpa, () -> "DEFAULT").commit(preview);

            assertTrue(result.committed());
            assertFalse(result.rolledBack());
            try (EntityManager em = jpa.em())
            {
                Company company = em.createQuery(
                        "from Company c where c.code = 'DEFAULT'",
                        Company.class).getSingleResult();
                ChartOfAccounts imported = em.createQuery(
                        "from ChartOfAccounts c where c.company = :company and c.name = 'Imported Chart'",
                        ChartOfAccounts.class)
                        .setParameter("company", company)
                        .getSingleResult();
                assertEquals(ChartStatus.DRAFT, imported.getStatus());
                assertEquals("Default Chart", company.getActiveChartOfAccounts().getName());
                Account child = em.createQuery(
                        "select a from Account a left join fetch a.parent where a.chart = :chart and a.code = '1010'",
                        Account.class)
                        .setParameter("chart", imported)
                        .getSingleResult();
                assertEquals("1000", child.getParent().getCode());
                assertEquals(2L, em.createQuery(
                        "select count(a) from Account a where a.chart = :chart",
                        Long.class)
                        .setParameter("chart", imported)
                        .getSingleResult());
            }
        }
    }

    @Test
    void injectedLateFailureRollsBackWholeNewChart(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("rollback");
        try (Jpa jpa = new Jpa(database))
        {
            seedCompanyAndActiveChart(jpa);
            Path source = writeSource(tempDir.resolve("rollback.json"));
            ChartOfAccountsJsonService previewService = new ChartOfAccountsJsonService(jpa, () -> "DEFAULT");
            CoaImportPreview preview = previewService.preview(new CoaImportRequest(
                    source,
                    CoaImportMode.CREATE_NEW_CHART,
                    "Rollback Chart",
                    "2027",
                    Map.of(),
                    true));
            ChartOfAccountsJsonImportService service = new ChartOfAccountsJsonImportService(
                    jpa,
                    () -> "DEFAULT",
                    count -> {
                        if (count == 2)
                        {
                            throw new IllegalStateException("injected late failure");
                        }
                    });

            CoaImportResult result = service.commit(preview);

            assertFalse(result.committed());
            assertTrue(result.rolledBack());
            assertTrue(result.messages().stream()
                    .anyMatch(message -> message.code().equals("COA_COMMIT_ROLLED_BACK")));
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, em.createQuery(
                        "select count(c) from ChartOfAccounts c where c.name = 'Rollback Chart'",
                        Long.class).getSingleResult());
                assertEquals(0L, em.createQuery(
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'COA_JSON'",
                        Long.class).getSingleResult());
            }
        }
    }

    @Test
    void mergeIsIdempotentAndRetainsAbsentLocalAccount(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("merge");
        try (Jpa jpa = new Jpa(database))
        {
            seedCompanyAndActiveChart(jpa);
            seedLocalOnlyAccount(jpa);
            Path source = writeSource(tempDir.resolve("merge.json"));
            CoaImportRequest request = new CoaImportRequest(
                    source,
                    CoaImportMode.MERGE_BY_CODE,
                    "",
                    "",
                    Map.of(),
                    true);
            ChartOfAccountsJsonService previewService = new ChartOfAccountsJsonService(jpa, () -> "DEFAULT");
            ChartOfAccountsJsonImportService importService = new ChartOfAccountsJsonImportService(jpa, () -> "DEFAULT");

            CoaImportResult first = importService.commit(previewService.preview(request));
            CoaImportPreview secondPreview = previewService.preview(request);
            CoaImportResult second = importService.commit(secondPreview);

            assertTrue(first.committed());
            assertTrue(second.committed());
            assertEquals(2L, second.counts().identical());
            assertEquals(0L, second.counts().created());
            assertEquals(0L, second.counts().updated());
            try (EntityManager em = jpa.em())
            {
                assertEquals(1L, em.createQuery(
                        "select count(a) from Account a where a.code = '9999' and a.active = true",
                        Long.class).getSingleResult());
                assertEquals(3L, em.createQuery("select count(a) from Account a", Long.class).getSingleResult());
            }
        }
    }

    private static Path writeSource(Path target) throws Exception
    {
        Files.writeString(target, """
                {
                  "format" : "SCA-COA",
                  "version" : "1.0",
                  "chart" : {
                    "name" : "Portable Chart",
                    "chartVersion" : "2027",
                    "status" : "DRAFT",
                    "currency" : "USD"
                  },
                  "accounts" : [ {
                    "code" : "1000",
                    "name" : "Assets",
                    "type" : "ASSET",
                    "normalBalance" : "DEBIT",
                    "posting" : false,
                    "active" : true,
                    "openingBalance" : "0.00"
                  }, {
                    "code" : "1010",
                    "name" : "Checking",
                    "type" : "BANK",
                    "subtype" : "CASH",
                    "normalBalance" : "DEBIT",
                    "parentCode" : "1000",
                    "posting" : true,
                    "active" : true,
                    "openingBalance" : "25.00"
                  } ]
                }
                """);
        return target;
    }

    private static void seedCompanyAndActiveChart(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode("DEFAULT");
            company.setDisplayName("Default Company");
            company.setDefaultCurrency("USD");
            em.persist(company);

            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName("Default Chart");
            chart.setVersion("2026");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            company.setActiveChartOfAccounts(chart);
            em.getTransaction().commit();
        }
    }

    private static void seedLocalOnlyAccount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            ChartOfAccounts chart = em.createQuery(
                    "from ChartOfAccounts c where c.name = 'Default Chart'",
                    ChartOfAccounts.class).getSingleResult();
            Account local = new Account();
            local.setChart(chart);
            local.setCode("9999");
            local.setName("Local Only");
            local.setAccountType(AccountType.EXPENSE);
            local.setNormalBalance(NormalBalance.DEBIT);
            local.setPosting(true);
            local.setActive(true);
            em.persist(local);
            em.getTransaction().commit();
        }
    }
}
