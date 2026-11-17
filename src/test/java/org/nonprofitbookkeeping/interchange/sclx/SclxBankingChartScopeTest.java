package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.FundTransferTestFixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.DatabaseSnapshotTestSupport.rows;

class SclxBankingChartScopeTest
{
    @TempDir Path directory;

    @Test
    void differentOwnedChartBankLinkExportsWithDistinctIdentityAndPreservesDatabase() throws Exception
    {
        Path source = directory.resolve("bank-chart");
        try (Jpa jpa = new Jpa(source))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            long configuredId = configure(jpa, fixture.bank(), true);
            var before = rows(source);
            Path destination = directory.resolve("prior.sclx");
            Files.writeString(destination, "Existing export remains intact");
            var export = new SclxFileExportService(new SclxCoreSnapshotQueryService(jpa), () -> source);
            new SclxFileExportService(new SclxCoreSnapshotQueryService(jpa), () -> source)
                    .export(new SclxExportRequest(destination, Instant.parse("2026-11-01T00:00:00Z"), true));
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(destination.toFile());
            var data = SclxCharts.parse(root);
            assertEquals(2, data.charts().size());
            assertTrue(Files.readString(destination).contains("Legacy bank chart"));
            assertEquals(2L, java.util.stream.StreamSupport.stream(root.path("chartOfAccounts").spliterator(), false)
                    .filter(account -> account.path("code").asText().equals("1000")).count());
            assertEquals(before, rows(source));
        }
    }

    @Test
    void inactiveLedgerAccountWithinSelectedChartStillExportsWithoutWrites() throws Exception
    {
        Path source = directory.resolve("inactive-selected");
        try (Jpa jpa = new Jpa(source))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            configure(jpa, fixture.bank(), false);
            var before = rows(source);
            var document = new SclxCoreSnapshotQueryService(jpa).query(Instant.parse("2026-11-01T00:00:00Z"));
            assertFalse(document.chartOfAccounts().stream().filter(account -> "1000".equals(account.code()))
                    .findFirst().orElseThrow().active());
            assertEquals("Historical checking", SclxBankConfigurationExtension.data(document.extensions())
                    .accounts().get(0).name());
            assertEquals(before, rows(source));
        }
    }

    private static long configure(Jpa jpa, long originalAccountId, boolean otherChart)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Account ledger = em.find(Account.class, originalAccountId);
            Company company = ledger.getChart().getCompany();
            if (otherChart)
            {
                ChartOfAccounts chart = new ChartOfAccounts();
                chart.setCompany(company);
                chart.setName("Legacy bank chart");
                chart.setVersion("1");
                chart.setStatus(ChartStatus.ACTIVE);
                em.persist(chart);
                ledger = new Account();
                ledger.setChart(chart);
                ledger.setCode("1000");
                ledger.setName("Legacy cash");
                ledger.setAccountType(AccountType.ASSET);
                ledger.setAccountFunction(AccountFunction.BANK);
                ledger.setNormalBalance(NormalBalance.DEBIT);
                em.persist(ledger);
            }
            ledger.setActive(false);
            CompanyBankAccount configured = new CompanyBankAccount();
            configured.setCompany(company);
            configured.setAccount(ledger);
            configured.setName("Historical checking");
            em.persist(configured);
            em.getTransaction().commit();
            return configured.getId();
        }
    }
}
