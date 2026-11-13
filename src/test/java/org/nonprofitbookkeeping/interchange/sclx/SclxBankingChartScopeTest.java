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
    void differentChartBankLinkIdentifiesBankingContextAndPreservesDatabaseAndDestination() throws Exception
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
            var failure = assertThrows(IllegalArgumentException.class, () -> export.export(
                    new SclxExportRequest(destination, Instant.parse("2026-11-01T00:00:00Z"), true)));
            assertTrue(failure.getMessage().contains("Historical checking"));
            assertTrue(failure.getMessage().contains("Record: Configured bank account #" + configuredId));
            assertTrue(failure.getMessage().contains("\nLedger account: 1000"));
            assertTrue(failure.getMessage().contains("\nReferenced chart:"));
            assertTrue(failure.getMessage().contains("\nSelected chart:"));
            assertTrue(failure.getMessage().contains("Legacy bank chart"));
            assertTrue(failure.getMessage().contains("Transfer test chart"));
            assertTrue(failure.getMessage().contains("Banking / Bank accounts"), failure.getMessage());
            assertEquals(before, rows(source));
            assertEquals("Existing export remains intact", Files.readString(destination));
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
