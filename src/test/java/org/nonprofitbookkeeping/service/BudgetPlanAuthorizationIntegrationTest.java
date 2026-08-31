package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BudgetPlanAuthorizationIntegrationTest
{
    @Test
    void viewerCannotUseAnyServiceOwnedBudgetMutation(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-plan-viewer-authorization")))
        {
            seedReferences(jpa);
            BudgetPlanService setup = new BudgetPlanService(jpa);
            BudgetPlanView editable = setup.createDraft(command("editable"));
            BudgetPlanView archivable = setup.createDraft(command("archivable"));
            BudgetPlanView active = setup.activate(setup.createDraft(command("active")).id());
            FiscalPeriodRange range = setup.fiscalRange(LocalDate.of(2026, 6, 1));
            long planCount = planCount(jpa);

            BudgetPlanService budgets = guardedBudgets(
                    jpa,
                    () -> Optional.of(session("DEFAULT", ReservedSecurityRole.VIEWER)));

            assertThrows(AuthorizationException.class,
                    () -> budgets.createDraft(command("viewer-command")));
            assertThrows(AuthorizationException.class,
                    () -> budgets.createDraft(range));
            assertThrows(AuthorizationException.class,
                    () -> budgets.replaceDraftLines(editable.id(), List.of(
                            line(new BigDecimal("25.0000"), "Viewer line"))));
            assertThrows(AuthorizationException.class,
                    () -> budgets.activate(archivable.id()));
            assertThrows(AuthorizationException.class,
                    () -> budgets.archive(archivable.id()));
            assertThrows(AuthorizationException.class,
                    () -> budgets.createRevision(active.id()));

            assertEquals(planCount, planCount(jpa));
            assertEquals(0L, lineCount(jpa, editable.id()));
            assertEquals(BudgetPlan.Status.DRAFT, status(jpa, archivable.id()));
            assertEquals(BudgetPlan.Status.ACTIVE, status(jpa, active.id()));
        }
    }

    @Test
    void mutationTracksRoleCompanySwitchesAndMultiRoleUnion(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-plan-role-switching")))
        {
            seedReferences(jpa);
            AtomicReference<Optional<AuthenticatedUserSession>> current =
                    new AtomicReference<>(Optional.of(session("DEFAULT", ReservedSecurityRole.VIEWER)));
            BudgetPlanService budgets = guardedBudgets(jpa, current::get);

            assertThrows(AuthorizationException.class,
                    () -> budgets.createDraft(command("viewer")));

            current.set(Optional.of(session("DEFAULT", ReservedSecurityRole.ACCOUNTANT)));
            BudgetPlanView draft = budgets.createDraft(command("accountant"));
            budgets.replaceDraftLines(draft.id(), List.of(
                    line(new BigDecimal("100.0000"), "Accountant line")));
            assertEquals(1L, lineCount(jpa, draft.id()));

            current.set(Optional.of(session("DEFAULT", ReservedSecurityRole.VIEWER)));
            assertThrows(AuthorizationException.class, () -> budgets.archive(draft.id()));
            assertEquals(BudgetPlan.Status.DRAFT, status(jpa, draft.id()));

            current.set(Optional.of(session("DEFAULT", ReservedSecurityRole.MANAGER)));
            BudgetPlanView active = budgets.activate(draft.id());
            assertEquals(BudgetPlan.Status.ACTIVE, active.status());

            current.set(Optional.of(session("DEFAULT", ReservedSecurityRole.ADMIN)));
            BudgetPlanView revision = budgets.createRevision(active.id());
            assertEquals(BudgetPlan.Status.DRAFT, revision.status());
            assertEquals(1, revision.lines().size());

            current.set(Optional.of(session(
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.ACCOUNTANT))));
            BudgetPlanView archived = budgets.archive(revision.id());
            assertEquals(BudgetPlan.Status.ARCHIVED, archived.status());

            current.set(Optional.of(session("OTHER", ReservedSecurityRole.ACCOUNTANT)));
            assertThrows(AuthorizationException.class,
                    () -> budgets.createDraft(command("wrong-company")));
            assertEquals(2L, planCount(jpa));
        }
    }

    @Test
    void authorizationDoesNotBypassBudgetDomainProtections(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-plan-domain-protections")))
        {
            seedReferences(jpa);
            BudgetPlanService budgets = guardedBudgets(
                    jpa,
                    () -> Optional.of(session("DEFAULT", ReservedSecurityRole.ACCOUNTANT)));
            BudgetPlanView draft = budgets.createDraft(command("protected"));

            assertThrows(IllegalArgumentException.class,
                    () -> budgets.replaceDraftLines(draft.id(), List.of(
                            line(BigDecimal.ONE, "One"),
                            line(BigDecimal.TEN, "Duplicate scope"))));
            assertEquals(0L, lineCount(jpa, draft.id()));

            BudgetPlanView active = budgets.activate(draft.id());
            assertThrows(IllegalStateException.class, () -> budgets.archive(active.id()));
            assertThrows(IllegalStateException.class,
                    () -> budgets.replaceDraftLines(active.id(), List.of()));
            assertEquals(BudgetPlan.Status.ACTIVE, status(jpa, active.id()));
        }
    }

    @Test
    void callerOwnedImportHelperRemainsUsableInsideAuthorizedOuterTransaction(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-plan-caller-owned-import")))
        {
            seedReferences(jpa);
            AuthorizationGuard outerGuard = new AuthorizationGuard(
                    jpa,
                    () -> Optional.of(session("DEFAULT", ReservedSecurityRole.ACCOUNTANT)));
            outerGuard.require(
                    ApplicationPermission.BOOKKEEPING_WRITE,
                    "DEFAULT",
                    "commit governed budget import");

            BudgetPlanService budgets = new BudgetPlanService(
                    jpa,
                    () -> "DEFAULT",
                    new AuthorizationGuard(jpa, () -> Optional.empty()));

            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company company = company(em);
                BudgetPlan imported = budgets.createForImport(
                        em,
                        company,
                        command("imported"),
                        BudgetPlan.Status.DRAFT,
                        List.of(line(new BigDecimal("75.0000"), "Imported line")));
                em.flush();
                assertEquals(BudgetPlan.Status.DRAFT, imported.getStatus());
                em.getTransaction().commit();
            }

            assertEquals(1L, planCount(jpa));
            assertEquals(1L, planCount(jpa, "imported"));
        }
    }

    private static BudgetPlanService guardedBudgets(
            Jpa jpa,
            java.util.function.Supplier<Optional<AuthenticatedUserSession>> currentSession)
    {
        return new BudgetPlanService(
                jpa,
                () -> "DEFAULT",
                new AuthorizationGuard(jpa, currentSession));
    }

    private static BudgetPlanCommand command(String version)
    {
        return new BudgetPlanCommand(
                "FY2026 " + version,
                2026,
                version,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "Authorization test budget");
    }

    private static BudgetLineCommand line(BigDecimal amount, String notes)
    {
        return new BudgetLineCommand(
                1L,
                1L,
                YearMonth.of(2026, 1),
                amount,
                notes);
    }

    private static void seedReferences(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery(
                    "INSERT INTO fund (id, company_id, code, name, fund_type) VALUES (1, 1, 'OPERATING', 'Operating', 'UNRESTRICTED')")
                    .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO budget_category (id, company_id, code, name, is_active) VALUES (1, 1, 'PROGRAM', 'Program Services', TRUE)")
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static Company company(EntityManager em)
    {
        return em.createQuery("from Company c where c.code = 'DEFAULT'", Company.class)
                .getSingleResult();
    }

    private static long planCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(p) from BudgetPlan p", Long.class)
                    .getSingleResult();
        }
    }

    private static long planCount(Jpa jpa, String versionCode)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "select count(p) from BudgetPlan p where p.versionCode = :version",
                            Long.class)
                    .setParameter("version", versionCode)
                    .getSingleResult();
        }
    }

    private static long lineCount(Jpa jpa, long planId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "select count(l) from BudgetLine l where l.budgetPlan.id = :planId",
                            Long.class)
                    .setParameter("planId", planId)
                    .getSingleResult();
        }
    }

    private static BudgetPlan.Status status(Jpa jpa, long planId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "select p.status from BudgetPlan p where p.id = :planId",
                            BudgetPlan.Status.class)
                    .setParameter("planId", planId)
                    .getSingleResult();
        }
    }

    private static AuthenticatedUserSession session(String companyCode, ReservedSecurityRole role)
    {
        return session(companyCode, Set.of(role));
    }

    private static AuthenticatedUserSession session(String companyCode, Set<ReservedSecurityRole> roles)
    {
        Instant now = Instant.parse("2026-08-31T02:15:00Z");
        return new AuthenticatedUserSession(
                8L,
                "operator",
                "Operator",
                companyCode,
                roles,
                now,
                now);
    }
}
