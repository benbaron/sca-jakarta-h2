package org.nonprofitbookkeeping.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Runs the unchanged regression in bounded batches and preserves the first failing native files. */
public class OnlineBackupIntegrityProbe
{
    public static void main(String[] args) throws Exception
    {
        if (args.length > 2)
        {
            throw new IllegalArgumentException("Arguments: [runs, 1..1000] [diagnostic directory]");
        }
        int runs = args.length == 0 ? 50 : Integer.parseInt(args[0]);
        if (runs < 1 || runs > 1000)
        {
            throw new IllegalArgumentException("Run count must be between 1 and 1000");
        }
        Path root = args.length < 2 ? Files.createTempDirectory("native-backup-integrity-") : Path.of(args[1]);
        Files.createDirectories(root);
        for (int run = 0; run < runs; run++)
        {
            Path directory = Files.createTempDirectory(root, "run-");
            System.out.println("Run " + run + ": " + directory);
            try
            {
                new OnlineBackupIntegrityTest().evolvingOnlineBackupsRetainEveryCommittedClobAcrossRepeatedOpens(directory);
            }
            catch (Exception | AssertionError failure)
            {
                try (var output = new java.io.PrintWriter(Files.newBufferedWriter(directory.resolve("failure.txt"))))
                {
                    failure.printStackTrace(output);
                }
                System.err.println("Preserved failing native source/archive: " + directory);
                throw failure;
            }
            try (var paths = Files.walk(directory))
            {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                {
                    Files.delete(path);
                }
            }
        }
        System.out.println("Completed " + runs + " original 64-generation regressions; no assertions were replaced.");
    }
}
