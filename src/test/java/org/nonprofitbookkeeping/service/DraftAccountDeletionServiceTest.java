package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.interchange.coa.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DraftAccountDeletionServiceTest
{
    @TempDir Path directory;

    @Test

    void importedDraftDeletesChildMetadataAndAccountReceiptButRetainsParentChartAndAudit() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("imported")))
        {
            ChartAdminService service = service(jpa);
            long source = service.save(new ChartCommand(null,"Source","1",false,null)).id();
            Account group = account(jpa,source,"1000",null,false);
            account(jpa,source,"1010","1000",true);
            var json = new ChartOfAccountsJsonService(jpa,() -> "DEFAULT");
            Path file = directory.resolve("draft.json");
            json.exportChart(source,file);
            assertTrue(Files.readString(file).contains("\"DRAFT\""));
            var request = new CoaImportRequest(file,CoaImportMode.CREATE_NEW_CHART,"Imported draft","1",Map.of(),true);
            assertTrue(new ChartOfAccountsJsonImportService(jpa,() -> "DEFAULT").commit(json.preview(request)).committed());
            long chart;
            long child;
            long parent;
            try (var em = jpa.em())
            {
                chart = em.createQuery("from ChartOfAccounts where name='Imported draft'",ChartOfAccounts.class).getSingleResult().getId();
                child = em.createQuery("from Account where chart.id=:id and code='1010'",Account.class).setParameter("id",chart).getSingleResult().getId();
                parent = em.createQuery("from Account where chart.id=:id and code='1000'",Account.class).setParameter("id",chart).getSingleResult().getId();
            }
            metadata(jpa,child);
            long receipts = count(jpa,"InterchangeIdentity");
            long audit = count(jpa,"AuditEvent");
            assertTrue(service.previewAccountDeletion(chart,parent).blockers().stream().anyMatch(x -> x.contains("child accounts")));
            var preview = service.previewAccountDeletion(chart,child);
            assertTrue(preview.canDelete());
            assertEquals(1,preview.aliases());
            assertEquals(1,preview.reportMappings());
            assertEquals(1,preview.scheduleRequirements());
            assertEquals(1,preview.importMappings());
            service.deleteAccount(chart,child,preview.revision());
            assertEquals(3,count(jpa,"Account"));
            assertEquals(0,count(jpa,"AccountAlias"));
            assertEquals(0,count(jpa,"AccountReportSection"));
            assertEquals(0,count(jpa,"AccountScheduleRequirement"));
            assertEquals(1,count(jpa,"ReportSection"));
            assertEquals(1,count(jpa,"ScheduleKind"));
            assertEquals(receipts-1,count(jpa,"InterchangeIdentity"));
            assertEquals(audit+1,count(jpa,"AuditEvent"));
            assertEquals(0,count(jpa,"Txn"));
            try (var em = jpa.em())
            {
                assertNotNull(em.find(Account.class,group.getId()));
                assertNull(em.find(Account.class,child));
                assertNull(em.createQuery("from Company where code='DEFAULT'",Company.class).getSingleResult().getActiveChartOfAccounts());
                AuditEvent event = em.createQuery("from AuditEvent where actionType='ACCOUNT_DELETED'",AuditEvent.class).getSingleResult();
                assertEquals(Long.toString(child),event.getEntityId());
                assertTrue(event.getBeforeValue().contains("Imported draft"));
                assertTrue(event.getBeforeValue().contains("IMPORT"));
                assertTrue(event.getBeforeValue().contains("Alias text"));
            }
            var parentPreview = service.previewAccountDeletion(chart,parent);
            assertTrue(parentPreview.canDelete());
            service.deleteAccount(chart,parent,parentPreview.revision());
            json.exportChart(chart,directory.resolve("empty-draft.json"));
            assertTrue(Files.readString(directory.resolve("empty-draft.json")).contains("\"accounts\" : [ ]"));
            assertFalse(service.load(chart).currentForCompany());
            new CompanyAdminService(jpa).assignActiveChart(group.getChart().getCompany().getId(),source);
            var exported=new org.nonprofitbookkeeping.interchange.sclx.SclxCoreSnapshotQueryService(jpa,() -> "DEFAULT").query(Instant.now());
            String sclx=new String(new org.nonprofitbookkeeping.interchange.sclx.SclxJsonSerializer().serialize(exported),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(sclx.contains("ACCOUNT_DELETED"));
        }
    }

    @Test

    void blocksActiveRetiredCurrentForeignAndUnauthorizedDeletionAndExport() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("scope")))
        {
            var service = service(jpa);
            long chart = service.save(new ChartCommand(null,"Draft","1",false,null)).id();
            Account account = account(jpa,chart,"1000",null,true);
            for (ChartStatus status : List.of(ChartStatus.ACTIVE,ChartStatus.RETIRED))
            {
                mutate(jpa,em -> em.find(ChartOfAccounts.class,chart).setStatus(status));
                var preview = service.previewAccountDeletion(chart,account.getId());
                assertFalse(preview.canDelete());
                assertThrows(IllegalStateException.class,() -> service.deleteAccount(chart,account.getId(),preview.revision()));
            }
            mutate(jpa,em ->
            {
                ChartOfAccounts c = em.find(ChartOfAccounts.class,chart);
                c.setStatus(ChartStatus.DRAFT);
                c.getCompany().setActiveChartOfAccounts(c);
                Company other = new Company();
                other.setCode("OTHER");
                other.setDisplayName("Other");
                em.persist(other);
            });
            assertFalse(service.previewAccountDeletion(chart,account.getId()).canDelete());
            var foreign = new ChartAdminService(jpa,() -> "OTHER",null);
            assertThrows(CompanyOwnershipException.class,() -> foreign.previewAccountDeletion(chart,account.getId()));
            assertThrows(CompanyOwnershipException.class,() -> foreign.deleteAccount(chart,account.getId(),"revision"));
            Path path = directory.resolve("wrong-company.json");
            assertThrows(CompanyOwnershipException.class,() -> new ChartOfAccountsJsonService(jpa,() -> "OTHER").exportChart(chart,path));
            assertFalse(Files.exists(path));
            var viewer = new AuthenticatedUserSession(55L,"viewer","Viewer","DEFAULT",Set.of(ReservedSecurityRole.VIEWER),Instant.now(),Instant.now());
            var denied = new ChartAdminService(jpa,() -> "DEFAULT",new AuthorizationGuard(jpa,() -> Optional.of(viewer)));
            assertThrows(AuthorizationException.class,() -> denied.previewAccountDeletion(chart,account.getId()));
            assertThrows(AuthorizationException.class,() -> denied.deleteAccount(chart,account.getId(),"revision"));
            long otherChart = foreign.save(new ChartCommand(null,"Other chart","1",false,null)).id();
            assertThrows(IllegalArgumentException.class,() -> foreign.previewAccountDeletion(otherChart,account.getId()));
            assertEquals(1,count(jpa,"Account"));
        }
    }

    @Test

    void protectsEveryOperationalReferenceIncludingEachOptionalAssetLifecycleAccount()
    {
        try (Jpa jpa = new Jpa(directory.resolve("references")))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            long chart;
            try (var em = jpa.em())
            {
                chart=em.find(Account.class,fixture.bank()).getChart().getId();
            }
            final long chartId=chart;
            // Simulate retained references on a draft; never bypass them based on the lifecycle flag alone.
            mutate(jpa,em ->
            {
                var c=em.find(ChartOfAccounts.class,chartId);
                c.setStatus(ChartStatus.DRAFT);
                c.getCompany().setActiveChartOfAccounts(null);
                em.createQuery("from Txn",Txn.class).getSingleResult().setBankAccount(em.find(Account.class,fixture.allocation()));
                CompanyBankAccount bank=new CompanyBankAccount();
                bank.setCompany(c.getCompany());
                bank.setAccount(em.find(Account.class,fixture.equity()));
                bank.setName("Configured");
                em.persist(bank);
                FixedAsset asset=new FixedAsset();
                asset.setCompany(c.getCompany());
                asset.setName("Asset");
                asset.setFund(em.find(Fund.class,fixture.source()));
                asset.setAssetAccount(em.find(Account.class,fixture.bank()));
                asset.setAccumulatedDepreciationAccount(em.find(Account.class,fixture.income()));
                asset.setDepreciationExpenseAccount(em.find(Account.class,fixture.allocation()));
                asset.setAcquisitionDate(LocalDate.of(2026,1,1));
                asset.setUsefulLifeMonths(36);
                em.persist(asset);
                InventoryItem item=new InventoryItem();
                item.setCompany(c.getCompany());
                item.setName("Item");
                item.setItemType("Supply");
                item.setUnit("each");
                item.setInventoryAccount(em.find(Account.class,fixture.expense()));
                item.setFund(em.find(Fund.class,fixture.source()));
                item.setAcquisitionDate(LocalDate.of(2026,1,1));
                em.persist(item);
            });
            var service=service(jpa);
            blocked(service,chart,fixture.bank(),"journal lines");
            blocked(service,chart,fixture.allocation(),"journal bank references");
            blocked(service,chart,fixture.equity(),"configured bank accounts");
            for(long id:List.of(fixture.bank(),fixture.income(),fixture.allocation())) blocked(service,chart,id,"fixed assets");
            blocked(service,chart,fixture.expense(),"inventory items");
            for (int field=0;field<3;field++)
            {
                long id=account(jpa,chart,"LIFE"+field,null,true).getId();
                final int which=field;
                mutate(jpa,em ->
                {
                    FixedAssetLifecycleEvent event=em.createQuery("from FixedAssetLifecycleEvent",FixedAssetLifecycleEvent.class).getResultStream().findFirst().orElse(null);
                    if(event==null)
                    {
                        event=new FixedAssetLifecycleEvent();
                        event.setFixedAsset(em.createQuery("from FixedAsset",FixedAsset.class).getSingleResult());
                        event.setEventType(FixedAssetLifecycleEvent.EventType.RETIREMENT);
                        event.setEventDate(LocalDate.of(2026,1,2));
                        event.setTransaction(em.createQuery("from Txn",Txn.class).getSingleResult());
                        event.setAssetStatusBefore(FixedAsset.Status.ACTIVE);
                        event.setAssetStatusAfter(FixedAsset.Status.DISPOSED);
                        em.persist(event);
                    }
                    event.setProceedsAccount(which==0?em.find(Account.class,id):null);
                    event.setGainAccount(which==1?em.find(Account.class,id):null);
                    event.setLossAccount(which==2?em.find(Account.class,id):null);
                });
                blocked(service,chart,id,"asset lifecycle records");
            }
        }
    }

    @Test

    void staleDefinitionOrReceiptAndLateFailureLeaveAllRowsAndAuditIntact()
    {
        try (Jpa jpa = new Jpa(directory.resolve("rollback")))
        {
            var service=service(jpa);
            long chart=service.save(new ChartCommand(null,"Draft","1",false,null)).id();
            long id=account(jpa,chart,"1000",null,true).getId();
            metadata(jpa,id);
            var before=service.previewAccountDeletion(chart,id);
            mutate(jpa,em -> em.find(Account.class,id).setName("Changed"));
            assertThrows(IllegalStateException.class,() -> service.deleteAccount(chart,id,before.revision()));
            var current=service.previewAccountDeletion(chart,id);
            mutate(jpa,em -> em.persist(identity(em,em.find(Account.class,id),"SCLX","second")));
            assertThrows(IllegalStateException.class,() -> service.deleteAccount(chart,id,current.revision()));
            var fresh=service.previewAccountDeletion(chart,id);
            long audit=count(jpa,"AuditEvent");
            var failing=new ChartAdminService(jpa,() -> "DEFAULT",null,step ->
            {
                if(step.equals("ACCOUNT_DELETION_WRITTEN"))throw new IllegalStateException("late failure");
            });
            assertThrows(IllegalStateException.class,() -> failing.deleteAccount(chart,id,fresh.revision()));
            assertEquals(1,count(jpa,"Account"));
            assertEquals(1,count(jpa,"AccountAlias"));
            assertEquals(1,count(jpa,"AccountReportSection"));
            assertEquals(1,count(jpa,"AccountScheduleRequirement"));
            assertEquals(1,count(jpa,"InterchangeIdentity"));
            assertEquals(audit,count(jpa,"AuditEvent"));
            assertEquals(fresh.revision(),service.previewAccountDeletion(chart,id).revision());
            service.deleteAccount(chart,id,fresh.revision());
            assertEquals(0,count(jpa,"Account"));
            assertEquals(0,count(jpa,"InterchangeIdentity"));
        }
    }

    @Test

    void foreignReceiptIsProtectedAndViewedExportDoesNotUseTheActiveChart() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("export")))
        {
            var service=service(jpa);
            long active=service.save(new ChartCommand(null,"Current chart","1",false,null)).id();
            Account old=account(jpa,active,"OLD",null,true);
            try(var em=jpa.em())
            {
                new CompanyAdminService(jpa).assignActiveChart(old.getChart().getCompany().getId(),active);
            }
            long draft=service.save(new ChartCommand(null,"Viewed draft","2",false,null)).id();
            long id=account(jpa,draft,"DRAFT",null,true).getId();
            mutate(jpa,em ->
            {
                Company other=new Company();
                other.setCode("OTHER");
                other.setDisplayName("Other");
                em.persist(other);
                var receipt=identity(em,em.find(Account.class,id),"SCLX","foreign");
                receipt.setCompany(other);
                em.persist(receipt);
            });
            blocked(service,draft,id,"another company");
            var json=new ChartOfAccountsJsonService(jpa,() -> "DEFAULT");
            Path a=directory.resolve("viewed.json"),b=directory.resolve("again.json");
            json.exportChart(draft,a);
            json.exportChart(draft,b);
            assertArrayEquals(Files.readAllBytes(a),Files.readAllBytes(b));
            String text=Files.readString(a);
            assertTrue(text.contains("Viewed draft"));
            assertFalse(text.contains("Current chart"));
            assertFalse(text.contains("\"OLD\""));
            assertTrue(service.load(active).currentForCompany());
            assertFalse(service.load(draft).currentForCompany());
        }
    }

    private static void blocked(ChartAdminService service,long chart,long id,String text)
    {
        var p=service.previewAccountDeletion(chart,id);
        assertFalse(p.canDelete());
        assertTrue(String.join(";",p.blockers()).contains(text),p.blockers().toString());
        assertThrows(IllegalStateException.class,() -> service.deleteAccount(chart,id,p.revision()));
        var chartPreview=service.previewChartDeletion(chart);
        assertFalse(chartPreview.canDelete());
        assertTrue(String.join(";",chartPreview.blockers()).contains(text),chartPreview.blockers().toString());
        assertThrows(IllegalStateException.class,() -> service.deleteChart(chart,chartPreview.revision()));
    }

    private static ChartAdminService service(Jpa jpa)
    {
        return new ChartAdminService(jpa,() -> "DEFAULT",null);
    }

    private static Account account(Jpa jpa,long chart,String code,String parent,boolean posting)
    {
        return new AccountAdminService(jpa).save(new AccountCommand(null,code,"Account "+code,AccountType.ASSET,null,NormalBalance.DEBIT,null,parent,true,chart,
            new AccountDefinitionDetails(posting,null,null,BigDecimal.ZERO,"Notes")));
    }

    private static void metadata(Jpa jpa,long id)
    {
        mutate(jpa,em ->
        {
            Account a=em.find(Account.class,id);
            AccountAlias alias=new AccountAlias();
            alias.setAccount(a);
            alias.setAliasText("Alias text");
            em.persist(alias);
            ReportSection section=new ReportSection();
            section.setName("Test report");
            section.setReportType(ReportType.BALANCE_SHEET);
            em.persist(section);
            AccountReportSection report=new AccountReportSection();
            report.setAccount(a);
            report.setReportSection(section);
            em.persist(report);
            ScheduleKind kind=new ScheduleKind();
            kind.setCode("TEST");
            kind.setName("Test schedule");
            em.persist(kind);
            AccountScheduleRequirement requirement=new AccountScheduleRequirement();
            requirement.setAccount(a);
            requirement.setScheduleKind(kind);
            em.persist(requirement);
        });
    }

    private static InterchangeIdentity identity(EntityManager em,Account a,String format,String external)
    {
        InterchangeIdentity i=new InterchangeIdentity();
        i.setCompany(a.getChart().getCompany());
        i.setEntityType("ACCOUNT");
        i.setLocalEntityId(a.getId().toString());
        i.setFormatCode(format);
        i.setSourceSystem("test");
        i.setExternalId(external);
        i.setNormalizedContentHash("0".repeat(64));
        return i;
    }

    private static void mutate(Jpa jpa,java.util.function.Consumer<EntityManager> action)
    {
        try(var em=jpa.em())
        {
            em.getTransaction().begin();
            action.accept(em);
            em.getTransaction().commit();
        }
    }

    private static long count(Jpa jpa,String entity)
    {
        try(var em=jpa.em())
        {
            return em.createQuery("select count(a) from "+entity+" a",Long.class).getSingleResult();
        }
    }
}
