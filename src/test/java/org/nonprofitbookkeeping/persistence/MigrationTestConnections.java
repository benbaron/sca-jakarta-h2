package org.nonprofitbookkeeping.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;

/** Own pooled DDL sessions for each in-memory migration test, then close and discard its database. */
public abstract class MigrationTestConnections
{
    private final Map<String, HikariDataSource> pools = new LinkedHashMap<>();

    protected DataSource migrationDataSource(String url)
    {
        if (!url.startsWith("jdbc:h2:mem:"))
        {
            throw new IllegalArgumentException("Only isolated in-memory test databases are supported");
        }
        return pools.computeIfAbsent(url, key ->
        {
            var configuration = new HikariConfig();
            configuration.setJdbcUrl(key);
            configuration.setUsername("sa");
            configuration.setPassword("");
            configuration.setMaximumPoolSize(2);
            configuration.setMinimumIdle(0);
            configuration.setIdleTimeout(0);
            configuration.setMaxLifetime(0);
            return new HikariDataSource(configuration);
        });
    }

    @AfterEach
    protected void closeMigrationDatabases() throws Exception
    {
        for (var entry : pools.entrySet())
        {
            entry.getValue().close();
            try (var connection = DriverManager.getConnection(entry.getKey(), "sa", "");
                    var statement = connection.createStatement())
            {
                statement.execute("SHUTDOWN");
            }
        }
        pools.clear();
    }
}
