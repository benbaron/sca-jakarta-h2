package org.nonprofitbookkeeping.repository;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * Minimal DriverManager-backed DataSource for tests.
 */
public class TestDataSource implements DataSource
{
    private final String url;
    private final String username;
    private final String password;

    public TestDataSource(String url, String username, String password)
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
