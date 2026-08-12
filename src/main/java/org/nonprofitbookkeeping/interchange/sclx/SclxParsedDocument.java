package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable metadata and bounded JSON tree produced by strict SCLX parsing. */
public record SclxParsedDocument(
        SclxVersion version,
        Instant exportedAt,
        JsonNode root,
        long byteCount,
        String sha256,
        boolean bomStripped,
        List<SclxCompatibilityNotice> compatibilityNotices)
{
    public SclxParsedDocument
    {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(sha256, "sha256");
        compatibilityNotices = List.copyOf(Objects.requireNonNull(
                compatibilityNotices, "compatibilityNotices"));
        if (!root.isObject())
        {
            throw new IllegalArgumentException("SCLX root must be a JSON object");
        }
        if (byteCount < 0L)
        {
            throw new IllegalArgumentException("byteCount must not be negative");
        }
    }

    public Optional<Instant> optionalExportedAt()
    {
        return Optional.ofNullable(exportedAt);
    }
}
