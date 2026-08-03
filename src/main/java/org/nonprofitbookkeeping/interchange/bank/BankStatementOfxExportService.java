package org.nonprofitbookkeeping.interchange.bank;

import org.nonprofitbookkeeping.interchange.AtomicInterchangeFileWriter;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

/** Exports durable selected-account statement activity as deterministic OFX 2.x or governed QFX. */
public final class BankStatementOfxExportService
{
    private final BankStatementCsvExportService snapshotService;
    private final Supplier<Path> activeDatabasePath;
    private final OfxQfxStatementSerializer serializer;
    private final AtomicInterchangeFileWriter fileWriter;

    public BankStatementOfxExportService(Jpa jpa, Supplier<Path> activeDatabasePath)
    {
        this(new BankStatementCsvExportService(jpa, activeDatabasePath), activeDatabasePath,
                new OfxQfxStatementSerializer(), new AtomicInterchangeFileWriter());
    }

    BankStatementOfxExportService(
            BankStatementCsvExportService snapshotService,
            Supplier<Path> activeDatabasePath,
            OfxQfxStatementSerializer serializer,
            AtomicInterchangeFileWriter fileWriter)
    {
        this.snapshotService = Objects.requireNonNull(snapshotService, "snapshotService");
        this.activeDatabasePath = Objects.requireNonNull(activeDatabasePath, "activeDatabasePath");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.fileWriter = Objects.requireNonNull(fileWriter, "fileWriter");
    }

    public BankStatementExportResult export(BankStatementOfxExportRequest request)
    {
        Objects.requireNonNull(request, "request");
        BankStatementExportRequest statementRequest = request.statementRequest();
        BankStatementCsvExportService.Snapshot snapshot = snapshotService.snapshot(statementRequest);
        if (snapshot.rows().isEmpty())
        {
            throw new IllegalArgumentException("No durable bank-statement rows exist in the selected date range.");
        }
        OfxQfxStatementSerializer.Serialization serialized = serializer.serialize(snapshot, request);
        byte[] bytes = serialized.bytes();
        Path destination = fileWriter.write(
                request.destination(), bytes, request.overwriteExisting(), activeDatabasePath.get(),
                request.profile() == BankStatementOfxExportRequest.Profile.OFX_2_XML ? "OFX" : "QFX");
        return new BankStatementExportResult(
                destination, request.companyCode(), snapshot.bankAccountExternalId(),
                request.fromDate(), request.throughDate(), snapshot.rows().size(),
                bytes.length, sha256(bytes), serialized.messages());
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
