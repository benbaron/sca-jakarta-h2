package org.nonprofitbookkeeping.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AssetAcquisitionMigrationTest
{
    @Test
    void v85UpgradePreservesUnknownHistoryAndConstrainsExplicitSources() throws Exception
    {
        String url = url(); migrate(url, "85"); seed(url); Object portable;
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement())
        {
            portable = scalar(s, "select portable_id from fixed_asset where id = 5001");
        }
        migrate(url, null);
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement())
        {
            assertEquals(portable, scalar(s, "select portable_id from fixed_asset where id = 5001"));
            assertEquals("10.0000", scalar(s, "select acquisition_cost from fixed_asset where id = 5001").toString());
            assertNull(scalar(s, "select acquisition_source from fixed_asset where id = 5001"));
            assertNull(scalar(s, "select acquisition_split_id from fixed_asset where id = 5001"));
            assertEquals(0L, ((Number)scalar(s, "select count(*) from audit_event")).longValue());
            for (String invalid : java.util.List.of("acquisition_source = 'UNKNOWN'", "acquisition_source = 'NEW_PURCHASE'",
                    "acquisition_split_id = 4002", "acquisition_source = 'REGISTER_ONLY', acquisition_split_id = 4002",
                    "acquisition_source = 'EXISTING_ENTRY', acquisition_split_id = 99999"))
            {
                assertThrows(SQLException.class, () -> s.executeUpdate("update fixed_asset set " + invalid + " where id = 5001"));
            }
            s.executeUpdate("update fixed_asset set acquisition_source = 'EXISTING_ENTRY', acquisition_split_id = 4002 where id = 5001");
            assertThrows(SQLException.class, () -> s.executeUpdate("delete from txn_split where id = 4002"));
            s.executeUpdate("update fixed_asset set acquisition_source = 'REGISTER_ONLY', acquisition_split_id = null where id = 5001");
        }
    }

    @Test
    void partialDdlRecoveryRetainsExplicitSourceAndAddsConstraintsOnlyOnce() throws Exception
    {
        String url = url(); migrate(url, "85"); seed(url);
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement())
        {
            s.executeUpdate("alter table fixed_asset add column acquisition_source varchar(24)");
            s.executeUpdate("alter table fixed_asset add column acquisition_split_id bigint");
            s.executeUpdate("update fixed_asset set acquisition_source = 'EXISTING_ENTRY', acquisition_split_id = 4002 where id = 5001");
        }
        migrate(url, null); migrate(url, null);
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement())
        {
            assertEquals("EXISTING_ENTRY", scalar(s, "select acquisition_source from fixed_asset where id = 5001"));
            assertEquals(4002L, ((Number)scalar(s, "select acquisition_split_id from fixed_asset where id = 5001")).longValue());
            assertEquals(1L, ((Number)scalar(s, "select count(*) from information_schema.table_constraints where constraint_name = 'ck_fixed_asset_acquisition_source'")).longValue());
        }
    }

    private static void seed(String url) throws Exception
    {
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement())
        {
            long company = ((Number)scalar(s, "select id from company where code = 'DEFAULT'")).longValue();
            s.executeUpdate("insert into chart_of_accounts(id,company_id,name,version,status) values (101," + company + ",'Legacy','1','ACTIVE')");
            s.executeUpdate("insert into account(id,chart_id,code,name,account_type,normal_balance) values (1001,101,'1000','Bank','ASSET','DEBIT'),(1002,101,'5000','Expense','EXPENSE','DEBIT')");
            s.executeUpdate("insert into fund(id,company_id,code,name,fund_type) values (2001," + company + ",'GENERAL','General','UNRESTRICTED')");
            s.executeUpdate("insert into txn(id,company_id,txn_date,memo,status) values (3001," + company + ",DATE '2026-02-01','Legacy','ENTERED')");
            s.executeUpdate("insert into fixed_asset(id,company_id,asset_account_id,accumulated_depreciation_account_id,depreciation_expense_account_id,fund_id,name,acquisition_date,acquisition_cost,useful_life_months,depreciation_method,status) values (5001," + company + ",1001,1001,1002,2001,'Legacy asset',DATE '2026-02-01',10,60,'STRAIGHT_LINE','ACTIVE')");
            s.executeUpdate("insert into txn_split(id,txn_id,account_id,fund_id,amount_signed) values (4001,3001,1001,2001,-10),(4002,3001,1002,2001,10)");
        }
    }

    private static void migrate(String url, String target)
    {
        var config = Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration");
        if (target != null)
        {
            config.target(target);
        }
        config.load().migrate();
    }

    private static Object scalar(Statement statement, String sql) throws Exception
    {
        try (ResultSet result = statement.executeQuery(sql))
        {
            assertTrue(result.next()); return result.getObject(1);
        }
    }

    private static String url()
    {
        return "jdbc:h2:mem:acquisition-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC\\;SET SCHEMA PUBLIC";
    }
}
