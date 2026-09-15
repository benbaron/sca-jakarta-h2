package org.nonprofitbookkeeping.service.dashboard;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JpaDashboardQueryServiceTest
{
    @Test
    public void missingCompanyReturnsTruthfulEmptyProjection(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("dashboard-empty")))
        {
            DashboardSnapshot snapshot = new JpaDashboardQueryService(jpa)
                    .load("BARONY-RED", LocalDate.of(2026, 6, 1), 5);

            assertEquals(BigDecimal.ZERO, snapshot.bookCash());
            assertEquals(BigDecimal.ZERO, snapshot.yearToDateSurplus());
            assertTrue(snapshot.reconciledCash().isEmpty());
            assertTrue(snapshot.unreconciledDifference().isEmpty());
            assertTrue(snapshot.bankAccounts().isEmpty());
            assertTrue(snapshot.recentTransactions().isEmpty());
            assertFalse(snapshot.openItems().available());
            assertTrue(snapshot.reconciliations().isEmpty());
            assertTrue(snapshot.budgetActuals().isEmpty());
            assertEquals("BARONY-RED", snapshot.organization().code());
            assertEquals("UNCONFIGURED", snapshot.period().status());
            assertTrue(snapshot.monthlyResults().isEmpty());
        }
    }

    @Test
    public void loadRejectsMissingDateAndNonPositiveLimit(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("dashboard-validation")))
        {
            JpaDashboardQueryService service = new JpaDashboardQueryService(jpa);

            assertThrows(IllegalArgumentException.class, () -> service.load(null, 5));
            assertThrows(IllegalArgumentException.class, () ->
                    service.load(LocalDate.of(2026, 6, 1), 0));
        }
    }

    @Test
    public void projectionIsCompanyScopedFiscalAwareAndUsesCurrentAuthorities(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("dashboard-current-authority")))
        {
            seedTwoCompanies(jpa);

            DashboardSnapshot snapshot = new JpaDashboardQueryService(jpa)
                    .load("BARONY-RED", LocalDate.of(2026, 6, 1), 1);

            assertEquals(LocalDate.of(2026, 6, 30), snapshot.asOfDate());
            assertEquals(new BigDecimal("250.0000"), snapshot.bookCash());
            assertEquals(new BigDecimal("300.0000"), snapshot.reconciledCash().orElseThrow());
            assertEquals(new BigDecimal("-50.0000"), snapshot.unreconciledDifference().orElseThrow());
            assertEquals(new BigDecimal("250.0000"), snapshot.yearToDateSurplus());

            assertEquals(1, snapshot.bankAccounts().size());
            assertEquals("Checking", snapshot.bankAccounts().get(0).name());
            assertEquals(new BigDecimal("250.0000"), snapshot.bankAccounts().get(0).balance());

            assertEquals(1, snapshot.recentTransactions().size());
            DashboardSnapshot.RecentTransaction recent = snapshot.recentTransactions().get(0);
            assertEquals("Expense", recent.description());
            assertTrue(recent.accountSummary().contains("1000 Checking"));
            assertTrue(recent.accountSummary().contains("5000 Expense"));
            assertTrue(recent.fundSummary().contains("OPERATING Operating"));
            assertEquals(new BigDecimal("50.0000"), recent.debitTotal());
            assertEquals(new BigDecimal("50.0000"), recent.creditTotal());
            assertEquals(new BigDecimal("250.0000"), recent.runningBankBalance().orElseThrow());
            assertTrue(recent.affectsBank());
            assertTrue(recent.affectsBudget());

            assertEquals(new BigDecimal("250.0000"), snapshot.fundClassTotals().get("UNRESTRICTED"));
            assertFalse(snapshot.openItems().available());
            assertEquals(0L, snapshot.openItems().totalOpenItems());

            assertEquals(1, snapshot.reconciliations().size());
            DashboardSnapshot.ReconciliationStatus reconciliation = snapshot.reconciliations().get(0);
            assertEquals(LocalDate.of(2026, 6, 15), reconciliation.statementEndingOn());
            assertEquals("Operating Checking", reconciliation.bankAccount());
            assertEquals("UNRESOLVED", reconciliation.status());
            assertEquals(new BigDecimal("5.0000"), reconciliation.differenceAmount());

            assertEquals(1, snapshot.budgetActuals().size());
            DashboardSnapshot.BudgetActual budgetActual = snapshot.budgetActuals().get(0);
            assertEquals("PROGRAM", budgetActual.categoryCode());
            assertEquals(new BigDecimal("75.0000"), budgetActual.budget().orElseThrow());
            assertEquals(new BigDecimal("50.0000"), budgetActual.actual());

            assertEquals("BARONY-RED", snapshot.organization().code());
            assertEquals(2025, snapshot.period().fiscalYear().orElseThrow());
            assertEquals(12, snapshot.period().periodNumber().orElseThrow());
            assertEquals(LocalDate.of(2026, 6, 1), snapshot.period().startDate().orElseThrow());
            assertEquals(LocalDate.of(2026, 6, 30), snapshot.period().endDate().orElseThrow());
            assertEquals("CLOSED", snapshot.period().status());
            assertEquals(12, snapshot.monthlyResults().size());
            assertEquals(new BigDecimal("300.0000"), month(snapshot, 8));
            assertEquals(new BigDecimal("-50.0000"), month(snapshot, 2));
        }
    }

    @Test
    public void reversedTransactionsRemainVisibleButDoNotAffectDerivedIndicators(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("dashboard-reversed")))
        {
            seedTwoCompanies(jpa);
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("UPDATE txn SET status = 'REVERSED' WHERE id = 1001")
                        .executeUpdate();
                em.getTransaction().commit();
            }

            DashboardSnapshot snapshot = new JpaDashboardQueryService(jpa)
                    .load("BARONY-RED", LocalDate.of(2026, 6, 1), 10);

            assertEquals(new BigDecimal("-50.0000"), snapshot.bookCash());
            assertEquals(new BigDecimal("-50.0000"), snapshot.yearToDateSurplus());
            assertEquals(2, snapshot.recentTransactions().size());

            DashboardSnapshot.RecentTransaction reversed = snapshot.recentTransactions().stream()
                    .filter(row -> row.transactionId() == 1001L)
                    .findFirst()
                    .orElseThrow();
            assertFalse(reversed.affectsBank());
            assertFalse(reversed.affectsBudget());
        }
    }

    private static BigDecimal month(DashboardSnapshot snapshot, int month)
    {
        return snapshot.monthlyResults().stream()
                .filter(row -> row.month() == month)
                .findFirst()
                .orElseThrow()
                .surplus();
    }

    private static void seedTwoCompanies(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();

            em.createNativeQuery("""
                    INSERT INTO company
                        (id, code, display_name, fiscal_year_start_month, fiscal_year_start_day)
                    VALUES
                        (100, 'BARONY-RED', 'Barony Red', 7, 1),
                        (200, 'OTHER', 'Other Branch', 1, 1)
                    """).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO chart_of_accounts (id, company_id, name, version, status)
                    VALUES
                        (100, 100, 'Red Chart', '1', 'ACTIVE'),
                        (200, 200, 'Other Chart', '1', 'ACTIVE')
                    """).executeUpdate();
            em.createNativeQuery("UPDATE company SET active_chart_of_accounts_id = 100 WHERE id = 100")
                    .executeUpdate();
            em.createNativeQuery("UPDATE company SET active_chart_of_accounts_id = 200 WHERE id = 200")
                    .executeUpdate();

            em.createNativeQuery("""
                    INSERT INTO account
                        (id, chart_id, code, name, account_type, account_function, subtype, normal_balance)
                    VALUES
                        (1001, 100, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT'),
                        (1002, 100, '4000', 'Income', 'INCOME', NULL, NULL, 'CREDIT'),
                        (1003, 100, '5000', 'Expense', 'EXPENSE', NULL, NULL, 'DEBIT'),
                        (2001, 200, '1000', 'Other Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT'),
                        (2002, 200, '4000', 'Other Income', 'INCOME', NULL, NULL, 'CREDIT')
                    """).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO fund (id, company_id, code, name, fund_type)
                    VALUES
                        (1001, 100, 'OPERATING', 'Operating', 'UNRESTRICTED'),
                        (2001, 200, 'OTHER', 'Other Fund', 'UNRESTRICTED')
                    """).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO budget_category (id, company_id, code, name, is_active)
                    VALUES
                        (1001, 100, 'PROGRAM', 'Program Services', TRUE),
                        (2001, 200, 'OTHER', 'Other Category', TRUE)
                    """).executeUpdate();

            em.createNativeQuery("""
                    INSERT INTO txn (id, company_id, txn_date, memo, status)
                    VALUES
                        (1001, 100, DATE '2025-08-10', 'Income', 'ENTERED'),
                        (1002, 100, DATE '2026-02-10', 'Expense', 'ENTERED'),
                        (2001, 200, DATE '2026-03-01', 'Other income', 'ENTERED')
                    """).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO txn_split
                        (id, txn_id, account_id, fund_id, budget_category_id, amount_signed, bank_cleared, bank_cleared_on)
                    VALUES
                        (1001, 1001, 1001, 1001, NULL, 300.0000, TRUE, DATE '2025-08-31'),
                        (1002, 1001, 1002, 1001, NULL, -300.0000, FALSE, NULL),
                        (1003, 1002, 1001, 1001, NULL, -50.0000, FALSE, NULL),
                        (1004, 1002, 1003, 1001, 1001, 50.0000, FALSE, NULL),
                        (2001, 2001, 2001, 2001, NULL, 900.0000, TRUE, DATE '2026-03-31'),
                        (2002, 2001, 2002, 2001, NULL, -900.0000, FALSE, NULL)
                    """).executeUpdate();

            em.createNativeQuery("""
                    INSERT INTO company_bank_account
                        (id, company_id, name, account_type, account_id)
                    VALUES
                        (1001, 100, 'Operating Checking', 'CHECKING', 1001),
                        (2001, 200, 'Other Checking', 'CHECKING', 2001)
                    """).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO bank_reconciliation_session
                        (id, company_id, bank_account_id, statement_start_date, statement_end_date,
                         statement_ending_balance, status, difference_amount)
                    VALUES
                        (1001, 100, 1001, DATE '2026-06-01', DATE '2026-06-15', 250.0000, 'UNRESOLVED', 5.0000),
                        (2001, 200, 2001, DATE '2026-03-01', DATE '2026-03-31', 900.0000, 'FINALIZED', 0.0000)
                    """).executeUpdate();

            em.createNativeQuery("""
                    INSERT INTO budget_plan
                        (id, company_id, name, fiscal_year, version_code, status,
                         period_start, period_end, activated_at)
                    VALUES
                        (1001, 100, 'FY2025', 2025, 'active', 'ACTIVE',
                         DATE '2025-07-01', DATE '2026-06-30', CURRENT_TIMESTAMP),
                        (2001, 200, 'FY2026', 2026, 'active', 'ACTIVE',
                         DATE '2026-01-01', DATE '2026-12-31', CURRENT_TIMESTAMP)
                    """).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO budget_line
                        (id, budget_plan_id, budget_category_id, fund_id, period_month, amount)
                    VALUES
                        (1001, 1001, 1001, 1001, NULL, 75.0000),
                        (2001, 2001, 2001, 2001, NULL, 999.0000)
                    """).executeUpdate();

            em.createNativeQuery("""
                    INSERT INTO period_close_range
                        (id, company_id, company_code, start_date, end_date, range_kind, status, closed_by)
                    VALUES
                        (RANDOM_UUID(), 100, 'BARONY-RED', DATE '2026-06-01', DATE '2026-06-30',
                         'PERIOD', 'CLOSED', 'tester')
                    """).executeUpdate();

            em.createNativeQuery("""
                    INSERT INTO accounting_period
                        (id, company_id, fiscal_year, period_number, start_date, end_date, status)
                    VALUES
                        (1001, 100, 2026, 6, DATE '2026-06-01', DATE '2026-06-30', 'OPEN')
                    """).executeUpdate();

            em.createNativeQuery("""
                    INSERT INTO open_item_snapshot
                        (id, group_code, item_kind, item_ref, state, original_amount,
                         open_amount, last_updated_on, version)
                    VALUES
                        (RANDOM_UUID(), 'BARONY-RED', 'RECEIVABLE', 'LEGACY-AR', 'OPEN',
                         999.0000, 999.0000, DATE '2026-05-01', 0)
                    """).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO reconciliation_run
                        (id, group_code, statement_ending_on, bank_format,
                         imported_transaction_count, status, notes)
                    VALUES
                        (RANDOM_UUID(), 'BARONY-RED', DATE '2026-06-20', 'OFX',
                         99, 'COMPLETED', 'Legacy compatibility row')
                    """).executeUpdate();

            em.getTransaction().commit();
        }
    }
}
