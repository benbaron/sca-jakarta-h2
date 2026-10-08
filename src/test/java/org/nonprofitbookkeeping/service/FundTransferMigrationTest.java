package org.nonprofitbookkeeping.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FundTransferMigrationTest
{
    @Test
    void upgradePreservesLegacyRowsAndEnforcesRequestIdentity() throws Exception
    {
        String url = "jdbc:h2:mem:transfer_upgrade_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").target("77").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement())
        {
            sql.execute("INSERT INTO fund (id, code, name, fund_type) VALUES (1, 'ONE', 'One', 'UNRESTRICTED'), (2, 'TWO', 'Two', 'DESIGNATED')");
            sql.execute("INSERT INTO fund_transfer (id, transfer_date, from_fund_id, to_fund_id, amount, status, memo) VALUES (1, DATE '2026-01-01', 1, 2, 12.5000, 'DRAFT', 'Legacy fact')");
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            try (var rows = sql.executeQuery("SELECT amount, memo, request_id, request_hash FROM fund_transfer WHERE id = 1"))
            {
                assertTrue(rows.next());
                assertEquals("12.5000", rows.getBigDecimal(1).toPlainString());
                assertEquals("Legacy fact", rows.getString(2));
                assertNull(rows.getObject(3));
                assertNull(rows.getString(4));
            }
            String request = UUID.randomUUID().toString();
            String insert = "INSERT INTO fund_transfer (id, transfer_date, from_fund_id, to_fund_id, amount, status, request_id) VALUES (%d, DATE '2026-02-01', 1, 2, 1, 'POSTED', '%s')";
            sql.execute(insert.formatted(2, request));
            assertThrows(SQLException.class, () -> sql.execute(insert.formatted(3, request)));
            sql.execute("INSERT INTO fund_transfer (id, transfer_date, from_fund_id, to_fund_id, amount, status) VALUES (4, DATE '2026-02-01', 1, 2, 1, 'DRAFT')");
            sql.execute("DELETE FROM flyway_schema_history");
            org.nonprofitbookkeeping.persistence.DatabaseMigrationService.migrateJdbcUrl(url);
            assertThrows(SQLException.class, () -> sql.execute(insert.formatted(5, request)));
            try (var rows = sql.executeQuery("SELECT request_id FROM fund_transfer WHERE id = 2"))
            {
                assertTrue(rows.next());
                assertEquals(request, rows.getObject(1).toString());
            }
            try (var rows = sql.executeQuery("SELECT count(*) FROM fund_transfer"))
            {
                assertTrue(rows.next());
                assertEquals(3, rows.getInt(1));
            }
        }
    }
}
