package org.nonprofitbookkeeping.persistence;

import org.h2.tools.Restore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Performs consistent H2 backup and validated restore-to-new-copy operations. */
public final class DatabaseTransferService
{
    private static final long MAX_BACKUP_BYTES = 8L * 1024L * 1024L * 1024L;
    private static final ReentrantLock EXCLUSIVE_OPERATION = new ReentrantLock();

    private final Supplier<Path> activeDatabasePath;
    private final Consumer<Path> databaseSwitcher;

    public DatabaseTransferService(Supplier<Path> activeDatabasePath, Consumer<Path> databaseSwitcher)
    {
        this.activeDatabasePath = Objects.requireNonNull(activeDatabasePath, "activeDatabasePath");
        this.databaseSwitcher = Objects.requireNonNull(databaseSwitcher, "databaseSwitcher");
    }

    public BackupResult backUpDatabase(Path destination)
    {
        Objects.requireNonNull(destination, "destination");
        return exclusively(() -> createBackup(destination));
    }

    public RestoreResult restoreDatabaseCopy(Path backupFile, Path targetDatabase)
    {
        Objects.requireNonNull(backupFile, "backupFile");
        Objects.requireNonNull(targetDatabase, "targetDatabase");
        return exclusively(() -> restoreCopy(backupFile, targetDatabase));
    }

    public void switchToValidatedCopy(RestoreResult result)
    {
        Objects.requireNonNull(result, "result");
        if (!result.validated())
        {
            throw new IllegalArgumentException("The restored database copy has not been validated.");
        }
        databaseSwitcher.accept(result.targetDatabase());
    }

    private BackupResult createBackup(Path requestedDestination)
    {
        Path source = databaseBase(activeDatabasePath.get());
        Path destination = requestedDestination.toAbsolutePath().normalize();
        requireDifferent(source, destination, "Backup destination must differ from the active database path.");
        requireAbsent(destination, "Backup destination already exists: ");
        createParent(destination);

        Path temporary = destination.resolveSibling(destination.getFileName() + ".partial");
        deleteQuietly(temporary);
        Instant started = Instant.now();
        try
        {
            try (Connection connection = DriverManager.getConnection(DatabaseMigrationService.jdbcUrlFor(source), "sa", "");
                    Statement statement = connection.createStatement())
            {
                statement.execute("BACKUP TO '" + sqlLiteral(temporary) + "'");
            }

            long bytes = Files.size(temporary);
            requireSupportedSize(bytes);
            String sha256 = sha256(temporary);
            moveAtomically(temporary, destination);
            return new BackupResult(source, destination, started, Instant.now(), bytes, sha256, readCounts(source));
        }
        catch (IOException | SQLException ex)
        {
            deleteQuietly(temporary);
            throw new IllegalStateException("Database backup failed: " + ex.getMessage(), ex);
        }
    }

    private RestoreResult restoreCopy(Path requestedBackup, Path requestedTarget)
    {
        Path backup = requestedBackup.toAbsolutePath().normalize();
        Path target = databaseBase(requestedTarget);
        Path active = databaseBase(activeDatabasePath.get());
        if (!Files.isRegularFile(backup))
        {
            throw new IllegalArgumentException("Backup file does not exist: " + backup);
        }
        try
        {
            requireSupportedSize(Files.size(backup));
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not inspect backup file: " + backup, ex);
        }

        requireDifferent(active, target, "The active database cannot be overwritten in place.");
        requireDifferent(backup, target, "Restore target must differ from the backup path.");
        Path finalFile = h2File(target);
        requireAbsent(finalFile, "Restore target already exists: ");
        createParent(finalFile);

        Path workspace;
        try
        {
            workspace = Files.createTempDirectory(finalFile.getParent(), ".npbk-restore-");
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not create restore workspace.", ex);
        }

        Path temporaryBase = workspace.resolve(target.getFileName());
        Path temporaryFile = h2File(temporaryBase);
        Instant started = Instant.now();
        try
        {
            Restore.execute(backup.toString(), temporaryBase.toString(), null);
            if (!Files.isRegularFile(temporaryFile))
            {
                throw new IllegalStateException("The backup did not contain a restorable H2 database.");
            }
            DatabaseMigrationService.migrate(temporaryBase);
            DatabaseCounts counts;
            try (Jpa ignored = new Jpa(temporaryBase))
            {
                counts = readCounts(temporaryBase);
            }
            moveAtomically(temporaryFile, finalFile);
            deleteTreeQuietly(workspace);
            return new RestoreResult(backup, target, started, Instant.now(), true, counts, sha256(backup));
        }
        catch (IOException | SQLException ex)
        {
            deleteTreeQuietly(workspace);
            throw new IllegalStateException("Database restore validation failed: " + ex.getMessage(), ex);
        }
        catch (RuntimeException ex)
        {
            deleteTreeQuietly(workspace);
            throw ex;
        }
    }

    private static DatabaseCounts readCounts(Path databaseBase) throws SQLException
    {
        try (Connection connection = DriverManager.getConnection(DatabaseMigrationService.jdbcUrlFor(databaseBase), "sa", ""))
        {
            return new DatabaseCounts(count(connection, "company"), count(connection, "txn"), count(connection, "txn_split"));
        }
    }

    private static long count(Connection connection, String table) throws SQLException
    {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table))
        {
            result.next();
            return result.getLong(1);
        }
    }

    private static <T> T exclusively(Operation<T> operation)
    {
        if (!EXCLUSIVE_OPERATION.tryLock())
        {
            throw new IllegalStateException("Another database transfer operation is already running.");
        }
        try
        {
            return operation.run();
        }
        finally
        {
            EXCLUSIVE_OPERATION.unlock();
        }
    }

    private static Path databaseBase(Path path)
    {
        Objects.requireNonNull(path, "path");
        String value = path.toAbsolutePath().normalize().toString();
        if (value.endsWith(".mv.db"))
        {
            value = value.substring(0, value.length() - 6);
        }
        else if (value.endsWith(".db"))
        {
            value = value.substring(0, value.length() - 3);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static Path h2File(Path base)
    {
        return Path.of(base + ".mv.db");
    }

    private static void requireDifferent(Path first, Path second, String message)
    {
        if (first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize()))
        {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireAbsent(Path path, String prefix)
    {
        if (Files.exists(path))
        {
            throw new IllegalArgumentException(prefix + path);
        }
    }

    private static void requireSupportedSize(long bytes)
    {
        if (bytes <= 0 || bytes > MAX_BACKUP_BYTES)
        {
            throw new IllegalArgumentException("Backup size is outside the supported range: " + bytes);
        }
    }

    private static void createParent(Path path)
    {
        try
        {
            if (path.getParent() != null)
            {
                Files.createDirectories(path.getParent());
            }
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not create directory: " + path.getParent(), ex);
        }
    }

    private static String sqlLiteral(Path path)
    {
        return path.toAbsolutePath().normalize().toString().replace("'", "''");
    }

    private static String sha256(Path path) throws IOException
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path))
            {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0)
                {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException
    {
        try
        {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException ex)
        {
            Files.move(source, target);
        }
    }

    private static void deleteQuietly(Path path)
    {
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
        }
    }

    private static void deleteTreeQuietly(Path directory)
    {
        try (var paths = Files.walk(directory))
        {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(DatabaseTransferService::deleteQuietly);
        }
        catch (IOException ignored)
        {
        }
    }

    @FunctionalInterface
    private interface Operation<T>
    {
        T run();
    }

    public record DatabaseCounts(long companies, long transactions, long transactionSplits)
    {
    }

    public record BackupResult(Path sourceDatabase, Path backupFile, Instant startedAt, Instant completedAt,
            long byteCount, String sha256, DatabaseCounts counts)
    {
    }

    public record RestoreResult(Path backupFile, Path targetDatabase, Instant startedAt, Instant completedAt,
            boolean validated, DatabaseCounts counts, String backupSha256)
    {
    }
}