package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.interchange.coa.ChartOfAccountsJsonService;
import org.nonprofitbookkeeping.interchange.sclx.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
class DraftChartDeletionServiceTest
{
    @TempDir Path directory;
    @Test
    void deletesAnEmptyDraftAndLeavesTheCompanyAndCurrentChart() throws Exception
    {
        try(Jpa jpa=db())
        {
            var ledger=FundTransferTestFixture.seed(jpa);
            var service=service(jpa);
            long chart=service.save(new ChartCommand(null,"Partial empty draft","1",false,null)).id();
            var preview=service.previewChartDeletion(chart);
            assertTrue(preview.canDelete());
            assertEquals(0,preview.accounts());
            service.deleteChart(chart,preview.revision());
            try(var em=jpa.em())
            {
                assertNull(em.find(ChartOfAccounts.class,chart));
                assertEquals(em.find(Account.class,ledger.bank()).getChart().getId(),company(em).getActiveChartOfAccounts().getId());
                assertEquals(1L,em.createQuery("select count(t) from Txn t",Long.class).getSingleResult());
            }
            assertEquals(1,count(jpa,"ChartOfAccounts"));
            assertEquals(1,count(jpa,"AuditEvent where actionType='CHART_DELETED'"));
        }
    }
    @Test
    void deletesUnusedPartialHierarchyMetadataAndOnlyItsOwnReceiptsWithExportableAudit() throws Exception
    {
        try(Jpa jpa=db())
        {
            var f=fixture(jpa);
            var service=service(jpa);
            long before=count(jpa,"AuditEvent");
            long receipts=count(jpa,"InterchangeIdentity");
            var preview=service.previewChartDeletion(f.chart());
            assertTrue(preview.canDelete(),preview.blockers().toString());
            assertEquals(2,preview.accounts());
            assertEquals(1,preview.aliases());
            assertEquals(1,preview.reportMappings());
            assertEquals(1,preview.scheduleRequirements());
            assertEquals(3,preview.importMappings());
            service.deleteChart(f.chart(),preview.revision());
            assertEquals(5,count(jpa,"Account"));
            assertEquals(1,count(jpa,"ChartOfAccounts"));
            assertEquals(0,count(jpa,"AccountAlias"));
            assertEquals(0,count(jpa,"AccountReportSection"));
            assertEquals(0,count(jpa,"AccountScheduleRequirement"));
            assertEquals(1,count(jpa,"ReportSection"));
            assertEquals(1,count(jpa,"ScheduleKind"));
            assertEquals(receipts-3,count(jpa,"InterchangeIdentity"));
            assertEquals(before+1,count(jpa,"AuditEvent"));
            try(var em=jpa.em())
            {
                assertEquals(f.current(),company(em).getActiveChartOfAccounts().getId());
                assertEquals(1L,em.createQuery("select count(t) from Txn t",Long.class).getSingleResult());
                var audit=em.createQuery("from AuditEvent where actionType='CHART_DELETED'",AuditEvent.class).getSingleResult();
                assertEquals(Long.toString(f.chart()),audit.getEntityId());
                assertTrue(audit.getBeforeValue().contains("Draft leaf"));
                assertTrue(audit.getBeforeValue().contains("12.34"));
                assertTrue(audit.getBeforeValue().contains("Alias text"));
                assertFalse(audit.getBeforeValue().contains("jdbc:h2:"));
            }
            new ChartOfAccountsJsonService(jpa,() -> "DEFAULT").exportActiveChart(directory.resolve("retained.json"));
            var snapshot=new SclxCoreSnapshotQueryService(jpa,() -> "DEFAULT").query(Instant.now());
            assertTrue(new String(new SclxJsonSerializer().serialize(snapshot),java.nio.charset.StandardCharsets.UTF_8).contains("CHART_DELETED"));
        }
    }
    @Test
    void protectsLifecycleEveryCurrentPointerExternalChildrenOwnershipAndForeignReceipts()
    {
        try(Jpa jpa=db())
        {
            var f=fixture(jpa);
            var service=service(jpa);
            for(var state:List.of(ChartStatus.ACTIVE,ChartStatus.RETIRED))
            {
                mutate(jpa,em -> em.find(ChartOfAccounts.class,f.chart()).setStatus(state));
                blocked(service,f.chart(),"DRAFT");
            }
            mutate(jpa,em ->
            {
                var chart=em.find(ChartOfAccounts.class,f.chart());
                chart.setStatus(ChartStatus.DRAFT);
                Company other=new Company();
                other.setCode("OTHER");
                other.setDisplayName("Other");
                other.setActiveChartOfAccounts(chart);
                em.persist(other);
            });
            blocked(service,f.chart(),"current company selections");
            mutate(jpa,em -> em.createQuery("from Company where code='OTHER'",Company.class).getSingleResult().setActiveChartOfAccounts(null));
            mutate(jpa,em -> em.find(Account.class,f.outside()).setParent(em.find(Account.class,f.leaf())));
            blocked(service,f.chart(),"child accounts in another chart");
            mutate(jpa,em ->
            {
                em.find(Account.class,f.outside()).setParent(null);
                var receipt=receipt(company(em),"CHART",f.chart(),"foreign");
                receipt.setCompany(em.createQuery("from Company where code='OTHER'",Company.class).getSingleResult());
                em.persist(receipt);
            });
            blocked(service,f.chart(),"another company");
            var foreign=new ChartAdminService(jpa,() -> "OTHER",null);
            assertThrows(CompanyOwnershipException.class,() -> foreign.previewChartDeletion(f.chart()));
            assertThrows(CompanyOwnershipException.class,() -> foreign.deleteChart(f.chart(),"revision"));
            var viewer=new AuthenticatedUserSession(99L,"viewer","Viewer","DEFAULT",Set.of(ReservedSecurityRole.VIEWER),Instant.now(),Instant.now());
            var denied=new ChartAdminService(jpa,() -> "DEFAULT",new AuthorizationGuard(jpa,() -> Optional.of(viewer)));
            assertThrows(AuthorizationException.class,() -> denied.previewChartDeletion(f.chart()));
            assertThrows(AuthorizationException.class,() -> denied.deleteChart(f.chart(),"revision"));
            assertEquals(2,count(jpa,"ChartOfAccounts"));
            assertEquals(7,count(jpa,"Account"));
        }
    }
    @Test
    void staleDefinitionsOrReceiptsAndLateFailureRestoreHierarchyMappingsAndAudit()
    {
        try(Jpa jpa=db())
        {
            var f=fixture(jpa);
            var service=service(jpa);
            var old=service.previewChartDeletion(f.chart());
            mutate(jpa,em -> em.find(Account.class,f.leaf()).setName("Changed leaf"));
            assertThrows(IllegalStateException.class,() -> service.deleteChart(f.chart(),old.revision()));
            var beforeReceipt=service.previewChartDeletion(f.chart());
            mutate(jpa,em -> em.persist(receipt(company(em),"ACCOUNT",f.leaf(),"another source")));
            assertThrows(IllegalStateException.class,() -> service.deleteChart(f.chart(),beforeReceipt.revision()));
            var current=service.previewChartDeletion(f.chart());
            long audit=count(jpa,"AuditEvent");
            long identities=count(jpa,"InterchangeIdentity");
            var failing=new ChartAdminService(jpa,() -> "DEFAULT",null,step ->
            {
                if(step.equals("CHART_DELETION_WRITTEN"))throw new IllegalStateException("late failure after removals and audit");
            });
            assertThrows(IllegalStateException.class,() -> failing.deleteChart(f.chart(),current.revision()));
            assertEquals(2,count(jpa,"ChartOfAccounts"));
            assertEquals(7,count(jpa,"Account"));
            assertEquals(1,count(jpa,"AccountAlias"));
            assertEquals(1,count(jpa,"AccountReportSection"));
            assertEquals(1,count(jpa,"AccountScheduleRequirement"));
            assertEquals(identities,count(jpa,"InterchangeIdentity"));
            assertEquals(audit,count(jpa,"AuditEvent"));
            try(var em=jpa.em())
            {
                assertEquals(f.parent(),em.find(Account.class,f.leaf()).getParent().getId());
            }
            assertEquals(current.revision(),service.previewChartDeletion(f.chart()).revision());
            service.deleteChart(f.chart(),current.revision());
            assertEquals(1,count(jpa,"ChartOfAccounts"));
        }
    }

    private Fixture fixture(Jpa jpa)
    {
        var ledger=FundTransferTestFixture.seed(jpa);
        var service=service(jpa);
        long chart=service.save(new ChartCommand(null,"Unused partial draft","1",false,null)).id();
        var admin=new AccountAdminService(jpa);
        var parent=admin.save(new AccountCommand(null,"D100","Draft group",AccountType.ASSET,null,NormalBalance.DEBIT,null,null,true,chart,
            new AccountDefinitionDetails(false,null,null,BigDecimal.ZERO,null)));
        var leaf=admin.save(new AccountCommand(null,"D101","Draft leaf",AccountType.ASSET,null,NormalBalance.DEBIT,null,"D100",true,chart,
            new AccountDefinitionDetails(true,null,null,new BigDecimal("12.34"),"Definition notes")));
        mutate(jpa,em ->
        {
            var company=company(em);
            var account=em.find(Account.class,leaf.getId());
            var alias=new AccountAlias();
            alias.setAccount(account);
            alias.setAliasText("Alias text");
            em.persist(alias);
            var section=new ReportSection();
            section.setName("Report");
            section.setReportType(ReportType.BALANCE_SHEET);
            em.persist(section);
            var report=new AccountReportSection();
            report.setAccount(account);
            report.setReportSection(section);
            em.persist(report);
            var kind=new ScheduleKind();
            kind.setCode("DELETE_CHART");
            kind.setName("Schedule");
            em.persist(kind);
            var requirement=new AccountScheduleRequirement();
            requirement.setAccount(account);
            requirement.setScheduleKind(kind);
            em.persist(requirement);
            em.persist(receipt(company,"CHART",chart,"draft chart"));
            em.persist(receipt(company,"ACCOUNT",parent.getId(),"draft group"));
            em.persist(receipt(company,"ACCOUNT",leaf.getId(),"draft leaf"));
            em.persist(receipt(company,"ACCOUNT",ledger.bank(),"retained bank"));
            em.persist(receipt(company,"FUND",leaf.getId(),"different entity type"));
        });
        try(var em=jpa.em())
        {
            return new Fixture(chart,parent.getId(),leaf.getId(),company(em).getActiveChartOfAccounts().getId(),ledger.expense());
        }
    }

    private record Fixture(long chart,long parent,long leaf,long current,long outside)
    {
    }
    private Jpa db()
    {
        return new Jpa(directory.resolve("chart-delete"));
    }

    private static ChartAdminService service(Jpa jpa)
    {
        return new ChartAdminService(jpa,() -> "DEFAULT",null);
    }

    private static Company company(EntityManager em)
    {
        return em.createQuery("from Company where code='DEFAULT'",Company.class).getSingleResult();
    }

    private static InterchangeIdentity receipt(Company company,String type,long id,String external)
    {
        var i=new InterchangeIdentity();
        i.setCompany(company);
        i.setEntityType(type);
        i.setLocalEntityId(Long.toString(id));
        i.setExternalId(external);
        i.setFormatCode("TEST");
        i.setSourceSystem("test");
        i.setNormalizedContentHash("0".repeat(64));
        return i;
    }

    private static void blocked(ChartAdminService service,long id,String message)
    {
        var preview=service.previewChartDeletion(id);
        assertFalse(preview.canDelete());
        assertTrue(String.join(";",preview.blockers()).contains(message),preview.blockers().toString());
        assertThrows(IllegalStateException.class,() -> service.deleteChart(id,preview.revision()));
    }

    private static void mutate(Jpa jpa,Consumer<EntityManager> work)
    {
        try(var em=jpa.em())
        {
            em.getTransaction().begin();
            work.accept(em);
            em.getTransaction().commit();
        }
    }

    private static long count(Jpa jpa,String query)
    {
        try(var em=jpa.em())
        {
            return em.createQuery("select count(e) from "+query.replace(" where "," e where ")+(query.contains(" where ")?"":" e"),Long.class).getSingleResult();
        }
    }
}
