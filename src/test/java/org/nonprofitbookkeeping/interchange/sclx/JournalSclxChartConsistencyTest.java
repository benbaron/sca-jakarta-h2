package org.nonprofitbookkeeping.interchange.sclx;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.nonprofitbookkeeping.service.DatabaseSnapshotTestSupport.rows;

import static org.junit.jupiter.api.Assertions.*;

class JournalSclxChartConsistencyTest
{
    @TempDir Path directory;

    @Test
    void journalChoicesUseSelectedChartEvenWhenAnotherOwnedChartHasTheSameAccountCode()
    {
        try (Jpa jpa = new Jpa(directory.resolve("choices")))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            long outside = otherChartAccount(jpa);
            var choices = new TransactionReferenceDataService(jpa).loadActiveReferenceData().accounts();
            assertTrue(choices.stream().anyMatch(a -> a.id().equals(fixture.bank())));
            assertFalse(choices.stream().anyMatch(a -> a.id().equals(outside)),
                    "A new Journal draft must not offer another chart's account 1000");
            var saved = new TransactionEntryService(jpa).enterJournalDraft(command(fixture, fixture.bank()), UUID.randomUUID());
            var document = new SclxCoreSnapshotQueryService(jpa).query(Instant.parse("2026-11-01T00:00:00Z"));
            assertEquals(2, document.transactions().size());
            assertEquals(fixture.bank(), saved.lines().get(0).accountId());
        }
    }

    @Test
    void journalSaveRefreshesAChartAssignmentChangedAfterCompanyWasLoaded() throws Exception
    {
        Path source = directory.resolve("cached-company");
        try (Jpa jpa = new Jpa(source))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            long outside = otherChartAccount(jpa);
            Long companyId, chartId;
            try (var em = jpa.em())
            {
                companyId = em.createQuery("from Company c where c.code='DEFAULT'", Company.class).getSingleResult().getId();
                chartId = em.find(Account.class, outside).getChart().getId();
            }
            // Deterministically retain the company state read before a competing assignment commits.
            try (var cachedJpa = new Jpa(source)
            {
                private jakarta.persistence.EntityManager retained;

                void prime()
                {
                    retained = super.em();
                    retained.find(Company.class, companyId).getActiveChartOfAccounts().getId();
                }

                @Override public jakarta.persistence.EntityManager em()
                {
                    if (retained == null) { return super.em(); }
                    var result = retained;
                    retained = null;
                    return result;
                }

                @Override public void close()
                {
                    if (retained != null) { retained.close(); }
                    super.close();
                }
            })
            {
                cachedJpa.prime();
                new CompanyAdminService(jpa).assignActiveChart(companyId, chartId);
                var before = rows(source);
                assertThrows(IllegalArgumentException.class, () -> new TransactionEntryService(cachedJpa)
                        .enterJournalDraft(command(fixture, fixture.bank()), UUID.randomUUID()));
                assertEquals(before, rows(source));
            }
        }
    }

    @Test
    void staleAccountAndHeaderSelectionsRejectBeforeAnyWrite() throws Exception
    {
        Path source = directory.resolve("stale");
        try (Jpa jpa = new Jpa(source))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            long outside = otherChartAccount(jpa);
            var before = rows(source);
            var service = new TransactionEntryService(jpa);
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> service.enterJournalDraft(command(fixture, outside), UUID.randomUUID()));
            assertTrue(failure.getMessage().contains("selected Chart of Accounts"));
            assertEquals(before, rows(source));
            var valid = command(fixture, fixture.bank());
            var header = new TransactionCommand(valid.date(), null, "Stale header", outside, valid.lines());
            assertThrows(IllegalArgumentException.class, () -> service.enterJournalDraft(header, UUID.randomUUID()));
            assertEquals(before, rows(source));
        }
    }

    @Test
    void acknowledgedCreateReplaysAfterChartChangeButFreshDraftMustUseTheNewChart() throws Exception
    {
        Path source = directory.resolve("replay");
        try (Jpa jpa = new Jpa(source))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            long outside = otherChartAccount(jpa);
            var service = new TransactionEntryService(jpa);
            var request = UUID.randomUUID();
            var command = command(fixture, fixture.bank());
            var entered = service.enterJournalDraft(command, request);
            Long companyId, chartId;
            try (var em = jpa.em())
            {
                companyId = em.createQuery("from Company c where c.code='DEFAULT'", Company.class).getSingleResult().getId();
                chartId = em.find(Account.class, outside).getChart().getId();
            }
            new CompanyAdminService(jpa).assignActiveChart(companyId, chartId);
            var before = rows(source);
            assertEquals(entered, service.enterJournalDraft(command, request));
            assertEquals(before, rows(source));
            assertThrows(IllegalArgumentException.class, () -> service.enterJournalDraft(command, UUID.randomUUID()));
            assertEquals(before, rows(source));
            assertTrue(new TransactionReferenceDataService(jpa).loadActiveReferenceData().accounts().stream()
                    .allMatch(a -> a.id().equals(outside)));
        }
    }

    @Test
    void missingSelectedChartDoesNotOfferUnscopedAccountsOrWriteAJournalDraft() throws Exception
    {
        Path source = directory.resolve("missing-chart");
        try (Jpa jpa = new Jpa(source))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.createQuery("from Company c where c.code='DEFAULT'", Company.class).getSingleResult()
                        .setActiveChartOfAccounts(null);
                em.getTransaction().commit();
            }
            var before = rows(source);
            assertTrue(new TransactionReferenceDataService(jpa).loadActiveReferenceData().accounts().isEmpty());
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> new TransactionEntryService(jpa).enterJournalDraft(command(fixture, fixture.bank()), UUID.randomUUID()));
            assertTrue(failure.getMessage().contains("Company Admin"));
            assertEquals(before, rows(source));
        }
    }

    @Test
    void existingDifferentChartEntryGetsActionableErrorAndCanBeExplicitlyCorrectedWithoutMovingAccounts() throws Exception
    {
        Path source = directory.resolve("existing-entry");
        try (Jpa jpa = new Jpa(source))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            long outside = otherChartAccount(jpa);
            var service = new TransactionEntryService(jpa);
            // Reproduce what the prior Journal route accepted; historical/import APIs retain owned references.
            var existing = service.enter(command(fixture, outside));
            var export = new SclxFileExportService(new SclxCoreSnapshotQueryService(jpa), () -> source);
            Path destination = directory.resolve("company.sclx");
            var request = new SclxExportRequest(destination, Instant.parse("2026-11-01T00:00:00Z"), true);
            var before = rows(source);
            var failure = assertThrows(IllegalArgumentException.class, () -> export.export(request));
            assertTrue(failure.getMessage().contains("transaction #" + existing.id()));
            assertTrue(failure.getMessage().contains("Other owned chart"));
            assertTrue(failure.getMessage().contains("Transfer test chart"));
            assertTrue(failure.getMessage().contains("existing history was not changed"));
            assertFalse(Files.exists(destination));
            assertEquals(before, rows(source));
            Files.writeString(destination, "Retain prior export");
            assertThrows(IllegalArgumentException.class, () -> export.export(request));
            assertEquals("Retain prior export", Files.readString(destination));
            // Explicit ordinary Journal correction, not automatic code-based remapping.
            service.update(existing.id(), command(fixture, fixture.bank()));
            export.export(request);
            assertTrue(Files.readString(destination).contains("chartOfAccounts"));
            try (var em = jpa.em())
            {
                assertEquals("Other owned chart", em.find(Account.class, outside).getChart().getName());
            }
        }
    }

    private static TransactionCommand command(FundTransferTestFixture fixture, long bank)
    {
        return new TransactionCommand(FundTransferTestFixture.DATE, null, "Journal chart selection", null,
                List.of(FundTransferTestFixture.line(bank, fixture.source(), "10", "0"),
                        FundTransferTestFixture.line(fixture.income(), fixture.source(), "0", "10")));
    }

    @Test
    void exportRecognizesTheSamePersistedChartThroughProxyAndImplementationObjects()
    {
        try (Jpa jpa = new Jpa(directory.resolve("proxy")))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            try (var em = jpa.em())
            {
                var company = em.createQuery("from Company c where c.code='DEFAULT'", Company.class).getSingleResult();
                var proxy = company.getActiveChartOfAccounts();
                var bank = em.find(Account.class, fixture.bank());
                var income = em.find(Account.class, fixture.income());
                var fund = em.find(Fund.class, fixture.source());
                var implementation = (ChartOfAccounts) Hibernate.unproxy(proxy);
                company.setActiveChartOfAccounts(implementation);
                assertNotSame(bank.getChart(), implementation, "Exercise a real Hibernate proxy boundary");
                assertEquals(implementation.getId(), bank.getChart().getId());
                assertDoesNotThrow(() -> new SclxCoreSnapshotAssembler().assemble(company,
                        java.util.List.of(bank, income), java.util.List.of(fund), Instant.parse("2026-11-01T00:00:00Z")));
            }
        }
    }

    private static long otherChartAccount(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            var company = em.createQuery("from Company c where c.code='DEFAULT'", Company.class).getSingleResult();
            var chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName("Other owned chart");
            chart.setVersion("old");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            var account = new Account();
            account.setChart(chart);
            account.setCode("1000");
            account.setName("Other cash");
            account.setAccountType(AccountType.ASSET);
            account.setNormalBalance(NormalBalance.DEBIT);
            em.persist(account);
            em.getTransaction().commit();
            return account.getId();
        }
    }
}
