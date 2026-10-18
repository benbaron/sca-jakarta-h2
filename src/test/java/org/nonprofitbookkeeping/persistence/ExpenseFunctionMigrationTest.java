package org.nonprofitbookkeeping.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ExpenseFunctionMigrationTest
{
    @Test
    void v84HistoryKeepsIdentityAmountsAndUnknownPurposeWhileNewCodesAreConstrained() throws Exception
    {
        String url = url(); migrate(url, "84"); seed(url); Object portable;
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement())
        {
            portable = scalar(s, "select portable_id from txn where id = 3001");
        }
        migrate(url, null);
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement())
        {
            assertEquals(portable, scalar(s, "select portable_id from txn where id = 3001"));
            assertEquals("10.0000", scalar(s, "select amount_signed from txn_split where id = 4002").toString());
            assertNull(scalar(s, "select expense_function from txn_split where id = 4002"));
            assertEquals(0L, ((Number)scalar(s, "select count(*) from audit_event")).longValue());
            s.executeUpdate("update txn_split set expense_function = 'AR' where id = 4002");
            assertThrows(SQLException.class, () -> s.executeUpdate("update txn_split set expense_function = 'ZZ' where id = 4002"));
            s.executeUpdate("update txn_split set expense_function = NULL where id = 4002");
        }
    }

    @Test
    void recoveryOfExistingNullableColumnPreservesExplicitValueAndAddsTheConstraintOnce() throws Exception
    {
        String url = url(); migrate(url, "84"); seed(url);
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement())
        {
            s.executeUpdate("alter table txn_split add column expense_function varchar(2)");
            s.executeUpdate("update txn_split set expense_function = 'FR' where id = 4002");
        }
        migrate(url, null); migrate(url, null);
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement())
        {
            assertEquals("FR", scalar(s, "select expense_function from txn_split where id = 4002"));
            assertNull(scalar(s, "select expense_function from txn_split where id = 4001"));
            assertEquals(1L, ((Number)scalar(s, "select count(*) from information_schema.table_constraints where constraint_name = 'ck_txn_split_expense_function'")).longValue());
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
        return "jdbc:h2:mem:function-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC\\;SET SCHEMA PUBLIC";
    }
}
