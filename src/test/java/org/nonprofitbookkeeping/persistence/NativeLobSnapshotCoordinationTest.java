package org.nonprofitbookkeeping.persistence;

import org.h2.jdbc.JdbcConnection;
import org.h2.engine.SessionLocal;
import org.h2.mvstore.db.LobStorageMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for JDBC LOB construction outside SQL command/session locks. */
public class NativeLobSnapshotCoordinationTest
{
    @TempDir Path directory;

    @Test
    void actualEngineUsesPinnedBundledLobCoordination() throws Exception
    {
        try (Connection connection = connect(directory.resolve("patch-marker")))
        {
            var session = (SessionLocal) connection.unwrap(JdbcConnection.class).getSession();
            assertEquals(1, ((LobStorageMap) session.getDatabase().getLobStorage()).getSnapshotCoordinationVersion());
            assertEquals(1, session.getDatabase().getStore().getMvStore().getAtomicMapUpdateVersion());
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT H2VERSION()"))
            {
                assertTrue(result.next());
                assertEquals("2.4.240", result.getString(1), "An engine upgrade requires explicit vendor-patch review");
            }
        }
    }

    @Test
    void snapshotWaitsForParameterLobWithoutCommittingOrClosingTheWriter() throws Exception
    {
        Path source = directory.resolve("parameter-lob");
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch releaseReader = new CountDownLatch(1);
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch commitWriter = new CountDownLatch(1);
        AtomicReference<Thread> writerThread = new AtomicReference<>();
        AtomicReference<Thread> snapshotThread = new AtomicReference<>();
        var executor = Executors.newFixedThreadPool(2);
        try (Connection connection = connect(source))
        {
            try (var statement = connection.createStatement())
            {
                for (String table : new String[]{"company", "txn", "txn_split"})
                {
                    statement.execute("CREATE TABLE " + table + "(id INT PRIMARY KEY)");
                }
                statement.execute("CREATE TABLE payload(id INT PRIMARY KEY, content CLOB)");
                statement.execute("INSERT INTO payload VALUES(1, REPEAT('a', 7000))");
            }
            var writer = executor.submit(() ->
            {
                writerThread.set(Thread.currentThread());
                try (Connection writing = connect(source); var insert = writing.prepareStatement("INSERT INTO payload VALUES(2, ?)"))
                {
                    writing.setAutoCommit(false);
                    var reader = new FilterReader(new StringReader("b".repeat(12000)))
                    {
                        private boolean first = true;

                        @Override
                        public int read(char[] buffer, int offset, int length) throws IOException
                        {
                            if (first)
                            {
                                first = false;
                                reading.countDown();
                                try
                                {
                                    if (!releaseReader.await(10, TimeUnit.SECONDS))
                                    {
                                        throw new IOException("Timed out waiting for diagnostic reader release");
                                    }
                                }
                                catch (InterruptedException ex)
                                {
                                    Thread.currentThread().interrupt();
                                    throw new IOException(ex);
                                }
                            }
                            return super.read(buffer, offset, length);
                        }
                    };
                    insert.setCharacterStream(1, reader, 12000);
                    insert.executeUpdate();
                    inserted.countDown();
                    assertTrue(commitWriter.await(10, TimeUnit.SECONDS));
                    assertFalse(writing.isClosed());
                    assertFalse(writing.getAutoCommit());
                    writing.commit();
                }
                return null;
            });
            try
            {
                assertTrue(reading.await(5, TimeUnit.SECONDS));
                Path archive = directory.resolve("parameter.zip");
                var snapshot = executor.submit(() ->
                {
                    snapshotThread.set(Thread.currentThread());
                    return new DatabaseTransferService(() -> source, ignored -> fail("Backup must not switch databases"))
                            .backUpDatabase(archive);
                });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                java.lang.management.ThreadInfo blocked = null;
                while (System.nanoTime() < deadline && !snapshot.isDone())
                {
                    Thread thread = snapshotThread.get();
                    if (thread != null)
                    {
                        var info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.getId());
                        if (info != null && info.getThreadState() == Thread.State.BLOCKED
                                && info.getLockOwnerId() == writerThread.get().getId())
                        {
                            blocked = info;
                            break;
                        }
                    }
                    Thread.sleep(10);
                }
                assertFalse(snapshot.isDone(), "Snapshot must wait for the complete parameter-LOB operation");
                assertNotNull(blocked, "Snapshot must block on the LOB writer, not on a timer or retry loop");
                var session = (SessionLocal) connection.unwrap(JdbcConnection.class).getSession();
                assertEquals(System.identityHashCode(session.getDatabase().getLobSyncObject()), blocked.getLockInfo().getIdentityHashCode());
                releaseReader.countDown();
                snapshot.get(10, TimeUnit.SECONDS);
                assertTrue(inserted.await(5, TimeUnit.SECONDS));
                assertRows(connection, 1);
                Path restored = directory.resolve("restored");
                org.h2.tools.Restore.execute(archive.toString(), restored.toString(), null);
                try (Connection copy = connect(restored.resolve("parameter-lob")))
                {
                    assertRows(copy, 1);
                }
                commitWriter.countDown();
                writer.get(10, TimeUnit.SECONDS);
                assertRows(connection, 2);
            }
            finally
            {
                releaseReader.countDown();
                commitWriter.countDown();
                writer.get(10, TimeUnit.SECONDS);
            }
        }
        finally
        {
            releaseReader.countDown();
            commitWriter.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        try (Connection reopened = connect(source))
        {
            assertRows(reopened, 2);
        }
    }

    @Test
    void physicalCheckpointsCannotSplitAnAtomicMetadataMapGroup() throws Exception
    {
        Path file = directory.resolve("atomic-maps.mv.db");
        CountDownLatch partial = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<Thread> writerThread = new AtomicReference<>();
        AtomicReference<Thread> checkpointThread = new AtomicReference<>();
        var executor = Executors.newFixedThreadPool(2);
        try (var store = new org.h2.mvstore.MVStore.Builder().fileName(file.toString()).open())
        {
            assertEquals(1, store.getAtomicMapUpdateVersion());
            var metadata = store.<String, String>openMap("metadata");
            var references = store.<String, String>openMap("references");
            metadata.put("generation", "old");
            references.put("generation", "old");
            store.commit();
            store.setAutoCommitDelay(1);
            var writer = executor.submit(() ->
            {
                writerThread.set(Thread.currentThread());
                store.executeAtomicMapUpdate(() ->
                {
                    long version = store.getCurrentVersion();
                    metadata.put("generation", "new");
                    store.commit();
                    assertEquals(version, store.getCurrentVersion(), "Reentrant auto-checkpoint must wait for the complete group");
                    partial.countDown();
                    try
                    {
                        assertTrue(complete.await(10, TimeUnit.SECONDS));
                    }
                    catch (InterruptedException ex)
                    {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                    references.put("generation", "new");
                });
            });
            try
            {
                assertTrue(partial.await(5, TimeUnit.SECONDS));
                var checkpoint = executor.submit(() ->
                {
                    checkpointThread.set(Thread.currentThread());
                    return store.commit();
                });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waitingOnWriter = false;
                while (System.nanoTime() < deadline && !checkpoint.isDone())
                {
                    Thread thread = checkpointThread.get();
                    if (thread != null)
                    {
                        var info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.getId());
                        if (info != null && info.getLockOwnerId() == writerThread.get().getId())
                        {
                            waitingOnWriter = true;
                            break;
                        }
                    }
                    Thread.sleep(10);
                }
                assertFalse(checkpoint.isDone(), "Checkpoint must not persist half of a metadata/reference update");
                assertTrue(waitingOnWriter, "The checkpoint must wait on the engine update boundary");
                complete.countDown();
                writer.get(10, TimeUnit.SECONDS);
                checkpoint.get(10, TimeUnit.SECONDS);
            }
            finally
            {
                complete.countDown();
                writer.get(10, TimeUnit.SECONDS);
            }
        }
        finally
        {
            complete.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        try (var reopened = new org.h2.mvstore.MVStore.Builder().fileName(file.toString()).open())
        {
            var metadata = reopened.<String, String>openMap("metadata");
            var references = reopened.<String, String>openMap("references");
            assertEquals("new", metadata.get("generation"));
            assertEquals("new", references.get("generation"));
            reopened.setAutoCommitDelay(1);
            reopened.executeAtomicMapUpdate(() ->
            {
                metadata.clear();
                references.clear();
            });
            reopened.commit();
        }
        try (var reopened = new org.h2.mvstore.MVStore.Builder().fileName(file.toString()).readOnly().open())
        {
            assertTrue(reopened.openMap("metadata").isEmpty());
            assertTrue(reopened.openMap("references").isEmpty());
        }
    }

    @Test
    void wrongClasspathFailsClosedBeforeWritingArchiveBytes() throws Exception
    {
        Path h2 = Path.of(org.h2.Driver.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String classpath = h2 + java.io.File.pathSeparator + System.getProperty("java.class.path");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-ea", "-cp", classpath, getClass().getName(), directory.resolve("wrong-classpath").toString())
                .redirectErrorStream(true).start();
        try
        {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("Rejected unsafe classpath before snapshot bytes"), output);
        }
        finally
        {
            if (process.isAlive())
            {
                process.destroyForcibly();
            }
        }
    }

    @Test
    void preservedInvalidImageIsStillRejectedRatherThanHavingAssertionsSuppressed() throws Exception
    {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-ea", "-cp", System.getProperty("java.class.path"), getClass().getName(),
                "--corrupt-archive", directory.toString()).redirectErrorStream(true).start();
        try
        {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("Rejected preserved dangling-LOB archive"), output);
        }
        finally
        {
            if (process.isAlive())
            {
                process.destroyForcibly();
            }
        }
    }

    /** Separate-JVM negative classpath probe; the original H2 dependency deliberately precedes app classes. */
    public static void main(String[] args) throws Exception
    {
        if (args[0].equals("--corrupt-archive"))
        {
            Path directory = Path.of(args[1]);
            Path zip = directory.resolve("invalid.zip");
            try (var input = NativeLobSnapshotCoordinationTest.class.getResourceAsStream(
                    "/fixtures/recovery/p27-c7-lob-failure/snapshot-14.zip"))
            {
                java.nio.file.Files.copy(java.util.Objects.requireNonNull(input), zip);
            }
            Path restored = directory.resolve("broken");
            org.h2.tools.Restore.execute(zip.toString(), restored.toString(), null);
            Connection copy = connect(restored.resolve("concurrent"));
            try (var statement = copy.createStatement(); var rows = statement.executeQuery("SELECT content FROM payload"))
            {
                int count = 0;
                while (rows.next())
                {
                    if (rows.getString(1).length() < 6000)
                    {
                        throw new AssertionError("Unexpected frozen fixture payload");
                    }
                    count++;
                }
                if (count != 3)
                {
                    throw new AssertionError("Unexpected frozen fixture row count");
                }
            }
            try
            {
                copy.close();
                throw new AssertionError("Damaged fixture unexpectedly closed without error");
            }
            catch (java.sql.SQLException expected)
            {
                Throwable cause = expected;
                while (cause != null && !(cause instanceof AssertionError))
                {
                    cause = cause.getCause();
                }
                if (cause == null)
                {
                    throw new AssertionError("Unrelated fixture error", expected);
                }
                System.out.println("Rejected preserved dangling-LOB archive");
            }
            return;
        }
        try (Connection connection = connect(Path.of(args[0])))
        {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try
            {
                H2NativeSnapshot.write(connection, output);
                throw new AssertionError("Unsafe classpath unexpectedly created a backup");
            }
            catch (java.sql.SQLException expected)
            {
                if (!expected.getMessage().contains("coordination is unavailable") || output.size() != 0 || connection.isClosed())
                {
                    throw new AssertionError("Incorrect classpath failure", expected);
                }
                try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT 1"))
                {
                    if (!result.next() || result.getInt(1) != 1)
                    {
                        throw new AssertionError("Source connection was damaged");
                    }
                }
                System.out.println("Rejected unsafe classpath before snapshot bytes");
            }
        }
    }

    private static Connection connect(Path source) throws Exception
    {
        return DriverManager.getConnection(DatabaseMigrationService.jdbcUrlFor(source) + ";WRITE_DELAY=1", "sa", "");
    }

    private static void assertRows(Connection connection, int expected) throws Exception
    {
        int count = 0;
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT id, content FROM payload ORDER BY id"))
        {
            while (result.next())
            {
                int id = result.getInt(1);
                assertEquals(id == 1 ? "a".repeat(7000) : "b".repeat(12000), result.getString(2));
                count++;
            }
        }
        assertEquals(expected, count);
    }
}
