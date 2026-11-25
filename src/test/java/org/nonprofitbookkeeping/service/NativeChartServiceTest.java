package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NativeChartServiceTest
{
    @TempDir Path directory;

    @Test

    void createsAnEmptyChartAndMaintainsItsCompleteDefinitionsWithoutActivatingOrUsingJson()
    {
        try (Jpa jpa = database())
        {
            var service = service(jpa, "DEFAULT");
            var chart = service.save(new ChartCommand(null, "Native chart", "1", false, null));
            assertEquals(ChartStatus.DRAFT, chart.status());
            assertFalse(chart.currentForCompany());
            var accounts = new AccountAdminService(jpa);
            var group = accounts.save(account(chart.id(), null, "1000", null, false, "0.00"));
            var leaf = accounts.save(account(chart.id(), null, "1010", "1000", true, "12.34"));
            assertEquals(chart.id(), leaf.getChart().getId());
            assertEquals(group.getId(), leaf.getParent().getId());
            assertEquals(new BigDecimal("12.34"), leaf.getOpeningBalance());
            assertEquals(LocalDate.of(2026, 1, 1), leaf.getEffectiveFrom());
            assertEquals("Definition notes", leaf.getDescription());
            assertEquals(2, new AccountLookupService(jpa).listAllCompanyAccountsForMaintenance().size());
            assertEquals(1, new AccountLookupService(jpa).listPostingAccountsIncludingInactive().size());
            assertEquals(0, count(jpa, "Txn"));
            assertEquals(3, count(jpa, "AuditEvent"));
            try (var em = jpa.em())
            {
                assertNull(em.createQuery("from Company where code='DEFAULT'", Company.class).getSingleResult().getActiveChartOfAccounts());
            }
        }
    }

    @Test

    void editsMetadataRetainsIdentityAndRequiresAnotherCurrentChartBeforeRetirement()
    {
        try (Jpa jpa = database())
        {
            var service = service(jpa, "DEFAULT");
            var original = service.save(new ChartCommand(null, "Original", "1", false, null));
            new CompanyAdminService(jpa).assignActiveChart(company(jpa, "DEFAULT"), original.id());
            var active = service.load(original.id());
            assertThrows(IllegalStateException.class, () -> service.save(new ChartCommand(active.id(), "Original", "1", true, active.revision())));
            var renamed = service.save(new ChartCommand(active.id(), "Renamed", "2", false, active.revision()));
            assertEquals(original.id(), renamed.id());
            assertTrue(renamed.currentForCompany());
            var second = service.save(new ChartCommand(null, "Second", "1", false, null));
            new CompanyAdminService(jpa).assignActiveChart(company(jpa, "DEFAULT"), second.id());
            var old = service.load(original.id());
            var retired = service.save(new ChartCommand(old.id(), old.name(), old.version(), true, old.revision()));
            assertEquals(ChartStatus.RETIRED, retired.status());
            assertThrows(IllegalStateException.class, () -> new AccountAdminService(jpa).save(account(old.id(), null, "1000", null, true, "0.00")));
            var restored = service.save(new ChartCommand(old.id(), old.name(), old.version(), false, retired.revision()));
            assertEquals(ChartStatus.DRAFT, restored.status());
            assertFalse(restored.currentForCompany());
            assertTrue(service.load(second.id()).currentForCompany());
        }
    }

    @Test

    void crossCompanyCloneCopiesHierarchyAliasesReportAndScheduleDefinitionsButNoFinancialFacts() throws Exception
    {
        try (Jpa jpa = database())
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            long source = addDefinitionAdjunctsAndTarget(jpa, fixture);
            var service = service(jpa, "TARGET");
            assertTrue(service.listCloneSources().stream().anyMatch(c -> c.id() == source && c.companyCode().equals("DEFAULT")));
            var before = service.inspectCloneSource(source);
            var clone = service.cloneChart(new ChartCloneCommand(source, before.revision(), "Copied definitions", "1"));
            assertEquals(ChartStatus.DRAFT, clone.status());
            assertEquals(6, clone.accountCount());
            assertEquals(0, clone.nonzeroOpeningCount());
            assertEquals(1, count(jpa, "Txn"));
            assertEquals(1, count(jpa, "CompanyBankAccount"));
            assertEquals(0, count(jpa, "InterchangeIdentity"));
            try (var em = jpa.em())
            {
                ChartOfAccounts sourceChart = em.find(ChartOfAccounts.class, source);
                ChartOfAccounts copied = em.find(ChartOfAccounts.class, clone.id());
                assertNotEquals(sourceChart.getPortableId(), copied.getPortableId());
                assertEquals("TARGET", copied.getCompany().getCode());
                assertNull(copied.getCompany().getActiveChartOfAccounts());
                var bank = em.createQuery("from Account where chart = :chart and code='1000'", Account.class).setParameter("chart", copied).getSingleResult();
                assertNotEquals(fixture.bank(), bank.getId());
                assertEquals("0500", bank.getParent().getCode());
                assertEquals(clone.id(), bank.getParent().getChart().getId());
                assertEquals(BigDecimal.ZERO.setScale(2), bank.getOpeningBalance());
                assertEquals("Keep definition text", bank.getDescription());
                assertEquals(new BigDecimal("99.50"), em.find(Account.class, fixture.bank()).getOpeningBalance());
                assertEquals(source, em.find(Account.class, fixture.bank()).getChart().getId());
                assertEquals(1L, em.createQuery("select count(a) from AccountAlias a where a.account.chart = :chart", Long.class).setParameter("chart", copied).getSingleResult());
                assertEquals(1L, em.createQuery("select count(a) from AccountReportSection a where a.account.chart = :chart", Long.class).setParameter("chart", copied).getSingleResult());
                assertEquals(1L, em.createQuery("select count(a) from AccountScheduleRequirement a where a.account.chart = :chart", Long.class).setParameter("chart", copied).getSingleResult());
                var alias = em.createQuery("from AccountAlias a where a.account.chart = :chart",AccountAlias.class).setParameter("chart",copied).getSingleResult();
                assertEquals("Operating bank",alias.getAliasText());
                assertEquals("Native fixture",alias.getSource());
                assertFalse(alias.isActive());
                var mapping = em.createQuery("from AccountReportSection a where a.account.chart = :chart",AccountReportSection.class).setParameter("chart",copied).getSingleResult();
                assertEquals(7,mapping.getSortOrder());
                assertEquals(SignPolicy.INVERT,mapping.getSignPolicy());
                var requirement = em.createQuery("from AccountScheduleRequirement a where a.account.chart = :chart",AccountScheduleRequirement.class).setParameter("chart",copied).getSingleResult();
                assertFalse(requirement.isRequired());
                assertEquals("Keep requirement",requirement.getNotes());
                var audit = em.createQuery("from AuditEvent where actionType='CHART_CLONED'", AuditEvent.class).getSingleResult();
                assertEquals("TARGET", audit.getCompany().getCode());
                assertTrue(audit.getAfterValue().contains("opening balances reset=1"));
            }
            new CompanyAdminService(jpa).assignActiveChart(company(jpa,"TARGET"),clone.id());
            var exported = new org.nonprofitbookkeeping.interchange.sclx.SclxCoreSnapshotQueryService(jpa,() -> "TARGET").query(Instant.now());
            byte[] bytes = new org.nonprofitbookkeeping.interchange.sclx.SclxJsonSerializer().serialize(exported);
            assertTrue(new String(bytes,java.nio.charset.StandardCharsets.UTF_8).contains("CHART_CLONED"));
            var jsonFile = directory.resolve("compatibility.json");
            new org.nonprofitbookkeeping.interchange.coa.ChartOfAccountsJsonService(jpa,() -> "TARGET").exportActiveChart(jsonFile);
            assertTrue(Files.readString(jsonFile).contains("Copied definitions"));
        }
    }

    @Test

    void lateFailureRollsBackTheWholeClonedGraphAndItsAudit()
    {
        try (Jpa jpa = database())
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            long source = addDefinitionAdjunctsAndTarget(jpa, fixture);
            var service = service(jpa, "TARGET");
            var snapshot = service.inspectCloneSource(source);
            long chartCount = count(jpa, "ChartOfAccounts");
            long auditCount = count(jpa, "AuditEvent");
            var failing = new ChartAdminService(jpa, () -> "TARGET", null, checkpoint ->
            {
                if (checkpoint.equals("AUDIT_WRITTEN")) throw new IllegalStateException("Injected after graph and audit writes");
            });
            assertThrows(IllegalStateException.class, () -> failing.cloneChart(new ChartCloneCommand(source, snapshot.revision(), "Rollback", "1")));
            assertEquals(chartCount, count(jpa, "ChartOfAccounts"));
            assertEquals(6, count(jpa, "Account"));
            assertEquals(1, count(jpa, "AccountAlias"));
            assertEquals(1, count(jpa, "AccountReportSection"));
            assertEquals(1, count(jpa, "AccountScheduleRequirement"));
            assertEquals(auditCount, count(jpa, "AuditEvent"));
        }
    }

    @Test

    void changedSourceDefinitionsAndDuplicateNamesCannotBeSilentlyAccepted()
    {
        try (Jpa jpa = database())
        {
            var service = service(jpa, "DEFAULT");
            var first = service.save(new ChartCommand(null, "Draft", "1", false, null));
            assertThrows(IllegalArgumentException.class, () -> service.save(new ChartCommand(null, " draft ", "1", false, null)));
            new AccountAdminService(jpa).save(account(first.id(), null, "1000", null, true, "0.00"));
            assertThrows(IllegalStateException.class, () -> service.save(new ChartCommand(first.id(), "Stale rename", "1", false, first.revision())));
            assertThrows(IllegalStateException.class, () -> service.cloneChart(new ChartCloneCommand(first.id(), first.revision(), "Stale copy", "1")));
            assertEquals(1, count(jpa, "ChartOfAccounts"));
            assertEquals("Draft", service.load(first.id()).name());
        }
    }

    @Test

    void completeAccountEditingRejectsPrecisionHierarchyAndStructuralHistoryChanges()
    {
        try (Jpa jpa = database())
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            var service = service(jpa, "DEFAULT");
            long current;
            try (var em = jpa.em())
            {
                current = em.find(Account.class, fixture.bank()).getChart().getId();
            }
            var accounts = new AccountAdminService(jpa);
            AccountCommand changedOpening = new AccountCommand(fixture.bank(), "1000", "1000", AccountType.ASSET, AccountFunction.BANK,
                NormalBalance.DEBIT, null, null, true, current, new AccountDefinitionDetails(true, null, null, new BigDecimal("1.00"), ""));
            assertThrows(IllegalArgumentException.class, () -> accounts.save(changedOpening));
            var draft = service.save(new ChartCommand(null, "New version", "1", false, null));
            assertThrows(IllegalArgumentException.class, () -> accounts.save(account(draft.id(), null, "1", null, true, "0.001")));
            var a = accounts.save(account(draft.id(), null, "A", null, false, "0.00"));
            var b = accounts.save(account(draft.id(), null, "B", "A", false, "0.00"));
            assertThrows(IllegalArgumentException.class, () -> accounts.save(account(draft.id(), a.getId(), "A", "B", false, "0.00")));
            assertThrows(CompanyOwnershipException.class, () -> accounts.save(account(draft.id(), fixture.bank(), "1000", null, true, "0.00")));
            assertEquals(1, count(jpa, "Txn"));
            try (var em = jpa.em())
            {
                assertNull(em.find(Account.class, a.getId()).getParent());
            }
        }
    }

    @Test

    void copiedDatabaseCannotReuseAnOriginalChartRevision() throws Exception
    {
        ChartDetails original;
        try (Jpa jpa = database())
        {
            original = service(jpa, "DEFAULT").save(new ChartCommand(null, "Native", "1", false, null));
        }
        Files.copy(directory.resolve("native.mv.db"), directory.resolve("copy.mv.db"));
        try (Jpa copy = new Jpa(directory.resolve("copy")))
        {
            var service = service(copy, "DEFAULT");
            assertThrows(IllegalStateException.class, () -> service.save(new ChartCommand(original.id(), "Wrong database", "1", false, original.revision())));
            assertThrows(IllegalStateException.class, () -> service.cloneChart(new ChartCloneCommand(original.id(), original.revision(), "Wrong copy", "1")));
            assertEquals(1, count(copy, "ChartOfAccounts"));
        }
    }

    @Test

    void chartWritesRequireBookkeepingPermissionAndRejectForeignTargets()
    {
        try (Jpa jpa = database())
        {
            var service = service(jpa, "DEFAULT");
            var chart = service.save(new ChartCommand(null, "Native", "1", false, null));
            createTarget(jpa);
            var other = service(jpa, "TARGET");
            assertThrows(CompanyOwnershipException.class, () -> other.load(chart.id()));
            assertThrows(CompanyOwnershipException.class, () -> other.save(new ChartCommand(chart.id(), "Foreign", "1", false, chart.revision())));
            var user = new AuthenticatedUserSession(9001L, "accountant", "Accountant", "DEFAULT", Set.of(ReservedSecurityRole.VIEWER), Instant.now(), Instant.now());
            var denied = new ChartAdminService(jpa, () -> "DEFAULT", new AuthorizationGuard(jpa, () -> Optional.of(user)));
            assertThrows(AuthorizationException.class, () -> denied.save(new ChartCommand(null, "Denied", "1", false, null)));
            assertThrows(AuthorizationException.class, denied::listCloneSources);
            assertEquals(1, count(jpa, "ChartOfAccounts"));
            var accountant = new AuthenticatedUserSession(9002L,"bookkeeper","Bookkeeper","DEFAULT",
                Set.of(ReservedSecurityRole.ACCOUNTANT),Instant.now(),Instant.now());
            var guard = new AuthorizationGuard(jpa,() -> Optional.of(accountant));
            var allowed = new ChartAdminService(jpa,() -> "DEFAULT",guard);
            var created = allowed.save(new ChartCommand(null,"Bookkeeper chart","1",false,null));
            assertEquals(ChartStatus.DRAFT,created.status());
            assertFalse(allowed.listCloneSources().isEmpty());
            assertThrows(AuthorizationException.class,() -> new CompanyAdminService(jpa,guard)
                .assignActiveChart(company(jpa,"DEFAULT"),created.id()));
        }
    }

    private Jpa database()
    {
        return new Jpa(directory.resolve("native"));
    }

    private static ChartAdminService service(Jpa jpa, String company)
    {
        return new ChartAdminService(jpa, () -> company, null);
    }

    private static AccountCommand account(long chart, Long id, String code, String parent, boolean posting, String opening)
    {
        return new AccountCommand(id, code, "Account " + code, AccountType.ASSET, null, NormalBalance.DEBIT, null, parent, true, chart,
            new AccountDefinitionDetails(posting, LocalDate.of(2026,1,1), LocalDate.of(2030,12,31), new BigDecimal(opening), "Definition notes"));
    }

    private static long company(Jpa jpa, String code)
    {
        try (var em = jpa.em())
        {
            return em.createQuery("from Company where code=:code", Company.class).setParameter("code",code).getSingleResult().getId();
        }
    }

    private static void createTarget(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Company target = new Company();
            target.setCode("TARGET");
            target.setDisplayName("Target company");
            target.setDefaultCurrency("EUR");
            em.persist(target);
            em.getTransaction().commit();
        }
    }

    private static long addDefinitionAdjunctsAndTarget(Jpa jpa, FundTransferTestFixture fixture)
    {
        createTarget(jpa);
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Account bank = em.find(Account.class, fixture.bank());
            ChartOfAccounts chart = bank.getChart();
            Account group = new Account();
            group.setChart(chart);
            group.setCode("0500");
            group.setName("Asset group");
            group.setAccountType(AccountType.ASSET);
            group.setNormalBalance(NormalBalance.DEBIT);
            group.setPosting(false);
            em.persist(group);
            bank.setParent(group);
            bank.setOpeningBalance(new BigDecimal("99.50"));
            bank.setDescription("Keep definition text");
            AccountAlias alias = new AccountAlias();
            alias.setAccount(bank);
            alias.setAliasText("Operating bank");
            alias.setSource("Native fixture");
            alias.setActive(false);
            em.persist(alias);
            ReportSection section = new ReportSection();
            section.setName("Clone report fixture");
            section.setReportType(ReportType.BALANCE_SHEET);
            em.persist(section);
            AccountReportSection mapping = new AccountReportSection();
            mapping.setAccount(bank);
            mapping.setReportSection(section);
            mapping.setSortOrder(7);
            mapping.setSignPolicy(SignPolicy.INVERT);
            em.persist(mapping);
            ScheduleKind kind = new ScheduleKind();
            kind.setCode("CLONE_TEST");
            kind.setName("Clone schedule fixture");
            em.persist(kind);
            AccountScheduleRequirement requirement = new AccountScheduleRequirement();
            requirement.setAccount(bank);
            requirement.setScheduleKind(kind);
            requirement.setRequired(false);
            requirement.setNotes("Keep requirement");
            em.persist(requirement);
            CompanyBankAccount configured = new CompanyBankAccount();
            configured.setCompany(chart.getCompany());
            configured.setAccount(bank);
            configured.setName("Source bank");
            em.persist(configured);
            em.getTransaction().commit();
            return chart.getId();
        }
    }

    private static long count(Jpa jpa, String entity)
    {
        try (var em = jpa.em())
        {
            return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
        }
    }
}
