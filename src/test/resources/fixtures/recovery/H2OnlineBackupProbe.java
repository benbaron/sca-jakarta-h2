import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.h2.tools.Restore;

/** Standalone synthetic diagnostic; deliberately not a timing-dependent JUnit gate. */
class H2OnlineBackupProbe
{
    public static void main(String[] args) throws Exception
    {
        boolean checkpoint = args.length == 1 && args[0].equals("checkpoint");
        if (args.length > 1 || (args.length == 1 && !checkpoint))
        {
            throw new IllegalArgumentException("Optional argument: checkpoint");
        }
        Path directory = Files.createTempDirectory("h2-online-backup-probe-");
        System.out.println("Synthetic diagnostic files: " + directory);
        String url = "jdbc:h2:file:" + directory.resolve("source")
                + ";RETENTION_TIME=0;WRITE_DELAY=1";
        long[] lengths = new long[5];
        long expectedLength = 0;
        try (var source = DriverManager.getConnection(url, "sa", "");
                var statement = source.createStatement())
        {
            statement.execute("CREATE TABLE example(id INT PRIMARY KEY, content CLOB)");
            for (int generation = 0; generation < 2000; generation++)
            {
                int id = generation % lengths.length;
                long length = 5000L + generation;
                expectedLength += length - lengths[id];
                lengths[id] = length;
                statement.execute("MERGE INTO example VALUES(" + id
                        + ", REPEAT('x'," + length + "))");
                try (var committed = statement.executeQuery("SELECT COUNT(*), SUM(LENGTH(content)) FROM example"))
                {
                    committed.next();
                    if (committed.getLong(1) != Math.min(generation + 1, lengths.length)
                            || committed.getLong(2) != expectedLength)
                    {
                        throw new IllegalStateException("Unexpected source content at generation " + generation);
                    }
                }
                Path backup = directory.resolve("backup.zip");
                Path copy = directory.resolve("copy-" + generation);
                if (checkpoint)
                {
                    statement.execute("CHECKPOINT SYNC");
                }
                statement.execute("BACKUP TO '" + backup.toString().replace("'", "''") + "'");
                Restore.execute(backup.toString(), copy.toString(), null);
                try (var restored = DriverManager.getConnection("jdbc:h2:file:" + copy.resolve("source"), "sa", "");
                        var query = restored.createStatement();
                        var result = query.executeQuery("SELECT COUNT(*), SUM(LENGTH(content)) FROM example"))
                {
                    result.next();
                    if (result.getLong(1) != Math.min(generation + 1, lengths.length)
                            || result.getLong(2) != expectedLength)
                    {
                        throw new IllegalStateException("Restored content differs at generation " + generation
                                + ": expected rows=" + Math.min(generation + 1, lengths.length)
                                + ", length=" + expectedLength + "; actual rows=" + result.getLong(1)
                                + ", length=" + result.getLong(2));
                    }
                }
                Files.delete(copy.resolve("source.mv.db"));
                Files.delete(copy);
                Files.delete(backup);
                if (generation % 100 == 0)
                {
                    System.out.println("Verified generation " + generation);
                }
            }
        }
        System.out.println("2,000 snapshots verified; this is not proof of an intermittent defect's absence.");
    }
}
