package org.nonprofitbookkeeping.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JournalInformationMigrationTest extends MigrationTestConnections
{
    @Test
    void v93UpgradeAddsOnlyNullableInformationAndPreservesLegacyTransactionIdentity() throws Exception
    {
        String url = "jdbc:h2:mem:journal_information_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(migrationDataSource(url)).target("93").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement())
        {
            sql.executeUpdate("insert into txn(id,txn_date,memo,status) values (99001,DATE '2025-12-31','Retained history','ENTERED')");
            Object portable;
            try (var result = sql.executeQuery("select portable_id from txn where id=99001"))
            {
                assertTrue(result.next());
                portable = result.getObject(1);
            }
            Flyway.configure().dataSource(migrationDataSource(url)).load().migrate();
            try (var result = sql.executeQuery("select portable_id,txn_date,memo,status,transfer_or_check_reference,source_statement_date,incoming_check_transfer_date from txn where id=99001"))
            {
                assertTrue(result.next());
                assertEquals(portable, result.getObject(1));
                assertEquals("2025-12-31", result.getDate(2).toString());
                assertEquals("Retained history", result.getString(3));
                assertEquals("ENTERED", result.getString(4));
                assertNull(result.getString(5));
                assertNull(result.getDate(6));
                assertNull(result.getDate(7));
            }
            sql.executeUpdate("update txn set transfer_or_check_reference='000042',source_statement_date=DATE '2026-01-05',incoming_check_transfer_date=DATE '2025-12-29' where id=99001");
            Flyway.configure().dataSource(migrationDataSource(url)).load().migrate();
            try (var result = sql.executeQuery("select transfer_or_check_reference,source_statement_date,incoming_check_transfer_date from txn where id=99001"))
            {
                assertTrue(result.next());
                assertEquals("000042", result.getString(1));
                assertEquals("2026-01-05", result.getDate(2).toString());
                assertEquals("2025-12-29", result.getDate(3).toString());
            }
        }
    }
}
