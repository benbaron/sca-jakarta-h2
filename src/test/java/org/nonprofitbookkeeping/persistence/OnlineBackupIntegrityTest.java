package org.nonprofitbookkeeping.persistence;

import org.h2.tools.Restore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/** Exercise native archive contents, including CLOB values, rather than successful archive creation alone. */
class OnlineBackupIntegrityTest
{
    @Test
    void evolvingOnlineBackupsRetainEveryCommittedClobAcrossRepeatedOpens(@TempDir Path directory) throws Exception
    {
        Path source = directory.resolve("source");
        String url = DatabaseMigrationService.jdbcUrlFor(source) + ";RETENTION_TIME=0;WRITE_DELAY=1";
        var transfer = new DatabaseTransferService(() -> source, ignored -> fail("Backup cannot switch the source"));
        // These three empty tables satisfy transfer count metadata; this test exercises physical storage, not ledger entry.
        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement())
        {
            statement.execute("CREATE TABLE company(id INT PRIMARY KEY)");
            statement.execute("CREATE TABLE txn(id INT PRIMARY KEY)");
            statement.execute("CREATE TABLE txn_split(id INT PRIMARY KEY)");
            statement.execute("CREATE TABLE backup_probe(id INT PRIMARY KEY, content CLOB)");
            for (int generation = 0; generation < 64; generation++)
            {
                statement.execute("MERGE INTO backup_probe VALUES(" + generation % 5
                        + ", REPEAT('" + (char) ('a' + generation % 26) + "'," + (5000 + generation) + "))");
                List<String> expected = contents(connection);
                Path backup = directory.resolve("generation-" + generation + ".zip");
                var result = transfer.backUpDatabase(backup);
                assertEquals(0, result.counts().transactions());
                Path restored = directory.resolve("copy-" + generation);
                Restore.execute(backup.toString(), restored.toString(), null);
                for (int opening = 0; opening < 2; opening++)
                {
                    try (var copy = DriverManager.getConnection(DatabaseMigrationService.jdbcUrlFor(restored.resolve("source")), "sa", ""))
                    {
                        assertEquals(expected, contents(copy), "Generation " + generation + ", opening " + opening);
                    }
                }
                assertNoStorageErrors(restored.resolve("source"));
                try (var copy = DriverManager.getConnection(DatabaseMigrationService.jdbcUrlFor(restored.resolve("source")), "sa", "");
                        var compact = copy.createStatement())
                {
                    compact.execute("SHUTDOWN COMPACT");
                }
                try (var copy = DriverManager.getConnection(DatabaseMigrationService.jdbcUrlFor(restored.resolve("source")), "sa", ""))
                {
                    assertEquals(expected, contents(copy), "Explicit compact/restart generation " + generation);
                }
                assertNoStorageErrors(restored.resolve("source"));
                assertEquals(expected, contents(connection), "Backup must preserve the live source");
                // H2 may also create a trace file; clean the entire isolated test copy after both connections close.
                try (var paths = Files.walk(restored))
                {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    {
                        Files.delete(path);
                    }
                }
                Files.delete(backup);
            }
        }
        try (var reopened = DriverManager.getConnection(DatabaseMigrationService.jdbcUrlFor(source), "sa", ""))
        {
            assertEquals(5, contents(reopened).size());
        }
        assertNoStorageErrors(source);
    }

    private static void assertNoStorageErrors(Path database) throws Exception
    {
        Path trace = Path.of(database + ".trace.db");
        if (Files.isRegularFile(trace))
        {
            assertFalse(Files.readString(trace).contains("MVStoreException"),
                    "H2 must not conceal storage failures in its trace: " + trace);
        }
    }

    private static List<String> contents(Connection connection) throws Exception
    {
        var rows = new ArrayList<String>();
        try (var query = connection.createStatement();
                var result = query.executeQuery("SELECT id, content FROM backup_probe ORDER BY id"))
        {
            while (result.next())
            {
                rows.add(result.getInt(1) + ":" + result.getString(2));
            }
        }
        return rows;
    }
}
