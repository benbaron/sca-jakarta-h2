package org.nonprofitbookkeeping.persistence;

import org.flywaydb.core.Flyway;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Runs database migrations before Hibernate validates the schema. */
public final class DatabaseMigrationService
{
    private static final String SCHEMA_NAME = "PUBLIC";
    private static final String HISTORY_TABLE = "flyway_schema_history";

    private DatabaseMigrationService()
    {
    }

    public static void migrate(Path databaseFile)
    {
        if (databaseFile == null)
        {
            throw new IllegalArgumentException("databaseFile is required");
        }
        migrateJdbcUrl(jdbcUrlFor(databaseFile));
    }

    public static void migrateJdbcUrl(String jdbcUrl)
    {
        if (jdbcUrl == null || jdbcUrl.isBlank())
        {
            throw new IllegalArgumentException("jdbcUrl is required");
        }

        System.err.println("[NPBK] Flyway migration starting.");
        System.err.println("[NPBK] JDBC URL: " + redactJdbcUrl(jdbcUrl));
        try
        {
            Optional<String> untrackedVersion = detectUntrackedSchemaVersion(jdbcUrl);
            if (untrackedVersion.isPresent())
            {
                String version = untrackedVersion.get();
                System.err.println("[NPBK] Existing application schema has no usable Flyway history; "
                        + "recording a non-destructive baseline at version " + version + ".");
                Flyway.configure()
                        .dataSource(jdbcUrl, "sa", "")
                        .locations("classpath:db/migration")
                        .baselineVersion(version)
                        .baselineDescription("Recovered existing application schema")
                        .load()
                        .baseline();
            }

            Flyway.configure()
                    .dataSource(jdbcUrl, "sa", "")
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();
            System.err.println("[NPBK] Flyway migration complete.");
        }
        catch (RuntimeException ex)
        {
            System.err.println("[NPBK] Flyway migration failed: " + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace(System.err);
            throw ex;
        }
    }

    private static Optional<String> detectUntrackedSchemaVersion(String jdbcUrl)
    {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", ""))
        {
            if (hasSuccessfulVersionedHistory(connection))
            {
                return Optional.empty();
            }

            List<MigrationProbe> probes = migrationProbes();
            boolean anyApplicationObject = probes.stream().anyMatch(probe -> probe.started(connection));
            if (!anyApplicationObject)
            {
                return Optional.empty();
            }

            String highestCompleteVersion = null;
            for (int index = 0; index < probes.size(); index++)
            {
                MigrationProbe probe = probes.get(index);
                if (probe.complete(connection))
                {
                    highestCompleteVersion = probe.version();
                    continue;
                }

                boolean currentOrLaterObjectsExist = false;
                for (int later = index; later < probes.size(); later++)
                {
                    if (probes.get(later).started(connection))
                    {
                        currentOrLaterObjectsExist = true;
                        break;
                    }
                }

                if (currentOrLaterObjectsExist)
                {
                    throw new IllegalStateException(
                            "Database contains a partial or non-contiguous untracked schema near migration "
                                    + probe.version()
                                    + ". No tables were deleted or recreated. Restore the Flyway history from backup "
                                    + "or repair the schema deliberately before retrying.");
                }
                break;
            }

            if (highestCompleteVersion == null)
            {
                throw new IllegalStateException(
                        "Database contains application tables but does not match the complete version 1 schema. "
                                + "No tables were deleted or recreated.");
            }
            return Optional.of(highestCompleteVersion);
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not inspect existing database migration state", ex);
        }
    }

    private static boolean hasSuccessfulVersionedHistory(Connection connection) throws SQLException
    {
        String actualHistoryTable = findTableName(connection, HISTORY_TABLE);
        if (actualHistoryTable == null)
        {
            return false;
        }

        String successColumn = findColumnName(connection, actualHistoryTable, "success");
        String versionColumn = findColumnName(connection, actualHistoryTable, "version");
        if (successColumn == null || versionColumn == null)
        {
            throw new IllegalStateException("Existing Flyway history table has an unexpected structure");
        }

        String sql = "SELECT COUNT(*) FROM " + quoteIdentifier(SCHEMA_NAME) + "."
                + quoteIdentifier(actualHistoryTable)
                + " WHERE " + quoteIdentifier(successColumn) + " = TRUE"
                + " AND " + quoteIdentifier(versionColumn) + " IS NOT NULL";
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getLong(1) > 0;
        }
    }

    private static List<MigrationProbe> migrationProbes()
    {
        List<MigrationProbe> probes = new ArrayList<>();
        probes.add(MigrationProbe.tables("1",
                "chart_of_accounts", "account", "account_alias", "report_section",
                "account_report_section", "schedule_kind", "account_schedule_requirement",
                "fund", "fund_alias", "counterparty", "merchant", "activity", "txn",
                "txn_split", "fund_transfer"));
        probes.add(MigrationProbe.tables("2", "schedule_item", "schedule_link"));
        probes.add(MigrationProbe.tables("3", "account_subtype_schedule_default"));
        probes.add(MigrationProbe.tables("4", "journal_transaction", "journal_posting_line",
                "open_item_snapshot", "open_item_transition"));
        probes.add(MigrationProbe.columnsAndConstraints("5",
                List.of(new ColumnMarker("open_item_snapshot", "version")),
                List.of("fk_open_item_snapshot_last_txn", "fk_open_item_transition_trigger_txn")));
        probes.add(MigrationProbe.tables("6", "reconciliation_run", "period_close_run"));
        probes.add(MigrationProbe.constraints("7", "ck_reconciliation_run_status",
                "ck_reconciliation_run_bank_format", "ck_period_close_run_status"));
        probes.add(MigrationProbe.tablesAndConstraints("8",
                List.of("approval_audit_record"), List.of("chk_approval_audit_decision")));
        probes.add(MigrationProbe.tablesAndColumns("45",
                List.of("budget_category", "budget_category_alias"),
                List.of(new ColumnMarker("txn_split", "budget_category_id"))));
        probes.add(MigrationProbe.tables("46", "company", "company_bank_account", "company_tax_profile",
                "app_user", "app_role", "user_company_role"));
        probes.add(MigrationProbe.tables("47", "accounting_period", "period_reopen_event", "audit_event"));
        probes.add(MigrationProbe.columnsAndConstraints("48",
                List.of(
                        new ColumnMarker("txn", "status"),
                        new ColumnMarker("txn", "reversal_of_txn_id"),
                        new ColumnMarker("txn", "replacement_for_txn_id"),
                        new ColumnMarker("txn", "correction_note")),
                List.of("fk_txn_reversal_of", "fk_txn_replacement_for", "uq_txn_reversal_of", "ck_txn_status")));
        return probes;
    }

    public static String jdbcUrlFor(Path databaseFile)
    {
        if (databaseFile == null)
        {
            throw new IllegalArgumentException("databaseFile is required");
        }

        Path absolute = databaseFile.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null)
        {
            try
            {
                Files.createDirectories(parent);
            }
            catch (Exception ex)
            {
                throw new IllegalStateException("Could not create database directory: " + parent, ex);
            }
        }

        String normalized = stripH2FileSuffix(absolute.toString()).replace('\\', '/');
        return "jdbc:h2:file:" + normalized
                + ";MODE=PostgreSQL"
                + ";DATABASE_TO_LOWER=TRUE"
                + ";DEFAULT_NULL_ORDERING=HIGH"
                + ";INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC\\;SET SCHEMA PUBLIC";
    }

    private static String findTableName(Connection connection, String expectedName) throws SQLException
    {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(null, null, "%", new String[] {"TABLE"}))
        {
            while (tables.next())
            {
                String schema = tables.getString("TABLE_SCHEM");
                String table = tables.getString("TABLE_NAME");
                if (SCHEMA_NAME.equalsIgnoreCase(schema) && expectedName.equalsIgnoreCase(table))
                {
                    return table;
                }
            }
        }
        return null;
    }

    private static String findColumnName(Connection connection, String tableName, String expectedName)
            throws SQLException
    {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, "%", "%"))
        {
            while (columns.next())
            {
                String schema = columns.getString("TABLE_SCHEM");
                String table = columns.getString("TABLE_NAME");
                String column = columns.getString("COLUMN_NAME");
                if (SCHEMA_NAME.equalsIgnoreCase(schema)
                        && tableName.equalsIgnoreCase(table)
                        && expectedName.equalsIgnoreCase(column))
                {
                    return column;
                }
            }
        }
        return null;
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException
    {
        return findTableName(connection, tableName) != null;
    }

    private static boolean columnExists(Connection connection, ColumnMarker marker) throws SQLException
    {
        String actualTable = findTableName(connection, marker.tableName());
        return actualTable != null && findColumnName(connection, actualTable, marker.columnName()) != null;
    }

    private static boolean constraintExists(Connection connection, String constraintName) throws SQLException
    {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                + "WHERE LOWER(CONSTRAINT_SCHEMA) = LOWER(?) AND LOWER(CONSTRAINT_NAME) = LOWER(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, SCHEMA_NAME);
            statement.setString(2, constraintName);
            try (ResultSet rows = statement.executeQuery())
            {
                rows.next();
                return rows.getLong(1) > 0;
            }
        }
    }

    private static String quoteIdentifier(String identifier)
    {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private static String stripH2FileSuffix(String raw)
    {
        if (raw.endsWith(".mv.db"))
        {
            return raw.substring(0, raw.length() - ".mv.db".length());
        }
        if (raw.endsWith(".db"))
        {
            return raw.substring(0, raw.length() - ".db".length());
        }
        return raw;
    }

    private static String redactJdbcUrl(String jdbcUrl)
    {
        return jdbcUrl.replaceAll("(?i)(password=)[^;]*", "$1<redacted>");
    }

    private record ColumnMarker(String tableName, String columnName)
    {
    }

    private record MigrationProbe(
            String version,
            List<String> tables,
            List<ColumnMarker> columns,
            List<String> constraints)
    {
        static MigrationProbe tables(String version, String... tables)
        {
            return new MigrationProbe(version, List.of(tables), List.of(), List.of());
        }

        static MigrationProbe constraints(String version, String... constraints)
        {
            return new MigrationProbe(version, List.of(), List.of(), List.of(constraints));
        }

        static MigrationProbe tablesAndConstraints(
                String version,
                List<String> tables,
                List<String> constraints)
        {
            return new MigrationProbe(version, tables, List.of(), constraints);
        }

        static MigrationProbe tablesAndColumns(
                String version,
                List<String> tables,
                List<ColumnMarker> columns)
        {
            return new MigrationProbe(version, tables, columns, List.of());
        }

        static MigrationProbe columnsAndConstraints(
                String version,
                List<ColumnMarker> columns,
                List<String> constraints)
        {
            return new MigrationProbe(version, List.of(), columns, constraints);
        }

        boolean complete(Connection connection)
        {
            try
            {
                for (String table : tables)
                {
                    if (!tableExists(connection, table))
                    {
                        return false;
                    }
                }
                for (ColumnMarker column : columns)
                {
                    if (!columnExists(connection, column))
                    {
                        return false;
                    }
                }
                for (String constraint : constraints)
                {
                    if (!constraintExists(connection, constraint))
                    {
                        return false;
                    }
                }
                return true;
            }
            catch (SQLException ex)
            {
                throw new IllegalStateException("Could not inspect migration " + version + " schema markers", ex);
            }
        }

        boolean started(Connection connection)
        {
            try
            {
                for (String table : tables)
                {
                    if (tableExists(connection, table))
                    {
                        return true;
                    }
                }
                for (ColumnMarker column : columns)
                {
                    if (columnExists(connection, column))
                    {
                        return true;
                    }
                }
                for (String constraint : constraints)
                {
                    if (constraintExists(connection, constraint))
                    {
                        return true;
                    }
                }
                return false;
            }
            catch (SQLException ex)
            {
                throw new IllegalStateException("Could not inspect migration " + version + " schema markers", ex);
            }
        }
    }
}
