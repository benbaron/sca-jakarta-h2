package org.nonprofitbookkeeping.interchange.csv;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CsvRecordReaderTest
{
    private final CsvRecordReader.Limits limits = new CsvRecordReader.Limits("Test CSV", 4, 5, 100, 50, 12);

    @Test
    void keepsQuotedCommasEscapesMultilinePhysicalProvenanceAndEmptyTrailingCells()
    {
        var rows = CsvRecordReader.read("a,\"b,b\",\"c\"\"d\",\r\n\"line1\r\nline2\",x,,\r\nlast,,,", ',', limits);
        assertEquals(3, rows.size()); assertEquals(1, rows.get(0).lineNumber()); assertEquals(2, rows.get(1).lineNumber()); assertEquals(4, rows.get(2).lineNumber());
        assertEquals(java.util.List.of("a", "b,b", "c\"d", ""), rows.get(0).fields());
        assertEquals("line1\r\nline2", rows.get(1).fields().get(0));
        assertEquals("\"line1\r\nline2\",x,,", rows.get(1).original());
    }

    @Test
    void rejectsMalformedQuotesAndEnforcesEachConfiguredBound()
    {
        for (String bad : java.util.List.of("\"unclosed", "bad\"quote", "\"closed\"tail", "a,\u0000"))
            assertThrows(IllegalArgumentException.class, () -> CsvRecordReader.read(bad, ',', limits));
        assertThrows(IllegalArgumentException.class, () -> CsvRecordReader.read("a,b,c,d,e", ',', limits));
        assertThrows(IllegalArgumentException.class, () -> CsvRecordReader.read("x".repeat(51), ',', limits));
        assertThrows(IllegalArgumentException.class, () -> CsvRecordReader.read("a\nb\nc\nd\ne\nf", ',', limits));
        assertThrows(IllegalArgumentException.class, () -> CsvRecordReader.read("a,b,c,d\na,b,c,d\na,b,c,d\nx", ',', limits));
        assertThrows(IllegalArgumentException.class, () -> CsvRecordReader.read("x".repeat(40)+","+"x".repeat(40)+","+"x".repeat(30), ',', limits));
    }

    @Test
    void wideWorksheetLimitsDoNotRelaxBankStyleLimits()
    {
        String wide = String.join(",", java.util.Collections.nCopies(639, "value"));
        var bank = new CsvRecordReader.Limits("Bank CSV", 128, 10, 10000, 100, 10000);
        var workbook = new CsvRecordReader.Limits("Exchequer CSV", 1024, 10, 10000, 100, 10000);
        assertThrows(IllegalArgumentException.class, () -> CsvRecordReader.read(wide, ',', bank));
        assertEquals(639, CsvRecordReader.read(wide, ',', workbook).get(0).fields().size());
    }
}
