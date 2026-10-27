package org.nonprofitbookkeeping.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountingWriteGuardMigrationTest
{
    @Test
    void upgradePreservesHistoricalFactsAndGuardsEveryApplicationTable() throws Exception
    {
        String url = "jdbc:h2:mem:guard" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("89").load().migrate();
        String id = UUID.randomUUID().toString();
        try (var c = DriverManager.getConnection(url, "sa", ""); var s = c.createStatement())
        {
            s.execute("insert into company (id,code,display_name) values (7001,'GUARD','Preserved company')");
            s.execute("insert into period_close_range (id,company_id,company_code,start_date,end_date,range_kind,status,closed_by) values ('" + id + "',7001,'GUARD','2026-01-01','2026-01-31','CUSTOM','CLOSED','historical')");
            s.execute("create table guard_probe_parent(id integer primary key)");
            s.execute("create table guard_probe_child(id integer primary key, parent_id integer references guard_probe_parent(id) on delete cascade)");
            s.execute("insert into guard_probe_parent values(1)"); s.execute("insert into guard_probe_child values(1,1)");
        }
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();
        try (var c = DriverManager.getConnection(url, "sa", ""); var s = c.createStatement())
        {
            try (var r = s.executeQuery("select display_name from company where id=7001")) { assertTrue(r.next()); assertEquals("Preserved company", r.getString(1)); }
            try (var r = s.executeQuery("select closed_by from period_close_range where id='" + id + "'")) { assertTrue(r.next()); assertEquals("historical", r.getString(1)); }
            s.executeUpdate("merge into guard_probe_parent key(id) values(1)");
            s.executeUpdate("delete from guard_probe_parent where id=1");
            try (var r = s.executeQuery("select count(*) from guard_probe_child")) { assertTrue(r.next()); assertEquals(0, r.getInt(1)); }
            assertTrue(c.getAutoCommit());
            c.setAutoCommit(false);
            s.executeUpdate("update company set display_name='Must roll back' where id=7001"); c.rollback();
            assertFalse(c.getAutoCommit());
            try (var r = s.executeQuery("select display_name from company where id=7001")) { assertTrue(r.next()); assertEquals("Preserved company", r.getString(1)); }
            try (var r = s.executeQuery("""
                    select t.table_name from information_schema.tables t where lower(t.table_schema)='public' and t.table_type='BASE TABLE'
                    and lower(t.table_name) not in ('accounting_write_guard','flyway_schema_history','security_event')
                    and not exists (select 1 from information_schema.triggers g where g.event_object_table=t.table_name and lower(g.trigger_name) like 'accounting_guard_%')
                    """)) { assertFalse(r.next(), "Every source table must participate in the transaction guard"); }
            try (var r = s.executeQuery("""
                    select event_object_table from information_schema.triggers where lower(trigger_name) like 'accounting_guard_%'
                    group by event_object_table having count(distinct event_manipulation) <> 3
                    or min(action_timing) <> 'BEFORE' or min(action_orientation) <> 'STATEMENT'
                    """)) { assertFalse(r.next(), "Guard triggers must run before INSERT/UPDATE/DELETE statements"); }
            c.setAutoCommit(false); AccountingWriteGuardTrigger.lock(c);
            var pool = Executors.newSingleThreadExecutor(); var started = new CountDownLatch(1);
            try
            {
                Future<?> writer = pool.submit(() ->
                {
                    try (var other = DriverManager.getConnection(url, "sa", ""); var statement = other.createStatement())
                    {
                        statement.execute("set lock_timeout 10000"); started.countDown();
                        statement.executeUpdate("update company set display_name='Writer after guard' where id=7001");
                    }
                    catch (Exception ex) { throw new RuntimeException(ex); }
                });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> writer.get(150, TimeUnit.MILLISECONDS));
                c.rollback(); writer.get(5, TimeUnit.SECONDS);
            }
            finally { c.rollback(); pool.shutdownNow(); }
            c.setAutoCommit(true);
            s.executeUpdate("delete from accounting_write_guard");
            assertThrows(java.sql.SQLException.class, () -> s.executeUpdate("update company set display_name='Must not commit' where id=7001"));
        }
    }
}
