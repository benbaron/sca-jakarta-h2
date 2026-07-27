package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable result of one deterministic, atomically committed SCLX export. */
public record SclxExportResult(
        Path destination,
        String format,
        String version,
        Instant exportedAt,
        String organizationId,
        String organizationCode,
        long byteCount,
        String sha256,
        SclxExportCounts counts,
        List<InterchangeValidationMessage> messages,
        List<SclxExportSection> deferredSections,
        List<SclxExportSection> excludedSections)
{
    public SclxExportResult
    {
        destination = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        format = requireText(format, "format");
        version = requireText(version, "version");
        Objects.requireNonNull(exportedAt, "exportedAt");
        organizationId = requireText(organizationId, "organizationId");
        organizationCode = requireText(organizationCode, "organizationCode");
        if (byteCount < 0L)
        {
            throw new IllegalArgumentException("byteCount must not be negative");
        }
        sha256 = requireSha256(sha256);
        Objects.requireNonNull(counts, "counts");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        deferredSections = List.copyOf(Objects.requireNonNull(deferredSections, "deferredSections"));
        excludedSections = List.copyOf(Objects.requireNonNull(excludedSections, "excludedSections"));
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    private static String requireSha256(String value)
    {
        String normalized = requireText(value, "sha256").toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}"))
        {
            throw new IllegalArgumentException("sha256 must be a lowercase SHA-256 value");
        }
        return normalized;
    }
}
