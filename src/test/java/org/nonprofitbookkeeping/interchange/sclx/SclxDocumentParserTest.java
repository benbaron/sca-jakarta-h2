package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

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
