package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * OfxQfxTransactionExtractorTest component.
 */
public class OfxQfxTransactionExtractorTest
{
    @Test
    public void extract_supportsXmlStyleTagBodies()
    {
        OfxQfxTransactionExtractor extractor = new OfxQfxTransactionExtractor();

        String payload = "<OFX><STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260313000000</DTPOSTED><TRNAMT>-10.25</TRNAMT><FITID>TX-1</FITID><NAME>Printer Ink</NAME><MEMO>Office</MEMO></STMTTRN></OFX>";

        List<BankTransactionRecord> records = extractor.extract(payload);

        assertEquals(1, records.size());
        assertEquals("TX-1", records.get(0).fitId());
        assertEquals("-10.25", records.get(0).amount().toPlainString());
    }

    @Test
    public void extract_supportsOfxOneLineTagBodies()
    {
        OfxQfxTransactionExtractor extractor = new OfxQfxTransactionExtractor();

        String payload = "<QFX><STMTTRN>\n"
                + "<TRNTYPE>DEBIT\n"
                + "<DTPOSTED>20260313000000\n"
                + "<TRNAMT>-11.50\n"
                + "<FITID>TX-2\n"
                + "<NAME>Coffee Shop\n"
                + "<MEMO>Team sync\n"
                + "</STMTTRN></QFX>";

        List<BankTransactionRecord> records = extractor.extract(payload);

        assertEquals(1, records.size());
        assertEquals("TX-2", records.get(0).fitId());
        assertEquals("Coffee Shop", records.get(0).name());
    }
}
