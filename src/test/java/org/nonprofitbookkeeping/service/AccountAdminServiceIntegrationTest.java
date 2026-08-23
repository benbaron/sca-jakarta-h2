package org.nonprofitbookkeeping.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
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
    public void upsertSeparatesAssetBankFunctionFromCashClassification() throws Exception
    {
        Path db = Files.createTempFile("coa-admin-bank-classification-it", ".mv.db");
        runMigrations(db);

        Jpa jpa = new Jpa(db);
        try
        {
            seedActiveChart(jpa);
            AccountAdminService service = new AccountAdminService(jpa);

            Account cashBank = service.upsert(
                    "1000", "Operating Checking", AccountType.ASSET, AccountFunction.BANK,
                    NormalBalance.DEBIT, AccountSubtype.CASH, null, true);
            Account nonCashBank = service.upsert(
                    "1050", "Restricted Deposit", AccountType.ASSET, AccountFunction.BANK,
                    NormalBalance.DEBIT, AccountSubtype.OTHER_ASSET, null, true);

            assertEquals(AccountType.ASSET, cashBank.getAccountType());
            assertEquals(AccountFunction.BANK, cashBank.getAccountFunction());
            assertEquals(AccountSubtype.CASH, cashBank.getSubtype());
            assertEquals(AccountFunction.BANK, nonCashBank.getAccountFunction());
            assertEquals(AccountSubtype.OTHER_ASSET, nonCashBank.getSubtype());

            IllegalArgumentException liabilityBank = assertThrows(IllegalArgumentException.class,
                    () -> service.upsert(
                            "2000", "Invalid Bank", AccountType.LIABILITY, AccountFunction.BANK,
                            NormalBalance.CREDIT, null, null, true));
            assertEquals("BANK function requires an ASSET account with a DEBIT normal balance.",
                    liabilityBank.getMessage());
        }
        finally
        {
            jpa.close();
        }
    }

    @Test
    public void unconfiguredBankFunctionCanBeCleared() throws Exception
    {
        Path db = Files.createTempFile("coa-admin-clear-bank-function-it", ".mv.db");
        runMigrations(db);

        Jpa jpa = new Jpa(db);
        try
        {
            seedActiveChart(jpa);
            AccountAdminService service = new AccountAdminService(jpa);
            service.upsert(
                    "1010", "Operating Checking", AccountType.ASSET, AccountFunction.BANK,
                    NormalBalance.DEBIT, AccountSubtype.CASH, null, true);

            Account cleared = service.upsert(
                    "1010", "Operating Checking", AccountType.ASSET, null,
                    NormalBalance.DEBIT, AccountSubtype.CASH, null, true);

            assertNull(cleared.getAccountFunction());
            assertEquals(AccountType.ASSET, cleared.getAccountType());
            assertEquals(AccountSubtype.CASH, cleared.getSubtype());
        }
        finally
        {
            jpa.close();
        }
    }

    @Test
    public void configuredBankCannotBeDeclassifiedAndFailedUpdateLeavesPersistedStateUnchanged() throws Exception
    {
        Path db = Files.createTempFile("coa-admin-configured-bank-guard-it", ".mv.db");
        runMigrations(db);

        Jpa jpa = new Jpa(db);
        try
        {
            seedActiveChart(jpa);
            AccountAdminService service = new AccountAdminService(jpa);
            Account bank = service.upsert(
                    "1010", "Operating Checking", AccountType.ASSET, AccountFunction.BANK,
                    NormalBalance.DEBIT, AccountSubtype.CASH, null, true);
            configureBankAccount(jpa, bank.getId(), "DEFAULT");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.upsert(
                            "1010", "Operating Checking", AccountType.ASSET, null,
                            NormalBalance.DEBIT, AccountSubtype.CASH, null, true));
            assertEquals(
                    "This account is configured for banking. Remove or change its Banking configuration before changing its ASSET / BANK / DEBIT classification.",
                    ex.getMessage());

            try (var em = jpa.em())
            {
                Account persisted = em.createQuery(
                                "from Account a where a.code = '1010'",
                                Account.class)
                        .getSingleResult();
                assertEquals(AccountType.ASSET, persisted.getAccountType());
                assertEquals(AccountFunction.BANK, persisted.getAccountFunction());
                assertEquals(NormalBalance.DEBIT, persisted.getNormalBalance());
                assertEquals(AccountSubtype.CASH, persisted.getSubtype());
            }

            Account nonCash = service.upsert(
                    "1010", "Operating Checking - Restricted", AccountType.ASSET, AccountFunction.BANK,
                    NormalBalance.DEBIT, AccountSubtype.OTHER_ASSET, null, true);
            assertEquals(AccountFunction.BANK, nonCash.getAccountFunction());
            assertEquals(AccountSubtype.OTHER_ASSET, nonCash.getSubtype());
            assertEquals("Operating Checking - Restricted", nonCash.getName());
        }
        finally
        {
            jpa.close();
        }
    }

    @Test
    public void foreignCompanyBankReferenceDoesNotBlockCurrentCompanyEdit() throws Exception
    {
        Path db = Files.createTempFile("coa-admin-company-isolation-it", ".mv.db");
        runMigrations(db);

        Jpa jpa = new Jpa(db);
        try
        {
            seedActiveChart(jpa);
            AccountAdminService service = new AccountAdminService(jpa);
            Account bank = service.upsert(
                    "1010", "Operating Checking", AccountType.ASSET, AccountFunction.BANK,
                    NormalBalance.DEBIT, AccountSubtype.CASH, null, true);

            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                Company other = new Company();
                other.setCode("OTHER_COMPANY");
                other.setDisplayName("Other Company");
                other.setDefaultCurrency("USD");
                em.persist(other);
                em.flush();

                Account managedBank = em.find(Account.class, bank.getId());
                CompanyBankAccount foreignReference = new CompanyBankAccount();
                foreignReference.setCompany(other);
                foreignReference.setAccount(managedBank);
                foreignReference.setName("Foreign bank reference");
                em.persist(foreignReference);
                em.getTransaction().commit();
            }

            Account cleared = service.upsert(
                    "1010", "Operating Checking", AccountType.ASSET, null,
                    NormalBalance.DEBIT, AccountSubtype.CASH, null, true);
            assertNull(cleared.getAccountFunction());
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

    private static void configureBankAccount(Jpa jpa, long accountId, String companyCode)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = em.createQuery(
                            "from Company c where c.code = :code",
                            Company.class)
                    .setParameter("code", companyCode)
                    .getSingleResult();
            Account account = em.find(Account.class, accountId);
            CompanyBankAccount configured = new CompanyBankAccount();
            configured.setCompany(company);
            configured.setAccount(account);
            configured.setName("Configured " + account.getName());
            em.persist(configured);
            em.getTransaction().commit();
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
