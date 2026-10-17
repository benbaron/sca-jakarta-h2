package org.nonprofitbookkeeping.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.persistence.DatabaseMigrationService;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NmrItemMigrationTest
{
    @Test
    void upgradePreservesUndesignatedRowsAndRejectsInvalidLinksAndRecoveryIsIdempotent() throws Exception
    {
        String url = "jdbc:h2:mem:nmr_item_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("81").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement())
        {
            sql.execute("INSERT INTO txn (id, txn_date, memo) VALUES (100,DATE '2026-01-01','Legacy fact')");
            sql.execute("INSERT INTO txn_supplemental_line (id,txn_id,line_order,kind,description,amount) VALUES (100,100,0,'OTHER_LIABILITY','Legacy detail',12.3456)");
            sql.execute("INSERT INTO counterparty (id,display_name,kind) VALUES (100,'Synthetic recipient','OTHER')");
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            try (var rows = sql.executeQuery("SELECT description,amount,nmr_recipient_id FROM txn_supplemental_line WHERE id=100"))
            {
                assertTrue(rows.next()); assertEquals("Legacy detail", rows.getString(1));
                assertEquals("12.3456", rows.getBigDecimal(2).toPlainString()); assertNull(rows.getObject(3));
            }
            assertThrows(SQLException.class, () -> sql.execute("UPDATE txn_supplemental_line SET nmr_recipient_id=100 WHERE id=100"));
            assertThrows(SQLException.class, () -> sql.execute("UPDATE txn_supplemental_line SET nmr_recipient_id=999 WHERE id=100"));
            sql.execute("DELETE FROM flyway_schema_history");
            DatabaseMigrationService.migrateJdbcUrl(url);
            try (var rows = sql.executeQuery("SELECT count(*),count(nmr_recipient_id) FROM txn_supplemental_line"))
            {
                assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); assertEquals(0, rows.getInt(2));
            }
        }
    }
}
