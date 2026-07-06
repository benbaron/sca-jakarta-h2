package org.nonprofitbookkeeping.persistence;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatabaseMigrationRecoveryTest
{
    @Test
    public void recoversCompleteSchemaWhenFlywayHistoryIsEmpty() throws Exception
    {
        String jdbcUrl = jdbcUrl("complete-schema-empty-history");
        DatabaseMigrationService.migrateJdbcUrl(jdbcUrl);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement())
        {
            statement.executeUpdate("""
                    INSERT INTO chart_of_accounts (name, version, status)
                    VALUES ('Recovery sentinel', '1', 'ACTIVE')
                    """);
            statement.executeUpdate("DELETE FROM flyway_schema_history");
        }

        DatabaseMigrationService.migrateJdbcUrl(jdbcUrl);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement())
        {
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM chart_of_accounts WHERE name = 'Recovery sentinel'"));
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM flyway_schema_history "
                            + "WHERE version = '51' AND type = 'BASELINE' AND success = TRUE"));
            assertEquals(0L, archivedHistoryRowCount(connection));
        }
    }

    @Test
    public void recoversCompleteSchemaWhenHistoryContainsSchemaAndFailedRows() throws Exception
    {
        String jdbcUrl = jdbcUrl("complete-schema-failed-history");
        DatabaseMigrationService.migrateJdbcUrl(jdbcUrl);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement())
        {
            statement.executeUpdate("""
                    INSERT INTO chart_of_accounts (name, version, status)
                    VALUES ('Failed-history sentinel', '1', 'ACTIVE')
                    """);
            statement.executeUpdate("DELETE FROM flyway_schema_history");
            statement.executeUpdate("""
                    INSERT INTO flyway_schema_history
                        (installed_rank, version, description, type, script, checksum,
                         installed_by, execution_time, success)
                    VALUES
                        (-1, NULL, '<< Flyway Schema Creation >>', 'SCHEMA',
                         '<< Flyway Schema Creation >>', NULL, 'sa', 0, TRUE)
                    """);
            statement.executeUpdate("""
                    INSERT INTO flyway_schema_history
                        (installed_rank, version, description, type, script, checksum,
                         installed_by, execution_time, success)
                    VALUES
                        (1, '1', 'init', 'SQL', 'V1__init.sql', NULL, 'sa', 1, FALSE)
                    """);
        }

        DatabaseMigrationService.migrateJdbcUrl(jdbcUrl);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement())
        {
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM chart_of_accounts WHERE name = 'Failed-history sentinel'"));
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM flyway_schema_history "
                            + "WHERE version = '51' AND type = 'BASELINE' AND success = TRUE"));
            assertEquals(2L, archivedHistoryRowCount(connection));
        }
    }

    @Test
    public void migratesFreshSchemaNormally() throws Exception
    {
        String jdbcUrl = jdbcUrl("fresh-schema");

        DatabaseMigrationService.migrateJdbcUrl(jdbcUrl);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement())
        {
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM flyway_schema_history "
                            + "WHERE version = '51' AND success = TRUE"));
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE lower(table_name) = 'budget_line' AND lower(column_name) = 'amount'"));
        }
    }

    @Test
    public void rejectsPartialUntrackedSchemaWithoutDeletingData() throws Exception
    {
        String jdbcUrl = jdbcUrl("partial-schema");
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement())
        {
            statement.executeUpdate("""
                    CREATE TABLE chart_of_accounts
                    (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        name VARCHAR(200) NOT NULL,
                        version VARCHAR(50) NOT NULL,
                        status VARCHAR(20) NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO chart_of_accounts (name, version, status)
                    VALUES ('Partial sentinel', '1', 'ACTIVE')
                    """);
        }

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DatabaseMigrationService.migrateJdbcUrl(jdbcUrl));

        assertTrue(exception.getMessage().contains("partial or non-contiguous untracked schema"));
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement())
        {
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM chart_of_accounts WHERE name = 'Partial sentinel'"));
            assertEquals(0L, scalarLong(statement,
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE lower(table_name) = 'account'"));
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

    private static long archivedHistoryRowCount(Connection connection) throws Exception
    {
        DatabaseMetaData metadata = connection.getMetaData();
        String archivedTable = null;
        try (ResultSet tables = metadata.getTables(null, null, "%", new String[] {"TABLE"}))
        {
            while (tables.next())
            {
                String schema = tables.getString("TABLE_SCHEM");
                String table = tables.getString("TABLE_NAME");
                if ("PUBLIC".equalsIgnoreCase(schema)
                        && table.toLowerCase().startsWith("flyway_schema_history_orphaned_"))
                {
                    archivedTable = table;
                    break;
                }
            }
        }

        assertNotNull(archivedTable, "Recovery must retain an archived copy of Flyway history");
        try (Statement statement = connection.createStatement())
        {
            return scalarLong(statement, "SELECT COUNT(*) FROM \""
                    + archivedTable.replace("\"", "\"\"") + "\"");
        }
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
