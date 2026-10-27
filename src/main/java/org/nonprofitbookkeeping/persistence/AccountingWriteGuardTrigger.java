package org.nonprofitbookkeeping.persistence;

import org.h2.api.Trigger;
import java.sql.Connection;
import java.sql.SQLException;

/** Statement-level transaction mutex shared by accounting-source writes and close validation. */
public final class AccountingWriteGuardTrigger implements Trigger
{
    /** V90 installer invoked once by its temporary SQL alias; future tables require an explicit migration. */
    public static void install(Connection connection) throws SQLException
    {
        var tables = new java.util.ArrayList<String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                select table_name from information_schema.tables
                where lower(table_schema) = 'public' and table_type = 'BASE TABLE'
                and lower(table_name) not in ('flyway_schema_history', 'accounting_write_guard', 'security_event')
                order by table_name
                """))
        {
            while (rows.next()) tables.add(rows.getString(1));
        }
        for (String table : tables)
        {
            String quoted = "\"" + table.replace("\"", "\"\"") + "\"";
            try (var statement = connection.createStatement())
            {
                statement.execute("create trigger if not exists public.\"accounting_guard_" + table.replace("\"", "\"\"")
                        + "\" before insert, update, delete on public." + quoted
                        + " call 'org.nonprofitbookkeeping.persistence.AccountingWriteGuardTrigger'");
            }
        }
    }

    @Override
    public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException
    {
        // Statement triggers (including FK cascades) can inherit auto-commit=true.
        // Suppress only the nested query's commit; restore the flag without JDBC's implicit commit.
        var session = connection.unwrap(org.h2.jdbc.JdbcConnection.class).getSession();
        if (!(session instanceof org.h2.engine.SessionLocal local))
        {
            throw new SQLException("Accounting guard requires an H2 local trigger session");
        }
        boolean autoCommit = local.getAutoCommit();
        local.setAutoCommit(false);
        try
        {
            lock(connection);
        }
        finally
        {
            local.setAutoCommit(autoCommit);
        }
    }

    public static void lock(Connection connection) throws SQLException
    {
        try (var statement = connection.prepareStatement("select id from public.accounting_write_guard where id = 1 for update");
                var rows = statement.executeQuery())
        {
            if (!rows.next())
            {
                throw new SQLException("Accounting write guard is absent; migration/recovery is required");
            }
        }
    }
}
