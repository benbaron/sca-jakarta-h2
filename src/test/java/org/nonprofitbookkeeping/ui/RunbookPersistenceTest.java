package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunbookPersistenceTest
{
    @TempDir
    Path tempDir;

    @Test
    void saveThenLoadAssetEntries_roundTrips()
    {
        RunbookPersistence.setDirectoryForTests(tempDir);
        try
        {
            List<String> rows = List.of("a", "b");
            RunbookPersistence.saveAssetEntries(rows);
            assertEquals(rows, RunbookPersistence.loadAssetEntries());
        }
        finally
        {
            RunbookPersistence.clearDirectoryOverrideForTests();
        }
    }

    @Test
    void saveThenLoadInventoryEntries_roundTrips()
    {
        RunbookPersistence.setDirectoryForTests(tempDir);
        try
        {
            List<String> rows = List.of("i", "j");
            RunbookPersistence.saveInventoryEntries(rows);
            assertEquals(rows, RunbookPersistence.loadInventoryEntries());
        }
        finally
        {
            RunbookPersistence.clearDirectoryOverrideForTests();
        }
    }
}
