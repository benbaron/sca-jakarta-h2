package org.nonprofitbookkeeping.ui;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;

final class UiDataSources
{
    private UiDataSources()
    {
    }

    static DataSource forCurrentSessionDatabase()
    {
        return forDatabasePath(Path.of(MainWindow.sharedSessionState().databaseSelection().activeDatabasePath()));
    }

    static DataSource forDatabasePath(Path databaseFile)
    {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(jdbcUrlFor(databaseFile));
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private static String jdbcUrlFor(Path databaseFile)
    {
        String raw = databaseFile.toString();
        String normalized = raw;
        if (raw.endsWith(".mv.db"))
        {
            normalized = raw.substring(0, raw.length() - ".mv.db".length());
        }
        else if (raw.endsWith(".db"))
        {
            normalized = raw.substring(0, raw.length() - ".db".length());
        }
        return "jdbc:h2:file:" + normalized + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
    }
}
