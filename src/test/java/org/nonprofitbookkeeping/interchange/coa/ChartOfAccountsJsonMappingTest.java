package org.nonprofitbookkeeping.interchange.coa;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartOfAccountsJsonMappingTest
{
    private static final String TEST_COMPANY_CODE = "COA_MAP_TEST";

    @Test
    void mapCodesPersistsMappedHierarchyAndBlocksIncompleteOrCollidingMaps(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("mapped");
        try (Jpa jpa = new Jpa(database))
        {
            seedCompanyAndChart(jpa);
            Path source = writeSource(tempDir.resolve("mapped.json"));
            ChartOfAccountsJsonService previews = new ChartOfAccountsJsonService(jpa, () -> TEST_COMPANY_CODE);

            CoaImportPreview incomplete = previews.preview(new CoaImportRequest(
                    source,
                    CoaImportMode.MAP_CODES,
                    "",
                    "",
                    Map.of("1000", "A100"),
                    false));
            assertTrue(incomplete.hasBlockingErrors());
            assertTrue(incomplete.messages().stream()
                    .anyMatch(message -> message.code().equals("COA_UNMAPPED_CODE")));

            CoaImportPreview collision = previews.preview(new CoaImportRequest(
                    source,
                    CoaImportMode.MAP_CODES,
                    "",
                    "",
                    Map.of("1000", "A100", "1010", "A100"),
                    false));
            assertTrue(collision.hasBlockingErrors());
            assertTrue(collision.messages().stream()
                    .anyMatch(message -> message.code().equals("COA_DUPLICATE_TARGET_CODE")));

            CoaImportRequest mappedRequest = new CoaImportRequest(
                    source,
                    CoaImportMode.MAP_CODES,
                    "",
                    "",
                    Map.of("1000", "A100", "1010", "A110"),
                    true);
            CoaImportPreview mapped = previews.preview(mappedRequest);
            assertFalse(mapped.hasBlockingErrors());
            CoaImportResult result = new ChartOfAccountsJsonImportService(jpa, () -> TEST_COMPANY_CODE).commit(mapped);
            assertTrue(result.committed());

            try (EntityManager em = jpa.em())
            {
                Account child = em.createQuery(
                        "select a from Account a left join fetch a.parent where a.code = 'A110'",
                        Account.class).getSingleResult();
                assertEquals("A100", child.getParent().getCode());
                assertEquals(2L, em.createQuery(
                        "select count(a) from Account a where a.code in ('A100', 'A110')",
                        Long.class).getSingleResult());
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
                    "name" : "Mapped Source",
                    "chartVersion" : "1",
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
                    "openingBalance" : "0.00"
                  } ]
                }
                """);
        return target;
    }

    private static void seedCompanyAndChart(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode(TEST_COMPANY_CODE);
            company.setDisplayName("COA Mapping Test Company");
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
}
