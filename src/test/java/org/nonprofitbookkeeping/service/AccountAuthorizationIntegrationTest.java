package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
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
            seedActiveChart(jpa);
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

    private static void seedActiveChart(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
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

    private static AuthenticatedUserSession session(String companyCode, ReservedSecurityRole role)
    {
        Instant now = Instant.parse("2026-08-31T01:00:00Z");
        return new AuthenticatedUserSession(9L, "operator", "Operator", companyCode,
                Set.of(role), now, now);
    }
}
