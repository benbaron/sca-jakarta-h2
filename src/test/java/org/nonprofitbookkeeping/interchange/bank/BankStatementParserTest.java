package org.nonprofitbookkeeping.interchange.bank;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankStatementParserTest
{
    private final BankStatementParser parser = new BankStatementParser();

    @Test
    public void parsesGovernedOfx2WithAccountBalancesAndCorrection()
    {
        BankStatementDocument result = parser.parse(resource("ofx/valid/ofx2-checking.xml"));

        assertEquals(BankingDataFormat.OFX, result.format());
        assertEquals(BankStatementDocument.Variant.OFX_2_XML, result.variant());
        assertEquals("220", result.version());
        assertEquals("FICTIONAL-4321", result.account().accountId());
        assertEquals("USD", result.currency());
        assertEquals(new BigDecimal("340.25"), result.ledgerBalance());
        assertEquals(3, result.transactions().size());
        assertEquals("REPLACE", result.transactions().get(2).correctionAction());
        assertEquals("FIT-FICTIONAL-0003", result.transactions().get(2).correctedSourceTransactionId());
    }

    @Test
    public void parsesGovernedQfxXmlEnvelope()
    {
        BankStatementDocument result = parser.parse(resource("qfx/valid/qfx-xml-header.qfx"));

        assertEquals(BankingDataFormat.QFX, result.format());
        assertEquals(BankStatementDocument.Variant.QFX_2_XML, result.variant());
        assertEquals("202", result.version());
        assertEquals(1, result.transactions().size());
        assertEquals("INV-0042", result.transactions().get(0).reference());
    }

    @Test
    public void parsesGovernedQfxSgmlUnclosedScalars()
    {
        BankStatementDocument result = parser.parse(resource("qfx/valid/qfx-sgml-v1.qfx"));

        assertEquals(BankingDataFormat.QFX, result.format());
        assertEquals(BankStatementDocument.Variant.QFX_1_SGML, result.variant());
        assertEquals("103", result.version());
        assertEquals("QFX-SGML-FICTIONAL-001", result.transactions().get(0).sourceTransactionId());
        assertEquals(new BigDecimal("85.00"), result.transactions().get(0).amount());
    }

    @Test
    public void contentControlsWhenFilenameExtensionDisagrees()
    {
        byte[] bytes;
        try
        {
            bytes = java.nio.file.Files.readAllBytes(resource("ofx/valid/ofx2-checking.xml"));
        }
        catch (java.io.IOException ex)
        {
            throw new IllegalStateException(ex);
        }

        BankStatementDocument result = parser.parse(bytes, "statement.qfx");

        assertEquals(BankingDataFormat.OFX, result.format());
        assertTrue(result.messages().stream()
                .anyMatch(message -> "FILENAME_CONTENT_MISMATCH".equals(message.code())));
    }

    @Test
    public void rejectsMalformedUnsupportedMultiAccountAndMissingIdentityFixtures()
    {
        assertFailure("XML_INVALID", "ofx/invalid/malformed.xml");
        assertFailure("VERSION_UNSUPPORTED", "ofx/invalid/unsupported-version.xml");
        assertFailure("MESSAGE_SET_UNSUPPORTED", "ofx/invalid/unsupported-message-set.xml");
        assertFailure("MULTI_ACCOUNT", "ofx/invalid/multi-account.xml");
        assertFailure("ELEMENT_MISSING", "ofx/invalid/missing-fitid.xml");
        assertFailure("FITID_DUPLICATE", "ofx/invalid/duplicate-fitid.xml");
    }

    @Test
    public void rejectsXmlEntitiesBeforeResolution()
    {
        assertFailure("XML_INVALID", "ofx/invalid/xml-external-entity.xml");
        assertFailure("XML_INVALID", "ofx/invalid/xml-entity-expansion.xml");
    }

    @Test
    public void rejectsEncryptedCompressedAndMalformedQfxHeaders()
    {
        assertFailure("HEADER_MALFORMED", "qfx/invalid/malformed-header.qfx");
        assertFailure("SECURITY_UNSUPPORTED", "qfx/invalid/encrypted.qfx");
        assertFailure("COMPRESSION_UNSUPPORTED", "qfx/invalid/unsupported-compression.qfx");
    }

    @Test
    public void rejectsNulAndUtf16Input()
    {
        IllegalArgumentException nul = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("<OFX>\0</OFX>".getBytes(StandardCharsets.UTF_8), "bad.ofx"));
        assertTrue(nul.getMessage().contains("ENCODING_NUL"));

        IllegalArgumentException utf16 = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(new byte[] {(byte) 0xff, (byte) 0xfe, 0x3c, 0}, "bad.ofx"));
        assertTrue(utf16.getMessage().contains("ENCODING_UNSUPPORTED"));
    }

    private void assertFailure(String code, String relative)
    {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(resource(relative)));
        assertTrue(failure.getMessage().contains(code), failure.getMessage());
    }

    private static Path resource(String relative)
    {
        return Path.of("src/test/resources/data-exchange/bank-statement").resolve(relative);
    }
}
