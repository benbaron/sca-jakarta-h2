package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxFileExportServiceTest
{
    @TempDir
    Path tempDir;

    @Test
    void returnsExactHashCountsWarningsAndExclusionsForCommittedBytes() throws Exception
    {
        SclxExportDocument document = SclxJsonSerializerTest.document();
        SclxFileExportService service = new SclxFileExportService(
                exportedAt -> {
                    assertEquals(document.exportedAt(), exportedAt);
                    return document;
                },
                () -> tempDir.resolve("ledger"),
                new SclxJsonSerializer(),
                new SclxAtomicFileWriter());
        Path destination = tempDir.resolve("test-company.sclx");

        SclxExportResult result = service.export(new SclxExportRequest(
                destination, document.exportedAt(), false));
        byte[] bytes = Files.readAllBytes(destination);

        assertArrayEquals(new SclxJsonSerializer().serialize(document), bytes);
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), result.sha256());
        assertEquals(bytes.length, result.byteCount());
        assertEquals(2, result.counts().accounts());
        assertEquals(1, result.counts().budgetLines());
        assertEquals(2, result.counts().transactionLines());
        assertEquals(result.deferredSections().size(), result.counts().warnings());
        assertEquals(result.excludedSections().size(), result.counts().exclusions());
        assertFalse(result.messages().isEmpty());
        assertTrue(result.excludedSections().contains(SclxExportSection.DATABASE_INTERNALS));

        SclxParsedDocument parsed = new SclxDocumentParser().parse(destination);
        assertEquals(SclxVersion.V1_3, parsed.version());
        assertEquals(document.exportedAt(), parsed.exportedAt());
        assertEquals(result.sha256(), parsed.sha256());
    }
}
