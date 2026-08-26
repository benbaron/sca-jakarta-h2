package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetPlanLifecycleTest
{
    @Test
    void archivedDraftIsRetainedWithLinesAndListedAsHistory(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-archive-draft")))
        {
            seedReferences(jpa);
            BudgetPlanService service = new BudgetPlanService(jpa);
            BudgetPlanView draft = service.createDraft(command("draft-archive"));
            service.replaceDraftLines(draft.id(), List.of(
                    new BudgetLineCommand(1L, null, null, new BigDecimal("450.0000"), "Retained")));

            BudgetPlanView archived = service.archive(draft.id());

            assertEquals(draft.id(), archived.id());
            assertEquals(BudgetPlan.Status.ARCHIVED, archived.status());
            assertTrue(archived.archivedAt() != null);
            assertEquals(1, archived.lines().size());
            assertEquals(new BigDecimal("450.0000"), archived.lines().get(0).amount());
            assertTrue(service.editableAndActiveForFiscalYear(2026).stream()
                    .noneMatch(plan -> plan.id().equals(draft.id())));
            assertTrue(service.versionsForFiscalYear(2026).stream()
                    .anyMatch(plan -> plan.id().equals(draft.id())
                            && plan.status() == BudgetPlan.Status.ARCHIVED));
        }
    }

    @Test
    void activeBudgetCannotBeManuallyArchived(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-archive-active")))
        {
            seedReferences(jpa);
            BudgetPlanService service = new BudgetPlanService(jpa);
            BudgetPlanView active = service.activate(service.createDraft(command("active-archive-guard")).id());

            assertThrows(IllegalStateException.class, () -> service.archive(active.id()));
            assertEquals(BudgetPlan.Status.ACTIVE, service.load(active.id()).orElseThrow().status());
        }
    }

    private static BudgetPlanCommand command(String version)
    {
        return new BudgetPlanCommand(
                "FY2026 " + version,
                2026,
                version,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "Lifecycle test budget");
    }

    private static void seedReferences(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO fund (id, company_id, code, name, fund_type) VALUES (1, 1, 'OPERATING', 'Operating', 'UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO budget_category (id, company_id, code, name, is_active) VALUES (1, 1, 'PROGRAM', 'Program Services', TRUE)").executeUpdate();
            em.getTransaction().commit();
        }
    }
}
