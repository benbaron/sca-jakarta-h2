package org.nonprofitbookkeeping.persistence;

import org.h2.tools.Restore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class H2NativeSnapshotTest
{
    @TempDir Path directory;

    @Test
    void archiveOutputFailureDoesNotPanicOrCloseTheLiveDatabase() throws Exception
    {
        Path source = directory.resolve("output-failure");
        try (Connection connection = connect(source))
        {
            seed(connection);
            OutputStream broken = new OutputStream()
            {
                private int remaining = 256;

                @Override
                public void write(int value) throws IOException
                {
                    if (--remaining < 0)
                    {
                        throw new IOException("Injected archive output failure");
                    }
                }
            };
            IOException failure = assertThrows(IOException.class, () -> H2NativeSnapshot.write(connection, broken));
            assertTrue(failure.getMessage().contains("Injected archive output failure"));
            assertFalse(connection.isClosed());
            assertSnapshot(connection, 0, 0);
            update(connection, 1);
            assertSnapshot(connection, 1, 1);
        }
        try (Connection reopened = connect(source))
        {
            assertSnapshot(reopened, 1, 1);
        }
        assertNoStorageErrors(source);
    }

    @Test
    void nonAdministratorCannotCreateNativeArchive() throws Exception
    {
        Path source = directory.resolve("permissions");
        try (Connection admin = connect(source))
        {
            seed(admin);
            try (var statement = admin.createStatement())
            {
                statement.execute("CREATE USER snapshot_reader PASSWORD 'synthetic-test-only'");
                statement.execute("GRANT SELECT ON payload TO snapshot_reader");
            }
            try (Connection reader = DriverManager.getConnection("jdbc:h2:file:" + source + ";IFEXISTS=TRUE",
                    "snapshot_reader", "synthetic-test-only"))
            {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                var failure = assertThrows(org.h2.message.DbException.class, () -> H2NativeSnapshot.write(reader, output));
                assertEquals(org.h2.api.ErrorCode.ADMIN_RIGHTS_REQUIRED, failure.getErrorCode());
                assertEquals(0, output.size());
                assertFalse(reader.isClosed());
                assertSnapshot(reader, 0, 0);
            }
            assertSnapshot(admin, 0, 0);
        }
        assertNoStorageErrors(source);
    }

    @Test
    void concurrentAtomicUpdatesProduceWholeSnapshotsContainingPriorCommits() throws Exception
    {
        Path source = directory.resolve("concurrent");
        Semaphore next = new Semaphore(0);
        Semaphore begun = new Semaphore(0);
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger committed = new AtomicInteger();
        var executor = Executors.newSingleThreadExecutor();
        try (Connection connection = connect(source))
        {
            seed(connection);
            var writer = executor.submit(() ->
            {
                try (Connection writing = connect(source))
                {
                    writing.setAutoCommit(false);
                    for (int version = 1; version <= 24; version++)
                    {
                        next.acquire();
                        if (stop.get())
                        {
                            break;
                        }
                        begun.release();
                        update(writing, version);
                        writing.commit();
                        committed.set(version);
                    }
                }
                return null;
            });
            try
            {
                var transfer = new DatabaseTransferService(() -> source, ignored -> fail("Backup must not switch databases"));
                for (int index = 0; index < 24; index++)
                {
                    int priorCommit = committed.get();
                    next.release();
                    assertTrue(begun.tryAcquire(5, TimeUnit.SECONDS));
                    Path archive = directory.resolve("snapshot-" + index + ".zip");
                    transfer.backUpDatabase(archive);
                    Path restored = directory.resolve("copy-" + index);
                    Restore.execute(archive.toString(), restored.toString(), null);
                    try (Connection copy = connect(restored.resolve("concurrent")))
                    {
                        assertSnapshot(copy, priorCommit, index + 1);
                    }
                    assertNoStorageErrors(restored.resolve("concurrent"));
                }
                writer.get(10, TimeUnit.SECONDS);
                assertEquals(24, committed.get());
                assertSnapshot(connection, 24, 24);
            }
            finally
            {
                stop.set(true);
                next.release();
                writer.get(10, TimeUnit.SECONDS);
            }
        }
        finally
        {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertNoStorageErrors(source);
    }

    private static Connection connect(Path source) throws Exception
    {
        return DriverManager.getConnection(DatabaseMigrationService.jdbcUrlFor(source) + ";WRITE_DELAY=1", "sa", "");
    }

    private static void seed(Connection connection) throws Exception
    {
        try (var statement = connection.createStatement())
        {
            for (String table : new String[]{"company", "txn", "txn_split"})
            {
                statement.execute("CREATE TABLE " + table + "(id INT PRIMARY KEY)");
            }
            statement.execute("CREATE TABLE payload(id INT PRIMARY KEY, generation INT, content CLOB)");
        }
        try (var insert = connection.prepareStatement("INSERT INTO payload VALUES(?,0,?)"))
        {
            for (int id = 0; id < 3; id++)
            {
                insert.setInt(1, id);
                insert.setString(2, content(0));
                insert.executeUpdate();
            }
        }
    }

    private static void update(Connection connection, int version) throws Exception
    {
        try (var update = connection.prepareStatement("UPDATE payload SET generation=?,content=?"))
        {
            update.setInt(1, version);
            update.setString(2, content(version));
            assertEquals(3, update.executeUpdate());
        }
    }

    private static void assertSnapshot(Connection connection, int minimum, int maximum) throws Exception
    {
        int rows = 0;
        Integer generation = null;
        try (var query = connection.createStatement();
                var result = query.executeQuery("SELECT id,generation,content FROM payload ORDER BY id"))
        {
            while (result.next())
            {
                assertEquals(rows++, result.getInt(1));
                int version = result.getInt(2);
                assertTrue(version >= minimum && version <= maximum, "A snapshot must contain every prior committed generation");
                if (generation == null)
                {
                    generation = version;
                }
                assertEquals(generation.intValue(), version, "An atomic update must never be split across snapshot rows");
                assertEquals(content(version), result.getString(3));
            }
        }
        assertEquals(3, rows);
    }

    private static String content(int version)
    {
        return String.valueOf((char) ('a' + version % 26)).repeat(6000 + version);
    }

    private static void assertNoStorageErrors(Path source) throws Exception
    {
        Path trace = Path.of(source + ".trace.db");
        if (Files.exists(trace))
        {
            assertFalse(Files.readString(trace).contains("MVStoreException"));
        }
    }
}
