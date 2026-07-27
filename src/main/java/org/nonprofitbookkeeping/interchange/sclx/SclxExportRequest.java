package org.nonprofitbookkeeping.interchange.sclx;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/** Immutable request for one selected-company SCLX file export. */
public record SclxExportRequest(
        Path destination,
        Instant exportedAt,
        boolean overwriteExisting)
{
    public SclxExportRequest
    {
        destination = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        Objects.requireNonNull(exportedAt, "exportedAt");
    }
}
