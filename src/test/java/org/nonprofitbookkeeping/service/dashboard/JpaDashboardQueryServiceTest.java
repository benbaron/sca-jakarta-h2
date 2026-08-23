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
    public void emptyDatabaseReturnsZeroAndNoFictionalValues(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("dashboard-empty")))
        {
            DashboardSnapshot snapshot = new JpaDashboardQueryService(jpa)
                    .load("BARONY-RED", LocalDate.of(2026, 6, 30), 5);

            assertEquals(BigDecimal.ZERO, snapshot.bookCash());
            assertEquals(BigDecimal.ZERO, snapshot.yearToDateSurplus());
            assertTrue(snapshot.reconciledCash().isEmpty());
            assertTrue(snapshot.unreconciledDifference().isEmpty());
            assertTrue(snapshot.bankAccounts().isEmpty());
            assertTrue(snapshot.recentTransactions().isEmpty());
            assertEquals(0L, snapshot.openItems().totalOpenItems());
            assertEquals(BigDecimal.ZERO, snapshot.openItems().totalOpenAmount());
            assertTrue(snapshot.reconciliations().isEmpty());
            assertTrue(snapshot.budgetActuals().isEmpty());
            assertEquals("BARONY-RED", snapshot.organization().code());
            assertEquals("UNCONFIGURED", snapshot.period().status());
            assertEquals(6, snapshot.monthlyResults().size());
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
                    service.load(LocalDate.of(2026, 6, 30), 0));
        }
    }

    @Test
    public void populatedDatabaseProjectsReferenceDashboardFromRealData(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("dashboard-populated")))
        {
            seed(jpa);

            DashboardSnapshot snapshot = new JpaDashboardQueryService(jpa)
                    .load("BARONY-RED", LocalDate.of(2026, 6, 30), 1);

            assertEquals(new BigDecimal("250.0000"), snapshot.bookCash());
            assertEquals(new BigDecimal("250.0000"), snapshot.yearToDateSurplus());
            assertEquals(1, snapshot.bankAccounts().size());
            assertEquals("Checking", snapshot.bankAccounts().get(0).name());

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

            assertTrue(snapshot.fundClassTotals().containsKey("UNRESTRICTED"));
            assertEquals(1L, snapshot.openItems().countFor("RECEIVABLE"));
            assertEquals(new BigDecimal("75.0000"), snapshot.openItems().amountFor("RECEIVABLE"));
            assertEquals(1L, snapshot.openItems().totalOpenItems());
            assertEquals(new BigDecimal("75.0000"), snapshot.openItems().totalOpenAmount());

            assertEquals(1, snapshot.reconciliations().size());
            DashboardSnapshot.ReconciliationStatus reconciliation = snapshot.reconciliations().get(0);
            assertEquals(LocalDate.of(2026, 6, 15), reconciliation.statementEndingOn());
            assertEquals("COMPLETED", reconciliation.status());
            assertEquals(3, reconciliation.importedTransactionCount());

            assertEquals(1, snapshot.budgetActuals().size());
            DashboardSnapshot.BudgetActual budgetActual = snapshot.budgetActuals().get(0);
            assertEquals("PROGRAM", budgetActual.categoryCode());
            assertEquals(new BigDecimal("50.0000"), budgetActual.actual());
            assertTrue(budgetActual.budget().isEmpty());

            assertEquals("BARONY-RED", snapshot.organization().code());
            assertEquals("UNCONFIGURED", snapshot.period().status());
            assertEquals(new BigDecimal("300.0000"), snapshot.monthlyResults().get(0).surplus());
            assertEquals(new BigDecimal("-50.0000"), snapshot.monthlyResults().get(1).surplus());
        }
    }

    @Test
    public void bankFunctionNonCashIsOperationalButExcludedFromBookCash(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("dashboard-bank-noncash")))
        {
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) "
                        + "VALUES (10, 'Bank Classification', '1', 'ACTIVE')").executeUpdate();
                em.createNativeQuery("INSERT INTO account "
                        + "(id, chart_id, code, name, account_type, account_function, subtype, normal_balance) "
                        + "VALUES (10, 10, '1050', 'Restricted Deposit', 'ASSET', 'BANK', 'OTHER_ASSET', 'DEBIT')")
                        .executeUpdate();
                em.createNativeQuery("INSERT INTO account "
                        + "(id, chart_id, code, name, account_type, normal_balance) "
                        + "VALUES (11, 10, '3000', 'Net Assets', 'EQUITY', 'CREDIT')")
                        .executeUpdate();
                em.createNativeQuery("INSERT INTO fund (id, code, name, fund_type) "
                        + "VALUES (10, 'OPERATING', 'Operating', 'UNRESTRICTED')").executeUpdate();
                em.createNativeQuery("INSERT INTO txn (id, txn_date, memo, status) "
                        + "VALUES (10, DATE '2026-03-01', 'Restricted deposit', 'ENTERED')").executeUpdate();
                em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) "
                        + "VALUES (10, 10, 10, 10, 40.0000)").executeUpdate();
                em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) "
                        + "VALUES (11, 10, 11, 10, -40.0000)").executeUpdate();
                em.getTransaction().commit();
            }

            DashboardSnapshot snapshot = new JpaDashboardQueryService(jpa)
                    .load("", LocalDate.of(2026, 3, 31), 5);

            assertEquals(BigDecimal.ZERO, snapshot.bookCash());
            assertEquals(1, snapshot.bankAccounts().size());
            assertEquals("Restricted Deposit", snapshot.bankAccounts().get(0).name());
            assertEquals(new BigDecimal("40.0000"), snapshot.bankAccounts().get(0).balance());
            assertTrue(snapshot.recentTransactions().get(0).affectsBank());
        }
    }

    @Test
    public void reversedTransactionsAreExcludedFromDerivedIndicators(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("dashboard-reversed")))
        {
            seed(jpa);
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("UPDATE txn SET status = 'REVERSED' WHERE id = 1")
                        .executeUpdate();
                em.getTransaction().commit();
            }

            DashboardSnapshot snapshot = new JpaDashboardQueryService(jpa)
                    .load("BARONY-RED", LocalDate.of(2026, 6, 30), 10);

            assertEquals(new BigDecimal("-50.0000"), snapshot.bookCash());
            assertEquals(new BigDecimal("-50.0000"), snapshot.yearToDateSurplus());
            assertEquals(2, snapshot.recentTransactions().size());

            DashboardSnapshot.RecentTransaction reversed = snapshot.recentTransactions().stream()
                    .filter(row -> row.transactionId() == 1L)
                    .findFirst()
                    .orElseThrow();
            assertFalse(reversed.affectsBank());
            assertFalse(reversed.affectsBudget());
        }
    }

    @Test
    public void activeBudgetPlanSuppliesDashboardBudgetComparison(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("dashboard-budget")))
        {
            seed(jpa);
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("INSERT INTO budget_plan (id, name, fiscal_year, version_code, status, period_start, period_end, activated_at) VALUES (1, 'FY2026', 2026, 'active', 'ACTIVE', DATE '2026-01-01', DATE '2026-12-31', CURRENT_TIMESTAMP)").executeUpdate();
                em.createNativeQuery("INSERT INTO budget_line (id, budget_plan_id, budget_category_id, fund_id, period_month, amount) VALUES (1, 1, 1, NULL, NULL, 75.0000)").executeUpdate();
                em.getTransaction().commit();
            }

            DashboardSnapshot snapshot = new JpaDashboardQueryService(jpa)
                    .load("BARONY-RED", LocalDate.of(2026, 6, 30), 10);

            DashboardSnapshot.BudgetActual budgetActual = snapshot.budgetActuals().get(0);
            assertEquals(new BigDecimal("75.0000"), budgetActual.budget().orElseThrow());
            assertEquals(new BigDecimal("50.0000"), budgetActual.actual());
        }
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (1, 'Test', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) VALUES (1, 1, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (2, 1, '4000', 'Income', 'INCOME', 'CREDIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (3, 1, '5000', 'Expense', 'EXPENSE', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, code, name, fund_type) VALUES (1, 'OPERATING', 'Operating', 'UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO budget_category (id, code, name, is_active) VALUES (1, 'PROGRAM', 'Program Services', TRUE)").executeUpdate();

            em.createNativeQuery("INSERT INTO txn (id, txn_date, memo, status) VALUES (1, DATE '2026-01-10', 'Income', 'ENTERED')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (1, 1, 1, 1, 300.0000)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (2, 1, 2, 1, -300.0000)").executeUpdate();

            em.createNativeQuery("INSERT INTO txn (id, txn_date, memo, status) VALUES (2, DATE '2026-02-10', 'Expense', 'ENTERED')").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (3, 2, 1, 1, -50.0000)").executeUpdate();
            em.createNativeQuery("INSERT INTO txn_split (id, txn_id, account_id, fund_id, budget_category_id, amount_signed) VALUES (4, 2, 3, 1, 1, 50.0000)").executeUpdate();

            em.createNativeQuery("""
                    INSERT INTO open_item_snapshot
                    (id, group_code, item_kind, item_ref, state, original_amount,
                     open_amount, last_updated_on, version)
                    VALUES
                    (RANDOM_UUID(), 'BARONY-RED', 'RECEIVABLE', 'AR-001', 'OPEN',
                     75.0000, 75.0000, DATE '2026-05-01', 0)
                    """).executeUpdate();

            em.createNativeQuery("""
                    INSERT INTO reconciliation_run
                    (id, group_code, statement_ending_on, bank_format,
                     imported_transaction_count, status, notes)
                    VALUES
                    (RANDOM_UUID(), 'BARONY-RED', DATE '2026-06-15', 'OFX',
                     3, 'COMPLETED', 'Test reconciliation')
                    """).executeUpdate();
            em.getTransaction().commit();
        }
    }
}
