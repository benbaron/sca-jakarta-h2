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

class SupplementalOpenItemLifecycleMigrationTest
{
    @Test
    void migrationPreservesLegacySupplementalRowsAndRequiresLifecycleTriple() throws Exception
    {
        String url = jdbcUrl("supplemental-lifecycle");
        migrateTo(url, "76");
        long txnId;
        long lineId;
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            long companyId = scalarLong(statement, "SELECT id FROM company WHERE code = 'DEFAULT'");
            statement.executeUpdate("INSERT INTO txn (company_id, txn_date, memo, status) VALUES ("
                    + companyId + ", DATE '2026-01-10', 'legacy detail', 'ENTERED')");
            txnId = scalarLong(statement, "SELECT max(id) FROM txn");
            statement.executeUpdate("INSERT INTO txn_supplemental_line "
                    + "(txn_id, line_order, kind, description, amount) VALUES ("
                    + txnId + ", 0, 'RECEIVABLE', 'Legacy receivable', 25.0000)");
            lineId = scalarLong(statement, "SELECT max(id) FROM txn_supplemental_line");
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            assertEquals("Legacy receivable", scalarObject(statement,
                    "SELECT description FROM txn_supplemental_line WHERE id = " + lineId));
            assertNull(scalarObject(statement,
                    "SELECT item_id FROM txn_supplemental_line WHERE id = " + lineId));
            assertNull(scalarObject(statement,
                    "SELECT txn_split_id FROM txn_supplemental_line WHERE id = " + lineId));
            assertNull(scalarObject(statement,
                    "SELECT item_effect FROM txn_supplemental_line WHERE id = " + lineId));

            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "UPDATE txn_supplemental_line SET item_id = UUID '11111111-1111-1111-1111-111111111111' WHERE id = " + lineId));
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
        return ((Number) scalarObject(statement, sql)).longValue();
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
