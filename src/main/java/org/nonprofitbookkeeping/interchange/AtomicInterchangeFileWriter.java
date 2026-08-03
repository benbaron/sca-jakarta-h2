package org.nonprofitbookkeeping.interchange;

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

/** Shared durable same-directory temporary-file commit boundary for governed interchange exports. */
public final class AtomicInterchangeFileWriter
{
    public Path write(
            Path requestedDestination,
            byte[] bytes,
            boolean overwriteExisting,
            Path activeDatabasePath,
            String operationLabel)
    {
        Path destination = Objects.requireNonNull(requestedDestination, "requestedDestination")
                .toAbsolutePath().normalize();
        Objects.requireNonNull(bytes, "bytes");
        String label = requiredLabel(operationLabel);
        requirePermittedDestination(destination, overwriteExisting, activeDatabasePath, label);
        Path parent = destination.getParent();
        try
        {
            Files.createDirectories(parent);
            rejectSymlinkAncestors(parent, label);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not prepare " + label + " destination: " + parent, ex);
        }

        Path temporary = null;
        try
        {
            temporary = Files.createTempFile(parent, ".interchange-", ".partial");
            try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))
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
            throw new IllegalStateException("Could not write " + label + " export: " + ex.getMessage(), ex);
        }
    }

    private static void requirePermittedDestination(
            Path destination,
            boolean overwriteExisting,
            Path activeDatabasePath,
            String label)
    {
        if (destination.getParent() == null)
        {
            throw new IllegalArgumentException(label + " destination must name a file");
        }
        if (Files.isSymbolicLink(destination))
        {
            throw new IllegalArgumentException(label + " destination must not be a symbolic link: " + destination);
        }
        if (Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS))
        {
            throw new IllegalArgumentException(label + " destination is a directory: " + destination);
        }
        rejectSymlinkAncestors(destination.getParent(), label);
        if (matchesActiveDatabase(destination, activeDatabasePath))
        {
            throw new IllegalArgumentException(label + " destination must not overwrite the active database: " + destination);
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !overwriteExisting)
        {
            throw new IllegalArgumentException(
                    label + " destination already exists and overwrite was not confirmed: " + destination);
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS))
        {
            throw new IllegalArgumentException(label + " destination is not a regular file: " + destination);
        }
    }

    private static void rejectSymlinkAncestors(Path parent, String label)
    {
        Path current = parent;
        while (current != null)
        {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
            {
                throw new IllegalArgumentException(
                        label + " destination path must not traverse a symbolic link: " + current);
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
        return prohibited.stream().anyMatch(candidate -> destination.equals(candidate) || sameFile(destination, candidate));
    }

    private static boolean sameFile(Path left, Path right)
    {
        try
        {
            return Files.exists(left) && Files.exists(right) && Files.isSameFile(left, right);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not compare interchange destination with active database", ex);
        }
    }

    private static void commit(Path temporary, Path destination, boolean overwriteExisting) throws IOException
    {
        try
        {
            if (overwriteExisting)
            {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            else
            {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            }
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
        Path backup = destination.resolveSibling(".interchange-" + UUID.randomUUID() + ".backup");
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

    private static void forceDirectory(Path directory)
    {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ))
        {
            channel.force(true);
        }
        catch (IOException | RuntimeException ignored)
        {
            // Directory forcing is not supported on every filesystem.
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

    private static String requiredLabel(String value)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException("operationLabel is required");
        }
        return value.trim();
    }
}
