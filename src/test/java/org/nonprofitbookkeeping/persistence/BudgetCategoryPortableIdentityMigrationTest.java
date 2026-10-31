package org.nonprofitbookkeeping.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BudgetCategoryPortableIdentityMigrationTest
{
    @Test
    void upgradePreservesCategoryAliasAndBudgetLinksWithStableUniqueUuidAndWriteGuards() throws Exception
    {
        String url = "jdbc:h2:mem:category_upgrade_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("91").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement())
        {
            sql.execute("INSERT INTO budget_category (id,code,name,is_active,effective_from,effective_to,description,created_at,updated_at) VALUES (11,'HISTORY','Retained name',FALSE,DATE '2020-01-01',DATE '2025-12-31',' Exact description ',TIMESTAMP WITH TIME ZONE '2020-01-01 00:00:00+00',TIMESTAMP WITH TIME ZONE '2025-01-01 01:02:03.123456+00')");
            sql.execute("INSERT INTO budget_category_alias (id,budget_category_id,alias,source_system) VALUES (12,11,'Legacy alias','source')");
            sql.execute("INSERT INTO budget_plan (id,name,fiscal_year,version_code,period_start,period_end,status) VALUES (13,'Retained plan',2025,'V1',DATE '2025-01-01',DATE '2025-12-31','DRAFT')");
            sql.execute("INSERT INTO budget_line (id,budget_plan_id,budget_category_id,amount) VALUES (14,13,11,12.3456)");
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            Object identity;
            try (var rs = sql.executeQuery("select c.portable_id,c.name,c.is_active,c.description,c.updated_at,l.budget_category_id,a.budget_category_id,l.amount from budget_category c join budget_line l on l.budget_category_id=c.id join budget_category_alias a on a.budget_category_id=c.id where c.id=11"))
            {
                assertTrue(rs.next()); identity=rs.getObject(1); assertNotNull(identity);
                assertEquals("Retained name",rs.getString(2)); assertFalse(rs.getBoolean(3)); assertEquals(" Exact description ",rs.getString(4));
                assertTrue(rs.getString(5).contains("123456")); assertEquals(11,rs.getLong(6)); assertEquals(11,rs.getLong(7)); assertEquals("12.3456",rs.getBigDecimal(8).toPlainString());
            }
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            try (var rs=sql.executeQuery("select portable_id from budget_category where id=11")) { assertTrue(rs.next()); assertEquals(identity,rs.getObject(1)); }
            assertThrows(SQLException.class, () -> sql.execute("insert into budget_category (code,name,portable_id) values ('DUP','Duplicate','"+identity+"')"));
            try (var rs=sql.executeQuery("select count(*) from information_schema.triggers where event_object_table='budget_category' and trigger_name like 'accounting_guard_%'"))
            { assertTrue(rs.next()); assertEquals(3,rs.getInt(1)); }
            try (var rs=sql.executeQuery("select effective_from,effective_to from budget_category where id=11"))
            { assertTrue(rs.next()); assertEquals("2020-01-01",rs.getString(1)); assertEquals("2025-12-31",rs.getString(2)); }
        }
    }
}
