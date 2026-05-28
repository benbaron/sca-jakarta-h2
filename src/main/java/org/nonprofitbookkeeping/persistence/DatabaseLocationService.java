package org.nonprofitbookkeeping.persistence;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves database file locations for development and installed-user runs. */
public final class DatabaseLocationService
{
    private static final String APP_DIR_NAME = "SCA Ledger";
    private static final String DEFAULT_DB_FILE = "sca-ledger.mv.db";

    private DatabaseLocationService()
    {
    }

    public static Path defaultUserDatabasePath()
    {
        String os = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home", ".");
        String localAppData = System.getenv("LOCALAPPDATA");
        String appData = System.getenv("APPDATA");

        Path base;
        if (os.contains("win") && localAppData != null && !localAppData.isBlank())
        {
            base = Path.of(localAppData, APP_DIR_NAME);
        }
        else if (os.contains("win") && appData != null && !appData.isBlank())
        {
            base = Path.of(appData, APP_DIR_NAME);
        }
        else if (os.contains("mac"))
        {
            base = Path.of(userHome, "Library", "Application Support", APP_DIR_NAME);
        }
        else
        {
            base = Path.of(userHome, ".local", "share", "sca-ledger");
        }

        return base.resolve(DEFAULT_DB_FILE).toAbsolutePath().normalize();
    }

    public static Path resolveDatabasePath(String storedPath)
    {
        if (storedPath == null || storedPath.isBlank())
        {
            return defaultUserDatabasePath();
        }
        Path path = Path.of(storedPath.trim());
        if (!path.isAbsolute())
        {
            // Old development builds stored values such as data/sca-ledger.mv.db.
            // Keep those usable in-place, but make the JDBC URL absolute before use.
            path = path.toAbsolutePath();
        }
        return path.normalize();
    }

    public static Path ensureParentDirectory(Path databaseFile)
    {
        Path absolute = databaseFile.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null)
        {
            try
            {
                Files.createDirectories(parent);
            }
            catch (Exception ex)
            {
                throw new IllegalStateException("Could not create database directory: " + parent, ex);
            }
        }
        return absolute;
    }
}
