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

public class PartyPortableIdentityMigrationTest
{
    @Test
    public void backfillsExistingRowsAndDefaultsNewParties() throws Exception
    {
        String url = jdbcUrl("party-portable-identity");
        migrateTo(url, "62");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            statement.executeUpdate("INSERT INTO counterparty (display_name, kind) VALUES ('First Party', 'OTHER')");
            statement.executeUpdate("INSERT INTO counterparty (display_name, kind) VALUES ('Second Party', 'ORG')");
            statement.executeUpdate("INSERT INTO merchant (name) VALUES ('First Merchant')");
            statement.executeUpdate("INSERT INTO merchant (name) VALUES ('Second Merchant')");
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            assertBackfilled(statement, "counterparty");
            assertBackfilled(statement, "merchant");

            UUID firstParty = scalarUuid(statement,
                    "SELECT portable_id FROM counterparty WHERE display_name = 'First Party'");
            UUID secondParty = scalarUuid(statement,
                    "SELECT portable_id FROM counterparty WHERE display_name = 'Second Party'");
            UUID firstMerchant = scalarUuid(statement,
                    "SELECT portable_id FROM merchant WHERE name = 'First Merchant'");
            UUID secondMerchant = scalarUuid(statement,
                    "SELECT portable_id FROM merchant WHERE name = 'Second Merchant'");
            assertNotNull(firstParty);
            assertNotNull(secondParty);
            assertNotNull(firstMerchant);
            assertNotNull(secondMerchant);
            assertNotEquals(firstParty, secondParty);
            assertNotEquals(firstMerchant, secondMerchant);

            statement.executeUpdate("INSERT INTO counterparty (display_name, kind) VALUES ('Third Party', 'PERSON')");
            statement.executeUpdate("INSERT INTO merchant (name) VALUES ('Third Merchant')");
            assertNotNull(scalarUuid(statement,
                    "SELECT portable_id FROM counterparty WHERE display_name = 'Third Party'"));
            assertNotNull(scalarUuid(statement,
                    "SELECT portable_id FROM merchant WHERE name = 'Third Merchant'"));

            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO counterparty (display_name, kind, portable_id) VALUES "
                            + "('Duplicate Party', 'OTHER', UUID '" + firstParty + "')"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO merchant (name, portable_id) VALUES "
                            + "('Duplicate Merchant', UUID '" + firstMerchant + "')"));
        }
    }

    private static void assertBackfilled(Statement statement, String table) throws SQLException
    {
        assertEquals(0L, scalarLong(statement,
                "SELECT COUNT(*) FROM " + table + " WHERE portable_id IS NULL"));
        assertEquals(2L, scalarLong(statement,
                "SELECT COUNT(DISTINCT portable_id) FROM " + table));
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
