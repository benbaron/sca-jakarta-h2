package org.nonprofitbookkeeping.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Migrated-H2 acceptance coverage for P16-S2 atomic accepted-row COA CSV commit. */
public class CoaCsvImportServiceTest
{
    @Test
    public void commitsParentBeforeChildWithIdentitiesAndOneAuditThenRecommitIsIdempotent() throws Exception
    {
        Path db = database("coa-csv-atomic-success");
        runMigrations(db);
        Jpa jpa = new Jpa(db);
        try
        {
            seedCompany(jpa, "ALPHA");
            Path csv = csv("""
                    code,name,account_type,normal_balance,parent_code
                    1100,Accounts Receivable,ASSET,DEBIT,1000
                    1000,Cash,ASSET,DEBIT,
                    """);
            CoaCsvImportService service = new CoaCsvImportService(jpa, () -> "ALPHA");

            CoaCsvImportService.CoaCsvBatchPreview preview = service.preview(csv);
            assertFalse(preview.hasBlockingErrors());
            CoaCsvImportService.CoaCsvBatchCommitResult first = service.commit(preview.confirmedCopy(), "tester");

            assertTrue(first.committed());
            assertEquals(2, first.createdCount());
            assertEquals(0, first.updatedCount());
            assertEquals(0, first.skippedCount());
            try (var em = jpa.em())
            {
                Account child = em.createQuery("from Account a where a.code = '1100'", Account.class).getSingleResult();
                assertNotNull(child.getParent());
                assertEquals("1000", child.getParent().getCode());
                assertEquals(3L, em.createQuery(
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'COA_CSV'", Long.class)
                        .getSingleResult());
                assertEquals(1L, em.createQuery(
                        "select count(a) from AuditEvent a where a.actionType = 'COA_CSV_IMPORT'", Long.class)
                        .getSingleResult());
            }

            CoaCsvImportService.CoaCsvBatchPreview secondPreview = service.preview(csv);
            CoaCsvImportService.CoaCsvBatchCommitResult second = service.commit(
                    secondPreview.confirmedCopy(), "tester");
            assertTrue(second.committed());
            assertEquals(0, second.createdCount());
            assertEquals(0, second.updatedCount());
            assertEquals(2, second.skippedCount());
            try (var em = jpa.em())
            {
                assertEquals(3L, em.createQuery(
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'COA_CSV'", Long.class)
                        .getSingleResult());
                assertEquals(1L, em.createQuery(
                        "select count(a) from AuditEvent a where a.actionType = 'COA_CSV_IMPORT'", Long.class)
                        .getSingleResult());
            }
        }
        finally
        {
            jpa.close();
        }
    }

    @Test
    public void lateFailureRollsBackAccountsIdentitiesAndAuditAndRestartSeesNothing() throws Exception
    {
        Path db = database("coa-csv-atomic-rollback");
        runMigrations(db);
        Jpa jpa = new Jpa(db);
        try
        {
            seedCompany(jpa, "ALPHA");
            Path csv = csv("""
                    code,name,account_type,normal_balance,parent_code
                    1000,Cash,ASSET,DEBIT,
                    1100,Accounts Receivable,ASSET,DEBIT,1000
                    """);
            CoaCsvImportService service = new CoaCsvImportService(jpa, () -> "ALPHA", writes ->
            {
                if (writes == 1)
                {
                    throw new IllegalStateException("injected late row failure");
                }
            });

            CoaCsvImportService.CoaCsvBatchCommitResult result = service.commit(
                    service.preview(csv).confirmedCopy(), "tester");

            assertFalse(result.committed());
            assertTrue(result.rolledBack());
            assertEquals(0, result.createdCount());
            assertEquals(0, result.updatedCount());
            assertEquals(0, result.skippedCount());
            assertTrue(result.errors().get(0).contains("injected late row failure"));
            assertNoImportFacts(jpa);
        }
        finally
        {
            jpa.close();
        }

        Jpa reopened = new Jpa(db);
        try
        {
            assertNoImportFacts(reopened);
        }
        finally
        {
            reopened.close();
        }
    }

    @Test
    public void sourceDriftRequiresNewPreviewAndWritesNothing() throws Exception
    {
        Path db = database("coa-csv-source-drift");
        runMigrations(db);
        Jpa jpa = new Jpa(db);
        try
        {
            seedCompany(jpa, "ALPHA");
            Path csv = csv("""
                    code,name,account_type,normal_balance,parent_code
                    1000,Cash,ASSET,DEBIT,
                    """);
            CoaCsvImportService service = new CoaCsvImportService(jpa, () -> "ALPHA");
            CoaCsvImportService.CoaCsvBatchPreview preview = service.preview(csv);
            Files.writeString(csv, """
                    code,name,account_type,normal_balance,parent_code
                    1000,Cash Changed,ASSET,DEBIT,
                    """);

            CoaCsvImportService.CoaCsvBatchCommitResult result = service.commit(
                    preview.confirmedCopy(), "tester");

            assertFalse(result.committed());
            assertFalse(result.rolledBack());
            assertEquals("source", result.errorPath());
            assertNoImportFacts(jpa);
        }
        finally
        {
            jpa.close();
        }
    }

    @Test
    public void targetDriftRequiresNewPreviewWithoutRollingBackExternalChange() throws Exception
    {
        Path db = database("coa-csv-target-drift");
        runMigrations(db);
        Jpa jpa = new Jpa(db);
        try
        {
            seedCompany(jpa, "ALPHA");
            Path csv = csv("""
                    code,name,account_type,normal_balance,parent_code
                    1000,Cash,ASSET,DEBIT,
                    """);
            CoaCsvImportService service = new CoaCsvImportService(jpa, () -> "ALPHA");
            CoaCsvImportService.CoaCsvBatchPreview preview = service.preview(csv);
            new AccountAdminService(jpa, () -> "ALPHA").upsert(
                    "9999", "External Change", AccountType.EXPENSE, NormalBalance.DEBIT, null, null, true);

            CoaCsvImportService.CoaCsvBatchCommitResult result = service.commit(
                    preview.confirmedCopy(), "tester");

            assertFalse(result.committed());
            assertTrue(result.rolledBack());
            assertEquals("preview-drift", result.errorPath());
            try (var em = jpa.em())
            {
                assertEquals(1L, em.createQuery("select count(a) from Account a", Long.class).getSingleResult());
                assertEquals(1L, em.createQuery(
                        "select count(a) from Account a where a.code = '9999'", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery(
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'COA_CSV'", Long.class)
                        .getSingleResult());
            }
        }
        finally
        {
            jpa.close();
        }
    }

    @Test
    public void companySwitchAfterPreviewIsRejectedWithoutCrossCompanyWrites() throws Exception
    {
        Path db = database("coa-csv-company-drift");
        runMigrations(db);
        Jpa jpa = new Jpa(db);
        try
        {
            seedCompany(jpa, "ALPHA");
            seedCompany(jpa, "BETA");
            AtomicReference<String> active = new AtomicReference<>("ALPHA");
            CoaCsvImportService service = new CoaCsvImportService(jpa, active::get);
            Path csv = csv("""
                    code,name,account_type,normal_balance,parent_code
                    1000,Cash,ASSET,DEBIT,
                    """);
            CoaCsvImportService.CoaCsvBatchPreview preview = service.preview(csv);
            active.set("BETA");

            CoaCsvImportService.CoaCsvBatchCommitResult result = service.commit(
                    preview.confirmedCopy(), "tester");

            assertFalse(result.committed());
            assertFalse(result.rolledBack());
            assertEquals("company", result.errorPath());
            assertNoImportFacts(jpa);
        }
        finally
        {
            jpa.close();
        }
    }

    @Test
    public void duplicateAcceptedCodesAndInactiveParentAreBlockingPreviewErrors() throws Exception
    {
        Path db = database("coa-csv-preview-blockers");
        runMigrations(db);
        Jpa jpa = new Jpa(db);
        try
        {
            seedCompany(jpa, "ALPHA");
            new AccountAdminService(jpa, () -> "ALPHA").upsert(
                    "0900", "Inactive Parent", AccountType.ASSET, NormalBalance.DEBIT, null, null, false);
            CoaCsvImportService service = new CoaCsvImportService(jpa, () -> "ALPHA");
            Path csv = csv("""
                    code,name,account_type,normal_balance,parent_code
                    1000,Cash,ASSET,DEBIT,
                    1000,Cash Duplicate,ASSET,DEBIT,
                    1100,Child,ASSET,DEBIT,0900
                    """);

            CoaCsvImportService.CoaCsvBatchPreview preview = service.preview(csv);

            assertTrue(preview.hasBlockingErrors());
            assertTrue(preview.blockingErrors().stream().anyMatch(value -> value.contains("Duplicate account code")));
            assertTrue(preview.blockingErrors().stream().anyMatch(value -> value.contains("parent account is inactive")));
        }
        finally
        {
            jpa.close();
        }
    }

    private static void assertNoImportFacts(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            assertEquals(0L, em.createQuery(
                    "select count(a) from Account a where a.code in ('1000', '1100')", Long.class)
                    .getSingleResult());
            assertEquals(0L, em.createQuery(
                    "select count(i) from InterchangeIdentity i where i.formatCode = 'COA_CSV'", Long.class)
                    .getSingleResult());
            assertEquals(0L, em.createQuery(
                    "select count(a) from AuditEvent a where a.actionType = 'COA_CSV_IMPORT'", Long.class)
                    .getSingleResult());
        }
    }

    private static Path csv(String value) throws Exception
    {
        Path path = Files.createTempFile("coa-csv-p16-s2", ".csv");
        Files.writeString(path, value);
        return path;
    }

    private static Path database(String prefix) throws Exception
    {
        return Files.createTempFile(prefix, ".mv.db");
    }

    private static void seedCompany(Jpa jpa, String code)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode(code);
            company.setDisplayName(code + " Company");
            company.setActive(true);
            em.persist(company);
            em.flush();

            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName(code + " Chart");
            chart.setVersion("v1");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            em.flush();
            company.setActiveChartOfAccounts(chart);
            em.getTransaction().commit();
        }
    }

    private static void runMigrations(Path databaseFile)
    {
        String raw = databaseFile.toString();
        String normalized = raw.endsWith(".mv.db") ? raw.substring(0, raw.length() - 6) : raw;
        String jdbc = "jdbc:h2:file:" + normalized
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        Flyway.configure().dataSource(jdbc, "sa", "").load().migrate();
    }
}
