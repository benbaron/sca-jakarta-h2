package org.nonprofitbookkeeping.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BankingPortableIdentityMigrationTest
{
    private static final List<String> TABLES = List.of(
            "bank",
            "company_bank_account",
            "bank_import_batch",
            "bank_statement_line",
            "import_issue",
            "bank_reconciliation_session",
            "bank_reconciliation_match");

    @Test
    public void backfillsExistingRowsAndDefaultsNewBankingRecords() throws Exception
    {
        String url = jdbcUrl("banking-portable-identity");
        migrateTo(url, "63");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            long companyId = scalarLong(statement, "SELECT id FROM company WHERE code = 'DEFAULT'");
            insertGraph(statement, companyId, 1000L, "first");
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            for (String table : TABLES)
            {
                assertEquals(0L, scalarLong(statement,
                        "SELECT COUNT(*) FROM " + table + " WHERE portable_id IS NULL"));
                assertEquals(1L, scalarLong(statement,
                        "SELECT COUNT(DISTINCT portable_id) FROM " + table));
            }

            UUID firstBank = portableId(statement, "bank", 1001L);
            UUID firstAccount = portableId(statement, "company_bank_account", 1002L);
            UUID firstBatch = portableId(statement, "bank_import_batch", 1003L);
            UUID firstLine = portableId(statement, "bank_statement_line", 1004L);
            UUID firstIssue = portableId(statement, "import_issue", 1005L);
            UUID firstSession = portableId(statement, "bank_reconciliation_session", 1006L);
            UUID firstMatch = portableId(statement, "bank_reconciliation_match", 1007L);

            long companyId = scalarLong(statement, "SELECT id FROM company WHERE code = 'DEFAULT'");
            insertGraph(statement, companyId, 2000L, "second");

            UUID secondBank = portableId(statement, "bank", 2001L);
            UUID secondAccount = portableId(statement, "company_bank_account", 2002L);
            UUID secondBatch = portableId(statement, "bank_import_batch", 2003L);
            UUID secondLine = portableId(statement, "bank_statement_line", 2004L);
            UUID secondIssue = portableId(statement, "import_issue", 2005L);
            UUID secondSession = portableId(statement, "bank_reconciliation_session", 2006L);
            UUID secondMatch = portableId(statement, "bank_reconciliation_match", 2007L);

            assertDistinct(firstBank, secondBank);
            assertDistinct(firstAccount, secondAccount);
            assertDistinct(firstBatch, secondBatch);
            assertDistinct(firstLine, secondLine);
            assertDistinct(firstIssue, secondIssue);
            assertDistinct(firstSession, secondSession);
            assertDistinct(firstMatch, secondMatch);

            assertDuplicateRejected(statement,
                    "INSERT INTO bank (id, company_id, name, portable_id) VALUES "
                            + "(3001, " + companyId + ", 'Duplicate Bank', UUID '" + firstBank + "')");
            assertDuplicateRejected(statement,
                    "INSERT INTO company_bank_account (id, company_id, name, portable_id) VALUES "
                            + "(3002, " + companyId + ", 'Duplicate Account', UUID '" + firstAccount + "')");
            assertDuplicateRejected(statement,
                    "INSERT INTO bank_import_batch "
                            + "(id, company_id, source_name, source_format, portable_id) VALUES "
                            + "(3003, " + companyId + ", 'duplicate.ofx', 'OFX', UUID '" + firstBatch + "')");
            assertDuplicateRejected(statement,
                    "INSERT INTO bank_statement_line "
                            + "(id, batch_id, company_id, source_row_number, deterministic_fingerprint, portable_id) VALUES "
                            + "(3004, 2003, " + companyId + ", 2, 'duplicate-fingerprint', UUID '" + firstLine + "')");
            assertDuplicateRejected(statement,
                    "INSERT INTO import_issue "
                            + "(id, batch_id, severity, code, message, portable_id) VALUES "
                            + "(3005, 2003, 'WARNING', 'DUPLICATE', 'Duplicate identity', UUID '" + firstIssue + "')");
            assertDuplicateRejected(statement,
                    "INSERT INTO bank_reconciliation_session "
                            + "(id, company_id, bank_account_id, statement_start_date, statement_end_date, portable_id) VALUES "
                            + "(3006, " + companyId + ", 2002, DATE '2026-03-01', DATE '2026-03-31', UUID '"
                            + firstSession + "')");
            assertDuplicateRejected(statement,
                    "INSERT INTO bank_reconciliation_match "
                            + "(id, session_id, statement_line_id, match_status, portable_id) VALUES "
                            + "(3007, 2006, 2004, 'MATCHED', UUID '" + firstMatch + "')");
        }
    }

    private static void insertGraph(Statement statement, long companyId, long baseId, String suffix)
            throws SQLException
    {
        long bankId = baseId + 1L;
        long accountId = baseId + 2L;
        long batchId = baseId + 3L;
        long lineId = baseId + 4L;
        long issueId = baseId + 5L;
        long sessionId = baseId + 6L;
        long matchId = baseId + 7L;

        statement.executeUpdate("INSERT INTO bank (id, company_id, name) VALUES ("
                + bankId + ", " + companyId + ", 'Bank " + suffix + "')");
        statement.executeUpdate("INSERT INTO company_bank_account "
                + "(id, company_id, bank_id, name) VALUES ("
                + accountId + ", " + companyId + ", " + bankId + ", 'Account " + suffix + "')");
        statement.executeUpdate("INSERT INTO bank_import_batch "
                + "(id, company_id, bank_account_id, source_name, source_hash, source_format) VALUES ("
                + batchId + ", " + companyId + ", " + accountId + ", '" + suffix
                + ".ofx', 'hash-" + suffix + "', 'OFX')");
        statement.executeUpdate("INSERT INTO bank_statement_line "
                + "(id, batch_id, company_id, bank_account_id, source_row_number, deterministic_fingerprint, "
                + "transaction_date, amount) VALUES ("
                + lineId + ", " + batchId + ", " + companyId + ", " + accountId
                + ", 1, 'fingerprint-" + suffix + "', DATE '2026-01-15', 10.00)");
        statement.executeUpdate("INSERT INTO import_issue "
                + "(id, batch_id, statement_line_id, source_row_number, severity, code, message) VALUES ("
                + issueId + ", " + batchId + ", " + lineId
                + ", 1, 'WARNING', 'TEST', 'Issue " + suffix + "')");
        statement.executeUpdate("INSERT INTO bank_reconciliation_session "
                + "(id, company_id, bank_account_id, statement_start_date, statement_end_date) VALUES ("
                + sessionId + ", " + companyId + ", " + accountId
                + ", DATE '2026-01-01', DATE '2026-01-31')");
        statement.executeUpdate("INSERT INTO bank_reconciliation_match "
                + "(id, session_id, statement_line_id, match_status) VALUES ("
                + matchId + ", " + sessionId + ", " + lineId + ", 'MATCHED')");
    }

    private static void assertDistinct(UUID first, UUID second)
    {
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
    }

    private static void assertDuplicateRejected(Statement statement, String sql)
    {
        assertThrows(SQLException.class, () -> statement.executeUpdate(sql));
    }

    private static UUID portableId(Statement statement, String table, long id) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(
                "SELECT portable_id FROM " + table + " WHERE id = " + id))
        {
            rows.next();
            return rows.getObject(1, UUID.class);
        }
    }

    private static void migrateTo(String url, String target)
    {
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private static void migrate(String url)
    {
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static Connection connect(String url) throws SQLException
    {
        return DriverManager.getConnection(url, "sa", "");
    }

    private static String jdbcUrl(String name)
    {
        return "jdbc:h2:mem:" + name + '-' + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
                + ";INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC\\;SET SCHEMA PUBLIC";
    }

    private static long scalarLong(Statement statement, String sql) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getLong(1);
        }
    }
}
