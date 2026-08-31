package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BudgetCategoryAuthorizationIntegrationTest
{
    @Test
    void mutationFailsClosedAndTracksRoleAndCompanySwitches(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-category-authorization")))
        {
            AtomicReference<Optional<AuthenticatedUserSession>> current =
                    new AtomicReference<>(Optional.of(session("DEFAULT", ReservedSecurityRole.VIEWER)));
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get);
            BudgetCategoryAdminService categories =
                    new BudgetCategoryAdminService(jpa, () -> "DEFAULT", guard);

            assertThrows(AuthorizationException.class,
                    () -> categories.upsert("OPS", "Operations", true));
            assertEquals(0L, categoryCount(jpa, "OPS"));

            current.set(Optional.of(session("DEFAULT", ReservedSecurityRole.ACCOUNTANT)));
            categories.upsert("OPS", "Operations", true);
            assertEquals(1L, categoryCount(jpa, "OPS"));

            current.set(Optional.of(session("DEFAULT", ReservedSecurityRole.VIEWER)));
            assertThrows(AuthorizationException.class,
                    () -> categories.upsert("OPS", "Viewer Rewrite", true));
            assertEquals("Operations", categoryName(jpa, "OPS"));

            current.set(Optional.of(session("OTHER", ReservedSecurityRole.ACCOUNTANT)));
            assertThrows(AuthorizationException.class,
                    () -> categories.upsert("OPS", "Wrong Company", true));
            assertEquals("Operations", categoryName(jpa, "OPS"));
        }
    }

    private static long categoryCount(Jpa jpa, String code)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(b) from BudgetCategory b where b.code = :code", Long.class)
                    .setParameter("code", code)
                    .getSingleResult();
        }
    }

    private static String categoryName(Jpa jpa, String code)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select b.name from BudgetCategory b where b.code = :code", String.class)
                    .setParameter("code", code)
                    .getSingleResult();
        }
    }

    private static AuthenticatedUserSession session(String companyCode, ReservedSecurityRole role)
    {
        Instant now = Instant.parse("2026-08-30T22:00:00Z");
        return new AuthenticatedUserSession(8L, "operator", "Operator", companyCode,
                Set.of(role), now, now);
    }
}
