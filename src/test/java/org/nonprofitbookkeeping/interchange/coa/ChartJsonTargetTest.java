package org.nonprofitbookkeeping.interchange.coa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class ChartJsonTargetTest
{
    @TempDir Path directory;
    @Test
    void mergesIntoTheNamedDraftWithoutChangingTheCurrentChartOrItsSameCodeAccount() throws Exception
    {
        try(Jpa jpa=new Jpa(directory.resolve("merge")))
        {
            var ledger=FundTransferTestFixture.seed(jpa);
            long draft=draft(jpa,"Draft target");
            long account=new AccountAdminService(jpa).save(new AccountCommand(null,"1000","Draft checking",AccountType.ASSET,AccountFunction.BANK,NormalBalance.DEBIT,
                AccountSubtype.CASH,null,true,draft,new AccountDefinitionDetails(true,null,null,BigDecimal.ZERO,null))).getId();
            var json=new ChartOfAccountsJsonService(jpa,() -> "DEFAULT");
            var request=request(source(),draft);
            var preview=json.preview(request);
            assertFalse(preview.hasBlockingErrors());
            assertEquals(draft,preview.scope().chartId());
            assertTrue(new ChartOfAccountsJsonImportService(jpa,() -> "DEFAULT").commit(preview).committed());
            long identities;
            try(var em=jpa.em())
            {
                identities=em.createQuery("select count(i) from InterchangeIdentity i",Long.class).getSingleResult();
                em.getTransaction().begin();
                em.find(ChartOfAccounts.class,draft).setName("R".repeat(200));
                em.getTransaction().commit();
            }
            assertTrue(new ChartOfAccountsJsonImportService(jpa,() -> "DEFAULT").commit(json.preview(request)).committed());
            try(var em=jpa.em())
            {
                assertEquals(identities,em.createQuery("select count(i) from InterchangeIdentity i",Long.class).getSingleResult());
            }
            try(var em=jpa.em())
            {
                assertEquals("Imported checking",em.find(Account.class,account).getName());
                assertEquals("1000",em.find(Account.class,ledger.bank()).getName());
                assertNotEquals(draft,em.find(Account.class,ledger.bank()).getChart().getId());
                assertEquals(ChartStatus.DRAFT,em.find(ChartOfAccounts.class,draft).getStatus());
                assertEquals(em.find(Account.class,ledger.bank()).getChart().getId(),em.createQuery("from Company where code='DEFAULT'",Company.class).getSingleResult().getActiveChartOfAccounts().getId());
                assertEquals(1L,em.createQuery("select count(t) from Txn t",Long.class).getSingleResult());
            }
        }
    }
    @Test
    void reviewedEditsRetainTheExplicitTargetEvenWhenAnotherChartBecomesCurrent() throws Exception
    {
        try(Jpa jpa=new Jpa(directory.resolve("review")))
        {
            FundTransferTestFixture.seed(jpa);
            long target=draft(jpa,"Reviewed draft");
            var json=new ChartOfAccountsJsonService(jpa,() -> "DEFAULT");
            var original=json.preview(request(source(),target));
            var review=new CoaImportReviewDraft(original);
            int row=review.rows().get(0);
            var value=review.account(row);
            review.apply(row,CoaImportReviewDraft.definition(value,value.code(),"Reviewed checking",value.type(),value.function(),value.subtype(),value.normalBalance(),value.parentCode(),value.posting(),value.active(),value.effectiveFrom(),value.effectiveTo(),value.openingBalance(),value.description(),value.currency()));
            assertEquals(target,review.request(true).targetChartId());
            long other=draft(jpa,"Different current chart");
            try(var em=jpa.em())
            {
                new CompanyAdminService(jpa).assignActiveChart(em.createQuery("from Company where code='DEFAULT'",Company.class).getSingleResult().getId(),other);
            }
            var approved=json.preview(review.request(true));
            assertFalse(approved.hasBlockingErrors());
            assertEquals(target,approved.scope().chartId());
            assertTrue(new ChartOfAccountsJsonImportService(jpa,() -> "DEFAULT").commit(approved).committed());
            try(var em=jpa.em())
            {
                assertEquals("Reviewed checking",em.createQuery("from Account where chart.id=:id and code='1000'",Account.class).setParameter("id",target).getSingleResult().getName());
                assertEquals(other,em.createQuery("from Company where code='DEFAULT'",Company.class).getSingleResult().getActiveChartOfAccounts().getId());
                assertEquals(ChartStatus.DRAFT,em.find(ChartOfAccounts.class,target).getStatus());
            }
        }
    }
    @Test
    void wrongOwnerRetiredMissingDriftAndLateFailureCannotRedirectOrPartiallyMerge() throws Exception
    {
        try(Jpa jpa=new Jpa(directory.resolve("guards")))
        {
            FundTransferTestFixture.seed(jpa);
            long target=draft(jpa,"Guarded draft");
            var json=new ChartOfAccountsJsonService(jpa,() -> "DEFAULT");
            Path file=source();
            var request=request(file,target);
            var approved=json.preview(request);
            try(var em=jpa.em())
            {
                em.getTransaction().begin();
                em.find(ChartOfAccounts.class,target).setVersion("changed");
                em.getTransaction().commit();
            }
            assertThrows(IllegalStateException.class,() -> new ChartOfAccountsJsonImportService(jpa,() -> "DEFAULT").commit(approved));
            var current=json.preview(request);
            var failing=new ChartOfAccountsJsonImportService(jpa,() -> "DEFAULT",writes ->
            {
                throw new IllegalStateException("late failure");
            });
            assertTrue(failing.commit(current).rolledBack());
            try(var em=jpa.em())
            {
                assertEquals(0L,em.createQuery("select count(a) from Account a where a.chart.id=:id",Long.class).setParameter("id",target).getSingleResult());
                em.getTransaction().begin();
                em.find(ChartOfAccounts.class,target).setStatus(ChartStatus.RETIRED);
                Company other=new Company();
                other.setCode("OTHER");
                other.setDisplayName("Other");
                em.persist(other);
                em.getTransaction().commit();
            }
            assertThrows(IllegalStateException.class,() -> json.preview(request));
            long foreign=new ChartAdminService(jpa,() -> "OTHER",null).save(new ChartCommand(null,"Foreign","1",false,null)).id();
            assertThrows(CompanyOwnershipException.class,() -> json.preview(request(file,foreign)));
            assertThrows(IllegalArgumentException.class,() -> json.preview(request(file,Long.MAX_VALUE)));
            assertThrows(IllegalArgumentException.class,() -> new CoaImportRequest(file,CoaImportMode.CREATE_NEW_CHART,"New","1",Map.of(),true,null,target));
        }
    }

    private long draft(Jpa jpa,String name)
    {
        return new ChartAdminService(jpa,() -> "DEFAULT",null).save(new ChartCommand(null,name,"1",false,null)).id();
    }

    private CoaImportRequest request(Path file,long id)
    {
        return new CoaImportRequest(file,CoaImportMode.MERGE_BY_CODE,"","",Map.of(),true,null,id);
    }

    private Path source() throws Exception
    {
        return Files.writeString(directory.resolve("chart.json"),"""
                {"format":"SCA-COA","version":"1.0","chart":{"name":"Source","chartVersion":"1","status":"DRAFT","currency":"USD"},
                "accounts":[{"code":"1000","name":"Imported checking","type":"BANK","subtype":"CASH","normalBalance":"DEBIT","posting":true,"active":true,"openingBalance":"0.00"}]}
                """);
    }
}
