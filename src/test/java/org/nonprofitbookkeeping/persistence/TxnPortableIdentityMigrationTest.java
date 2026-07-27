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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TxnPortableIdentityMigrationTest
{
    @Test
    public void backfillsExistingRowsAndDefaultsNewTransactions() throws Exception
    {
        String url = jdbcUrl("txn-portable-identity");
        migrateTo(url, "61");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            statement.executeUpdate("INSERT INTO txn (txn_date, memo) VALUES (DATE '2026-01-01', 'First')");
            statement.executeUpdate("INSERT INTO txn (txn_date, memo) VALUES (DATE '2026-01-02', 'Second')");
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            assertEquals(0L, scalarLong(statement, "SELECT COUNT(*) FROM txn WHERE portable_id IS NULL"));
            assertEquals(2L, scalarLong(statement, "SELECT COUNT(DISTINCT portable_id) FROM txn"));

            UUID first = scalarUuid(statement, "SELECT portable_id FROM txn WHERE memo = 'First'");
            UUID second = scalarUuid(statement, "SELECT portable_id FROM txn WHERE memo = 'Second'");
            assertNotNull(first);
            assertNotNull(second);
            assertNotEquals(first, second);

            statement.executeUpdate("INSERT INTO txn (txn_date, memo) VALUES (DATE '2026-01-03', 'Third')");
            UUID third = scalarUuid(statement, "SELECT portable_id FROM txn WHERE memo = 'Third'");
            assertNotNull(third);

            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO txn (txn_date, memo, portable_id) VALUES "
                            + "(DATE '2026-01-04', 'Duplicate', UUID '" + first + "')"));
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
        try (ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static UUID scalarUuid(Statement statement, String sql) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getObject(1, UUID.class);
        }
    }
}
