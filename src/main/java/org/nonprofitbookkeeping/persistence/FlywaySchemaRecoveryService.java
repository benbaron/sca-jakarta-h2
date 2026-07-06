package org.nonprofitbookkeeping.persistence;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * Recovers a complete current application schema whose Flyway history table is
 * missing, empty, or contains only non-versioned and failed migration rows.
 * Partial schemas are never modified automatically.
 */
final class FlywaySchemaRecoveryService
{
    private static final String SCHEMA_NAME = "PUBLIC";
    private static final String HISTORY_TABLE = "flyway_schema_history";
    private static final String CURRENT_VERSION = "51";

    private static final List<String> REQUIRED_TABLES = List.of(
            "chart_of_accounts",
            "account",
            "fund",
            "txn",
            "txn_split",
            "schedule_item",
            "journal_transaction",
            "open_item_snapshot",
            "reconciliation_run",
            "approval_audit_record",
            "budget_category",
            "budget_plan",
            "budget_line",
            "company",
            "app_user",
            "accounting_period",
            "period_reopen_event",
            "audit_event");

    private static final List<ColumnMarker> REQUIRED_COLUMNS = List.of(
            new ColumnMarker("txn_split", "budget_category_id"),
            new ColumnMarker("open_item_snapshot", "version"),
            new ColumnMarker("txn", "status"),
            new ColumnMarker("txn", "reversal_of_txn_id"),
            new ColumnMarker("txn", "replacement_for_txn_id"),
            new ColumnMarker("txn", "correction_note"),
            new ColumnMarker("budget_plan", "fiscal_year"),
            new ColumnMarker("budget_plan", "version_code"),
            new ColumnMarker("budget_line", "budget_category_id"),
            new ColumnMarker("budget_line", "period_month"));

    private FlywaySchemaRecoveryService()
    {
    }

    static Optional<String> prepareBaselineForUntrackedSchema(String jdbcUrl)
    {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", ""))
        {
            HistoryState historyState = inspectHistory(connection);
            if (historyState == HistoryState.TRACKED)
            {
                return Optional.empty();
            }
            if (historyState == HistoryState.MALFORMED)
            {
                throw new IllegalStateException(
                        "Flyway history table has an unexpected structure. "
                                + "No automatic recovery was attempted.");
            }

            boolean anyApplicationTable = REQUIRED_TABLES.stream()
                    .anyMatch(table -> tableExistsUnchecked(connection, table));
            if (!anyApplicationTable)
            {
                if (historyState == HistoryState.UNTRACKED_ROWS)
                {
                    throw new IllegalStateException(
                            "Flyway history contains rows but the application schema is absent. "
                                    + "No automatic recovery was attempted.");
                }
                return Optional.empty();
            }

            if (!matchesCurrentSchema(connection))
            {
                throw new IllegalStateException(
                        "Database contains a partial or non-contiguous untracked schema. "
                                + "No tables were deleted or recreated.");
            }

            if (historyState != HistoryState.ABSENT)
            {
                archiveHistoryTable(connection);
            }
            return Optional.of(CURRENT_VERSION);
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not inspect existing database migration state", ex);
        }
    }

    private static boolean matchesCurrentSchema(Connection connection) throws SQLException
    {
        for (String table : REQUIRED_TABLES)
        {
            if (!tableExists(connection, table))
            {
                return false;
            }
        }
        for (ColumnMarker column : REQUIRED_COLUMNS)
        {
            if (!columnExists(connection, column))
            {
                return false;
            }
        }
        return true;
    }

    private static HistoryState inspectHistory(Connection connection) throws SQLException
    {
        String actualHistoryTable = findTableName(connection, HISTORY_TABLE);
        if (actualHistoryTable == null)
        {
            return HistoryState.ABSENT;
        }

        long totalRows = scalarLong(connection,
                "SELECT COUNT(*) FROM " + quoteIdentifier(actualHistoryTable));
        if (totalRows == 0)
        {
            return HistoryState.EMPTY;
        }

        String successColumn = findColumnName(connection, actualHistoryTable, "success");
        String versionColumn = findColumnName(connection, actualHistoryTable, "version");
        if (successColumn == null || versionColumn == null)
        {
            return HistoryState.MALFORMED;
        }

        String sql = "SELECT COUNT(*) FROM " + quoteIdentifier(actualHistoryTable)
                + " WHERE " + quoteIdentifier(successColumn) + " = TRUE"
                + " AND " + quoteIdentifier(versionColumn) + " IS NOT NULL";
        return scalarLong(connection, sql) > 0
                ? HistoryState.TRACKED
                : HistoryState.UNTRACKED_ROWS;
    }

    private static void archiveHistoryTable(Connection connection) throws SQLException
    {
        String actualHistoryTable = findTableName(connection, HISTORY_TABLE);
        if (actualHistoryTable == null)
        {
            return;
        }

        long rowCount = scalarLong(connection,
                "SELECT COUNT(*) FROM " + quoteIdentifier(actualHistoryTable));
        String archivedName = HISTORY_TABLE + "_orphaned_" + System.currentTimeMillis();
        try (Statement statement = connection.createStatement())
        {
            statement.execute("CREATE TABLE " + quoteIdentifier(archivedName)
                    + " AS SELECT * FROM " + quoteIdentifier(actualHistoryTable));
            statement.execute("DROP TABLE " + quoteIdentifier(actualHistoryTable));
        }
        System.err.println("[NPBK] Archived Flyway history table as " + archivedName
                + " with " + rowCount + " row(s).");
    }

    private static long scalarLong(Connection connection, String sql) throws SQLException
    {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static boolean tableExistsUnchecked(Connection connection, String tableName)
    {
        try
        {
            return tableExists(connection, tableName);
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not inspect table " + tableName, ex);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException
    {
        return findTableName(connection, tableName) != null;
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

    private static boolean columnExists(Connection connection, ColumnMarker marker) throws SQLException
    {
        String actualTable = findTableName(connection, marker.tableName());
        return actualTable != null && findColumnName(connection, actualTable, marker.columnName()) != null;
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

    private static String quoteIdentifier(String identifier)
    {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private enum HistoryState
    {
        ABSENT,
        EMPTY,
        TRACKED,
        UNTRACKED_ROWS,
        MALFORMED
    }

    private record ColumnMarker(String tableName, String columnName)
    {
    }
}
