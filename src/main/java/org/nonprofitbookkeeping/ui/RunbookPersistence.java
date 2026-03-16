package org.nonprofitbookkeeping.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * RunbookPersistence component.
 */
final class RunbookPersistence
{
    private static final String STORAGE_DIR = ".sca-jakarta-h2/runbooks";
    private static Path overrideDir;

    private RunbookPersistence()
    {
    }

    static List<String> loadScheduleEntries()
    {
        return load("schedules.log");
    }

    static void saveScheduleEntries(List<String> lines)
    {
        save("schedules.log", lines);
    }

    static List<String> loadAssetEntries()
    {
        return load("assets.log");
    }

    static void saveAssetEntries(List<String> lines)
    {
        save("assets.log", lines);
    }

    static List<String> loadDepreciationEntries()
    {
        return load("depreciation.log");
    }

    static void saveDepreciationEntries(List<String> lines)
    {
        save("depreciation.log", lines);
    }

    static List<String> loadInventoryEntries()
    {
        return load("inventory.log");
    }

    static void saveInventoryEntries(List<String> lines)
    {
        save("inventory.log", lines);
    }

    private static List<String> load(String fileName)
    {
        Path path = defaultDir().resolve(fileName);
        if (!Files.exists(path))
        {
            return List.of();
        }
        try
        {
            return List.copyOf(Files.readAllLines(path, StandardCharsets.UTF_8));
        }
        catch (IOException ex)
        {
            return List.of();
        }
    }

    private static void save(String fileName, List<String> lines)
    {
        Path path = defaultDir().resolve(fileName);
        try
        {
            Files.createDirectories(path.getParent());
            Files.write(path, lines == null ? List.of() : lines, StandardCharsets.UTF_8);
        }
        catch (IOException ignored)
        {
            // keep UI deterministic when persistence is unavailable
        }
    }

    private static Path defaultDir()
    {
        if (overrideDir != null)
        {
            return overrideDir;
        }
        return Paths.get(System.getProperty("user.home"), STORAGE_DIR);
    }

    static void setDirectoryForTests(Path directory)
    {
        overrideDir = directory;
    }

    static void clearDirectoryOverrideForTests()
    {
        overrideDir = null;
    }
}
