package org.nonprofitbookkeeping.interchange.coa;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartOfAccountsJsonServiceTest
{
    private static final String TEST_COMPANY_CODE = "COA_JSON_TEST";

    @Test
    void donorFixturePreviewsOnceAndMapsCheckingToBankCash(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("donor-preview");
        try (Jpa jpa = new Jpa(database))
        {
            seedCompanyAndChart(jpa);
            Path fixture = Path.of("src/test/resources/data-exchange/coa-json/valid/donor-generated.json");
            ChartOfAccountsJsonService service = new ChartOfAccountsJsonService(jpa, () -> TEST_COMPANY_CODE);

            CoaImportPreview preview = service.preview(new CoaImportRequest(
                    fixture,
                    CoaImportMode.CREATE_NEW_CHART,
                    "Imported donor chart",
                    "2026",
                    Map.of(),
                    true));

            assertFalse(preview.hasBlockingErrors());
            assertTrue(preview.confirmationsSatisfied());
            assertEquals(3L, preview.counts().total());
            assertEquals(3L, preview.counts().created());
            assertEquals(3, preview.items().size());
            CoaAccountData checking = preview.items().stream()
                    .map(CoaPreviewItem::account)
                    .filter(account -> account.sourceCode().equals("1010"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(AccountType.BANK, checking.type());
            assertEquals(AccountSubtype.CASH, checking.subtype());
            assertEquals("1000", checking.parentCode());
            CoaAccountData root = preview.items().stream()
                    .map(CoaPreviewItem::account)
                    .filter(account -> account.sourceCode().equals("1000"))
                    .findFirst()
                    .orElseThrow();
            assertFalse(root.posting());
            assertTrue(preview.messages().stream()
                    .anyMatch(message -> message.code().equals("COA_DONOR_REDUNDANT_SHAPE")));
        }
    }

    @Test
    void exportIsDeterministicAndParentBeforeChild(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("export-source");
        try (Jpa jpa = new Jpa(database))
        {
            seedCompanyAndChart(jpa);
            seedAccounts(jpa);
            ChartOfAccountsJsonService service = new ChartOfAccountsJsonService(jpa, () -> TEST_COMPANY_CODE);
            Path first = tempDir.resolve("first.json");
            Path second = tempDir.resolve("second.json");

            CoaExportResult firstResult = service.exportActiveChart(first);
            CoaExportResult secondResult = service.exportActiveChart(second);

            assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
            assertEquals(firstResult.sha256(), secondResult.sha256());
            assertEquals(2L, firstResult.accountCount());
            String json = Files.readString(first);
            assertTrue(json.indexOf("\"code\" : \"1000\"") < json.indexOf("\"code\" : \"1010\""));
            assertTrue(json.contains("\"format\" : \"SCA-COA\""));
            assertTrue(json.endsWith("\n"));
        }
    }

    @Test
    void previewRejectsDuplicateJsonKeys(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("duplicate-key");
        try (Jpa jpa = new Jpa(database))
        {
            seedCompanyAndChart(jpa);
            Path source = tempDir.resolve("duplicate.json");
            Files.writeString(source, """
                    {
                      "format": "SCA-COA",
                      "format": "SCA-COA",
                      "version": "1.0",
                      "chart": {"name":"Test","chartVersion":"1","status":"DRAFT","currency":"USD"},
                      "accounts": []
                    }
                    """);
            ChartOfAccountsJsonService service = new ChartOfAccountsJsonService(jpa, () -> TEST_COMPANY_CODE);

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.preview(new CoaImportRequest(
                            source,
                            CoaImportMode.CREATE_NEW_CHART,
                            "Target",
                            "1",
                            Map.of(),
                            false)));
            assertTrue(error.getMessage().contains("Malformed Chart of Accounts JSON"));
        }
    }

    @Test
    void previewBlocksHierarchyCycle(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("cycle");
        try (Jpa jpa = new Jpa(database))
        {
            seedCompanyAndChart(jpa);
            Path source = tempDir.resolve("cycle.json");
            Files.writeString(source, """
                    {
                      "format": "SCA-COA",
                      "version": "1.0",
                      "chart": {"name":"Test","chartVersion":"1","status":"DRAFT","currency":"USD"},
                      "accounts": [
                        {"code":"1000","name":"One","type":"ASSET","normalBalance":"DEBIT","parentCode":"1100","posting":false,"active":true,"openingBalance":"0.00"},
                        {"code":"1100","name":"Two","type":"ASSET","normalBalance":"DEBIT","parentCode":"1000","posting":false,"active":true,"openingBalance":"0.00"}
                      ]
                    }
                    """);
            ChartOfAccountsJsonService service = new ChartOfAccountsJsonService(jpa, () -> TEST_COMPANY_CODE);

            CoaImportPreview preview = service.preview(new CoaImportRequest(
                    source,
                    CoaImportMode.CREATE_NEW_CHART,
                    "Target",
                    "1",
                    Map.of(),
                    false));

            assertTrue(preview.hasBlockingErrors());
            assertTrue(preview.messages().stream()
                    .anyMatch(message -> message.code().equals("COA_HIERARCHY_CYCLE")));
        }
    }

    private static void seedCompanyAndChart(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode(TEST_COMPANY_CODE);
            company.setDisplayName("COA JSON Test Company");
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

    private static void seedAccounts(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            ChartOfAccounts chart = em.createQuery(
                    "from ChartOfAccounts c where c.name = 'Default Chart'",
                    ChartOfAccounts.class)
                    .getSingleResult();
            Account root = new Account();
            root.setChart(chart);
            root.setCode("1000");
            root.setName("Assets");
            root.setAccountType(AccountType.ASSET);
            root.setNormalBalance(NormalBalance.DEBIT);
            root.setPosting(false);
            root.setOpeningBalance(BigDecimal.ZERO);
            em.persist(root);

            Account child = new Account();
            child.setChart(chart);
            child.setCode("1010");
            child.setName("Checking");
            child.setAccountType(AccountType.BANK);
            child.setSubtype(AccountSubtype.CASH);
            child.setNormalBalance(NormalBalance.DEBIT);
            child.setParent(root);
            child.setPosting(true);
            child.setOpeningBalance(new BigDecimal("125.50"));
            em.persist(child);
            em.getTransaction().commit();
        }
    }
}
