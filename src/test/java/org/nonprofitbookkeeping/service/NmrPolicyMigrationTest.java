package org.nonprofitbookkeeping.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.persistence.DatabaseMigrationService;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NmrPolicyMigrationTest
{
    @Test
    void upgradeAndUntrackedRecoveryPreserveLegacyFactsAndSeedNoPolicy() throws Exception
    {
        String url = "jdbc:h2:mem:nmr_upgrade_" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("80").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement())
        {
            sql.execute("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (1, 'Legacy chart', '1', 'ACTIVE')");
            sql.execute("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (1,1,'1000','Bank','ASSET','DEBIT')");
            sql.execute("INSERT INTO fund (id, code, name, fund_type) VALUES (1,'GENERAL','General','UNRESTRICTED')");
            sql.execute("INSERT INTO txn (id, txn_date, memo) VALUES (1,DATE '2026-01-01','Preserve existing fact')");
            sql.execute("INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) VALUES (1,1,1,1,12.3456)");
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            try (var rows = sql.executeQuery("SELECT count(*) FROM nmr_policy_version"))
            {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            sql.execute("INSERT INTO company (id, code, display_name) VALUES (100, 'POLICY-TEST', 'Policy test')");
            sql.execute("""
                    INSERT INTO nmr_policy_version
                    (portable_id, company_id, request_hash, saved_at, saved_by, policy_code, category,
                     valid_from, rate, currency, date_basis, rounding_rule, eligibility, exemptions,
                     recipient, reporting, merchant_path, source_reference, authority, source_date)
                    VALUES (RANDOM_UUID(), 100, 'test-hash', CURRENT_TIMESTAMP, 'test-actor', 'SOURCE', 'EXPLICIT',
                     DATE '2026-01-01', 15.1250, 'USD', 'Source basis', 'Source rounding', 'Source eligibility',
                     'Source exemptions', 'Source recipient', 'Source reporting', 'Source payment path',
                     'Test source', 'Test authority', DATE '2025-12-31')
                    """);
            assertThrows(SQLException.class, () -> sql.execute("UPDATE nmr_policy_version SET rate=-1"));
            assertThrows(SQLException.class, () -> sql.execute("UPDATE nmr_policy_version SET valid_to=DATE '2025-12-31'"));
            assertThrows(SQLException.class, () -> sql.execute("UPDATE nmr_policy_version SET company_id=99999"));
            assertThrows(SQLException.class, () -> sql.execute("UPDATE nmr_policy_version SET source_reference=' '"));
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            sql.execute("DELETE FROM flyway_schema_history");
            DatabaseMigrationService.migrateJdbcUrl(url);
            try (var rows = sql.executeQuery("SELECT rate, source_reference FROM nmr_policy_version"))
            {
                assertTrue(rows.next());
                assertEquals("15.1250", rows.getBigDecimal(1).toPlainString());
                assertEquals("Test source", rows.getString(2));
                assertFalse(rows.next());
            }
            try (var rows = sql.executeQuery("SELECT s.amount_signed, t.memo FROM txn_split s JOIN txn t ON t.id=s.txn_id WHERE s.id=1"))
            {
                assertTrue(rows.next());
                assertEquals("12.3456", rows.getBigDecimal(1).toPlainString());
                assertEquals("Preserve existing fact", rows.getString(2));
            }
        }
    }
}
