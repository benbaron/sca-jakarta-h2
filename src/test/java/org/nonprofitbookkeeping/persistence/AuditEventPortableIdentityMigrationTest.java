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

public class AuditEventPortableIdentityMigrationTest
{
    @Test
    public void backfillsDefaultsAndRejectsDuplicatePortableIdentities() throws Exception
    {
        String url = jdbcUrl("audit-event-portable-identity");
        migrateTo(url, "66");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            insertCompany(statement);
            statement.executeUpdate(auditInsertSql(17_001L, "Initial audit fact", null));
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            UUID first = portableId(statement, 17_001L);
            assertNotNull(first);
            assertEquals("Initial audit fact", scalarString(statement,
                    "SELECT summary FROM audit_event WHERE id = 17001"));

            statement.executeUpdate(auditInsertSql(17_002L, "Later audit fact", null));
            assertNotEquals(first, portableId(statement, 17_002L));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    auditInsertSql(17_003L, "Duplicate identity", first)));
        }
    }

    @Test
    public void toleratesExistingColumnAndConstraintDuringRecovery() throws Exception
    {
        String url = jdbcUrl("audit-event-portable-identity-recovery");
        migrateTo(url, "66");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            insertCompany(statement);
            statement.executeUpdate(auditInsertSql(17_001L, "Recovery audit fact", null));
            statement.executeUpdate("ALTER TABLE audit_event ADD COLUMN portable_id UUID");
            statement.executeUpdate("UPDATE audit_event SET portable_id = RANDOM_UUID()");
            statement.executeUpdate("ALTER TABLE audit_event ADD CONSTRAINT "
                    + "uq_audit_event_portable_id UNIQUE (portable_id)");
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            assertNotNull(portableId(statement, 17_001L));
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                            + "WHERE lower(table_name) = 'audit_event' "
                            + "AND lower(constraint_name) = 'uq_audit_event_portable_id'"));
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '67' AND success = TRUE"));
        }
    }

    private static void insertCompany(Statement statement) throws SQLException
    {
        statement.executeUpdate("INSERT INTO chart_of_accounts "
                + "(id, name, version, status) VALUES (17001, 'Audit Chart', '1', 'ACTIVE')");
        statement.executeUpdate("INSERT INTO company "
                + "(id, code, display_name, active_chart_of_accounts_id) VALUES "
                + "(17001, 'AUDPORT', 'Audit Portable Company', 17001)");
        statement.executeUpdate("UPDATE chart_of_accounts SET company_id = 17001 WHERE id = 17001");
    }

    private static String auditInsertSql(long id, String summary, UUID portableId)
    {
        String portableColumn = portableId == null ? "" : ", portable_id";
        String portableValue = portableId == null ? "" : ", UUID '" + portableId + "'";
        return "INSERT INTO audit_event "
                + "(id, company_id, occurred_at, actor, action_type, entity_type, entity_id, summary, "
                + "before_value, after_value, reason" + portableColumn + ") VALUES ("
                + id + ", 17001, TIMESTAMP '2026-01-01 00:00:00', 'treasurer', 'UPDATED', "
                + "'Transaction', 'txn-1', '" + summary.replace("'", "''")
                + "', 'before', 'after', 'correction'" + portableValue + ")";
    }

    private static UUID portableId(Statement statement, long id) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(
                "SELECT portable_id FROM audit_event WHERE id = " + id))
        {
            rows.next();
            return rows.getObject(1, UUID.class);
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

    private static String scalarString(Statement statement, String sql) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getString(1);
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
