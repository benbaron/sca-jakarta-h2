package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountAuthorizationIntegrationTest
{
    @Test
    void mutationFailsClosedAndTracksRoleAndCompanySwitches(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("account-authorization")))
        {
            seedActiveChart(jpa, "DEFAULT");
            AtomicReference<Optional<AuthenticatedUserSession>> current =
                    new AtomicReference<>(Optional.of(session("DEFAULT", ReservedSecurityRole.VIEWER)));
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get);
            AccountAdminService accounts = new AccountAdminService(jpa, () -> "DEFAULT", guard);

            assertThrows(AuthorizationException.class,
                    () -> accounts.upsert("6100", "Operations Expense", AccountType.EXPENSE,
                            NormalBalance.DEBIT, null, null, true));
            assertEquals(0L, accountCount(jpa, "6100"));

            current.set(Optional.of(session("DEFAULT", ReservedSecurityRole.ACCOUNTANT)));
            Account created = accounts.upsert("6100", "Operations Expense", AccountType.EXPENSE,
                    NormalBalance.DEBIT, null, null, true);
            assertEquals(1L, accountCount(jpa, "6100"));

            current.set(Optional.of(session("DEFAULT", ReservedSecurityRole.VIEWER)));
            assertThrows(AuthorizationException.class,
                    () -> accounts.save(new AccountCommand(
                            created.getId(),
                            "6100",
                            "Viewer Rewrite",
                            AccountType.EXPENSE,
                            null,
                            NormalBalance.DEBIT,
                            null,
                            null,
                            true)));
            assertEquals("Operations Expense", accountName(jpa, "6100"));

            current.set(Optional.of(session("OTHER", ReservedSecurityRole.ACCOUNTANT)));
            assertThrows(AuthorizationException.class,
                    () -> accounts.upsert("6100", "Wrong Company", AccountType.EXPENSE,
                            NormalBalance.DEBIT, null, null, true));
            assertEquals("Operations Expense", accountName(jpa, "6100"));
        }
    }

    @Test
    void managerAdminAndMultiRoleUnionAllowBookkeepingMutation(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("account-role-matrix")))
        {
            seedActiveChart(jpa, "DEFAULT");
            AtomicReference<Optional<AuthenticatedUserSession>> current =
                    new AtomicReference<>(Optional.of(session("DEFAULT", ReservedSecurityRole.MANAGER)));
            AccountAdminService accounts = new AccountAdminService(
                    jpa,
                    () -> "DEFAULT",
                    new AuthorizationGuard(jpa, current::get));

            accounts.upsert("6200", "Manager Expense", AccountType.EXPENSE,
                    NormalBalance.DEBIT, null, null, true);

            current.set(Optional.of(session("DEFAULT", ReservedSecurityRole.ADMIN)));
            accounts.upsert("6201", "Admin Expense", AccountType.EXPENSE,
                    NormalBalance.DEBIT, null, null, true);

            current.set(Optional.of(session(
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.ACCOUNTANT))));
            accounts.upsert("6202", "Union Expense", AccountType.EXPENSE,
                    NormalBalance.DEBIT, null, null, true);

            assertEquals(1L, accountCount(jpa, "6200"));
            assertEquals(1L, accountCount(jpa, "6201"));
            assertEquals(1L, accountCount(jpa, "6202"));
        }
    }

    @Test
    void authorizationDoesNotBypassBankClassificationValidation(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("account-bank-validation")))
        {
            seedActiveChart(jpa, "DEFAULT");
            AccountAdminService accounts = guardedAccounts(
                    jpa,
                    session("DEFAULT", ReservedSecurityRole.ACCOUNTANT));

            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> accounts.upsert(
                            "2000",
                            "Invalid Bank",
                            AccountType.LIABILITY,
                            AccountFunction.BANK,
                            NormalBalance.CREDIT,
                            null,
                            null,
                            true));

            assertEquals(
                    "BANK function requires an ASSET account with a DEBIT normal balance.",
                    failure.getMessage());
            assertEquals(0L, accountCount(jpa, "2000"));
        }
    }

    @Test
    void authorizationDoesNotBypassCompanyAndChartOwnershipValidation(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("account-ownership-validation")))
        {
            seedActiveChart(jpa, "DEFAULT");
            ForeignFixture foreign = seedForeignAccount(jpa);
            AccountAdminService accounts = guardedAccounts(
                    jpa,
                    session("DEFAULT", ReservedSecurityRole.ACCOUNTANT));

            assertThrows(
                    CompanyOwnershipException.class,
                    () -> accounts.save(new AccountCommand(
                            foreign.accountId(),
                            "7100",
                            "Foreign Rewrite",
                            AccountType.EXPENSE,
                            null,
                            NormalBalance.DEBIT,
                            null,
                            null,
                            true)));
            assertEquals("Foreign Expense", accountName(jpa, foreign.accountId()));

            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company company = company(em, "DEFAULT");
                ChartOfAccounts foreignChart = em.find(ChartOfAccounts.class, foreign.chartId());
                assertThrows(
                        CompanyOwnershipException.class,
                        () -> accounts.upsert(
                                em,
                                company,
                                foreignChart,
                                "7200",
                                "Cross-company Account",
                                AccountType.EXPENSE,
                                NormalBalance.DEBIT,
                                null,
                                null,
                                true));
                em.getTransaction().rollback();
            }
            assertEquals(0L, accountCount(jpa, "7200"));
        }
    }

    @Test
    void callerOwnedAccountHelperRemainsUsableInsideAuthorizedOuterTransaction(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("account-caller-owned-transaction")))
        {
            long chartId = seedActiveChart(jpa, "DEFAULT");
            AuthorizationGuard outerGuard = new AuthorizationGuard(
                    jpa,
                    () -> Optional.of(session("DEFAULT", ReservedSecurityRole.ACCOUNTANT)));
            outerGuard.require(
                    ApplicationPermission.BOOKKEEPING_WRITE,
                    "DEFAULT",
                    "commit governed Chart of Accounts import");

            AccountAdminService accounts = new AccountAdminService(
                    jpa,
                    () -> "DEFAULT",
                    new AuthorizationGuard(jpa, () -> Optional.empty()));

            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company company = company(em, "DEFAULT");
                ChartOfAccounts chart = em.find(ChartOfAccounts.class, chartId);
                accounts.upsert(
                        em,
                        company,
                        chart,
                        "7300",
                        "Imported Account",
                        AccountType.EXPENSE,
                        NormalBalance.DEBIT,
                        null,
                        null,
                        true);
                em.getTransaction().commit();
            }

            assertEquals(1L, accountCount(jpa, "7300"));
            assertEquals("Imported Account", accountName(jpa, "7300"));
        }
    }

    private static AccountAdminService guardedAccounts(Jpa jpa, AuthenticatedUserSession session)
    {
        return new AccountAdminService(
                jpa,
                () -> "DEFAULT",
                new AuthorizationGuard(jpa, () -> Optional.of(session)));
    }

    private static long seedActiveChart(Jpa jpa, String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = company(em, companyCode);
            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName(companyCode + " Chart");
            chart.setVersion("v1");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            em.flush();
            company.setActiveChartOfAccounts(chart);
            em.getTransaction().commit();
            return chart.getId();
        }
    }

    private static ForeignFixture seedForeignAccount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();

            Company other = new Company();
            other.setCode("OTHER");
            other.setDisplayName("Other Company");
            other.setDefaultCurrency("USD");
            em.persist(other);

            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(other);
            chart.setName("Other Chart");
            chart.setVersion("v1");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            em.flush();
            other.setActiveChartOfAccounts(chart);

            Account account = new Account();
            account.setChart(chart);
            account.setCode("7100");
            account.setName("Foreign Expense");
            account.setAccountType(AccountType.EXPENSE);
            account.setNormalBalance(NormalBalance.DEBIT);
            account.setPosting(true);
            account.setActive(true);
            em.persist(account);
            em.flush();

            ForeignFixture fixture = new ForeignFixture(chart.getId(), account.getId());
            em.getTransaction().commit();
            return fixture;
        }
    }

    private static Company company(EntityManager em, String code)
    {
        return em.createQuery("from Company c where c.code = :code", Company.class)
                .setParameter("code", code)
                .getSingleResult();
    }

    private static long accountCount(Jpa jpa, String code)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(a) from Account a where a.code = :code", Long.class)
                    .setParameter("code", code)
                    .getSingleResult();
        }
    }

    private static String accountName(Jpa jpa, String code)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select a.name from Account a where a.code = :code", String.class)
                    .setParameter("code", code)
                    .getSingleResult();
        }
    }

    private static String accountName(Jpa jpa, long accountId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select a.name from Account a where a.id = :id", String.class)
                    .setParameter("id", accountId)
                    .getSingleResult();
        }
    }

    private static AuthenticatedUserSession session(String companyCode, ReservedSecurityRole role)
    {
        return session(companyCode, Set.of(role));
    }

    private static AuthenticatedUserSession session(
            String companyCode,
            Set<ReservedSecurityRole> roles)
    {
        Instant now = Instant.parse("2026-08-31T01:00:00Z");
        return new AuthenticatedUserSession(
                9L,
                "operator",
                "Operator",
                companyCode,
                roles,
                now,
                now);
    }

    private record ForeignFixture(long chartId, long accountId)
    {
    }
}
