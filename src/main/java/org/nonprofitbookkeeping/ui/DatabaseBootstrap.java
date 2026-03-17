package org.nonprofitbookkeeping.ui;

import org.flywaydb.core.Flyway;

import java.nio.file.Path;

/**
 * Ensures a selected database is initialized/migrated before runtime reconnect.
 */
final class DatabaseBootstrap
{
    private DatabaseBootstrap()
    {
    }

    static void migrate(Path databaseFile)
    {
        String url = UiDataSources.jdbcUrlForTests(databaseFile);
        Flyway.configure()
                .dataSource(url, "sa", "")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }
}
