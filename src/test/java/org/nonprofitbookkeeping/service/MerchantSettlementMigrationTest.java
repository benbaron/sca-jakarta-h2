package org.nonprofitbookkeeping.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.persistence.DatabaseMigrationService;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MerchantSettlementMigrationTest
{
    @Test
    void upgradeAndHistoryRecoveryPreserveUnknownRowsExactObservationsAndComponentForeignKeys() throws Exception
    {
        String url = "jdbc:h2:mem:merchant_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("83").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement())
        {
            sql.execute("INSERT INTO chart_of_accounts (id,name,version,status) VALUES (100,'Legacy','1','ACTIVE')");
            sql.execute("INSERT INTO account (id,chart_id,code,name,account_type,subtype,normal_balance) VALUES (100,100,'1400','Receivable','ASSET','RECEIVABLE','DEBIT')");
            sql.execute("INSERT INTO fund (id,code,name,fund_type) VALUES (100,'GENERAL','General','UNRESTRICTED')");
            sql.execute("INSERT INTO txn (id,txn_date,memo) VALUES (100,DATE '2026-01-01','Legacy fact')");
            sql.execute("INSERT INTO txn_split (id,txn_id,account_id,fund_id,amount_signed) VALUES (100,100,100,100,12.3456),(101,100,100,100,12.3456)");
            sql.execute("INSERT INTO txn_supplemental_line (id,txn_id,line_order,kind,description,amount,item_id,txn_split_id,item_effect) VALUES (100,100,0,'RECEIVABLE','Legacy detail',12.3456,RANDOM_UUID(),100,'INCREASE')");
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            try (var rows = sql.executeQuery("SELECT amount,merchant_role,merchant_withheld_unconfirmed FROM txn_supplemental_line WHERE id=100"))
            {
                assertTrue(rows.next()); assertEquals("12.3456", rows.getBigDecimal(1).toPlainString()); assertNull(rows.getObject(2)); assertNull(rows.getObject(3));
            }
            assertThrows(SQLException.class, () -> sql.execute("UPDATE txn_supplemental_line SET merchant_role='SETTLEMENT'"));
            assertThrows(SQLException.class, () -> sql.execute("UPDATE txn_supplemental_line SET merchant_role='BAD'"));
            assertThrows(SQLException.class, () -> sql.execute("UPDATE txn_supplemental_line SET merchant_withheld_unconfirmed=1"));
            sql.execute("UPDATE txn_supplemental_line SET merchant_role='GROSS_RECEIPT',merchant_withheld_unconfirmed=1.2345,merchant_evidence_reference='external/observation'");
            assertThrows(SQLException.class, () -> sql.execute("UPDATE txn_supplemental_line SET merchant_withheld_unconfirmed=-1"));
            assertThrows(SQLException.class, () -> sql.execute("UPDATE txn_supplemental_line SET merchant_evidence_reference=' '"));
            sql.execute("INSERT INTO txn_supplemental_merchant_component VALUES (100,101,'PAYOUT')");
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO txn_supplemental_merchant_component VALUES (100,101,'FEE')"));
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO txn_supplemental_merchant_component VALUES (999,101,'FEE')"));
            sql.execute("DELETE FROM flyway_schema_history"); DatabaseMigrationService.migrateJdbcUrl(url);
            try (var rows = sql.executeQuery("SELECT amount,merchant_role,merchant_withheld_unconfirmed,merchant_evidence_reference FROM txn_supplemental_line"))
            {
                assertTrue(rows.next()); assertEquals("12.3456", rows.getBigDecimal(1).toPlainString()); assertEquals("GROSS_RECEIPT", rows.getString(2));
                assertEquals("1.2345", rows.getBigDecimal(3).toPlainString()); assertEquals("external/observation", rows.getString(4)); assertFalse(rows.next());
            }
            sql.execute("DELETE FROM txn_supplemental_line WHERE id=100");
            try (var rows = sql.executeQuery("SELECT count(*) FROM txn_supplemental_merchant_component")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
        }
    }
}
