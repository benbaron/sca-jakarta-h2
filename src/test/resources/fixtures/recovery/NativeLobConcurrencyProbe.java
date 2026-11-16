package org.nonprofitbookkeeping.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Bounded execution of the unchanged concurrency regression, preserving the first failure. */
public class NativeLobConcurrencyProbe
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
        Path root = args.length < 2 ? Files.createTempDirectory("native-lob-integrity-") : Path.of(args[1]);
        Files.createDirectories(root);
        for (int run = 0; run < runs; run++)
        {
            Path directory = Files.createTempDirectory(root, "run-");
            System.out.println("Run " + run + ": " + directory);
            H2NativeSnapshotTest test = new H2NativeSnapshotTest();
            test.directory = directory;
            try
            {
                test.concurrentAtomicUpdatesProduceWholeSnapshotsContainingPriorCommits();
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
        System.out.println("Completed " + runs + " unchanged concurrency regressions; no assertions were replaced.");
    }
}
