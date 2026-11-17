package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.FundTransferTestFixture;
import org.nonprofitbookkeeping.service.TransactionCommand;
import org.nonprofitbookkeeping.service.TransactionEntryService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.DatabaseSnapshotTestSupport.rows;

class SclxAllChartsTest
{
    @TempDir Path directory;
    private static final Instant WHEN = Instant.parse("2026-11-01T00:00:00Z");

    @Test
    void allChartsRoundTripWithDuplicateCodesInactiveHistoryBankLinkAndEmptyCharts() throws Exception
    {
        Path sourceDb = directory.resolve("source"), targetDb = directory.resolve("target");
        Path file = directory.resolve("all.sclx");
        try (Jpa source = new Jpa(sourceDb); Jpa target = new Jpa(targetDb))
        {
            seed(source);
            var before = rows(sourceDb);
            var document = new SclxCoreSnapshotQueryService(source).query(WHEN);
            Files.write(file, new SclxJsonSerializer().serialize(document));
            assertEquals(before, rows(sourceDb), "Export must not write source data");
            var data = SclxCharts.parse(new ObjectMapper().readTree(file.toFile()));
            assertEquals(3, data.charts().size());
            assertEquals(2, document.chartOfAccounts().stream().filter(a -> a.code().equals("1000")).count());
            assertEquals(document.chartOfAccounts().size(), document.chartOfAccounts().stream().map(SclxExportDocument.Account::accountId).distinct().count());
            var preview = new SclxImportPreviewService(target, () -> "DEFAULT").preview(file);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            var result = new SclxImportCommitService(target, () -> "DEFAULT").commit(file, preview, "tester");
            assertTrue(result.committed(), result.toString());
            assertGraph(target);
            var exported = new SclxCoreSnapshotQueryService(target).query(WHEN);
            assertEquals(document.chartOfAccounts(), exported.chartOfAccounts());
            assertEquals(document.transactions(), exported.transactions());
            assertEquals(document.extensions().scaJakartaH2().get(SclxCharts.KEY), exported.extensions().scaJakartaH2().get(SclxCharts.KEY));
            var targetBefore = rows(targetDb);
            var retry = new SclxImportCommitService(target, () -> "DEFAULT").commit(file,
                    new SclxImportPreviewService(target, () -> "DEFAULT").preview(file), "tester");
            assertTrue(retry.committed(), retry.toString());
            assertEquals(targetBefore, rows(targetDb));
        }
        try (Jpa reopened = new Jpa(targetDb)) { assertGraph(reopened); }
    }

    @Test
    void malformedMembershipAndSelectedChartGraphBlockPreviewWithoutWrites() throws Exception
    {
        try (Jpa source = new Jpa(directory.resolve("bad-source")); Jpa target = new Jpa(directory.resolve("bad-target")))
        {
            seed(source);
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode original = (ObjectNode) mapper.readTree(new SclxJsonSerializer().serialize(new SclxCoreSnapshotQueryService(source).query(WHEN)));
            Path file = directory.resolve("bad.sclx");
            for (String fault : List.of("missing", "dangling", "duplicate", "version", "null", "status", "selected", "extra", "stripped", "code", "crossParent", "duplicateChart"))
            {
                ObjectNode changed = original.deepCopy();
                ObjectNode graph = (ObjectNode) changed.path("extensions").path("scaJakartaH2").path("charts");
                var memberships = (com.fasterxml.jackson.databind.node.ArrayNode) graph.path("accounts");
                var charts = (com.fasterxml.jackson.databind.node.ArrayNode) graph.path("charts");
                switch (fault)
                {
                    case "missing" -> memberships.remove(0);
                    case "dangling" -> ((ObjectNode) memberships.get(0)).put("chartId", "missing");
                    case "duplicate" -> memberships.add(memberships.get(0).deepCopy());
                    case "version" -> graph.put("version", 2);
                    case "null" -> ((ObjectNode) changed.path("extensions").path("scaJakartaH2")).putNull("charts");
                    case "status" -> ((ObjectNode) charts.get(0)).put("status", "UNKNOWN");
                    case "selected" -> charts.forEach(c -> ((ObjectNode)c).put("selected", true));
                    case "extra" -> graph.put("unexpected", true);
                    case "stripped" -> ((ObjectNode) changed.path("extensions").path("scaJakartaH2")).remove("charts");
                    case "code" -> ((ObjectNode) changed.path("chartOfAccounts").get(0)).put("code", "CHANGED");
                    case "duplicateChart" -> charts.add(charts.get(0).deepCopy());
                    case "crossParent" ->
                    {
                        String first = memberships.get(0).path("chartId").asText();
                        String child = memberships.get(0).path("accountId").asText();
                        String parent = java.util.stream.StreamSupport.stream(memberships.spliterator(), false)
                                .filter(m -> !first.equals(m.path("chartId").asText())).findFirst().orElseThrow().path("accountId").asText();
                        changed.path("chartOfAccounts").forEach(a -> {
                            if (a.path("accountId").asText().equals(child)) ((ObjectNode)a).put("parentAccountId", parent);
                        });
                    }
                }
                mapper.writeValue(file.toFile(), changed);
                var before = rows(directory.resolve("bad-target"));
                assertTrue(new SclxImportPreviewService(target, () -> "DEFAULT").preview(file).hasBlockingErrors(), fault);
                assertEquals(before, rows(directory.resolve("bad-target")), fault);
            }
        }
    }

    @Test
    void nullSelectionAndUnusedChartExportWithoutRequiringAnActiveChart() throws Exception
    {
        try (Jpa source = new Jpa(directory.resolve("no-selection")))
        {
            try (var em = source.em())
            {
                em.getTransaction().begin();
                var company = em.createQuery("from Company c where c.code='DEFAULT'", Company.class).getSingleResult();
                chart(em, company, "Unused draft", ChartStatus.DRAFT);
                em.getTransaction().commit();
            }
            var document = new SclxCoreSnapshotQueryService(source).query(WHEN);
            var data = SclxCharts.parse(new ObjectMapper().readTree(new SclxJsonSerializer().serialize(document)));
            assertEquals(1, data.charts().size());
            assertFalse(data.charts().get(0).selected());
            assertTrue(document.chartOfAccounts().isEmpty());
        }
    }

    @Test
    void differentTargetCompanyCodeKeepsChartIdentityAndExactRetry() throws Exception
    {
        try (Jpa source = new Jpa(directory.resolve("source-code")); Jpa target = new Jpa(directory.resolve("target-code")))
        {
            seed(source);
            try (var em = target.em())
            {
                em.getTransaction().begin();
                var company = new Company(); company.setCode("OTHER"); company.setDisplayName("Other");
                company.setDefaultCurrency("USD"); em.persist(company); em.getTransaction().commit();
            }
            Path file = export(source, "different-company.sclx");
            var service = new SclxImportPreviewService(target, () -> "OTHER");
            var commit = new SclxImportCommitService(target, () -> "OTHER");
            var preview = service.preview(file);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            assertTrue(commit.commit(file, preview, "tester").committed());
            var before = rows(directory.resolve("target-code"));
            preview = service.preview(file);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            assertTrue(commit.commit(file, preview, "tester").committed());
            assertEquals(before, rows(directory.resolve("target-code")));
        }
    }

    @Test
    void chartUuidOwnedByAnotherCompanyBlocksPreviewAndCommit() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("foreign-chart")))
        {
            seed(jpa);
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                var company = new Company(); company.setCode("OTHER"); company.setDisplayName("Other");
                company.setDefaultCurrency("USD"); em.persist(company); em.getTransaction().commit();
            }
            Path file = export(jpa, "foreign.sclx");
            var before = rows(directory.resolve("foreign-chart"));
            var preview = new SclxImportPreviewService(jpa, () -> "OTHER").preview(file);
            assertTrue(preview.hasBlockingErrors());
            assertTrue(preview.operation().messages().stream().anyMatch(m -> m.code().equals("SCLX_NATIVE_PORTABLE_ID_OTHER_COMPANY")));
            assertThrows(IllegalStateException.class, () -> new SclxImportCommitService(jpa, () -> "OTHER").commit(file, preview, "tester"));
            assertEquals(before, rows(directory.resolve("foreign-chart")));
        }
    }

    @Test
    void lateImportFailureRollsBackNewChartsAccountsAndIdentities() throws Exception
    {
        try (Jpa source = new Jpa(directory.resolve("rollback-source")); Jpa target = new Jpa(directory.resolve("rollback-target")))
        {
            seed(source);
            Path file = export(source, "rollback.sclx");
            var before = rows(directory.resolve("rollback-target"));
            var preview = new SclxImportPreviewService(target, () -> "DEFAULT").preview(file);
            var result = new SclxImportCommitService(target, () -> "DEFAULT", writes -> {
                if (writes >= 8) throw new IllegalStateException("Injected late failure");
            }).commit(file, preview, "tester");
            assertFalse(result.committed());
            assertEquals(before, rows(directory.resolve("rollback-target")));
        }
    }

    @Test
    void chartMetadataAndTargetAccountEditsRequireExplicitConflictResolution() throws Exception
    {
        try (Jpa source = new Jpa(directory.resolve("conflict-source")); Jpa target = new Jpa(directory.resolve("conflict-target")))
        {
            seed(source);
            Path file = export(source, "conflict.sclx");
            var service = new SclxImportPreviewService(target, () -> "DEFAULT");
            assertTrue(new SclxImportCommitService(target, () -> "DEFAULT").commit(file, service.preview(file), "tester").committed());
            try (var em = target.em())
            {
                em.getTransaction().begin();
                em.createQuery("from ChartOfAccounts c where c.name='Retired chart'", ChartOfAccounts.class).getSingleResult().setName("Retained target name");
                em.createQuery("from Account a where a.name='Historical checking'", Account.class).getSingleResult().setName("Retained account name");
                em.getTransaction().commit();
            }
            var preview = service.preview(file);
            assertTrue(preview.hasBlockingErrors());
            assertTrue(preview.operation().items().stream().anyMatch(i -> i.entityType().equals("CHART")
                    && i.identityMatch() == org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch.CONFLICT));
            assertTrue(preview.operation().items().stream().anyMatch(i -> i.entityType().equals("ACCOUNT")
                    && i.identityMatch() == org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch.CONFLICT));
            var choices = preview.operation().items().stream()
                    .filter(i -> i.identityMatch() == org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch.CONFLICT)
                    .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
            preview = service.preview(file, List.of(), choices);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            assertTrue(new SclxImportCommitService(target, () -> "DEFAULT").commit(file, preview, "tester").committed());
            try (var em = target.em())
            {
                assertEquals(1L, em.createQuery("select count(c) from ChartOfAccounts c where c.name='Retained target name'", Long.class).getSingleResult());
                assertEquals(1L, em.createQuery("select count(a) from Account a where a.name='Retained account name'", Long.class).getSingleResult());
            }
        }
    }

    private Path export(Jpa jpa, String name) throws Exception
    {
        Path file = directory.resolve(name);
        Files.write(file, new SclxJsonSerializer().serialize(new SclxCoreSnapshotQueryService(jpa).query(Instant.parse("2026-11-01T00:00:00Z"))));
        return file;
    }

    private static void seed(Jpa jpa)
    {
        var fixture = FundTransferTestFixture.seed(jpa);
        long oldBank;
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = em.find(Account.class, fixture.bank()).getChart().getCompany();
            ChartOfAccounts old = chart(em, company, "Retired chart", ChartStatus.RETIRED);
            chart(em, company, "Unused draft", ChartStatus.DRAFT);
            Account account = new Account();
            account.setChart(old); account.setCode("1000"); account.setName("Historical checking");
            account.setAccountType(AccountType.ASSET); account.setAccountFunction(AccountFunction.BANK);
            account.setNormalBalance(NormalBalance.DEBIT); account.setPosting(true); account.setActive(true);
            em.persist(account);
            var configured = new CompanyBankAccount();
            configured.setCompany(company); configured.setAccount(account); configured.setName("Historical checking");
            em.persist(configured);
            em.flush(); oldBank = account.getId();
            em.getTransaction().commit();
        }
        new TransactionEntryService(jpa).enter(new TransactionCommand(FundTransferTestFixture.OPENING, null,
                "History on retired chart", null, List.of(FundTransferTestFixture.line(oldBank, fixture.source(), "10", "0"),
                FundTransferTestFixture.line(fixture.income(), fixture.source(), "0", "10"))));
        try (var em = jpa.em())
        {
            em.getTransaction().begin(); em.find(Account.class, oldBank).setActive(false); em.getTransaction().commit();
        }
    }

    private static ChartOfAccounts chart(jakarta.persistence.EntityManager em, Company company, String name, ChartStatus status)
    {
        var chart = new ChartOfAccounts(); chart.setCompany(company); chart.setName(name); chart.setVersion("1"); chart.setStatus(status);
        em.persist(chart); return chart;
    }

    private static void assertGraph(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            assertEquals(3L, em.createQuery("select count(c) from ChartOfAccounts c", Long.class).getSingleResult());
            var company = em.createQuery("from Company c where c.code='DEFAULT'", Company.class).getSingleResult();
            assertEquals("Transfer test chart", company.getActiveChartOfAccounts().getName());
            var configured = em.createQuery("from CompanyBankAccount b where b.name='Historical checking'", CompanyBankAccount.class).getSingleResult();
            assertEquals("Retired chart", configured.getAccount().getChart().getName());
            assertFalse(configured.getAccount().isActive());
            assertEquals(ChartStatus.RETIRED, configured.getAccount().getChart().getStatus());
            assertEquals(1L, em.createQuery("select count(s) from TxnSplit s where s.account=:account", Long.class)
                    .setParameter("account", configured.getAccount()).getSingleResult());
        }
    }
}
