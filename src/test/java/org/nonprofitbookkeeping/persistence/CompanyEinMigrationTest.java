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

class CompanyEinMigrationTest
{
    @Test
    void legacyTaxProfileEinIsBackfilledWithoutDroppingLegacyData() throws Exception
    {
        String url = jdbcUrl("company-ein-backfill");
        migrateTo(url, "74");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            long companyId = scalarLong(statement, "SELECT id FROM company WHERE code = 'DEFAULT'");
            statement.executeUpdate("""
                    INSERT INTO company_tax_profile
                        (company_id, ein, tax_jurisdiction, filing_name, notes)
                    VALUES
                        (""" + companyId + ", ' 12-3456789 ', 'Legacy jurisdiction', 'Legacy filing name', 'Keep me')");
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            assertEquals("12-3456789", scalarObject(statement,
                    "SELECT ein FROM company WHERE code = 'DEFAULT'"));
            assertEquals("Legacy jurisdiction", scalarObject(statement,
                    "SELECT tax_jurisdiction FROM company_tax_profile WHERE company_id = "
                            + scalarLong(statement, "SELECT id FROM company WHERE code = 'DEFAULT'")));
            assertEquals("Keep me", scalarObject(statement,
                    "SELECT notes FROM company_tax_profile WHERE company_id = "
                            + scalarLong(statement, "SELECT id FROM company WHERE code = 'DEFAULT'")));
        }
    }

    @Test
    void freshCompaniesMayHaveNoEin() throws Exception
    {
        String url = jdbcUrl("company-ein-nullable");
        migrate(url);
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            assertNull(scalarObject(statement, "SELECT ein FROM company WHERE code = 'DEFAULT'"));
            statement.executeUpdate("INSERT INTO company (code, display_name, ein) VALUES ('WITH-EIN', 'With EIN', '98-7654321')");
            assertEquals("98-7654321", scalarObject(statement,
                    "SELECT ein FROM company WHERE code = 'WITH-EIN'"));
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
