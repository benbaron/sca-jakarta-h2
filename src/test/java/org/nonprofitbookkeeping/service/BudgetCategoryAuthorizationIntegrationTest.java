package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void stableIdSaveAuditsLifecycleDatesAndRejectsDuplicateOrInvalidUpdates(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-category-maintenance")))
        {
            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(
                    Optional.of(session("DEFAULT", ReservedSecurityRole.ACCOUNTANT)));
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get);
            BudgetCategoryAdminService categories = new BudgetCategoryAdminService(jpa, () -> "DEFAULT", guard);
            BudgetCategoryView created = categories.save(new BudgetCategoryCommand(null, "OPS", "Operations", true,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "Annual operations"));
            assertEquals("OPS", created.code());
            assertEquals(1L, auditCount(jpa, "BUDGET_CATEGORY_CREATED"));
            BudgetCategoryView updated = categories.save(new BudgetCategoryCommand(created.id(), "OPS", "Retired Operations", false,
                    created.effectiveFrom(), created.effectiveTo(), "Retained history"));
            assertEquals("Retired Operations", updated.name());
            assertEquals(false, updated.active());
            assertEquals(1L, auditCount(jpa, "BUDGET_CATEGORY_UPDATED"));
            assertThrows(IllegalArgumentException.class, () -> categories.save(new BudgetCategoryCommand(null,
                    "ops", "Duplicate", true, null, null, "")));
            assertThrows(IllegalArgumentException.class, () -> categories.save(new BudgetCategoryCommand(created.id(),
                    "OPS2", "Invalid dates", true, LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1), "")));
        }
    }

    private static long auditCount(Jpa jpa, String action)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(a) from AuditEvent a where a.actionType = :action", Long.class)
                    .setParameter("action", action).getSingleResult();
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
