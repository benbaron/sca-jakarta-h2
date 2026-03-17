package org.nonprofitbookkeeping.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AccountAdminServiceIntegrationTest component.
 */
public class AccountAdminServiceIntegrationTest
{
    @Test
    public void upsert_createsThenUpdatesAccountWithSubtypeAndParent() throws Exception
    {
        Path db = Files.createTempFile("coa-admin-it", ".mv.db");
        runMigrations(db);

        Jpa jpa = new Jpa(db);
        try
        {
            seedActiveChart(jpa);

            AccountAdminService service = new AccountAdminService(jpa);
            Account parent = service.upsert("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT, AccountSubtype.CASH, null, true);
            Account child = service.upsert("1100", "Accounts Receivable", AccountType.ASSET, NormalBalance.DEBIT, AccountSubtype.RECEIVABLE, "1000", true);

            assertNotNull(parent.getId());
            assertNotNull(child.getId());
            assertEquals(AccountSubtype.RECEIVABLE, child.getSubtype());
            assertEquals("1000", child.getParent().getCode());

            Account updated = service.upsert("1100", "Accounts Receivable - Current", AccountType.ASSET, NormalBalance.DEBIT, AccountSubtype.RECEIVABLE, null, false);
            assertEquals(child.getId(), updated.getId());
            assertEquals("Accounts Receivable - Current", updated.getName());
            assertEquals(false, updated.isActive());
            assertNull(updated.getParent());
        }
        finally
        {
            jpa.close();
        }
    }

    @Test
    public void upsert_rejectsMissingParentCodeInChart() throws Exception
    {
        Path db = Files.createTempFile("coa-admin-it", ".mv.db");
        runMigrations(db);

        Jpa jpa = new Jpa(db);
        try
        {
            seedActiveChart(jpa);
            AccountAdminService service = new AccountAdminService(jpa);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.upsert("1200", "Prepaid", AccountType.ASSET, NormalBalance.DEBIT, AccountSubtype.PREPAID, "9999", true));
            assertEquals("Parent account code does not exist in active chart: 9999.", ex.getMessage());
        }
        finally
        {
            jpa.close();
        }
    }

    private static void seedActiveChart(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setName("Default Chart");
            chart.setVersion("v1");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            em.getTransaction().commit();
        }
    }

    private static void runMigrations(Path databaseFile)
    {
        String raw = databaseFile.toString();
        String normalized = raw.endsWith(".mv.db") ? raw.substring(0, raw.length() - 6) : raw;
        String jdbc = "jdbc:h2:file:" + normalized + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        Flyway.configure().dataSource(jdbc, "sa", "").load().migrate();
    }
}
