package org.nonprofitbookkeeping.service.dashboard;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JpaDashboardQueryServiceTest
{
    @Test
    public void emptyDatabase_returnsZeroAndNoFictionalValues(@TempDir Path tempDir)
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
            assertTrue(snapshot.reconciliations().isEmpty());
            assertTrue(snapshot.budgetActuals().isEmpty());
        }
    }

    @Test
    public void load_rejectsMissingDateAndNonPositiveLimit(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("dashboard-validation")))
        {
            JpaDashboardQueryService service = new JpaDashboardQueryService(jpa);

            assertThrows(IllegalArgumentException.class, () -> service.load(null, 5));
            assertThrows(IllegalArgumentException.class, () -> service.load(LocalDate.of(2026, 6, 30), 0));
        }
    }

    @Test
    public void populatedDatabase_projectsEveryExperimentCardFromRealData(@TempDir Path tempDir)
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

            assertTrue(snapshot.fundClassTotals().containsKey("UNRESTRICTED"));
            assertEquals(1L, snapshot.openItems().countFor("RECEIVABLE"));
            assertEquals(1L, snapshot.openItems().totalOpenItems());

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
        }
    }

    @Test
    public void reversedTransactions_areExcludedFromBalancesButRemainVisibleInHistory(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("dashboard-reversed")))
        {
            seed(jpa);
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("UPDATE txn SET status = 'REVERSED' WHERE id = 1").executeUpdate();
                em.getTransaction().commit();
            }

            DashboardSnapshot snapshot = new JpaDashboardQueryService(jpa)
                    .load("BARONY-RED", LocalDate.of(2026, 6, 30), 10);

            assertEquals(new BigDecimal("-50.0000"), snapshot.bookCash());
            assertEquals(new BigDecimal("-50.0000"), snapshot.yearToDateSurplus());
            assertEquals(2, snapshot.recentTransactions().size());
        }
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (1, 'Test', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (1, 1, '1000', 'Checking', 'BANK', 'DEBIT')").executeUpdate();
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
