package org.nonprofitbookkeeping.interchange.exchequer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.service.ImportPreviewService;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.interchange.exchequer.ExchequerReportCsvDocument.*;

class ExchequerReportCsvImporterTest
{
    @TempDir Path directory;
    private final ExchequerReportCsvImporter importer = new ExchequerReportCsvImporter();
    private static final int[] SPLITS = {32, 58, 84, 110};

    @Test
    void readsWideReportFourSplitsLiteralDatesOpeningAndSourceFacts() throws Exception
    {
        List<List<String>> records = header();
        List<String> first = row(1, "10/22/2026", "100.00");
        first.set(5, " 00123\t"); first.set(7, " Example payee "); first.set(8, "Quoted, \"notes\"\r\non two lines");
        first.set(14, "Example merchant"); first.set(15, "2");
        for (int index = 0; index < 4; index++) split(first, index, "25.00", "Example income " + index, "");
        first.set(36, "FR (Fundr.)"); first.set(37, "item-1"); first.set(38, "2.5"); records.add(first);
        List<String> second = row(2, "11/2/2026", "(50.25)"); split(second, 0, "50.25", "", "Example expense"); records.add(second);
        records.add(blank());
        Path path = write("Example 2026-Q1.csv", records);
        byte[] original = Files.readAllBytes(path);
        var document = importer.read(path);
        assertFalse(document.hasBlockingErrors(), document.messages().toString());
        assertEquals(2, document.postingRows().size());
        assertEquals(LocalDate.of(2026, 10, 22), document.rows().get(0).transactionDate());
        assertEquals(LocalDate.of(2026, 11, 2), document.rows().get(1).transactionDate());
        assertEquals(4, document.rows().get(0).splits().size());
        assertEquals(new BigDecimal("100.00"), document.rows().get(0).netAmount());
        assertEquals(new BigDecimal("-50.25"), document.rows().get(1).netAmount());
        assertEquals(new BigDecimal("-50.25"), document.rows().get(1).splits().get(0).bankEffect());
        assertEquals(first.get(5), document.rows().get(0).reference());
        assertEquals(first.get(7), document.rows().get(0).payee());
        assertEquals(first.get(8), document.rows().get(0).notes());
        assertEquals(first, document.rows().get(0).originalFields());
        assertEquals("FR", document.rows().get(0).splits().get(0).expenseFunctionCode());
        assertEquals(new BigDecimal("2.5"), document.rows().get(0).splits().get(0).quantity());
        assertEquals(LocalDate.of(2025, 12, 31), document.metadata().openingBalance().date());
        assertEquals(new BigDecimal("1000.00"), document.metadata().openingBalance().amount());
        assertEquals(64, document.sourceSha256().length());
        assertArrayEquals(original, Files.readAllBytes(path));
        assertThrows(UnsupportedOperationException.class, () -> document.rows().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.rows().get(0).originalFields().clear());
    }

    @Test
    void neverFiltersOrValidatesQuarterYearOrFilename() throws Exception
    {
        var records = header();
        for (String date : List.of("12/31/2025", "1/1/2027", "2026-10-22"))
        {
            var row = row(records.size() - 7, date, "1"); split(row, 0, "1", "Income", "");
            row.set(22, "FALSE"); row.set(23, "FALSE"); row.set(24, "TRUE"); records.add(row);
        }
        var result = new ImportPreviewService().previewExchequerReportCsv(write("report-1901-Q2.csv", records));
        assertFalse(result.hasBlockingErrors(), result.messages().toString());
        assertEquals(List.of(LocalDate.of(2025,12,31), LocalDate.of(2027,1,1), LocalDate.of(2026,10,22)),
                result.postingRows().stream().map(Row::transactionDate).toList());
        assertTrue(result.messages().stream().noneMatch(m -> m.code().contains("QUARTER") || m.code().contains("YEAR")));
    }

    @Test
    void skipsFormulaDefaultsButRetainsZeroAndNoteOnlyRowsWithoutInventingPostings() throws Exception
    {
        var records = header();
        records.add(row(1, "", "0.00"));
        var voidRow = row(2, "", "0"); voidRow.set(5, "VOID-123"); voidRow.set(8, "Cancelled source record"); records.add(voidRow);
        var note = row(3, "", "0"); note.set(7, "Source note"); note.set(35, "Unfinished category"); records.add(note);
        var computed = blank(); computed.set(171, "#REF!"); computed.set(500, "600"); records.add(computed);
        var document = importer.read(write("defaults.csv", records));
        assertFalse(document.hasBlockingErrors(), document.messages().toString());
        assertEquals(3, document.numberedRowCount()); assertEquals(1, document.skippedTemplateRows());
        assertEquals(1, document.skippedHelperRows()); assertEquals(2, document.rows().size());
        assertTrue(document.postingRows().isEmpty());
        assertTrue(document.rows().stream().allMatch(r -> r.kind() == RowKind.NON_POSTING));
        assertEquals("Unfinished category", document.rows().get(1).splits().get(0).expenseCategory());
    }

    @Test
    void preservesNegativeAdjustmentsAndZeroNetMonetaryTransactions() throws Exception
    {
        var records = header();
        var adjustment = row(1, "2/1/2026", "25.00"); split(adjustment, 0, "(25.00)", "", "Expense credit"); records.add(adjustment);
        var transfer = row(2, "2/2/2026", "0"); split(transfer, 0, "10", "Income", ""); split(transfer, 1, "10", "", "Expense"); records.add(transfer);
        var document = importer.read(write("adjustments.csv", records));
        assertFalse(document.hasBlockingErrors(), document.messages().toString());
        assertEquals(2, document.postingRows().size());
        assertEquals(new BigDecimal("-25.00"), document.rows().get(0).splits().get(0).amount());
        assertEquals(new BigDecimal("25.00"), document.rows().get(0).splits().get(0).bankEffect());
    }

    @Test
    void retainsMalformedBusinessRowsWithBlockingDiagnosticsAndValidNeighbors() throws Exception
    {
        for (String fault : List.of("date", "statementDate", "missingDate", "amount", "precision", "balance", "bothCategories", "noCategory", "missingSplit", "splitRow", "timing", "duplicate"))
        {
            var records = header();
            var bad = row(1, "2/28/2026", "(10.00)"); split(bad, 0, "10.00", "", "Expense");
            switch (fault)
            {
                case "date" -> bad.set(3, "2/30/2026");
                case "statementDate" -> bad.set(4, "2/30/2026");
                case "missingDate" -> bad.set(3, "");
                case "amount" -> bad.set(33, "1,00.00");
                case "precision" -> bad.set(33, "10.12345");
                case "balance" -> bad.set(2, "11");
                case "bothCategories" -> bad.set(34, "Income too");
                case "noCategory" -> bad.set(35, "");
                case "missingSplit" -> bad.set(33, "");
                case "splitRow" -> bad.set(32, "2");
                case "timing" -> bad.set(10, "UNKNOWN");
                case "duplicate" -> { }
            }
            records.add(bad);
            var good = row(fault.equals("duplicate") ? 1 : 2, "3/1/2026", "1"); split(good, 0, "1", "Income", ""); records.add(good);
            var document = importer.read(write(fault + ".csv", records));
            assertTrue(document.hasBlockingErrors(), fault);
            assertEquals(2, document.rows().size(), fault);
            assertTrue(document.rows().stream().anyMatch(r -> r.kind() == RowKind.INVALID), fault);
            assertEquals(bad, document.rows().get(0).originalFields(), fault);
        }
    }

    @Test
    void enforcesLayoutAndRejectsTruncationAndMalformedQuoting() throws Exception
    {
        for (String fault : List.of("width", "header", "version", "opening"))
        {
            var records = header(); var row = row(1, "1/1/2026", "1"); split(row, 0, "1", "Income", ""); records.add(row);
            switch (fault)
            {
                case "width" -> row.remove(638);
                case "header" -> records.get(6).set(34, "Unknown column");
                case "version" -> records.get(0).set(116, "SCA EXCHEQUER REPORT (2099-01)");
                case "opening" -> records.get(7).set(0, "1");
            }
            Path source = write(fault + ".csv", records);
            assertThrows(IllegalArgumentException.class, () -> importer.read(source), fault);
        }
        Path source = directory.resolve("quotes.csv"); Files.writeString(source, "\"unclosed");
        assertThrows(IllegalArgumentException.class, () -> importer.read(source));
    }

    @Test
    void supportsBomAndExplicitEncodingWithoutReplacingInvalidBytes() throws Exception
    {
        var records = header(); var row = row(1, "1/1/2026", "1"); row.set(8, "Café"); split(row, 0, "1", "Income", ""); records.add(row);
        Path utf = write("bom.csv", records); Files.writeString(utf, "\uFEFF" + Files.readString(utf));
        assertFalse(importer.read(utf).hasBlockingErrors());
        Path legacy = directory.resolve("legacy.csv"); Files.write(legacy, csv(records).getBytes(Charset.forName("windows-1252")));
        assertThrows(IllegalArgumentException.class, () -> importer.read(legacy));
        assertEquals("Café", importer.read(legacy, Charset.forName("windows-1252")).rows().get(0).notes());
    }

    @Test
    void retainsNotCashedStatementAnnotationWithoutChangingEntryDate() throws Exception
    {
        var records = header(); var row = row(1, "10/22/2026", "(1)");
        split(row, 0, "1", "", "Expense"); row.set(4, "not cashed"); records.add(row);
        var report = importer.read(write("status.csv", records));
        assertFalse(report.hasBlockingErrors(), report.messages().toString());
        assertEquals(1, report.postingRows().size());
        assertEquals(LocalDate.of(2026,10,22), report.rows().get(0).transactionDate());
        assertNull(report.rows().get(0).statementDate());
        assertEquals("not cashed", report.rows().get(0).statementDateText());
        assertTrue(report.messages().stream().anyMatch(m -> m.code().equals("EXCHEQUER_STATEMENT_STATUS")));
    }

    @Test
    void computedErrorsAndUnknownPurposesRemainVisibleWithoutChangingFinancialFacts() throws Exception
    {
        var records = header(); var row = row(1, "1/1/2026", "(1)"); split(row, 0, "1", "", "Expense");
        row.set(1, "#REF!"); row.set(36, "Unknown purpose"); row.set(15, "999"); records.add(row);
        var report = importer.read(write("annotations.csv", records));
        assertFalse(report.hasBlockingErrors(), report.messages().toString());
        assertNull(report.rows().get(0).runningBalance()); assertEquals("Unknown purpose", report.rows().get(0).splits().get(0).usedFor());
        assertTrue(report.messages().stream().anyMatch(m -> m.code().equals("EXCHEQUER_COMPUTED_BALANCE")));
        assertTrue(report.messages().stream().anyMatch(m -> m.code().equals("EXCHEQUER_PURPOSE")));
        assertTrue(report.messages().stream().anyMatch(m -> m.code().equals("EXCHEQUER_UNRESOLVED_LINK")));
    }

    private Path write(String name, List<List<String>> records) throws Exception
    {
        Path source = directory.resolve(name); Files.writeString(source, csv(records), StandardCharsets.UTF_8); return source;
    }

    private static String csv(List<List<String>> records)
    {
        return records.stream().map(row -> row.stream().map(value -> "\"" + value.replace("\"", "\"\"") + "\"")
                .collect(java.util.stream.Collectors.joining(","))).collect(java.util.stream.Collectors.joining("\r\n"));
    }

    private static List<String> blank()
    {
        return new ArrayList<>(java.util.Collections.nCopies(639, ""));
    }

    private static List<List<String>> header()
    {
        List<List<String>> records = new ArrayList<>(); for (int i = 0; i < 8; i++) records.add(blank());
        records.get(0).set(116, "SCA, INC. EXCHEQUER REPORT (2026-03)");
        records.get(1).set(1, "Example branch"); records.get(2).set(1, "2026 LEDGER, REPORTING TO Q3");
        records.get(5).set(1, "Checking"); records.get(7).set(1, "1,000.00"); records.get(7).set(3, "12/31/2025");
        String[] core = {"Row", "Balance*", "Transaction", "Date", "on Statement", "or Chq #", "or Tfr Date", "Legal Name of Person/Organization", "Details/Notes", "Bank Account", "The Bank", "Budget Category", "The Budget", "Fund", "Merchant", "Row #", "Reversed"};
        for (int i = 0; i < core.length; i++) records.get(6).set(i, core[i]);
        for (int start : SPLITS)
        {
            String[] headers = {"Row", "Amount ", "Income Category", "Expense Category", "(Exp. Type)", "Num", "#Purch"};
            for (int i = 0; i < headers.length; i++) records.get(6).set(start + i, headers[i]);
        }
        return records;
    }

    private static List<String> row(int number, String date, String net)
    {
        var row = blank(); row.set(0, Integer.toString(number)); row.set(1, "1,000.00"); row.set(2, net); row.set(3, date);
        row.set(9, "Checking"); row.set(10, "Now"); row.set(12, "Now"); row.set(13, "General Fund");
        for (int start : SPLITS) { row.set(start, Integer.toString(number)); row.set(start + 4, "AR (Events)"); }
        return row;
    }

    private static void split(List<String> row, int ordinal, String amount, String income, String expense)
    {
        int start = SPLITS[ordinal]; row.set(start + 1, amount); row.set(start + 2, income); row.set(start + 3, expense);
    }
}
