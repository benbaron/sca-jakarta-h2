package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SclxBudgetCategoriesTest
{
    @TempDir Path directory;
    static final ObjectMapper JSON = new ObjectMapper();
    static final Instant CREATED = Instant.parse("2020-01-01T01:02:03.123456Z"), UPDATED = Instant.parse("2026-02-01T01:02:03.654321Z");

    Path source() throws Exception
    {
        Path db=directory.resolve("source"), file=directory.resolve("source.sclx");
        try (Jpa jpa=new Jpa(db))
        {
            var f=FundTransferTestFixture.seed(jpa); long category;
            try (var em=jpa.em())
            {
                em.getTransaction().begin(); var company=new CompanyOwnershipService(jpa).requireCompany(em,"DEFAULT");
                var used=category(company,"PROGRAM","  Program café  ","  原本\nExact description  ");
                used.setEffectiveFrom(LocalDate.of(2020,1,1)); used.setEffectiveTo(LocalDate.of(2027,12,31)); em.persist(used);
                var unused=category(company,"UNUSED","Program café",null); unused.setActive(false); em.persist(unused);
                em.persist(category(company,"EMPTY","Empty description",""));
                em.flush(); category=used.getId(); em.getTransaction().commit();
            }
            var budgets=new BudgetPlanService(jpa);
            var plan=budgets.createDraft(new BudgetPlanCommand("Retained category links",2026,"V1",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),""));
            budgets.replaceDraftLines(plan.id(),List.of(new BudgetLineCommand(category,f.source(),null,null,new BigDecimal("50.1234"),"Retained line")));
            new TransactionEntryService(jpa).enter(new TransactionCommand(FundTransferTestFixture.DATE,null,"Categorized expense",null,
                    List.of(new TransactionLineCommand(f.expense(),f.source(),category,null,null,new BigDecimal("7.1234"),BigDecimal.ZERO,false,""),
                            FundTransferTestFixture.line(f.bank(),f.source(),"0","7.1234"))));
            try (var em=jpa.em())
            {
                em.getTransaction().begin(); var used=em.find(BudgetCategory.class,category);
                used.setActive(false); used.setEffectiveTo(LocalDate.of(2025,12,31)); em.getTransaction().commit();
            }
            var doc=new SclxCoreSnapshotQueryService(jpa).query(Instant.EPOCH);
            var bytes=new SclxJsonSerializer().serialize(doc); assertArrayEquals(bytes,new SclxJsonSerializer().serialize(doc));
            assertEquals(3,SclxExportCounts.from(doc,0,0).budgetCategories()); Files.write(file,bytes);
        }
        return file;
    }

    static BudgetCategory category(Company company,String code,String name,String description)
    {
        var c=new BudgetCategory(); c.setCompany(company); c.setCode(code); c.setName(name); c.setDescription(description);
        c.initializeImportMetadata(UUID.randomUUID(),CREATED,UPDATED); return c;
    }

    @Test
    void completeUnusedInactiveMetadataAndStableBudgetJournalLinksSurviveRemappingRestartAndRetry() throws Exception
    {
        Path file=source(), db=directory.resolve("target"); var expected=data(file);
        long audits;
        try (Jpa jpa=new Jpa(db))
        {
            try (var em=jpa.em())
            {
                em.getTransaction().begin(); var company=new Company(); company.setCode("COPY"); company.setDisplayName("Copy"); em.persist(company);
                em.persist(category(company,"OCCUPIED","Existing target master","Retain")); em.getTransaction().commit();
            }
            commit(jpa,"COPY",file);
            var actual=exported(jpa,"COPY"); expected.byCode().forEach((code,fact)->assertEquals(fact.value(),actual.byCode().get(code).value()));
            assertEquals(4,actual.byCode().size()); assertFalse(actual.byCode().get("PROGRAM").value().active());
            assertNull(actual.byCode().get("UNUSED").value().description()); assertEquals("",actual.byCode().get("EMPTY").value().description());
            try (var em=jpa.em())
            {
                var master=em.createQuery("from BudgetCategory c where c.company.code='COPY' and c.code='PROGRAM'",BudgetCategory.class).getSingleResult();
                assertEquals(master.getId(),em.createQuery("select l.budgetCategory.id from BudgetLine l",Long.class).getSingleResult());
                assertEquals(master.getId(),em.createQuery("select s.budgetCategory.id from TxnSplit s where s.budgetCategory is not null",Long.class).getSingleResult());
                assertEquals(2,count(jpa,"Txn")); assertEquals(4,count(jpa,"TxnSplit"));
                assertEquals(0L,em.createQuery("select count(e) from AuditEvent e where e.actionType like 'BUDGET_CATEGORY_%'",Long.class).getSingleResult());
            }
            audits=count(jpa,"AuditEvent");
        }
        try (Jpa jpa=new Jpa(db))
        {
            commit(jpa,"COPY",file); assertEquals(audits,count(jpa,"AuditEvent")); assertEquals(4,count(jpa,"BudgetCategory"));
            expected.byCode().forEach((code,fact)->assertEquals(fact.value(),exported(jpa,"COPY").byCode().get(code).value()));
        }
    }

    @Test
    void malformedPartialUnsupportedAndOversizeMasterGraphsBlockBeforeAnyBusinessWrite() throws Exception
    {
        Path file=source(); ObjectNode original=(ObjectNode)JSON.readTree(file.toFile());
        try (Jpa jpa=new Jpa(directory.resolve("invalid")))
        {
            for (String kind:List.of("version","overflow","extra","missing","null","bool","name","code","date","timestamp","precision","duplicate","alias","case","partial","description"))
            {
                var root=original.deepCopy(); var ext=(ObjectNode)root.path("extensions").path("scaJakartaH2").path(SclxBudgetCategories.KEY);
                var rows=(ArrayNode)ext.path("categories"); var row=(ObjectNode)rows.get(0);
                switch(kind)
                {
                    case "version"->ext.put("version",2);
                    case "overflow"->ext.put("version",4294967297L);
                    case "extra"->row.put("unexpected",true);
                    case "missing"->row.remove("description");
                    case "null"->root.with("extensions").with("scaJakartaH2").putNull(SclxBudgetCategories.KEY);
                    case "bool"->row.put("active","false");
                    case "name"->row.put("name"," ");
                    case "code"->row.put("code","x".repeat(65));
                    case "date"->{row.put("effectiveFrom","2026-01-01");row.put("effectiveTo","2020-01-01");}
                    case "timestamp"->row.putNull("createdAt");
                    case "precision"->row.put("updatedAt","2026-01-01T00:00:00.123456789Z");
                    case "duplicate"->rows.add(row.deepCopy());
                    case "alias"->{var alias=row.deepCopy();alias.put("categoryId",alias.path("categoryId").asText().replace(":DEFAULT:",":OTHER:"));alias.put("code","ALIAS");rows.add(alias);}
                    case "case"->{var alias=row.deepCopy();alias.put("categoryId",SclxBudgetCategories.id("DEFAULT",UUID.randomUUID()));alias.put("code",row.path("code").asText().toLowerCase(Locale.ROOT));rows.add(alias);}
                    case "partial"->{for(int i=0;i<rows.size();i++)if(rows.get(i).path("code").asText().equals("PROGRAM")){rows.remove(i);break;}}
                    case "description"->row.put("description","€".repeat(350000));
                }
                Path input=directory.resolve(kind+".sclx"); JSON.writeValue(input.toFile(),root);
                var preview=new SclxImportPreviewService(jpa,()->"DEFAULT").preview(input);
                assertTrue(preview.hasBlockingErrors(),kind);
                assertThrows(IllegalStateException.class,()->new SclxImportCommitService(jpa,()->"DEFAULT").commit(input,preview,"tester",true,true),kind);
                assertEquals(0,count(jpa,"BudgetCategory")); assertEquals(0,count(jpa,"Txn")); assertEquals(0,count(jpa,"InterchangeIdentity"));
            }
        }
    }

    @Test
    void changedSourceAndChangedNativeMetadataRequireKeepTargetWithoutChangingAccountingLinks() throws Exception
    {
        Path file=source(); var expected=data(file);
        try (Jpa jpa=new Jpa(directory.resolve("conflicts")))
        {
            commit(jpa,"DEFAULT",file);
            var root=(ObjectNode)JSON.readTree(file.toFile());
            for(var row:root.path("extensions").path("scaJakartaH2").path(SclxBudgetCategories.KEY).path("categories"))
                if(row.path("code").asText().equals("PROGRAM"))((ObjectNode)row).put("description","Changed source metadata");
            Path changed=directory.resolve("changed.sclx");JSON.writeValue(changed.toFile(),root);
            keep(jpa,changed); assertEquals(expected,exported(jpa,"DEFAULT"));
            long audits=count(jpa,"AuditEvent");commit(jpa,"DEFAULT",file);assertEquals(audits,count(jpa,"AuditEvent"));
            try(var em=jpa.em())
            {
                em.getTransaction().begin();em.createQuery("update BudgetCategory c set c.name='Changed target label', c.code='TARGET-RENAMED' where c.code='PROGRAM'").executeUpdate();em.getTransaction().commit();
            }
            var preview=new SclxImportPreviewService(jpa,()->"DEFAULT").preview(file);assertTrue(preview.hasBlockingErrors());
            keep(jpa,file); assertEquals("Changed target label",exported(jpa,"DEFAULT").byCode().get("TARGET-RENAMED").value().name());
            assertEquals(3,count(jpa,"BudgetCategory"));assertEquals(2,count(jpa,"Txn"));
        }
    }

    @Test
    void codeCollisionKeepsExplicitTargetAndForeignUuidBlocksRatherThanDuplicatingOrReowning() throws Exception
    {
        Path file=source();var expected=data(file);
        try(Jpa jpa=new Jpa(directory.resolve("code-collision")))
        {
            UUID targetUuid;
            try(var em=jpa.em())
            {
                em.getTransaction().begin();var owner=new CompanyOwnershipService(jpa).requireCompany(em,"DEFAULT");
                var target=category(owner,"program","Target wins","Retained target");targetUuid=target.getPortableId();em.persist(target);em.getTransaction().commit();
            }
            keep(jpa,file);
            try(var em=jpa.em())
            {
                var kept=em.createQuery("from BudgetCategory c where c.code='program'",BudgetCategory.class).getSingleResult();
                assertEquals(targetUuid,kept.getPortableId());assertEquals("Target wins",kept.getName());
                assertEquals(kept.getId(),em.createQuery("select l.budgetCategory.id from BudgetLine l",Long.class).getSingleResult());
            }
            assertEquals(3,count(jpa,"BudgetCategory"));
        }
        try(Jpa jpa=new Jpa(directory.resolve("foreign"));var em=jpa.em())
        {
            em.getTransaction().begin();var owner=new Company();owner.setCode("OTHER");owner.setDisplayName("Other");em.persist(owner);
            new BudgetCategoryAdminService(jpa).importForInterchange(em,owner,expected.byCode().get("UNUSED").value());em.getTransaction().commit();
            var preview=new SclxImportPreviewService(jpa,()->"DEFAULT").preview(file);
            assertTrue(preview.operation().messages().stream().anyMatch(m->m.code().equals("SCLX_NATIVE_PORTABLE_ID_OTHER_COMPANY")));
            assertThrows(IllegalStateException.class,()->new SclxImportCommitService(jpa,()->"DEFAULT").commit(file,preview,"tester",true,true));
            assertEquals(1,count(jpa,"BudgetCategory"));assertEquals(0,count(jpa,"Txn"));
        }
    }

    @Test
    void identicalNativeMastersWithoutSourceIdentitiesAreReusedAndMissingRetainedRowsFailClosed() throws Exception
    {
        Path file=source();var expected=data(file);
        try(Jpa jpa=new Jpa(directory.resolve("native")))
        {
            try(var em=jpa.em())
            {
                em.getTransaction().begin();var owner=new CompanyOwnershipService(jpa).requireCompany(em,"DEFAULT");
                for(var fact:expected.byCode().values())new BudgetCategoryAdminService(jpa).importForInterchange(em,owner,fact.value());
                em.getTransaction().commit();
            }
            commit(jpa,"DEFAULT",file);assertEquals(3,count(jpa,"BudgetCategory"));assertEquals(expected,exported(jpa,"DEFAULT"));
            String originalId;
            try(var em=jpa.em())
            {
                em.getTransaction().begin();
                originalId=em.createQuery("select c.id from BudgetCategory c where c.code='PROGRAM'",Long.class).getSingleResult().toString();
                String wrongId=em.createQuery("select c.id from BudgetCategory c where c.code='EMPTY'",Long.class).getSingleResult().toString();
                em.createQuery("update InterchangeIdentity i set i.localEntityId=:wrong where i.entityType='BUDGET_CATEGORY' and i.externalId=:external")
                        .setParameter("wrong",wrongId).setParameter("external",expected.byCode().get("PROGRAM").id()).executeUpdate();
                em.getTransaction().commit();
            }
            var drift=new SclxImportPreviewService(jpa,()->"DEFAULT").preview(file);
            assertTrue(drift.operation().messages().stream().anyMatch(m->m.code().equals("SCLX_CATEGORY_IDENTITY_CHANGED")));
            assertThrows(IllegalStateException.class,()->new SclxImportCommitService(jpa,()->"DEFAULT").commit(file,drift,"tester",true,true));
            try(var em=jpa.em())
            {
                em.getTransaction().begin();
                em.createQuery("update InterchangeIdentity i set i.localEntityId=:original where i.entityType='BUDGET_CATEGORY' and i.externalId=:external")
                        .setParameter("original",originalId).setParameter("external",expected.byCode().get("PROGRAM").id()).executeUpdate();
                em.getTransaction().commit();
            }
            try(var em=jpa.em())
            {
                em.getTransaction().begin();em.createQuery("delete from BudgetCategory c where c.code='UNUSED'").executeUpdate();em.getTransaction().commit();
            }
            var preview=new SclxImportPreviewService(jpa,()->"DEFAULT").preview(file);
            assertTrue(preview.operation().messages().stream().anyMatch(m->m.code().equals("SCLX_CATEGORY_IDENTITY_CHANGED")));
            assertThrows(IllegalStateException.class,()->new SclxImportCommitService(jpa,()->"DEFAULT").commit(file,preview,"tester",true,true));
            assertEquals(2,count(jpa,"BudgetCategory"));
        }
    }

    @Test
    void legacyAbsenceKeepsCodeAsNameWithoutInventingUnusedOrHistoricalMasterFacts() throws Exception
    {
        var root=(ObjectNode)JSON.readTree(source().toFile());((ObjectNode)root.path("extensions").path("scaJakartaH2")).remove(SclxBudgetCategories.KEY);
        assertFalse(SclxBudgetCategories.parse(root).present());Path legacy=directory.resolve("legacy.sclx");JSON.writeValue(legacy.toFile(),root);
        try(Jpa jpa=new Jpa(directory.resolve("legacy")))
        {
            commit(jpa,"DEFAULT",legacy);assertEquals(1,count(jpa,"BudgetCategory"));
            try(var em=jpa.em())
            {
                var category=em.createQuery("from BudgetCategory",BudgetCategory.class).getSingleResult();
                assertEquals("PROGRAM",category.getName());assertTrue(category.isActive());assertNull(category.getDescription());assertNull(category.getEffectiveTo());
            }
            assertEquals(2,count(jpa,"Txn"));
        }
    }

    @Test
    void viewerAndLateFailureCannotLeaveMastersOrLedgerAndRetrySucceeds() throws Exception
    {
        Path file=source();List<Integer>writes=new ArrayList<>();
        try(Jpa probe=new Jpa(directory.resolve("probe")))
        {
            var preview=new SclxImportPreviewService(probe,()->"DEFAULT").preview(file);
            assertTrue(new SclxImportCommitService(probe,()->"DEFAULT",writes::add).commit(file,preview,"tester",true,true).committed());
        }
        int late=writes.get(writes.size()-2);assertTrue(late>3);
        try(Jpa jpa=new Jpa(directory.resolve("rollback")))
        {
            var preview=new SclxImportPreviewService(jpa,()->"DEFAULT").preview(file);
            var viewer=new AuthenticatedUserSession(9001,"viewer","Viewer","DEFAULT",Set.of(ReservedSecurityRole.VIEWER),Instant.EPOCH,Instant.EPOCH);
            assertThrows(AuthorizationException.class,()->new SclxImportCommitService(jpa,()->"DEFAULT",new AuthorizationGuard(jpa,()->Optional.of(viewer))).commit(file,preview,"tester",true,true));
            var reached=new java.util.concurrent.atomic.AtomicBoolean();
            var failed=new SclxImportCommitService(jpa,()->"DEFAULT",n->{if(n>=late){reached.set(true);throw new IllegalStateException("Synthetic late failure");}}).commit(file,preview,"tester",true,true);
            assertTrue(reached.get());assertFalse(failed.committed());
            for(String entity:List.of("BudgetCategory","BudgetPlan","BudgetLine","Account","Fund","Txn","TxnSplit","AuditEvent","InterchangeIdentity"))assertEquals(0,count(jpa,entity),entity);
            commit(jpa,"DEFAULT",file);assertEquals(3,count(jpa,"BudgetCategory"));
        }
    }

    @Test
    void preservesLegalNativeCodeWhitespaceInsteadOfNormalizingHistoricalMasterKeys() throws Exception
    {
        String code="\u2003CODE\u2003";
        BudgetCategoryAdminService.ImportFact fact;
        try(Jpa jpa=new Jpa(directory.resolve("unicode-source")))
        {
            var saved=new BudgetCategoryAdminService(jpa).save(new BudgetCategoryCommand(null,code,"Unicode code",true,null,null,null));
            try(var em=jpa.em())
            {
                var category=em.find(BudgetCategory.class,saved.id());
                ObjectNode root=JSON.createObjectNode();
                root.putObject("extensions").putObject("scaJakartaH2").set(SclxBudgetCategories.KEY,
                        JSON.valueToTree(SclxBudgetCategories.export("DEFAULT",List.of(category))));
                fact=SclxBudgetCategories.parse(root).byCode().get(code).value();
                assertEquals(code,fact.code());
            }
        }
        try(Jpa jpa=new Jpa(directory.resolve("unicode-target"));var em=jpa.em())
        {
            em.getTransaction().begin();var owner=new CompanyOwnershipService(jpa).requireCompany(em,"DEFAULT");
            var restored=new BudgetCategoryAdminService(jpa).importForInterchange(em,owner,fact);
            em.getTransaction().commit();em.clear();assertEquals(code,em.find(BudgetCategory.class,restored.getId()).getCode());
        }
    }

    static SclxBudgetCategories.Data data(Path file)throws Exception{return SclxBudgetCategories.parse(JSON.readTree(file.toFile()));}
    static SclxBudgetCategories.Data exported(Jpa jpa,String company)
    {
        try{return SclxBudgetCategories.parse(JSON.readTree(new SclxJsonSerializer().serialize(new SclxCoreSnapshotQueryService(jpa,()->company).query(Instant.EPOCH))));}
        catch(Exception ex){throw new AssertionError(ex);}
    }
    static void commit(Jpa jpa,String company,Path file)
    {
        var preview=new SclxImportPreviewService(jpa,()->company).preview(file);assertFalse(preview.hasBlockingErrors(),preview.operation().messages().toString());
        var result=new SclxImportCommitService(jpa,()->company).commit(file,preview,"tester",true,true);assertTrue(result.committed(),result.toString());
    }
    static void keep(Jpa jpa,Path file)
    {
        var service=new SclxImportPreviewService(jpa,()->"DEFAULT");var preview=service.preview(file);
        var choices=preview.operation().items().stream().filter(i->i.identityMatch()==InterchangeIdentityMatch.CONFLICT)
                .map(i->new SclxImportConflictSelection(i.entityType(),i.externalId(),SclxImportConflictChoice.KEEP_TARGET)).toList();assertFalse(choices.isEmpty());
        var resolved=service.preview(file,List.of(),choices);assertFalse(resolved.hasBlockingErrors(),resolved.operation().messages().toString());
        var result=new SclxImportCommitService(jpa,()->"DEFAULT").commit(file,resolved,"tester",true,true);assertTrue(result.committed(),result.toString());
    }
    static long count(Jpa jpa,String entity){try(var em=jpa.em()){return em.createQuery("select count(e) from "+entity+" e",Long.class).getSingleResult();}}
}
