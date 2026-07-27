package org.nonprofitbookkeeping.interchange.sclx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Commits final SCLX bytes through a durable same-directory temporary file. */
final class SclxAtomicFileWriter
{
    Path write(Path requestedDestination, byte[] bytes, boolean overwriteExisting, Path activeDatabasePath)
    {
        Path destination = Objects.requireNonNull(requestedDestination, "requestedDestination")
                .toAbsolutePath()
                .normalize();
        Objects.requireNonNull(bytes, "bytes");
        requirePermittedDestination(destination, overwriteExisting, activeDatabasePath);

        Path parent = destination.getParent();
        try
        {
            Files.createDirectories(parent);
            rejectSymlinkAncestors(parent);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not prepare SCLX destination directory: " + parent, ex);
        }

        Path temporary = null;
        try
        {
            String prefix = "." + safePrefix(destination.getFileName().toString()) + "-";
            temporary = Files.createTempFile(parent, prefix, ".partial");
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING))
            {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining())
                {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            commit(temporary, destination, overwriteExisting);
            forceDirectory(parent);
            return destination;
        }
        catch (IOException ex)
        {
            deleteQuietly(temporary);
            throw new IllegalStateException("Could not write SCLX export: " + ex.getMessage(), ex);
        }
    }

    private static void requirePermittedDestination(
            Path destination,
            boolean overwriteExisting,
            Path activeDatabasePath)
    {
        if (destination.getParent() == null)
        {
            throw new IllegalArgumentException("SCLX destination must name a file, not a filesystem root");
        }
        if (Files.isSymbolicLink(destination))
        {
            throw new IllegalArgumentException("SCLX destination must not be a symbolic link: " + destination);
        }
        if (Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS))
        {
            throw new IllegalArgumentException("SCLX destination is a directory: " + destination);
        }
        rejectSymlinkAncestors(destination.getParent());
        if (matchesActiveDatabase(destination, activeDatabasePath))
        {
            throw new IllegalArgumentException("SCLX destination must not overwrite the active database: " + destination);
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !overwriteExisting)
        {
            throw new IllegalArgumentException(
                    "SCLX destination already exists and overwrite was not confirmed: " + destination);
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS))
        {
            throw new IllegalArgumentException("SCLX destination is not a regular file: " + destination);
        }
    }

    private static void rejectSymlinkAncestors(Path parent)
    {
        Path current = parent;
        while (current != null)
        {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
            {
                throw new IllegalArgumentException(
                        "SCLX destination path must not traverse a symbolic link: " + current);
            }
            current = current.getParent();
        }
    }

    private static boolean matchesActiveDatabase(Path destination, Path activeDatabasePath)
    {
        if (activeDatabasePath == null)
        {
            return false;
        }
        Path active = activeDatabasePath.toAbsolutePath().normalize();
        String activeText = active.toString();
        String baseText = activeText.endsWith(".mv.db")
                ? activeText.substring(0, activeText.length() - 6)
                : activeText.endsWith(".db")
                        ? activeText.substring(0, activeText.length() - 3)
                        : activeText;
        List<Path> prohibited = new ArrayList<>();
        prohibited.add(Path.of(baseText).toAbsolutePath().normalize());
        prohibited.add(Path.of(baseText + ".mv.db").toAbsolutePath().normalize());
        prohibited.add(Path.of(baseText + ".trace.db").toAbsolutePath().normalize());
        prohibited.add(Path.of(baseText + ".lock.db").toAbsolutePath().normalize());
        for (Path candidate : prohibited)
        {
            if (destination.equals(candidate) || sameExistingFile(destination, candidate))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean sameExistingFile(Path left, Path right)
    {
        try
        {
            return Files.exists(left) && Files.exists(right) && Files.isSameFile(left, right);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not compare SCLX destination with active database path", ex);
        }
    }

    private static void commit(Path temporary, Path destination, boolean overwriteExisting) throws IOException
    {
        StandardCopyOption[] atomicOptions = overwriteExisting
                ? new StandardCopyOption[] { StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING }
                : new StandardCopyOption[] { StandardCopyOption.ATOMIC_MOVE };
        try
        {
            Files.move(temporary, destination, atomicOptions);
            return;
        }
        catch (AtomicMoveNotSupportedException ex)
        {
            // Safe same-directory fallback below.
        }

        if (!overwriteExisting || !Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
        {
            Files.move(temporary, destination);
            return;
        }

        Path backup = destination.resolveSibling(
                "." + safePrefix(destination.getFileName().toString()) + "-" + UUID.randomUUID() + ".backup");
        Files.move(destination, backup);
        boolean committed = false;
        try
        {
            Files.move(temporary, destination);
            committed = true;
        }
        finally
        {
            if (committed)
            {
                deleteQuietly(backup);
            }
            else
            {
                Files.deleteIfExists(destination);
                Files.move(backup, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String safePrefix(String filename)
    {
        String sanitized = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.length() < 3)
        {
            sanitized = "sclx";
        }
        return sanitized.length() > 48 ? sanitized.substring(0, 48) : sanitized;
    }

    private static void forceDirectory(Path directory)
    {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ))
        {
            channel.force(true);
        }
        catch (IOException | RuntimeException ignored)
        {
            // Directory forcing is not available on every supported filesystem/JVM combination.
        }
    }

    private static void deleteQuietly(Path path)
    {
        if (path == null)
        {
            return;
        }
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
        }
    }
}
