package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Reconstructs, serializes, and atomically commits one selected-company SCLX 1.3 file. */
public final class SclxFileExportService
{
    private final Function<Instant, SclxExportDocument> snapshotLoader;
    private final Supplier<Path> activeDatabasePath;
    private final SclxJsonSerializer serializer;
    private final SclxAtomicFileWriter fileWriter;

    public SclxFileExportService(
            SclxCoreSnapshotQueryService snapshotQueryService,
            Supplier<Path> activeDatabasePath)
    {
        this(snapshotQueryService::query, activeDatabasePath, new SclxJsonSerializer(), new SclxAtomicFileWriter());
    }

    SclxFileExportService(
            Function<Instant, SclxExportDocument> snapshotLoader,
            Supplier<Path> activeDatabasePath,
            SclxJsonSerializer serializer,
            SclxAtomicFileWriter fileWriter)
    {
        this.snapshotLoader = Objects.requireNonNull(snapshotLoader, "snapshotLoader");
        this.activeDatabasePath = Objects.requireNonNull(activeDatabasePath, "activeDatabasePath");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.fileWriter = Objects.requireNonNull(fileWriter, "fileWriter");
    }

    public SclxExportResult export(SclxExportRequest request)
    {
        Objects.requireNonNull(request, "request");
        SclxExportDocument document = snapshotLoader.apply(request.exportedAt());
        byte[] bytes = serializer.serialize(document);

        List<SclxExportSection> deferredSections = Arrays.stream(SclxExportSection.values())
                .filter(SclxExportSection::deferred)
                .toList();
        List<SclxExportSection> excludedSections = Arrays.stream(SclxExportSection.values())
                .filter(section -> section.support() == SclxExportSection.Support.EXCLUDED)
                .toList();
        List<InterchangeValidationMessage> messages = deferredSections.stream()
                .map(section -> new InterchangeValidationMessage(
                        InterchangeMessageSeverity.WARNING,
                        "SCLX_DEFERRED_SECTION",
                        section.outputPath(),
                        section.description() + " is not yet included by the current P15-S4 snapshot.",
                        false))
                .toList();

        Path activeDatabase = Objects.requireNonNull(
                activeDatabasePath.get(), "activeDatabasePath supplier returned null");
        Path destination = fileWriter.write(
                request.destination(),
                bytes,
                request.overwriteExisting(),
                activeDatabase);
        String hash = sha256(bytes);
        return new SclxExportResult(
                destination,
                document.format(),
                document.version(),
                document.exportedAt(),
                document.organization().organizationId(),
                document.organization().code(),
                bytes.length,
                hash,
                SclxExportCounts.from(document, messages.size(), excludedSections.size()),
                messages,
                deferredSections,
                excludedSections);
    }

    private static String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}
