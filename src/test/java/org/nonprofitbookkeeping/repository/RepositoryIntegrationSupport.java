package org.nonprofitbookkeeping.repository;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Shared in-memory DB setup for repository tests.
 */
public final class RepositoryIntegrationSupport
{
    private RepositoryIntegrationSupport()
    {
    }

    public static DataSource migratedDataSource()
    {
        String dbName = "repo_test_" + UUID.randomUUID();
        String url = "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        DataSource ds = new TestDataSource(url, "sa", "");

        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        return ds;
    }
}
