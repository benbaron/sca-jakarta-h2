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

public class InventoryPortableIdentityMigrationTest
{
    @Test
    public void backfillsDefaultsAndRejectsDuplicatePortableIdentities() throws Exception
    {
        String url = jdbcUrl("inventory-portable-identity");
        migrateTo(url, "65");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            insertSupportGraph(statement);
            insertItemAndMovement(statement, 16_001L, 16_002L, "Event Tokens");
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            UUID firstItem = portableId(statement, "inventory_item", 16_001L);
            UUID firstMovement = portableId(statement, "inventory_movement", 16_002L);
            assertNotNull(firstItem);
            assertNotNull(firstMovement);
            assertEquals("Event Tokens", scalarString(statement,
                    "SELECT name FROM inventory_item WHERE id = 16001"));
            assertEquals("RECEIPT", scalarString(statement,
                    "SELECT movement_type FROM inventory_movement WHERE id = 16002"));

            insertItemAndMovement(statement, 17_001L, 17_002L, "Feast Supplies");
            assertNotEquals(firstItem, portableId(statement, "inventory_item", 17_001L));
            assertNotEquals(firstMovement, portableId(statement, "inventory_movement", 17_002L));

            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    itemInsertSql(18_001L, "Duplicate item", firstItem)));
            statement.executeUpdate(itemInsertSql(18_002L, "Third item", null));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    movementInsertSql(18_003L, 18_002L, firstMovement)));
        }
    }

    @Test
    public void toleratesExistingColumnsAndConstraintsDuringRecovery() throws Exception
    {
        String url = jdbcUrl("inventory-portable-identity-recovery");
        migrateTo(url, "65");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            insertSupportGraph(statement);
            insertItemAndMovement(statement, 16_001L, 16_002L, "Recovery Tokens");
            statement.executeUpdate("ALTER TABLE inventory_item ADD COLUMN portable_id UUID");
            statement.executeUpdate("UPDATE inventory_item SET portable_id = RANDOM_UUID()");
            statement.executeUpdate("ALTER TABLE inventory_movement ADD COLUMN portable_id UUID DEFAULT RANDOM_UUID()");
            statement.executeUpdate("UPDATE inventory_movement SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL");
            statement.executeUpdate("ALTER TABLE inventory_movement ALTER COLUMN portable_id SET NOT NULL");
            statement.executeUpdate("ALTER TABLE inventory_movement ADD CONSTRAINT "
                    + "uq_inventory_movement_portable_id UNIQUE (portable_id)");
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            assertNotNull(portableId(statement, "inventory_item", 16_001L));
            assertNotNull(portableId(statement, "inventory_movement", 16_002L));
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                            + "WHERE lower(table_name) = 'inventory_item' "
                            + "AND lower(constraint_name) = 'uq_inventory_item_portable_id'"));
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                            + "WHERE lower(table_name) = 'inventory_movement' "
                            + "AND lower(constraint_name) = 'uq_inventory_movement_portable_id'"));
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '66' AND success = TRUE"));
        }
    }

    private static void insertSupportGraph(Statement statement) throws SQLException
    {
        statement.executeUpdate("INSERT INTO chart_of_accounts "
                + "(id, name, version, status) VALUES (15001, 'Inventory Chart', '1', 'ACTIVE')");
        statement.executeUpdate("INSERT INTO company "
                + "(id, code, display_name, active_chart_of_accounts_id) VALUES "
                + "(15001, 'INVPORT', 'Inventory Portable Company', 15001)");
        statement.executeUpdate("UPDATE chart_of_accounts SET company_id = 15001 WHERE id = 15001");
        statement.executeUpdate("INSERT INTO fund "
                + "(id, company_id, code, name, fund_type) VALUES "
                + "(15001, 15001, 'OPERATING', 'Operating Fund', 'UNRESTRICTED')");
        statement.executeUpdate("INSERT INTO account "
                + "(id, chart_id, code, name, account_type, subtype, normal_balance) VALUES "
                + "(15001, 15001, '1400', 'Inventory', 'ASSET', 'INVENTORY', 'DEBIT')");
    }

    private static void insertItemAndMovement(
            Statement statement, long itemId, long movementId, String name) throws SQLException
    {
        statement.executeUpdate(itemInsertSql(itemId, name, null));
        statement.executeUpdate(movementInsertSql(movementId, itemId, null));
    }

    private static String itemInsertSql(long id, String name, UUID portableId)
    {
        String portableColumn = portableId == null ? "" : ", portable_id";
        String portableValue = portableId == null ? "" : ", UUID '" + portableId + "'";
        return "INSERT INTO inventory_item "
                + "(id, company_id, inventory_account_id, fund_id, name, item_type, quantity, unit_name, "
                + "unit_value, acquisition_date, item_condition, status, notes" + portableColumn + ") VALUES ("
                + id + ", 15001, 15001, 15001, '" + name.replace("'", "''")
                + "', 'SUPPLIES', 10.0000, 'each', 2.5000, DATE '2026-01-01', "
                + "'GOOD', 'ACTIVE', 'Portable identity fixture'" + portableValue + ")";
    }

    private static String movementInsertSql(long id, long itemId, UUID portableId)
    {
        String portableColumn = portableId == null ? "" : ", portable_id";
        String portableValue = portableId == null ? "" : ", UUID '" + portableId + "'";
        return "INSERT INTO inventory_movement "
                + "(id, inventory_item_id, movement_date, movement_type, quantity_change, "
                + "resulting_quantity, unit_value, notes" + portableColumn + ") VALUES ("
                + id + ", " + itemId + ", DATE '2026-01-01', 'RECEIPT', 10.0000, 10.0000, "
                + "2.5000, 'Initial receipt'" + portableValue + ")";
    }

    private static UUID portableId(Statement statement, String table, long id) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(
                "SELECT portable_id FROM " + table + " WHERE id = " + id))
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
