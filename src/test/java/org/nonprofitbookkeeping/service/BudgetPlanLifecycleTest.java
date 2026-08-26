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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

            IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.archive(active.id()));
            assertEquals("Only draft budget versions can be archived explicitly", error.getMessage());
            assertEquals(BudgetPlan.Status.ACTIVE, service.load(active.id()).orElseThrow().status());
        }
    }

    @Test
    void archiveAndActivationSerializeSoOnlyOneCanConsumeDraft(@TempDir Path tempDir) throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-archive-activate-race")))
        {
            seedReferences(jpa);
            BudgetPlanService service = new BudgetPlanService(jpa);
            BudgetPlanView draft = service.createDraft(command("archive-activate-race"));
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try
            {
                Future<BudgetPlanView> archive = pool.submit(() ->
                {
                    start.await();
                    return service.archive(draft.id());
                });
                Future<BudgetPlanView> activate = pool.submit(() ->
                {
                    start.await();
                    return service.activate(draft.id());
                });
                start.countDown();

                int successes = 0;
                int lifecycleRejections = 0;
                for (Future<BudgetPlanView> operation : List.of(archive, activate))
                {
                    try
                    {
                        operation.get();
                        successes++;
                    }
                    catch (ExecutionException ex)
                    {
                        assertTrue(ex.getCause() instanceof IllegalStateException);
                        lifecycleRejections++;
                    }
                }

                assertEquals(1, successes);
                assertEquals(1, lifecycleRejections);
                BudgetPlan.Status finalStatus = service.load(draft.id()).orElseThrow().status();
                assertTrue(finalStatus == BudgetPlan.Status.ACTIVE || finalStatus == BudgetPlan.Status.ARCHIVED);
            }
            finally
            {
                pool.shutdownNow();
            }
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
