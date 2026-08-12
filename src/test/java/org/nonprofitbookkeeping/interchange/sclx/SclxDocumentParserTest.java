package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxDocumentParserTest
{
    private final SclxDocumentParser parser = new SclxDocumentParser();

    @Test
    void parsesSupportedVersionAndMetadata()
    {
        byte[] bytes = """
                {
                  "format": "SCLX",
                  "version": "1.2",
                  "exportedAt": "2026-03-31T18:00:00Z",
                  "organization": {}
                }
                """.getBytes(StandardCharsets.UTF_8);

        SclxParsedDocument document = parser.parse(bytes);

        assertEquals(SclxVersion.V1_2, document.version());
        assertEquals(Instant.parse("2026-03-31T18:00:00Z"), document.exportedAt());
        assertEquals(bytes.length, document.byteCount());
        assertEquals(64, document.sha256().length());
        assertFalse(document.bomStripped());
        assertEquals("SCLX", document.root().path("format").textValue());
    }

    @Test
    void stripsUtf8BomAndHashesOriginalBytes()
    {
        byte[] json = "{\"format\":\"SCLX\",\"version\":\"1.3\"}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[json.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(json, 0, bytes, 3, json.length);

        SclxParsedDocument document = parser.parse(bytes);

        assertTrue(document.bomStripped());
        assertEquals(bytes.length, document.byteCount());
        assertEquals(SclxVersion.V1_3, document.version());
    }

    @Test
    void normalizesBoundedDonorAliasesWithoutChangingSourceIdentity() throws Exception
    {
        byte[] bytes = Files.readAllBytes(Path.of(
                "src/test/resources/compatibility/sclx/donor-sclx-1.3.json"));

        SclxParsedDocument document = parser.parse(bytes);

        assertEquals(Instant.parse("2026-07-16T03:32:07.301594800Z"), document.exportedAt());
        assertEquals(bytes.length, document.byteCount());
        assertEquals("2026-07-16T03:32:07.301594800Z",
                document.root().path("exportedAt").textValue());
        assertEquals("1000", document.root().path("chartOfAccounts").get(0).path("code").textValue());
        assertEquals("DEBIT", document.root().path("chartOfAccounts").get(0)
                .path("increaseSide").textValue());
        assertEquals("CREDIT", document.root().path("chartOfAccounts").get(1)
                .path("increaseSide").textValue());
        assertTrue(document.root().path("budgets").isEmpty());
        assertTrue(document.root().path("people").isEmpty());
        assertEquals(1, document.root().path("extensions").path("scaJakartaH2")
                .path("counterparties").path("counterparties").size());
        JsonNode transaction = document.root().path("transactions").get(0);
        assertEquals("2026-07-15", transaction.path("transactionDate").textValue());
        assertEquals("ENTERED", transaction.path("status").textValue());
        assertEquals("Donation [Reference: deposit 1]", transaction.path("description").textValue());
        assertEquals("General Fund", transaction.path("lines").get(0).path("fundId").textValue());
        assertEquals("person-donor", transaction.path("lines").get(0)
                .path("counterpartyId").textValue());
        Set<String> codes = document.compatibilityNotices().stream()
                .map(SclxCompatibilityNotice::code)
                .collect(Collectors.toSet());
        assertTrue(codes.contains("SCLX_DONOR_NUMERIC_EXPORTED_AT_NORMALIZED"));
        assertTrue(codes.contains("SCLX_DONOR_EMPTY_BUDGETS_SKIPPED"));
        assertTrue(codes.contains("SCLX_DONOR_GENERAL_FUND_ASSIGNED"));
        assertTrue(codes.contains("SCLX_DONOR_PEOPLE_MAPPED"));
    }

    @Test
    void rejectsNumericExportedAtBeyondNanosecondPrecision()
    {
        byte[] bytes = """
                {"format":"SCLX","version":"1.3","exportedAt":1.0000000001}
                """.getBytes(StandardCharsets.UTF_8);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse(bytes));

        assertTrue(exception.getMessage().contains("nanosecond precision"));
    }

    @Test
    void rejectsInvalidUtf8()
    {
        byte[] bytes = new byte[] {
                '{', '"', 'f', 'o', 'r', 'm', 'a', 't', '"', ':', '"',
                (byte) 0xC3, (byte) 0x28, '"', '}'
        };

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(bytes));

        assertTrue(exception.getMessage().contains("valid UTF-8"));
    }

    @Test
    void rejectsDuplicateKeys()
    {
        byte[] bytes = """
                {"format":"SCLX","version":"1.2","version":"1.3"}
                """.getBytes(StandardCharsets.UTF_8);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(bytes));

        assertTrue(exception.getMessage().contains("Malformed SCLX JSON"));
    }

    @Test
    void rejectsWrongFormatAndUnsupportedVersion()
    {
        byte[] wrongFormat = "{\"format\":\"SCA-COA\",\"version\":\"1.3\"}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] futureVersion = "{\"format\":\"SCLX\",\"version\":\"2.0\"}"
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(wrongFormat));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(futureVersion));
    }

    @Test
    void rejectsTrailingJson()
    {
        byte[] bytes = "{\"format\":\"SCLX\",\"version\":\"1.0\"} {}"
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(bytes));
    }
}
