package org.nonprofitbookkeeping.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.persistence.DatabaseMigrationService;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NmrSettlementMigrationTest
{
    @Test
    void upgradePreservesAmountsAndUnknownClassificationAndRecoveryRetainsNewEvidence() throws Exception
    {
        String url = "jdbc:h2:mem:nmr_settlement_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("82").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement())
        {
            sql.execute("INSERT INTO chart_of_accounts (id,name,version,status) VALUES (100,'Legacy','1','ACTIVE')");
            sql.execute("INSERT INTO account (id,chart_id,code,name,account_type,subtype,normal_balance) VALUES (100,100,'2300','Liability','LIABILITY','OTHER_LIABILITY','CREDIT')");
            sql.execute("INSERT INTO fund (id,code,name,fund_type) VALUES (100,'GENERAL','General','UNRESTRICTED')");
            sql.execute("INSERT INTO txn (id,txn_date,memo) VALUES (100,DATE '2026-01-01','Legacy fact')");
            sql.execute("INSERT INTO txn_split (id,txn_id,account_id,fund_id,amount_signed) VALUES (100,100,100,100,12.3456)");
            sql.execute("INSERT INTO counterparty (id,display_name,kind) VALUES (100,'Synthetic recipient','OTHER')");
            sql.execute("INSERT INTO txn_supplemental_line (id,txn_id,line_order,kind,description,amount,item_id,txn_split_id,item_effect,nmr_recipient_id) VALUES (100,100,0,'OTHER_LIABILITY','Legacy detail',12.3456,RANDOM_UUID(),100,'INCREASE',100)");
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            try (var rows = sql.executeQuery("SELECT amount,nmr_movement,nmr_evidence_reference FROM txn_supplemental_line WHERE id=100"))
            {
                assertTrue(rows.next()); assertEquals("12.3456", rows.getBigDecimal(1).toPlainString());
                assertNull(rows.getObject(2)); assertNull(rows.getObject(3));
            }
            assertThrows(SQLException.class, () -> sql.execute("UPDATE txn_supplemental_line SET nmr_movement='UNKNOWN'"));
            assertThrows(SQLException.class, () -> sql.execute("UPDATE txn_supplemental_line SET nmr_movement='BRANCH_REMITTANCE'"));
            assertThrows(SQLException.class, () -> sql.execute("UPDATE txn_supplemental_line SET nmr_evidence_reference=' '"));
            sql.execute("UPDATE txn_supplemental_line SET nmr_movement='COLLECTION',nmr_evidence_reference='external/reference'");
            assertThrows(SQLException.class, () -> sql.execute("UPDATE txn_supplemental_line SET nmr_recipient_id=NULL"));
            sql.execute("DELETE FROM flyway_schema_history"); DatabaseMigrationService.migrateJdbcUrl(url);
            try (var rows = sql.executeQuery("SELECT amount,nmr_movement,nmr_evidence_reference FROM txn_supplemental_line"))
            {
                assertTrue(rows.next()); assertEquals("12.3456", rows.getBigDecimal(1).toPlainString());
                assertEquals("COLLECTION", rows.getString(2)); assertEquals("external/reference", rows.getString(3)); assertFalse(rows.next());
            }
        }
    }
}
