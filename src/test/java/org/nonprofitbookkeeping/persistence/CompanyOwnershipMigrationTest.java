package org.nonprofitbookkeeping.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompanyOwnershipMigrationTest
{
    @Test
    public void singleCompanyLegacyRowsAreBackfilledDeterministically() throws Exception
    {
        String url = jdbcUrl("single-company-backfill");
        migrateTo(url, "60");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            statement.executeUpdate("INSERT INTO fund (id, code, name, fund_type) VALUES (901, 'LEGACY', 'Legacy Fund', 'UNRESTRICTED')");
            statement.executeUpdate("INSERT INTO budget_category (id, code, name) VALUES (902, 'LEGACY', 'Legacy Category')");
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            long companyId = scalarLong(statement, "SELECT id FROM company WHERE code = 'DEFAULT'");
            assertEquals(companyId, scalarLong(statement, "SELECT company_id FROM fund WHERE id = 901"));
            assertEquals(companyId, scalarLong(statement, "SELECT company_id FROM budget_category WHERE id = 902"));
            assertEquals(0L, scalarLong(statement, "SELECT COUNT(*) FROM company_ownership_issue WHERE resolved_at IS NULL"));
            assertEquals(1L, scalarLong(statement, "SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name) = 'interchange_identity'"));
        }
    }

    @Test
    public void multiCompanyAmbiguityIsRetainedAndDiagnosed() throws Exception
    {
        String url = jdbcUrl("multi-company-ambiguity");
        migrateTo(url, "60");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            statement.executeUpdate("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (901, 'Default Chart', '1', 'ACTIVE')");
            statement.executeUpdate("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (902, 'Other Chart', '1', 'ACTIVE')");
            statement.executeUpdate("UPDATE company SET active_chart_of_accounts_id = 901 WHERE code = 'DEFAULT'");
            statement.executeUpdate("INSERT INTO company (code, display_name, active_chart_of_accounts_id) VALUES ('OTHER', 'Other Company', 902)");
            statement.executeUpdate("INSERT INTO fund (id, code, name, fund_type) VALUES (903, 'UNOWNED', 'Unowned Fund', 'UNRESTRICTED')");
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            assertEquals(scalarLong(statement, "SELECT id FROM company WHERE code = 'DEFAULT'"),
                    scalarLong(statement, "SELECT company_id FROM chart_of_accounts WHERE id = 901"));
            assertEquals(scalarLong(statement, "SELECT id FROM company WHERE code = 'OTHER'"),
                    scalarLong(statement, "SELECT company_id FROM chart_of_accounts WHERE id = 902"));
            assertNull(scalarObject(statement, "SELECT company_id FROM fund WHERE id = 903"));
            assertEquals(1L, scalarLong(statement, "SELECT COUNT(*) FROM company_ownership_issue WHERE entity_type = 'FUND' AND entity_id = '903' AND issue_code = 'UNRESOLVED_OWNER' AND resolved_at IS NULL"));
        }
    }

    @Test
    public void businessKeysAreUniqueWithinCompanyAndReusableAcrossCompanies() throws Exception
    {
        String url = jdbcUrl("company-scoped-keys");
        migrate(url);
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            long defaultId = scalarLong(statement, "SELECT id FROM company WHERE code = 'DEFAULT'");
            statement.executeUpdate("INSERT INTO company (code, display_name) VALUES ('OTHER', 'Other Company')");
            long otherId = scalarLong(statement, "SELECT id FROM company WHERE code = 'OTHER'");
            statement.executeUpdate("INSERT INTO fund (company_id, code, name, fund_type) VALUES (" + defaultId + ", 'SHARED', 'Default Shared', 'UNRESTRICTED')");
            statement.executeUpdate("INSERT INTO fund (company_id, code, name, fund_type) VALUES (" + otherId + ", 'SHARED', 'Other Shared', 'UNRESTRICTED')");
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO fund (company_id, code, name, fund_type) VALUES (" + defaultId + ", 'SHARED', 'Duplicate', 'UNRESTRICTED')"));

            statement.executeUpdate("INSERT INTO activity (company_id, code, name) VALUES (" + defaultId + ", 'EVENT', 'Default Event')");
            statement.executeUpdate("INSERT INTO activity (company_id, code, name) VALUES (" + otherId + ", 'EVENT', 'Other Event')");
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO activity (company_id, code, name) VALUES (" + defaultId + ", 'EVENT', 'Duplicate Event')"));

            statement.executeUpdate("INSERT INTO merchant (company_id, name) VALUES (" + defaultId + ", 'Shared Merchant')");
            statement.executeUpdate("INSERT INTO merchant (company_id, name) VALUES (" + otherId + ", 'Shared Merchant')");
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO merchant (company_id, name) VALUES (" + defaultId + ", 'Shared Merchant')"));
        }
    }

    @Test
    public void migrationAddsAllRequiredOwnershipColumnsAndForeignKeys() throws Exception
    {
        String url = jdbcUrl("ownership-columns");
        migrate(url);
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            for (String table : new String[] {
                    "chart_of_accounts", "txn", "fund", "budget_category", "budget_plan",
                    "activity", "counterparty", "merchant", "accounting_period", "audit_event",
                    "period_close_range", "period_close_event"})
            {
                assertEquals(1L, scalarLong(statement,
                        "SELECT COUNT(*) FROM information_schema.columns WHERE lower(table_name) = '"
                                + table + "' AND lower(column_name) = 'company_id'"), table);
            }
            assertTrue(scalarLong(statement,
                    "SELECT COUNT(*) FROM information_schema.table_constraints WHERE lower(table_name) = 'interchange_identity' AND constraint_type = 'UNIQUE'") >= 1L);
        }
    }

    private static void migrateTo(String url, String target)
    {
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private static void migrate(String url)
    {
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static Connection connect(String url) throws SQLException
    {
        return DriverManager.getConnection(url, "sa", "");
    }

    private static String jdbcUrl(String name)
    {
        return "jdbc:h2:mem:" + name + '-' + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
                + ";INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC\\;SET SCHEMA PUBLIC";
    }

    private static long scalarLong(Statement statement, String sql) throws SQLException
    {
        Object value = scalarObject(statement, sql);
        return ((Number) value).longValue();
    }

    private static Object scalarObject(Statement statement, String sql) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getObject(1);
        }
    }
}
