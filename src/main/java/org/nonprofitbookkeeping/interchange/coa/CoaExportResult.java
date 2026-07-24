package org.nonprofitbookkeeping.interchange.coa;

import java.nio.file.Path;

/** Deterministic Chart of Accounts JSON export result. */
public record CoaExportResult(
        Path destination,
        long byteCount,
        String sha256,
        long chartCount,
        long accountCount)
{
    public CoaExportResult
    {
        if (destination == null)
        {
            throw new IllegalArgumentException("destination is required");
        }
        destination = destination.toAbsolutePath().normalize();
        sha256 = sha256 == null ? "" : sha256.trim().toLowerCase();
    }
}
