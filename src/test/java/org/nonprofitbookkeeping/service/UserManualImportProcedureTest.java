package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.exchequer.ExchequerCsvFixture;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.UserManualProcedureFixture.*;

@DisplayName("Manual: Import Exchequer CSV → review, edit/delete, commit → Journal")
class UserManualImportProcedureTest
{
    @TempDir Path directory;

    @Test
    void reviewMapsAllFourSplitsDeletesUnwantedProposalAndCommitsLiteralDateWithoutClearing() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("four-splits")))
        {
            var f = setUp(jpa, directory);
            var rows = ExchequerCsvFixture.header();
            var row = ExchequerCsvFixture.row(1, "12/31/2025", "100.00");
            row.set(4, "1/2/2026");
            row.set(5, "000042");
            row.set(6, "12/30/2025");
            for (int i = 0; i < 4; i++)
            {
                ExchequerCsvFixture.split(row, i, Integer.toString((i + 1) * 10), "Income allocation " + (i + 1), "");
            }
            rows.add(row);
            var deleted = ExchequerCsvFixture.row(2, "1/5/2026", "50");
            ExchequerCsvFixture.split(deleted, 0, "50", "Unwanted proposal", "");
            rows.add(deleted);
            Path source = directory.resolve("report-2026-Q4.csv");
            Files.writeString(source, ExchequerCsvFixture.csv(rows));
            byte[] original = Files.readAllBytes(source);
            long audits = count(jpa, "AuditEvent");
            var importer = new ExchequerImportService(jpa, COMPANY, null);
            var preview = importer.preview(source);
            assertEquals(2, preview.entries().size());
            assertEquals(5, preview.entries().get(0).command().lines().size());
            assertEquals(0, count(jpa, "Txn"));
            assertEquals(audits, count(jpa, "AuditEvent"), "Review/preview must not write business records");
            var proposed = preview.entries().get(0);
            var mapped = new ArrayList<TransactionLineCommand>();
            for (int i = 0; i < proposed.command().lines().size(); i++)
            {
                var line = proposed.command().lines().get(i);
                mapped.add(new TransactionLineCommand(i == 0 ? f.checking() : f.income(),
                        i < 3 ? f.general() : f.designated(), null, null, null,
                        line.debit(), line.credit(), false, line.notes()));
            }
            var reviewed = new ExchequerImportService.ProposedEntry(proposed.sourceLine(), new TransactionCommand(
                    proposed.command().date(), null, "Edited four-allocation entry", null, mapped,
                    List.of(), proposed.command().information()));
            // The second proposed record is deliberately omitted, as Delete does in the review page.
            var result = importer.commit(preview, List.of(reviewed), false);
            assertEquals(1, result.transactionIds().size());
            var saved = f.journal(jpa).load(result.transactionIds().get(0));
            assertEquals(LocalDate.of(2025, 12, 31), saved.date());
            assertEquals("Edited four-allocation entry", saved.memo());
            assertEquals("000042", saved.information().transferOrCheckReference());
            assertEquals(LocalDate.of(2026, 1, 2), saved.information().statementDate());
            assertEquals(LocalDate.of(2025, 12, 30), saved.information().incomingDate());
            assertEquals(5, saved.lines().size());
            assertEquals(List.of(10, 20, 30, 40), saved.lines().stream().filter(l -> l.credit().signum() > 0)
                    .map(l -> l.credit().intValueExact()).sorted().toList());
            assertEquals(2, saved.lines().stream().filter(l -> l.fundId() == f.designated()).count());
            UserManualBookkeepingProcedureTest.money("100", saved.debitTotal());
            UserManualBookkeepingProcedureTest.money("100", saved.creditTotal());
            assertEquals(1, count(jpa, "Txn"));
            assertFalse(new BankingWorkspaceQuery(jpa).journal(COMPANY, f.checkingBank(), END).get(0).cleared());
            assertThrows(IllegalArgumentException.class, () -> importer.commit(preview, List.of(reviewed), true));
            assertEquals(1, count(jpa, "Txn"), "Retry cannot duplicate an already imported source record");
            assertArrayEquals(original, Files.readAllBytes(source));
        }
    }

    @Test
    void offsettingInvalidRowsCannotCommitThenCorrectedProposalsCanBeRetriedTogether() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("correct-import")))
        {
            var f = setUp(jpa, directory);
            Path source = ExchequerCsvFixture.write(directory.resolve("source.csv"), "3/1/2026", "3/2/2026");
            byte[] original = Files.readAllBytes(source);
            var importer = new ExchequerImportService(jpa, COMPANY, null);
            var preview = importer.preview(source);
            long audits = count(jpa, "AuditEvent");
            var invalid = List.of(mapped(preview.entries().get(0), f, "100", "99.99"),
                    mapped(preview.entries().get(1), f, "99.99", "100"));
            assertThrows(IllegalArgumentException.class, () -> importer.commit(preview, invalid, false));
            assertEquals(0, count(jpa, "Txn"));
            assertEquals(0, count(jpa, "TxnSplit"));
            assertEquals(audits, count(jpa, "AuditEvent"));
            assertTrue(importer.importedSourceLines(preview).isEmpty());
            var corrected = List.of(mapped(preview.entries().get(0), f, "100", "100"),
                    mapped(preview.entries().get(1), f, "99.99", "99.99"));
            var result = importer.commit(preview, corrected, false);
            assertEquals(2, result.transactionIds().size());
            assertEquals(2, count(jpa, "Txn"));
            for (long id : result.transactionIds())
            {
                var saved = f.journal(jpa).load(id);
                assertEquals(0, saved.debitTotal().compareTo(saved.creditTotal()));
            }
            assertArrayEquals(original, Files.readAllBytes(source));
        }
    }

    private static ExchequerImportService.ProposedEntry mapped(ExchequerImportService.ProposedEntry source,
            UserManualProcedureFixture f, String debit, String credit)
    {
        return new ExchequerImportService.ProposedEntry(source.sourceLine(), new TransactionCommand(
                source.command().date(), null, "Reviewed " + source.sourceLine(), null,
                List.of(line(f.checking(), f.general(), debit, "0"), line(f.income(), f.general(), "0", credit)),
                List.of(), source.command().information()));
    }
}
