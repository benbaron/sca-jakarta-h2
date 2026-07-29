package org.nonprofitbookkeeping.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FixedAssetPortableIdentityMigrationTest
{
    private static final long CHART_ID = 15_001L;
    private static final long COMPANY_ID = 15_001L;
    private static final long FUND_ID = 15_001L;
    private static final long ASSET_ACCOUNT_ID = 15_001L;
    private static final long ACCUMULATED_DEPRECIATION_ACCOUNT_ID = 15_002L;
    private static final long DEPRECIATION_EXPENSE_ACCOUNT_ID = 15_003L;

    @Test
    public void backfillsDefaultsRejectsDuplicatesAndPreservesAccountingFacts() throws Exception
    {
        String url = jdbcUrl("fixed-asset-portable-identity");
        migrateTo(url, "64");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            insertSupportGraph(statement);
            insertAssetAndRun(statement, 16_001L, 16_002L, 16_003L,
                    "First Pavilion", "2026-01-31", "1250.0000", "50.0000",
                    "104.1667", "First completed depreciation");
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            UUID firstAsset = portableId(statement, "fixed_asset", 16_001L);
            UUID firstRun = portableId(statement, "fixed_asset_depreciation_run", 16_003L);
            assertNotNull(firstAsset);
            assertNotNull(firstRun);

            assertEquals(COMPANY_ID, scalarLong(statement,
                    "SELECT company_id FROM fixed_asset WHERE id = 16001"));
            assertEquals(ASSET_ACCOUNT_ID, scalarLong(statement,
                    "SELECT asset_account_id FROM fixed_asset WHERE id = 16001"));
            assertEquals(ACCUMULATED_DEPRECIATION_ACCOUNT_ID, scalarLong(statement,
                    "SELECT accumulated_depreciation_account_id FROM fixed_asset WHERE id = 16001"));
            assertEquals(DEPRECIATION_EXPENSE_ACCOUNT_ID, scalarLong(statement,
                    "SELECT depreciation_expense_account_id FROM fixed_asset WHERE id = 16001"));
            assertEquals(FUND_ID, scalarLong(statement,
                    "SELECT fund_id FROM fixed_asset WHERE id = 16001"));
            assertEquals("First Pavilion", scalarString(statement,
                    "SELECT name FROM fixed_asset WHERE id = 16001"));
            assertEquals(new BigDecimal("1250.0000"), scalarDecimal(statement,
                    "SELECT acquisition_cost FROM fixed_asset WHERE id = 16001"));
            assertEquals(new BigDecimal("50.0000"), scalarDecimal(statement,
                    "SELECT salvage_value FROM fixed_asset WHERE id = 16001"));
            assertEquals(LocalDate.of(2026, 1, 1), scalarDate(statement,
                    "SELECT acquisition_date FROM fixed_asset WHERE id = 16001"));
            assertEquals(new BigDecimal("0.0000"), scalarDecimal(statement,
                    "SELECT opening_accumulated_depreciation FROM fixed_asset WHERE id = 16001"));
            assertEquals(60L, scalarLong(statement,
                    "SELECT useful_life_months FROM fixed_asset WHERE id = 16001"));
            assertEquals("STRAIGHT_LINE", scalarString(statement,
                    "SELECT depreciation_method FROM fixed_asset WHERE id = 16001"));
            assertEquals("ACTIVE", scalarString(statement,
                    "SELECT status FROM fixed_asset WHERE id = 16001"));
            assertEquals("Portable identity fixture", scalarString(statement,
                    "SELECT notes FROM fixed_asset WHERE id = 16001"));
            assertEquals(16_001L, scalarLong(statement,
                    "SELECT fixed_asset_id FROM fixed_asset_depreciation_run WHERE id = 16003"));
            assertEquals(16_002L, scalarLong(statement,
                    "SELECT transaction_id FROM fixed_asset_depreciation_run WHERE id = 16003"));
            assertEquals(new BigDecimal("104.1667"), scalarDecimal(statement,
                    "SELECT depreciation_amount FROM fixed_asset_depreciation_run WHERE id = 16003"));
            assertEquals(LocalDate.of(2026, 1, 31), scalarDate(statement,
                    "SELECT run_date FROM fixed_asset_depreciation_run WHERE id = 16003"));
            assertEquals("First completed depreciation", scalarString(statement,
                    "SELECT notes FROM fixed_asset_depreciation_run WHERE id = 16003"));
            assertEquals(COMPANY_ID, scalarLong(statement,
                    "SELECT company_id FROM txn WHERE id = 16002"));
            assertEquals("First Pavilion depreciation", scalarString(statement,
                    "SELECT memo FROM txn WHERE id = 16002"));

            insertAssetAndRun(statement, 17_001L, 17_002L, 17_003L,
                    "Second Pavilion", "2026-02-28", "840.0000", "0.0000",
                    "14.0000", "Second completed depreciation");

            UUID secondAsset = portableId(statement, "fixed_asset", 17_001L);
            UUID secondRun = portableId(statement, "fixed_asset_depreciation_run", 17_003L);
            assertDistinct(firstAsset, secondAsset);
            assertDistinct(firstRun, secondRun);

            insertAssetAndRun(statement, 18_001L, 18_002L, 18_003L,
                    "Third Pavilion", "2026-03-31", "500.0000", "0.0000",
                    "8.3333", "Third completed depreciation");
            UUID thirdAsset = portableId(statement, "fixed_asset", 18_001L);
            UUID thirdRun = portableId(statement, "fixed_asset_depreciation_run", 18_003L);
            assertDistinct(secondAsset, thirdAsset);
            assertDistinct(secondRun, thirdRun);

            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    assetInsertSql(19_001L, "Duplicate asset identity", "2026-04-01",
                            "500.0000", "0.0000", firstAsset)));

            statement.executeUpdate(assetInsertSql(19_002L, "Fourth Pavilion", "2026-04-01",
                    "500.0000", "0.0000"));
            statement.executeUpdate(txnInsertSql(19_003L, "Fourth depreciation transaction", "2026-04-30"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO fixed_asset_depreciation_run "
                            + "(id, fixed_asset_id, run_date, depreciation_amount, transaction_id, notes, portable_id) VALUES "
                            + "(19004, 19002, DATE '2026-04-30', 8.3333, 19003, "
                            + "'Duplicate run identity', UUID '" + firstRun + "')"));
        }
    }

    @Test
    public void toleratesExistingPortableIdentityColumnsAndConstraintsDuringRecovery() throws Exception
    {
        String url = jdbcUrl("fixed-asset-portable-identity-recovery");
        migrateTo(url, "64");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            insertSupportGraph(statement);
            insertAssetAndRun(statement, 16_001L, 16_002L, 16_003L,
                    "Recovery Pavilion", "2026-01-31", "600.0000", "0.0000",
                    "10.0000", "Recovery completed depreciation");

            statement.executeUpdate("ALTER TABLE fixed_asset ADD COLUMN portable_id UUID");
            statement.executeUpdate("UPDATE fixed_asset SET portable_id = RANDOM_UUID()");

            statement.executeUpdate("ALTER TABLE fixed_asset_depreciation_run "
                    + "ADD COLUMN portable_id UUID DEFAULT RANDOM_UUID()");
            statement.executeUpdate("UPDATE fixed_asset_depreciation_run SET portable_id = RANDOM_UUID() "
                    + "WHERE portable_id IS NULL");
            statement.executeUpdate("ALTER TABLE fixed_asset_depreciation_run "
                    + "ALTER COLUMN portable_id SET NOT NULL");
            statement.executeUpdate("ALTER TABLE fixed_asset_depreciation_run "
                    + "ADD CONSTRAINT uq_fixed_asset_depreciation_run_portable_id UNIQUE (portable_id)");
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            assertNotNull(portableId(statement, "fixed_asset", 16_001L));
            assertNotNull(portableId(statement, "fixed_asset_depreciation_run", 16_003L));
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                            + "WHERE lower(table_name) = 'fixed_asset' "
                            + "AND lower(constraint_name) = 'uq_fixed_asset_portable_id'"));
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                            + "WHERE lower(table_name) = 'fixed_asset_depreciation_run' "
                            + "AND lower(constraint_name) = 'uq_fixed_asset_depreciation_run_portable_id'"));
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '65' AND success = TRUE"));

            insertAssetAndRun(statement, 17_001L, 17_002L, 17_003L,
                    "Recovery New Pavilion", "2026-02-28", "700.0000", "0.0000",
                    "11.6667", "Recovery default depreciation");
            assertNotNull(portableId(statement, "fixed_asset", 17_001L));
            assertNotNull(portableId(statement, "fixed_asset_depreciation_run", 17_003L));
            assertEquals("Recovery Pavilion", scalarString(statement,
                    "SELECT name FROM fixed_asset WHERE id = 16001"));
            assertEquals(16_002L, scalarLong(statement,
                    "SELECT transaction_id FROM fixed_asset_depreciation_run WHERE id = 16003"));
        }
    }

    private static void insertSupportGraph(Statement statement) throws SQLException
    {
        statement.executeUpdate("INSERT INTO chart_of_accounts "
                + "(id, name, version, status) VALUES (15001, 'Portable Asset Chart', '1', 'ACTIVE')");
        statement.executeUpdate("INSERT INTO company "
                + "(id, code, display_name, active_chart_of_accounts_id) VALUES "
                + "(15001, 'FAPORT', 'Portable Asset Company', 15001)");
        statement.executeUpdate("UPDATE chart_of_accounts SET company_id = 15001 WHERE id = 15001");
        statement.executeUpdate("INSERT INTO fund "
                + "(id, company_id, code, name, fund_type) VALUES "
                + "(15001, 15001, 'OPERATING', 'Operating Fund', 'UNRESTRICTED')");
        statement.executeUpdate("INSERT INTO account "
                + "(id, chart_id, code, name, account_type, subtype, normal_balance) VALUES "
                + "(15001, 15001, '1500', 'Pavilion Equipment', 'ASSET', 'FIXED_ASSET', 'DEBIT')");
        statement.executeUpdate("INSERT INTO account "
                + "(id, chart_id, code, name, account_type, subtype, normal_balance) VALUES "
                + "(15002, 15001, '1590', 'Accumulated Depreciation', 'ASSET', 'FIXED_ASSET', 'CREDIT')");
        statement.executeUpdate("INSERT INTO account "
                + "(id, chart_id, code, name, account_type, normal_balance) VALUES "
                + "(15003, 15001, '6100', 'Depreciation Expense', 'EXPENSE', 'DEBIT')");
    }

    private static void insertAssetAndRun(Statement statement, long assetId, long txnId, long runId,
            String name, String runDate, String acquisitionCost, String salvageValue,
            String depreciationAmount, String notes) throws SQLException
    {
        statement.executeUpdate(assetInsertSql(assetId, name, "2026-01-01",
                acquisitionCost, salvageValue));
        statement.executeUpdate(txnInsertSql(txnId, name + " depreciation", runDate));
        statement.executeUpdate("INSERT INTO fixed_asset_depreciation_run "
                + "(id, fixed_asset_id, run_date, depreciation_amount, transaction_id, notes) VALUES ("
                + runId + ", " + assetId + ", DATE '" + runDate + "', " + depreciationAmount
                + ", " + txnId + ", '" + notes.replace("'", "''") + "')");
    }

    private static String assetInsertSql(long assetId, String name, String acquisitionDate,
            String acquisitionCost, String salvageValue)
    {
        return assetInsertSql(assetId, name, acquisitionDate, acquisitionCost, salvageValue, null);
    }

    private static String assetInsertSql(long assetId, String name, String acquisitionDate,
            String acquisitionCost, String salvageValue, UUID portableId)
    {
        String portableColumn = portableId == null ? "" : ", portable_id";
        String portableValue = portableId == null ? "" : ", UUID '" + portableId + "'";
        return "INSERT INTO fixed_asset "
                + "(id, company_id, asset_account_id, accumulated_depreciation_account_id, "
                + "depreciation_expense_account_id, fund_id, name, acquisition_date, acquisition_cost, "
                + "salvage_value, useful_life_months, depreciation_method, opening_accumulated_depreciation, "
                + "status, notes" + portableColumn + ") VALUES ("
                + assetId + ", " + COMPANY_ID + ", " + ASSET_ACCOUNT_ID + ", "
                + ACCUMULATED_DEPRECIATION_ACCOUNT_ID + ", " + DEPRECIATION_EXPENSE_ACCOUNT_ID + ", "
                + FUND_ID + ", '" + name.replace("'", "''") + "', DATE '" + acquisitionDate + "', "
                + acquisitionCost + ", " + salvageValue
                + ", 60, 'STRAIGHT_LINE', 0.0000, 'ACTIVE', 'Portable identity fixture'"
                + portableValue + ")";
    }

    private static String txnInsertSql(long txnId, String memo, String txnDate)
    {
        return "INSERT INTO txn (id, company_id, txn_date, memo, status) VALUES ("
                + txnId + ", " + COMPANY_ID + ", DATE '" + txnDate + "', '"
                + memo.replace("'", "''") + "', 'ENTERED')";
    }

    private static void assertDistinct(UUID first, UUID second)
    {
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
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

    private static long scalarLong(Statement statement, String sql) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static String scalarString(Statement statement, String sql) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getString(1);
        }
    }

    private static BigDecimal scalarDecimal(Statement statement, String sql) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getBigDecimal(1);
        }
    }

    private static LocalDate scalarDate(Statement statement, String sql) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getObject(1, LocalDate.class);
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
}
