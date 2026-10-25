package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.*;

class CoreAccountingPacketServiceTest
{
    @TempDir Path directory;

    @Test
    void fullRowsExactAmountsCanonicalTotalsSnapshotScopeAndNoSourceWrites() throws Exception
    {
        Path db = directory.resolve("ledger");
        try (Jpa jpa = new Jpa(db))
        {
            var fixture = seed(jpa);
            var entry = fixture.entry(jpa);
            for (int i = 0; i < 251; i++) entry.enter(receipt(fixture, "1.2345", "=SUM(1,2)"));
            entry.enter(new TransactionCommand(DATE.plusDays(1), null, "OUTSIDE-CUTOFF", null,
                    List.of(fixture.line(fixture.cash(), "10", "0", fixture.event()),
                            fixture.line(fixture.income(), "0", "10", fixture.event()))));
            long transactions = count(jpa, "Txn");
            long audits = count(jpa, "AuditEvent");
            var financial = new FinancialReportService(jpa, () -> COMPANY);
            var service = new CoreAccountingPacketService(() -> db, () -> COMPANY, null);
            var result = service.export(DATE, DATE, directory.resolve("packet.zip"));
            try (ZipFile zip = new ZipFile(result.destination().toFile()))
            {
                assertEquals(7, zip.size());
                assertNull(zip.getEntry("snapshot.zip"));
                var manifest = new ObjectMapper().readTree(zip.getInputStream(zip.getEntry("manifest.json")));
                assertEquals(COMPANY, manifest.get("companyCode").asText());
                assertEquals(result.snapshotSha256(), manifest.get("snapshotSha256").asText());
                assertTrue(manifest.get("status").asText().contains("INCOMPLETE"));
                assertEquals(CoreAccountingPacketService.OMITTED.size(), manifest.get("omittedSections").size());
                String ledger = read(zip, "General Ledger.csv");
                assertFalse(ledger.contains("OUTSIDE-CUTOFF"));
                assertTrue(ledger.contains("'=SUM(1,2)"));
                assertTrue(ledger.contains("1.2345"));
                assertEquals(503, PoiXlsxFinancialReportAdapter.parseCsv(ledger).size());
                var expected = PoiXlsxFinancialReportAdapter.parseCsv(
                        FinancialReportRenderer.renderTrialBalanceCsv(financial.trialBalance(DATE, null)));
                assertEquals(expected, PoiXlsxFinancialReportAdapter.parseCsv(read(zip, "Trial Balance.csv")));
                assertTrue(read(zip, "Income Statement.csv").contains("309.8595"));
                try (XSSFWorkbook workbook = new XSSFWorkbook(zip.getInputStream(zip.getEntry("Core Accounting Packet.xlsx"))))
                {
                    assertEquals(6, workbook.getNumberOfSheets());
                    assertEquals(502, workbook.getSheet("General Ledger").getLastRowNum());
                    for (var row : workbook.getSheet("General Ledger"))
                        for (var cell : row) assertNotEquals(org.apache.poi.ss.usermodel.CellType.FORMULA, cell.getCellType());
                }
            }
            assertEquals(transactions, count(jpa, "Txn"));
            assertEquals(audits, count(jpa, "AuditEvent"));
            var second = service.export(DATE, DATE, directory.resolve("second.zip"));
            try (ZipFile first = new ZipFile(result.destination().toFile()); ZipFile next = new ZipFile(second.destination().toFile()))
            {
                for (String section : result.sections()) assertEquals(read(first, section + ".csv"), read(next, section + ".csv"));
            }
        }
    }

    @Test
    void expenseDetailsAreNotTruncatedByInteractiveLimitsAndUnknownsRemainVisible() throws Exception
    {
        Path db = directory.resolve("expenses");
        try (Jpa jpa = new Jpa(db))
        {
            var fixture = NmrSettlementTestFixture.seed(jpa);
            var entry = fixture.base().entry(jpa);
            for (int i = 0; i < 501; i++)
            {
                entry.enter(new TransactionCommand(DATE, null, "Expense " + i, null,
                        List.of(fixture.line(fixture.fee(), "1.2345", "0", null),
                                fixture.line(fixture.base().cash(), "0", "1.2345", null))));
            }
            var result = new CoreAccountingPacketService(() -> db, () -> COMPANY, null)
                    .export(DATE, DATE, directory.resolve("expenses.zip"));
            try (ZipFile zip = new ZipFile(result.destination().toFile()))
            {
                String csv = read(zip, "Functional Expenses.csv");
                assertTrue(csv.contains("Expense 500"));
                assertTrue(csv.contains("Showing 501 of 501"));
                assertTrue(csv.contains("Unclassified"));
                assertTrue(csv.contains("618.4845"));
            }
        }
    }

    @Test
    void yearEndPacketKeepsOriginalActivityAndNextYearReversalOnTheirOwnDates() throws Exception
    {
        Path db = directory.resolve("year-end");
        try (Jpa jpa = new Jpa(db))
        {
            var fixture = seed(jpa);
            var entry = fixture.entry(jpa);
            var yearEnd = java.time.LocalDate.of(2026, 12, 31);
            var nextYear = yearEnd.plusDays(1);
            long id = entry.enter(new TransactionCommand(yearEnd, null, "Year-end receipt", null,
                    List.of(fixture.line(fixture.cash(), "42.1234", "0", fixture.event()),
                            fixture.line(fixture.income(), "0", "42.1234", fixture.event())))).id();
            new TransactionCorrectionService(jpa, () -> COMPANY).reverse(id, nextYear, "tester", "Synthetic correction", false);
            long audits = count(jpa, "AuditEvent");
            var service = new CoreAccountingPacketService(() -> db, () -> COMPANY, null);
            var prior = service.export(yearEnd.withDayOfYear(1), yearEnd, directory.resolve("year.zip"));
            var current = service.export(nextYear, nextYear, directory.resolve("next-year.zip"));
            try (ZipFile before = new ZipFile(prior.destination().toFile()); ZipFile after = new ZipFile(current.destination().toFile()))
            {
                assertFalse(read(before, "General Ledger.csv").contains(nextYear.toString()));
                assertTrue(read(after, "General Ledger.csv").contains(nextYear.toString()));
                assertTrue(read(before, "Income Statement.csv").contains("42.1234"));
                assertTrue(read(after, "Income Statement.csv").contains("-42.1234"));
                var canonical = new FinancialReportService(jpa, () -> COMPANY);
                assertEquals(PoiXlsxFinancialReportAdapter.parseCsv(FinancialReportRenderer.renderBalanceSheetCsv(canonical.balanceSheet(nextYear, null))),
                        PoiXlsxFinancialReportAdapter.parseCsv(read(after, "Balance Sheet.csv")));
            }
            assertEquals(audits, count(jpa, "AuditEvent"));
        }
    }

    @Test
    void rejectsOverwriteInvalidDatesUnknownCompanyAndUnauthenticatedExportWithoutPartialOutput() throws Exception
    {
        Path db = directory.resolve("guards");
        try (Jpa jpa = new Jpa(db))
        {
            var fixture = seed(jpa);
            fixture.entry(jpa).enter(receipt(fixture, "10", "Synthetic"));
            var service = new CoreAccountingPacketService(() -> db, () -> COMPANY, null);
            Path existing = directory.resolve("existing.zip");
            Files.writeString(existing, "preserve");
            assertThrows(java.io.IOException.class, () -> service.export(DATE, DATE, existing));
            assertEquals("preserve", Files.readString(existing));
            Path failed = directory.resolve("failed.zip");
            var capacity = assertThrows(IllegalArgumentException.class,
                    () -> new CoreAccountingPacketService(() -> db, () -> COMPANY, null, 1).export(DATE, DATE, failed));
            assertTrue(capacity.getMessage().contains("ledger capacity"));
            assertFalse(Files.exists(failed));
            assertThrows(IllegalArgumentException.class, () -> service.export(DATE.plusDays(1), DATE, failed));
            assertThrows(IllegalArgumentException.class, () -> new CoreAccountingPacketService(() -> db, () -> "ABSENT", null)
                    .export(DATE, DATE, failed));
            var guard = new AuthorizationGuard(jpa, Optional::empty);
            assertThrows(AuthorizationException.class, () -> new CoreAccountingPacketService(() -> db, () -> COMPANY, guard)
                    .export(DATE, DATE, failed));
            assertFalse(Files.exists(failed));
            assertEquals(1, count(jpa, "Txn"));
            try (var files = Files.list(directory))
            {
                assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".partial")));
            }
            var other = new CoreAccountingPacketService(() -> db, () -> "DEFAULT", null)
                    .export(DATE, DATE, directory.resolve("other.zip"));
            try (ZipFile zip = new ZipFile(other.destination().toFile()))
            {
                assertFalse(read(zip, "General Ledger.csv").contains("Synthetic"));
            }
        }
    }

    private static TransactionCommand receipt(NmrItemTestFixture fixture, String amount, String memo)
    {
        return new TransactionCommand(DATE, null, memo, null,
                List.of(fixture.line(fixture.cash(), amount, "0", fixture.event()),
                        fixture.line(fixture.income(), "0", amount, fixture.event())));
    }

    private static long count(Jpa jpa, String entity)
    {
        try (var em = jpa.em()) { return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult(); }
    }

    private static String read(ZipFile zip, String name) throws Exception
    {
        try (var input = zip.getInputStream(zip.getEntry(name)))
        {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
