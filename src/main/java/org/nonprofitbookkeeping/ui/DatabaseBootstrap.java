package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.persistence.DatabaseMigrationService;

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
        DatabaseMigrationService.migrate(databaseFile);
    }
}
