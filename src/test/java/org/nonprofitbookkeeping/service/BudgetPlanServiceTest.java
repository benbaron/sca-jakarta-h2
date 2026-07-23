package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BudgetPlanServiceTest
{
    @Test
    public void draftLinesCanBeReplacedAndActivated(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-service")))
        {
            seedReferences(jpa);
            BudgetPlanService service = new BudgetPlanService(jpa);

            BudgetPlanView draft = service.createDraft(command("draft-1"));
            BudgetPlanView withLines = service.replaceDraftLines(draft.id(), List.of(
                    new BudgetLineCommand(1L, 1L, YearMonth.of(2026, 1), new BigDecimal("100.0000"), "January"),
                    new BudgetLineCommand(1L, null, null, new BigDecimal("1200.0000"), "Annual")));

            assertEquals(BudgetPlan.Status.DRAFT, withLines.status());
            assertEquals(2, withLines.lines().size());

            BudgetPlanView active = service.activate(draft.id());

            assertEquals(BudgetPlan.Status.ACTIVE, active.status());
            assertTrue(active.activatedAt() != null);
            assertEquals(active.id(), service.activeForFiscalYear(2026).orElseThrow().id());
        }
    }

    @Test
    public void activationArchivesPriorActiveVersion(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-activation")))
        {
            seedReferences(jpa);
            BudgetPlanService service = new BudgetPlanService(jpa);
            BudgetPlanView first = service.activate(service.createDraft(command("draft-1")).id());
            BudgetPlanView second = service.activate(service.createDraft(command("draft-2")).id());

            assertEquals(BudgetPlan.Status.ARCHIVED, service.load(first.id()).orElseThrow().status());
            assertEquals(BudgetPlan.Status.ACTIVE, service.load(second.id()).orElseThrow().status());
            assertEquals(second.id(), service.activeForFiscalYear(2026).orElseThrow().id());
        }
    }

    @Test
    public void onlyDraftBudgetsCanBeEditedAndDuplicateScopesAreRejected(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-validation")))
        {
            seedReferences(jpa);
            BudgetPlanService service = new BudgetPlanService(jpa);
            BudgetPlanView draft = service.createDraft(command("draft-1"));

            assertThrows(IllegalArgumentException.class, () -> service.replaceDraftLines(draft.id(), List.of(
                    new BudgetLineCommand(1L, 1L, null, BigDecimal.ONE, ""),
                    new BudgetLineCommand(1L, 1L, null, BigDecimal.TEN, ""))));

            service.activate(draft.id());
            assertThrows(IllegalStateException.class, () -> service.replaceDraftLines(draft.id(), List.of()));
        }
    }

    @Test
    public void activeVarianceCombinesBudgetAndActualRows(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-variance")))
        {
            seedReferences(jpa);
            seedActual(jpa);
            BudgetPlanService service = new BudgetPlanService(jpa);
            BudgetPlanView draft = service.createDraft(command("draft-1"));
            service.replaceDraftLines(draft.id(), List.of(
                    new BudgetLineCommand(1L, 1L, null, new BigDecimal("100.0000"), "")));
            service.activate(draft.id());

            List<BudgetVarianceView> rows = service.activeVariance(LocalDate.of(2026, 6, 30));

            assertFalse(rows.isEmpty());
            BudgetVarianceView row = rows.get(0);
            assertEquals("PROGRAM", row.budgetCategoryCode());
            assertEquals(new BigDecimal("100.0000"), row.budget());
            assertEquals(new BigDecimal("50.0000"), row.actual());
            assertEquals(new BigDecimal("-50.0000"), row.variance());
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
                "Test budget");
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

    private static void seedActual(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, company_id, name, version, status) VALUES (1, 1, 'Test', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (1, 1, '1000', 'Checking', 'BANK', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (2, 1, '5000', 'Expense', 'EXPENSE', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn (id, company_id, txn_date, memo, status) VALUES (1, 1, DATE '2026-02-10', 'Expense', 'ENTERED')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (1, 1, 1, 1, -50.0000)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, budget_category_id, amount_signed) VALUES (2, 1, 2, 1, 1, 50.0000)").executeUpdate();
            em.getTransaction().commit();
        }
    }
}
