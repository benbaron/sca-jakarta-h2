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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    @Test
    public void eventAndFundScopesAreMutuallyExclusiveAndNeverDoubleCountActuals(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-event-fund-scopes")))
        {
            seedReferences(jpa);
            seedScopedActuals(jpa);
            BudgetPlanService service = new BudgetPlanService(jpa);
            BudgetPlanView draft = service.createDraft(command("scoped"));
            service.replaceDraftLines(draft.id(), List.of(
                    new BudgetLineCommand(1L, 1L, 1L, null, new BigDecimal("100.0000"), "Event A / Operating"),
                    new BudgetLineCommand(1L, 2L, 1L, null, new BigDecimal("200.0000"), "Event A / Restricted"),
                    new BudgetLineCommand(1L, 1L, 2L, null, new BigDecimal("300.0000"), "Event B / Operating"),
                    new BudgetLineCommand(1L, null, null, null, new BigDecimal("400.0000"), "General / all funds")));
            service.activate(draft.id());

            List<BudgetVarianceView> rows = service.activeVariance(LocalDate.of(2026, 12, 31));

            assertEquals(4, rows.size());
            assertScopedRow(rows, 1L, 1L, "100.0000", "40.0000");
            assertScopedRow(rows, 2L, 1L, "200.0000", "50.0000");
            assertScopedRow(rows, 1L, 2L, "300.0000", "60.0000");
            assertScopedRow(rows, null, null, "400.0000", "150.0000");
            assertEquals(new BigDecimal("300.0000"), rows.stream()
                    .map(BudgetVarianceView::actual).reduce(BigDecimal.ZERO, BigDecimal::add));

            BudgetPlanView revision = service.createRevision(draft.id());
            assertTrue(revision.lines().stream().anyMatch(line -> Long.valueOf(2L).equals(line.activityId())));
        }
    }

    @Test
    public void allFundsAndFundSpecificLinesCannotOverlapWithinEventScope(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-overlap")))
        {
            seedReferences(jpa);
            BudgetPlanService service = new BudgetPlanService(jpa);
            BudgetPlanView draft = service.createDraft(command("overlap"));

            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> service.replaceDraftLines(draft.id(), List.of(
                            new BudgetLineCommand(1L, null, 1L, null, BigDecimal.TEN, "All funds"),
                            new BudgetLineCommand(1L, 1L, 1L, null, BigDecimal.ONE, "Specific fund"))));

            assertTrue(failure.getMessage().contains("cannot overlap"));
            assertTrue(service.load(draft.id()).orElseThrow().lines().isEmpty());
        }
    }



    @Test
    public void concurrentActivationLeavesExactlyOneActiveVersion(@TempDir Path tempDir) throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-concurrent-activation")))
        {
            seedReferences(jpa);
            BudgetPlanService service = new BudgetPlanService(jpa);
            BudgetPlanView first = service.createDraft(command("concurrent-1"));
            BudgetPlanView second = service.createDraft(command("concurrent-2"));
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try
            {
                Future<BudgetPlanView> one = pool.submit(() -> {
                    start.await();
                    return service.activate(first.id());
                });
                Future<BudgetPlanView> two = pool.submit(() -> {
                    start.await();
                    return service.activate(second.id());
                });
                start.countDown();
                one.get();
                two.get();
            }
            finally
            {
                pool.shutdownNow();
            }

            List<BudgetPlanView> visible = service.editableAndActiveForFiscalYear(2026);
            assertEquals(1, visible.stream().filter(plan -> plan.status() == BudgetPlan.Status.ACTIVE).count());
            assertEquals(1, List.of(
                    service.load(first.id()).orElseThrow(),
                    service.load(second.id()).orElseThrow()).stream()
                    .filter(plan -> plan.status() == BudgetPlan.Status.ARCHIVED).count());
        }
    }

    @Test
    public void explicitDraftReloadPreservesStableIdentity(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-stable-draft")))
        {
            seedReferences(jpa);
            setFiscalStart(jpa, 7, 1);
            BudgetPlanService service = new BudgetPlanService(jpa);
            FiscalPeriodRange range = service.fiscalRange(LocalDate.of(2027, 2, 15));

            BudgetPlanView draft = service.createDraft(range);
            BudgetPlanView saved = service.replaceDraftLines(draft.id(), List.of(
                    new BudgetLineCommand(1L, null, null, new BigDecimal("125.0000"), "Saved draft")));

            assertEquals(draft.id(), saved.id());
            List<BudgetPlanView> versions = service.editableAndActiveForFiscalYear(2026);
            assertEquals(1, versions.size());
            assertEquals(draft.id(), versions.get(0).id());
            assertEquals(new BigDecimal("125.0000"), versions.get(0).lines().get(0).amount());
            assertEquals(LocalDate.of(2026, 7, 1), versions.get(0).periodStart());
            assertEquals(LocalDate.of(2027, 6, 30), versions.get(0).periodEnd());
        }
    }

    @Test
    public void revisionCopiesSelectedActivePlanAndActivationTargetsSelectedDraft(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-revision")))
        {
            seedReferences(jpa);
            BudgetPlanService service = new BudgetPlanService(jpa);
            BudgetPlanView firstDraft = service.createDraft(command("draft-1"));
            service.replaceDraftLines(firstDraft.id(), List.of(
                    new BudgetLineCommand(1L, 1L, null, new BigDecimal("300.0000"), "Original")));
            BudgetPlanView firstActive = service.activate(firstDraft.id());

            BudgetPlanView revision = service.createRevision(firstActive.id());

            assertEquals(BudgetPlan.Status.DRAFT, revision.status());
            assertEquals(firstActive.fiscalYear(), revision.fiscalYear());
            assertEquals(firstActive.periodStart(), revision.periodStart());
            assertEquals(firstActive.periodEnd(), revision.periodEnd());
            assertEquals(1, revision.lines().size());
            assertEquals(new BigDecimal("300.0000"), revision.lines().get(0).amount());
            assertEquals("Original", revision.lines().get(0).notes());

            BudgetPlanView activatedRevision = service.activate(revision.id());
            assertEquals(revision.id(), activatedRevision.id());
            assertEquals(BudgetPlan.Status.ARCHIVED, service.load(firstActive.id()).orElseThrow().status());
            assertEquals(revision.id(), service.activeForFiscalYear(2026).orElseThrow().id());
        }
    }

    @Test
    public void nonJanuaryFiscalVarianceUsesFiscalStartAndSelectedPeriodEnd(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-non-january")))
        {
            seedReferences(jpa);
            setFiscalStart(jpa, 7, 1);
            seedActualOn(jpa, 1L, LocalDate.of(2026, 6, 30), new BigDecimal("10.0000"));
            seedActualOn(jpa, 2L, LocalDate.of(2026, 8, 10), new BigDecimal("50.0000"));
            seedActualOn(jpa, 3L, LocalDate.of(2027, 3, 1), new BigDecimal("25.0000"));
            BudgetPlanService service = new BudgetPlanService(jpa);
            FiscalPeriodRange range = service.fiscalRange(LocalDate.of(2027, 2, 15));
            BudgetPlanView draft = service.createDraft(range);
            service.replaceDraftLines(draft.id(), List.of(
                    new BudgetLineCommand(1L, 1L, null, new BigDecimal("100.0000"), "Annual")));
            service.activate(draft.id());

            BudgetVarianceView row = service.activeVariance(range).get(0);

            assertEquals(new BigDecimal("100.0000"), row.budget());
            assertEquals(new BigDecimal("75.0000"), row.actual());
            assertEquals(new BigDecimal("-25.0000"), row.variance());
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

    private static void setFiscalStart(Jpa jpa, int month, int day)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("UPDATE company SET fiscal_year_start_month = ?1, fiscal_year_start_day = ?2 WHERE id = 1")
                    .setParameter(1, month)
                    .setParameter(2, day)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void seedActualOn(Jpa jpa, long txnId, LocalDate date, BigDecimal amount)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            long splitBase = txnId * 10L;
            long chartCount = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM chart_of_accounts WHERE id = 1").getSingleResult()).longValue();
            if (chartCount == 0L)
            {
                em.createNativeQuery("INSERT INTO chart_of_accounts (id, company_id, name, version, status) VALUES (1, 1, 'Test', '1', 'ACTIVE')").executeUpdate();
                em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) VALUES (1, 1, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')").executeUpdate();
                em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (2, 1, '5000', 'Expense', 'EXPENSE', 'DEBIT')").executeUpdate();
            }
            em.createNativeQuery("INSERT INTO txn (id, company_id, txn_date, memo, status) VALUES (?1, 1, ?2, 'Expense', 'ENTERED')")
                    .setParameter(1, txnId)
                    .setParameter(2, date)
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (?1, ?2, 1, 1, ?3)")
                    .setParameter(1, splitBase + 1)
                    .setParameter(2, txnId)
                    .setParameter(3, amount.negate())
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, budget_category_id, amount_signed) VALUES (?1, ?2, 2, 1, 1, ?3)")
                    .setParameter(1, splitBase + 2)
                    .setParameter(2, txnId)
                    .setParameter(3, amount)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void seedReferences(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO fund (id, company_id, code, name, fund_type) VALUES (1, 1, 'OPERATING', 'Operating', 'UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, company_id, code, name, fund_type) VALUES (2, 1, 'RESTRICTED', 'Restricted', 'TEMP_RESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO budget_category (id, company_id, code, name, is_active) VALUES (1, 1, 'PROGRAM', 'Program Services', TRUE)").executeUpdate();
            em.createNativeQuery("INSERT INTO activity (id, company_id, code, name, is_active) VALUES (1, 1, 'EVENT-A', 'Event A', TRUE)").executeUpdate();
            em.createNativeQuery("INSERT INTO activity (id, company_id, code, name, is_active) VALUES (2, 1, 'EVENT-B', 'Event B', TRUE)").executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void seedScopedActuals(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, company_id, name, version, status) VALUES (1, 1, 'Test', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) VALUES (1, 1, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (2, 1, '5000', 'Expense', 'EXPENSE', 'DEBIT')").executeUpdate();
            scopedActual(em, 1L, 1L, 1L, "40.0000");
            scopedActual(em, 2L, 2L, 1L, "50.0000");
            scopedActual(em, 3L, 1L, 2L, "60.0000");
            scopedActual(em, 4L, 1L, null, "70.0000");
            scopedActual(em, 5L, 2L, null, "80.0000");
            em.getTransaction().commit();
        }
    }

    private static void scopedActual(EntityManager em, long txnId, long fundId, Long activityId, String amount)
    {
        em.createNativeQuery("INSERT INTO txn (id, company_id, txn_date, memo, status) VALUES (?1, 1, DATE '2026-04-01', 'Scoped expense', 'ENTERED')")
                .setParameter(1, txnId).executeUpdate();
        long base = txnId * 10L;
        em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, activity_id, amount_signed) VALUES (?1, ?2, 1, ?3, ?4, ?5)")
                .setParameter(1, base + 1).setParameter(2, txnId).setParameter(3, fundId)
                .setParameter(4, activityId).setParameter(5, new BigDecimal(amount).negate()).executeUpdate();
        em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, activity_id, budget_category_id, amount_signed) VALUES (?1, ?2, 2, ?3, ?4, 1, ?5)")
                .setParameter(1, base + 2).setParameter(2, txnId).setParameter(3, fundId)
                .setParameter(4, activityId).setParameter(5, new BigDecimal(amount)).executeUpdate();
    }

    private static void assertScopedRow(List<BudgetVarianceView> rows, Long fundId, Long activityId,
                                        String budget, String actual)
    {
        BudgetVarianceView row = rows.stream()
                .filter(candidate -> java.util.Objects.equals(candidate.fundId().orElse(null), fundId)
                        && java.util.Objects.equals(candidate.activityId().orElse(null), activityId))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal(budget), row.budget());
        assertEquals(new BigDecimal(actual), row.actual());
    }

    private static void seedActual(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, company_id, name, version, status) VALUES (1, 1, 'Test', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) VALUES (1, 1, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (2, 1, '5000', 'Expense', 'EXPENSE', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn (id, company_id, txn_date, memo, status) VALUES (1, 1, DATE '2026-02-10', 'Expense', 'ENTERED')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (1, 1, 1, 1, -50.0000)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, budget_category_id, amount_signed) VALUES (2, 1, 2, 1, 1, 50.0000)").executeUpdate();
            em.getTransaction().commit();
        }
    }
}
