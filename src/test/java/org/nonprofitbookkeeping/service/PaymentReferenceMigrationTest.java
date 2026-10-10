package org.nonprofitbookkeeping.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PaymentReferenceMigrationTest
{
    @Test
    void upgradeAndUntrackedRecoveryPreserveExistingLinesAndReferences() throws Exception
    {
        String url = "jdbc:h2:mem:payments_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("78").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement())
        {
            sql.execute("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (1, 'Test', '1', 'ACTIVE')");
            sql.execute("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (1,1,'1000','Bank','ASSET','DEBIT'), (2,1,'1010','Other bank','ASSET','DEBIT')");
            sql.execute("INSERT INTO fund (id, code, name, fund_type) VALUES (1,'GENERAL','General','UNRESTRICTED')");
            sql.execute("INSERT INTO txn (id, txn_date, memo) VALUES (1,DATE '2026-01-01','Check 000999 narrative')");
            sql.execute("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (1,1,1,1,-10)");
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            try (var rows = sql.executeQuery("SELECT amount_signed, payment_method FROM txn_split WHERE id=1"))
            {
                assertTrue(rows.next()); assertEquals("-10.0000", rows.getBigDecimal(1).toPlainString()); assertNull(rows.getString(2));
            }
            sql.execute("UPDATE txn_split SET payment_method='CHECK', payment_reference='000123', payment_check_key='000123' WHERE id=1");
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO txn_split (txn_id, account_id, fund_id, amount_signed, payment_method, payment_reference, payment_check_key) VALUES (1,1,1,-10,'CHECK','000123','000123')"));
            sql.execute("INSERT INTO txn_split (txn_id, account_id, fund_id, amount_signed, payment_method, payment_reference, payment_check_key) VALUES (1,2,1,-10,'CHECK','000123','000123')");
            assertThrows(SQLException.class, () -> sql.execute("UPDATE txn_split SET payment_delivered_on=DATE '2026-01-02' WHERE id=1"));
            sql.execute("DELETE FROM flyway_schema_history");
            org.nonprofitbookkeeping.persistence.DatabaseMigrationService.migrateJdbcUrl(url);
            try (var rows = sql.executeQuery("SELECT payment_reference, payment_check_key FROM txn_split WHERE id=1"))
            {
                assertTrue(rows.next()); assertEquals("000123", rows.getString(1)); assertEquals("000123", rows.getString(2));
            }
        }
    }
}
