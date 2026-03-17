package org.nonprofitbookkeeping.ui;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * UiDataSources component.
 */
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
        return new DriverManagerDataSource(jdbcUrlFor(databaseFile), "sa", "");
    }

    static String jdbcUrlForTests(Path databaseFile)
    {
        return jdbcUrlFor(databaseFile);
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
        return "jdbc:h2:file:" + normalized + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC\\;SET SCHEMA PUBLIC";
    }

    private static final class DriverManagerDataSource implements DataSource
    {
        private final String url;
        private final String username;
        private final String password;

        private DriverManagerDataSource(String url, String username, String password)
        {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException
        {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException
        {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter()
        {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out)
        {
        }

        @Override
        public void setLoginTimeout(int seconds)
        {
        }

        @Override
        public int getLoginTimeout()
        {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException
        {
            throw new SQLFeatureNotSupportedException("Not supported");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException
        {
            throw new SQLException("Not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface)
        {
            return false;
        }
    }
}
