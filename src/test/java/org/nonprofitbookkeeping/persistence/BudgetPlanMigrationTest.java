package org.nonprofitbookkeeping.persistence;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BudgetPlanMigrationTest
{
    @Test
    public void createsBudgetPlanAndLineTablesWithScopedUniqueness() throws Exception
    {
        String jdbcUrl = jdbcUrl("budget-plan-model");
        DatabaseMigrationService.migrateJdbcUrl(jdbcUrl);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement())
        {
            statement.executeUpdate("INSERT INTO budget_category (id, code, name, is_active) VALUES (1, 'PROGRAM', 'Program Services', TRUE)");
            statement.executeUpdate("INSERT INTO budget_plan (id, name, fiscal_year, version_code, status, period_start, period_end) VALUES (1, 'FY2026', 2026, 'draft-1', 'DRAFT', DATE '2026-01-01', DATE '2026-12-31')");
            statement.executeUpdate("INSERT INTO budget_line (budget_plan_id, budget_category_id, fund_id, period_month, amount) VALUES (1, 1, NULL, NULL, 125.0000)");

            assertEquals(1L, scalarLong(statement, "SELECT COUNT(*) FROM budget_line WHERE amount = 125.0000"));
            assertThrows(Exception.class, () -> statement.executeUpdate("INSERT INTO budget_line (budget_plan_id, budget_category_id, fund_id, period_month, amount) VALUES (1, 1, NULL, NULL, 200.0000)"));
            assertThrows(Exception.class, () -> statement.executeUpdate("INSERT INTO budget_plan (name, fiscal_year, version_code, status, period_start, period_end) VALUES ('FY2026 copy', 2026, 'draft-1', 'DRAFT', DATE '2026-01-01', DATE '2026-12-31')"));
        }
    }

    private static String jdbcUrl(String name)
    {
        return "jdbc:h2:mem:" + name + '-' + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL"
                + ";DATABASE_TO_LOWER=TRUE"
                + ";DEFAULT_NULL_ORDERING=HIGH"
                + ";DB_CLOSE_DELAY=-1"
                + ";INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC\\;SET SCHEMA PUBLIC";
    }

    private static long scalarLong(Statement statement, String sql) throws Exception
    {
        try (ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getLong(1);
        }
    }
}
