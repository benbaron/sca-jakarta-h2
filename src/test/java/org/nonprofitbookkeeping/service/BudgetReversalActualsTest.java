package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A01: real entry/correction commands must reconcile budget actuals to the ledger. */
class BudgetReversalActualsTest
{
    @ParameterizedTest
    @CsvSource({"false,2026-02-20", "false,2026-03-05", "true,2026-02-20", "true,2026-03-05"})
    void reversalPreservesOriginalUntilItsOwnDate(boolean income, LocalDate reversalDate, @TempDir Path dir)
    {
        try (Jpa jpa = new Jpa(dir.resolve("reversal")))
        {
            BudgetPlanService budget = setup(jpa, 1, LocalDate.of(2026, 2, 1));
            TransactionEntryService entry = new TransactionEntryService(jpa);
            long id = entry.enter(command(LocalDate.of(2026, 2, 10), income, "100", 1L, 1L)).id();
            String original = income ? "-100" : "100";
            assertActual(jpa, budget, LocalDate.of(2026, 2, 28), original);

            new TransactionCorrectionService(jpa).reverse(id, reversalDate, "tester", "A01", false);

            assertActual(jpa, budget, LocalDate.of(2026, 2, 9), "0");
            assertActual(jpa, budget, reversalDate.minusDays(1), original);
            assertActual(jpa, budget, reversalDate, "0");
            assertActual(jpa, budget, LocalDate.of(2026, 2, 28),
                    reversalDate.getMonthValue() == 3 ? original : "0");
            assertActual(jpa, budget, LocalDate.of(2026, 3, 31), "0");
        }
    }

    @ParameterizedTest
    @CsvSource({"false,60", "true,-60"})
    void replacementAndRefundRetainSignsAndAllocations(boolean income, String expected, @TempDir Path dir)
    {
        try (Jpa jpa = new Jpa(dir.resolve("replacement")))
        {
            BudgetPlanService budget = setup(jpa, 1, LocalDate.of(2026, 2, 1));
            TransactionEntryService entry = new TransactionEntryService(jpa);
            long id = entry.enter(command(LocalDate.of(2026, 2, 10), income, "100", 1L, 1L)).id();
            var correction = new TransactionCorrectionService(jpa).reverse(
                    id, LocalDate.of(2026, 2, 20), "tester", "replace amount", true);
            entry.update(correction.replacementTransactionId(),
                    command(LocalDate.of(2026, 2, 20), income, "80", 1L, 1L));
            assertActual(jpa, budget, LocalDate.of(2026, 2, 20), income ? "-80" : "80");
            entry.enter(command(LocalDate.of(2026, 2, 21), income, "-20", 1L, 1L));

            // These rows must not contaminate the selected category/fund.
            entry.enter(command(LocalDate.of(2026, 2, 22), income, "7", 2L, 1L));
            entry.enter(command(LocalDate.of(2026, 2, 22), income, "9", 1L, 2L));
            assertEquals(new BigDecimal(expected).setScale(4), actual(budget, LocalDate.of(2026, 2, 28), 1L, "PROGRAM"));
            assertEquals(new BigDecimal(income ? "-7" : "7").setScale(4),
                    actual(budget, LocalDate.of(2026, 2, 28), 2L, "PROGRAM"));
            assertEquals(new BigDecimal(income ? "-9" : "9").setScale(4),
                    actual(budget, LocalDate.of(2026, 2, 28), 1L, "OTHER"));
            assertReports(jpa, budget, LocalDate.of(2026, 2, 28), new BigDecimal(income ? "-69" : "69"));
        }
    }

    @Test
    void unclassifiedAmountsAreVisibleAndReverseOnTheirOwnDates(@TempDir Path dir)
    {
        try (Jpa jpa = new Jpa(dir.resolve("unclassified")))
        {
            BudgetPlanService budget = setup(jpa, 1, LocalDate.of(2026, 2, 1));
            long id = new TransactionEntryService(jpa)
                    .enter(command(LocalDate.of(2026, 2, 10), false, "25", 1L, null)).id();
            new TransactionCorrectionService(jpa).reverse(
                    id, LocalDate.of(2026, 3, 5), "tester", "unclassified", false);
            var rows = budget.activeVariance(LocalDate.of(2026, 2, 28));
            var unclassified = rows.stream().filter(row -> row.budgetCategoryCode().isEmpty()).findFirst().orElseThrow();
            assertEquals("Unclassified (no budget category)", unclassified.budgetCategoryName());
            assertEquals(0, unclassified.budget().signum());
            assertEquals(new BigDecimal("25.0000"), unclassified.actual());
            assertReports(jpa, budget, LocalDate.of(2026, 2, 28), unclassified.actual());
            assertEquals(new BigDecimal("0.0000"), actual(budget, LocalDate.of(2026, 2, 28), 1L, "PROGRAM"));
            assertEquals(new BigDecimal("0.0000"), actual(budget, LocalDate.of(2026, 3, 31), 1L, ""));
        }
    }

    @Test
    void reversalAcrossFiscalBoundaryDoesNotErasePriorYear(@TempDir Path dir)
    {
        try (Jpa jpa = new Jpa(dir.resolve("fiscal")))
        {
            BudgetPlanService budget = setup(jpa, 7, LocalDate.of(2026, 6, 1));
            long id = new TransactionEntryService(jpa)
                    .enter(command(LocalDate.of(2026, 6, 10), false, "100", 1L, 1L)).id();
            new TransactionCorrectionService(jpa).reverse(
                    id, LocalDate.of(2026, 7, 5), "tester", "next fiscal year", false);
            assertActual(jpa, budget, LocalDate.of(2026, 6, 30), "100");
            activate(budget, LocalDate.of(2026, 7, 1));
            assertActual(jpa, budget, LocalDate.of(2026, 7, 31), "-100");
        }
    }

    private static BudgetPlanService setup(Jpa jpa, int fiscalMonth, LocalDate period)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("UPDATE company SET fiscal_year_start_month = ?1 WHERE id = 1")
                    .setParameter(1, fiscalMonth).executeUpdate();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, company_id, name, version, status) VALUES (1, 1, 'Test', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("UPDATE company SET active_chart_of_accounts_id = 1 WHERE id = 1").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) VALUES (1, 1, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (2, 1, '5000', 'Expense', 'EXPENSE', 'DEBIT'), (3, 1, '4000', 'Income', 'INCOME', 'CREDIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, company_id, code, name, fund_type) VALUES (1, 1, 'GENERAL', 'General', 'UNRESTRICTED'), (2, 1, 'SECOND', 'Second', 'UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO budget_category (id, company_id, code, name, is_active) VALUES (1, 1, 'PROGRAM', 'Program', TRUE), (2, 1, 'OTHER', 'Other', TRUE)").executeUpdate();
            em.createNativeQuery("INSERT INTO activity (id, company_id, code, name, is_active) VALUES (1, 1, 'EVENT', 'Event', TRUE)").executeUpdate();
            em.getTransaction().commit();
        }
        BudgetPlanService service = new BudgetPlanService(jpa);
        activate(service, period);
        return service;
    }

    private static void activate(BudgetPlanService budget, LocalDate period)
    {
        var draft = budget.createDraft(budget.fiscalRange(period));
        budget.replaceDraftLines(draft.id(), List.of(
                new BudgetLineCommand(1L, 1L, null, new BigDecimal("1000"), "A01")));
        budget.activate(draft.id());
    }

    private static TransactionCommand command(LocalDate date, boolean income, String value, Long fund, Long category)
    {
        BigDecimal amount = new BigDecimal(value);
        BigDecimal debit = amount.max(BigDecimal.ZERO);
        BigDecimal credit = amount.negate().max(BigDecimal.ZERO);
        return new TransactionCommand(date, null, "A01", null, List.of(
                new TransactionLineCommand(income ? 3L : 2L, fund, category, 1L, null,
                        income ? credit : debit, income ? debit : credit, false, "Classified"),
                new TransactionLineCommand(1L, fund, null, null, null,
                        income ? debit : credit, income ? credit : debit, false, "Bank")));
    }

    private static BigDecimal actual(BudgetPlanService budget, LocalDate cutoff, Long fund, String category)
    {
        return budget.activeVariance(cutoff).stream()
                .filter(row -> row.fundId().orElse(-1L).equals(fund) && row.budgetCategoryCode().equals(category))
                .map(BudgetVarianceView::actual).reduce(BigDecimal.ZERO.setScale(4), BigDecimal::add);
    }

    private static void assertActual(Jpa jpa, BudgetPlanService budget, LocalDate cutoff, String expected)
    {
        BigDecimal amount = new BigDecimal(expected).setScale(4);
        assertEquals(amount, actual(budget, cutoff, 1L, "PROGRAM"), "Budget at " + cutoff);
        assertReports(jpa, budget, cutoff, amount);
    }

    private static void assertReports(Jpa jpa, BudgetPlanService budget, LocalDate cutoff, BigDecimal amount)
    {
        LocalDate start = budget.fiscalRange(cutoff).fiscalYearStart();
        FinancialReportService reports = new FinancialReportService(jpa, () -> "DEFAULT");
        var statement = reports.incomeStatement(start, cutoff, "GENERAL");
        assertEquals(0, amount.compareTo(statement.totalExpense().subtract(statement.totalIncome())), "Income Statement");
        BigDecimal gl = reports.generalLedgerDetail(start, cutoff, "GENERAL", 100).stream()
                .filter(row -> row.accountCode().equals("5000") || row.accountCode().equals("4000"))
                .map(row -> row.debit().subtract(row.credit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, amount.compareTo(gl), "General Ledger");
        var event = new EventAccountingQueryService(jpa, () -> "DEFAULT")
                .workspace(1L, start, cutoff, 1L).summary();
        assertEquals(0, amount.compareTo(event.expenses().subtract(event.income())), "Event/fund scope");
    }
}
