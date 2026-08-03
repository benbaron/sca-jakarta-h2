package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.interchange.AtomicInterchangeFileWriter;

import java.nio.file.Path;

/** Commits final SCLX bytes through a durable same-directory temporary file. */
final class SclxAtomicFileWriter
{
    private final AtomicInterchangeFileWriter delegate = new AtomicInterchangeFileWriter();

    Path write(Path requestedDestination, byte[] bytes, boolean overwriteExisting, Path activeDatabasePath)
    {
        return delegate.write(requestedDestination, bytes, overwriteExisting, activeDatabasePath, "SCLX");
    }
}
