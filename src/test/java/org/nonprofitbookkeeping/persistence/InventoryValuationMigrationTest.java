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
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryValuationMigrationTest
{
    @Test
    void v89BackfillsLegacyFixedValueAndSurvivesRestart() throws Exception
    {
        String url = "jdbc:h2:mem:inventory-value-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        migrate(url, "88");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement())
        {
            long company = number(statement, "select id from company where code='DEFAULT'");
            statement.executeUpdate("insert into chart_of_accounts(id,company_id,name,version,status) values(8901,"
                    + company + ",'Inventory','1','ACTIVE')");
            statement.executeUpdate("insert into account(id,chart_id,code,name,account_type,subtype,normal_balance) "
                    + "values(8901,8901,'1400','Inventory','ASSET','INVENTORY','DEBIT')");
            statement.executeUpdate("insert into fund(id,company_id,code,name,fund_type) values(8901,"
                    + company + ",'GENERAL','General','UNRESTRICTED')");
            statement.executeUpdate("insert into inventory_item(id,company_id,inventory_account_id,fund_id,name,item_type,"
                    + "quantity,unit_name,unit_value,acquisition_date,item_condition,status) values(8901,"
                    + company + ",8901,8901,'Legacy','SUPPLY',10,'each',2,date '2026-01-01','GOOD','ACTIVE')");
            statement.executeUpdate("insert into inventory_movement(id,inventory_item_id,movement_date,movement_type,"
                    + "quantity_change,resulting_quantity,unit_value) values(8901,8901,date '2026-01-01','RECEIPT',10,10,2)");
        }

        migrate(url, null);
        migrate(url, null);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement())
        {
            assertEquals("20.0000", scalar(statement,
                    "select carrying_value from inventory_item where id=8901"));
            assertEquals("MOVING_WEIGHTED_AVERAGE", scalar(statement,
                    "select valuation_method from inventory_item where id=8901"));
            assertEquals("20.0000", scalar(statement,
                    "select value_change from inventory_movement where id=8901"));
            assertEquals("20.0000", scalar(statement,
                    "select resulting_carrying_value from inventory_movement where id=8901"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "update inventory_item set quantity=0 where id=8901"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "update inventory_item set valuation_method='FIFO' where id=8901"));
        }
    }

    private static void migrate(String url, String target)
    {
        var configuration = Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }

    private static long number(Statement statement, String sql) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(sql)) { rows.next(); return rows.getLong(1); }
    }

    private static String scalar(Statement statement, String sql) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(sql)) { rows.next(); return rows.getObject(1).toString(); }
    }
}
