package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SclxAtomicFileWriterTest
{
    @TempDir
    Path tempDir;

    private final SclxAtomicFileWriter writer = new SclxAtomicFileWriter();

    @Test
    void refusesUnconfirmedOverwriteAndLeavesExistingBytesUnchanged() throws IOException
    {
        Path destination = tempDir.resolve("company.sclx");
        byte[] original = "original\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(destination, original);

        assertThrows(IllegalArgumentException.class,
                () -> writer.write(destination, "replacement\n".getBytes(), false, tempDir.resolve("ledger")));

        assertArrayEquals(original, Files.readAllBytes(destination));
        try (Stream<Path> files = Files.list(tempDir))
        {
            assertEquals(List.of("company.sclx"), files.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void replacesExistingFileOnlyAfterExplicitConfirmation() throws IOException
    {
        Path destination = tempDir.resolve("company.sclx");
        Files.writeString(destination, "original\n");

        writer.write(destination, "replacement\n".getBytes(), true, tempDir.resolve("ledger"));

        assertEquals("replacement\n", Files.readString(destination));
    }

    @Test
    void rejectsDirectoriesAndActiveDatabaseFiles() throws IOException
    {
        Path directory = Files.createDirectory(tempDir.resolve("output"));
        Path activeBase = tempDir.resolve("ledger");
        Path activeFile = Path.of(activeBase + ".mv.db");
        Files.writeString(activeFile, "database");

        assertThrows(IllegalArgumentException.class,
                () -> writer.write(directory, new byte[] { 1 }, false, activeBase));
        assertThrows(IllegalArgumentException.class,
                () -> writer.write(activeFile, new byte[] { 1 }, true, activeBase));
        assertEquals("database", Files.readString(activeFile));
    }

    @Test
    void rejectsDestinationPathsThatTraverseSymbolicLinks() throws IOException
    {
        Path realDirectory = Files.createDirectory(tempDir.resolve("real"));
        Path link = tempDir.resolve("link");
        try
        {
            Files.createSymbolicLink(link, realDirectory);
        }
        catch (UnsupportedOperationException | IOException | SecurityException ex)
        {
            assumeTrue(false, "Symbolic links are unavailable on this platform");
        }

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> writer.write(link.resolve("company.sclx"), new byte[] { 1 }, false, tempDir.resolve("ledger")));
        assertTrue(exception.getMessage().contains("symbolic link"));
    }
}
