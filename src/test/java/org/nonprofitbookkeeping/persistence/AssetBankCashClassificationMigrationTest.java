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

public class AssetBankCashClassificationMigrationTest
{
    @Test
    public void migratesLegacyBankTypeToAssetBankFunctionWithoutChangingCashClass() throws Exception
    {
        String url = jdbcUrl("asset-bank-cash");
        migrateTo(url, "72");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            statement.executeUpdate("INSERT INTO chart_of_accounts (id, name, version, status) "
                    + "VALUES (7300, 'Classification Migration', '1', 'ACTIVE')");
            statement.executeUpdate("INSERT INTO account "
                    + "(id, chart_id, code, name, account_type, subtype, normal_balance) VALUES "
                    + "(7301, 7300, '1000', 'Checking', 'BANK', 'CASH', 'DEBIT')");
            statement.executeUpdate("INSERT INTO account "
                    + "(id, chart_id, code, name, account_type, subtype, normal_balance) VALUES "
                    + "(7302, 7300, '1050', 'Restricted Deposit', 'BANK', 'OTHER_ASSET', 'DEBIT')");
            statement.executeUpdate("INSERT INTO account "
                    + "(id, chart_id, code, name, account_type, subtype, normal_balance) VALUES "
                    + "(7303, 7300, '1100', 'Other Asset', 'ASSET', 'OTHER_ASSET', 'DEBIT')");
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            assertAccount(statement, 7301L, "ASSET", "BANK", "CASH");
            assertAccount(statement, 7302L, "ASSET", "BANK", "OTHER_ASSET");
            assertAccount(statement, 7303L, "ASSET", null, "OTHER_ASSET");
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '73' AND success = TRUE"));
        }
    }

    private static void assertAccount(
            Statement statement,
            long id,
            String expectedType,
            String expectedFunction,
            String expectedSubtype) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(
                "SELECT account_type, account_function, subtype FROM account WHERE id = " + id))
        {
            rows.next();
            assertEquals(expectedType, rows.getString(1));
            if (expectedFunction == null)
            {
                assertNull(rows.getString(2));
            }
            else
            {
                assertEquals(expectedFunction, rows.getString(2));
            }
            assertEquals(expectedSubtype, rows.getString(3));
        }
    }

    private static long scalarLong(Statement statement, String sql) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static void migrateTo(String url, String target)
    {
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target(target).load().migrate();
    }

    private static void migrate(String url)
    {
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .load().migrate();
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
}
