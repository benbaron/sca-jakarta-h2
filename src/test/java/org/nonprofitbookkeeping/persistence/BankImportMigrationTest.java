package org.nonprofitbookkeeping.persistence;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BankImportMigrationTest
{
    @Test
    public void createsImportBatchStatementLineAndIssueTablesWithConstraints() throws Exception
    {
        String jdbcUrl = jdbcUrl("bank-import-model");
        DatabaseMigrationService.migrateJdbcUrl(jdbcUrl);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement())
        {
            statement.executeUpdate("""
                    INSERT INTO bank_import_batch (id, company_id, source_name, source_hash, source_format, status, total_line_count)
                    VALUES (1, 1, 'march.ofx', 'hash-1', 'OFX', 'IMPORTED', 1)
                    """);
            statement.executeUpdate("""
                    INSERT INTO bank_statement_line (id, batch_id, company_id, source_row_number, source_transaction_id, deterministic_fingerprint, transaction_date, posted_date, amount, transaction_type, name, memo)
                    VALUES (1, 1, 1, 1, 'FIT-1', 'fp-1', DATE '2026-03-15', DATE '2026-03-16', -25.7500, 'DEBIT', 'Vendor', 'Supplies')
                    """);
            statement.executeUpdate("""
                    INSERT INTO import_issue (batch_id, statement_line_id, source_row_number, severity, code, message)
                    VALUES (1, 1, 1, 'WARNING', 'PROBABLE_DUPLICATE', 'Possible duplicate transaction')
                    """);

            assertEquals(1L, scalarLong(statement, "SELECT COUNT(*) FROM bank_import_batch"));
            assertEquals(1L, scalarLong(statement, "SELECT COUNT(*) FROM bank_statement_line WHERE amount = -25.7500"));
            assertEquals(1L, scalarLong(statement, "SELECT COUNT(*) FROM import_issue WHERE severity = 'WARNING'"));
            assertEquals(1L, scalarLong(statement, """
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE LOWER(TABLE_NAME) = 'bank_import_batch' AND LOWER(COLUMN_NAME) = 'source_variant'
                    """));
            assertEquals(1L, scalarLong(statement, """
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE LOWER(TABLE_NAME) = 'bank_statement_line' AND LOWER(COLUMN_NAME) = 'correction_action'
                    """));
            assertEquals(1L, scalarLong(statement, """
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE LOWER(TABLE_NAME) = 'bank_csv_mapping_profile'
                    """));
            assertEquals(1L, scalarLong(statement, """
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE LOWER(TABLE_NAME) = 'bank_import_batch'
                      AND LOWER(COLUMN_NAME) = 'source_external_id'
                    """));
            assertEquals(2L, scalarLong(statement, """
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE LOWER(TABLE_NAME) IN ('bank_import_batch', 'bank_statement_line')
                      AND LOWER(COLUMN_NAME) = 'source_external_id'
                    """));
            assertEquals(1L, scalarLong(statement, """
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE LOWER(TABLE_NAME) = 'bank_statement_line'
                      AND LOWER(COLUMN_NAME) = 'source_payee_id'
                    """));

            assertThrows(Exception.class, () -> statement.executeUpdate("""
                    INSERT INTO bank_statement_line (batch_id, company_id, source_row_number, deterministic_fingerprint, transaction_date, amount)
                    VALUES (1, 1, 1, 'fp-2', DATE '2026-03-15', 10.0000)
                    """));
            assertThrows(Exception.class, () -> statement.executeUpdate("""
                    INSERT INTO bank_statement_line (batch_id, company_id, source_row_number, deterministic_fingerprint, transaction_date, amount)
                    VALUES (1, 1, 2, 'fp-1', DATE '2026-03-15', 10.0000)
                    """));
            statement.executeUpdate("""
                    INSERT INTO bank_statement_line (batch_id, company_id, source_row_number, deterministic_fingerprint, transaction_date, amount, status)
                    VALUES (1, 1, 3, 'fp-3', NULL, 0.0000, 'ERROR')
                    """);
            assertEquals(1L, scalarLong(statement, "SELECT COUNT(*) FROM bank_statement_line WHERE source_row_number = 3 AND transaction_date IS NULL AND amount = 0.0000 AND status = 'ERROR'"));
            assertThrows(Exception.class, () -> statement.executeUpdate("""
                    INSERT INTO import_issue (batch_id, source_row_number, severity, code, message)
                    VALUES (1, 2, 'BLOCKER', 'BAD', 'bad severity')
                    """));
        }
    }

    private static String jdbcUrl(String name)
    {
        return "jdbc:h2:mem:" + name + '-' + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL"
                + ";DATABASE_TO_LOWER=TRUE"
                + ";DEFAULT_NULL_ORDERING=HIGH"
                + ";DB_CLOSE_DELAY=-1"
                + ";INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC\\;SET SCHEMA PUBLIC";
    }

    private static long scalarLong(Statement statement, String sql) throws Exception
    {
        try (ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getLong(1);
        }
    }
}
