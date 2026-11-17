package org.nonprofitbookkeeping.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChartPortableIdentityMigrationTest extends MigrationTestConnections
{
    @Test
    void upgradePreservesChartAccountAndSelectionWithStableUniqueUuid() throws Exception
    {
        String url = "jdbc:h2:mem:chart_upgrade_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(migrationDataSource(url)).target("92").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement())
        {
            sql.execute("INSERT INTO chart_of_accounts (id,name,version,status) VALUES (901,'Retained chart','old','RETIRED')");
            sql.execute("INSERT INTO account (id,chart_id,code,name,account_type,normal_balance,is_posting,is_active) VALUES (902,901,'1000','Old checking','ASSET','DEBIT',TRUE,FALSE)");
            Flyway.configure().dataSource(migrationDataSource(url)).load().migrate();
            Object uuid;
            try (var rs = sql.executeQuery("select c.portable_id,c.name,c.version,c.status,a.chart_id,a.is_active from chart_of_accounts c join account a on a.chart_id=c.id where c.id=901"))
            {
                assertTrue(rs.next()); uuid = rs.getObject(1); assertNotNull(uuid);
                assertEquals("Retained chart", rs.getString(2)); assertEquals("old", rs.getString(3));
                assertEquals("RETIRED", rs.getString(4)); assertEquals(901L, rs.getLong(5)); assertFalse(rs.getBoolean(6));
            }
            Flyway.configure().dataSource(migrationDataSource(url)).load().migrate();
            try (var rs = sql.executeQuery("select portable_id from chart_of_accounts where id=901"))
            { assertTrue(rs.next()); assertEquals(uuid, rs.getObject(1)); }
            assertThrows(SQLException.class, () -> sql.execute("insert into chart_of_accounts (name,version,status,portable_id) values ('Duplicate','1','DRAFT','" + uuid + "')"));
        }
    }
}
