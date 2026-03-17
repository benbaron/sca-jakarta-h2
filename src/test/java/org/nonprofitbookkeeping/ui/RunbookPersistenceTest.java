package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RunbookPersistenceTest component.
 */
class RunbookPersistenceTest
{
    @TempDir
    Path tempDir;

    @Test
    void saveThenLoadScheduleEntries_roundTrips()
    {
        RunbookPersistence.setDirectoryForTests(tempDir);
        try
        {
            List<String> rows = List.of("a", "b");
            RunbookPersistence.saveScheduleEntries(rows);
            assertEquals(rows, RunbookPersistence.loadScheduleEntries());
        }
        finally
        {
            RunbookPersistence.clearDirectoryOverrideForTests();
        }
    }
}
