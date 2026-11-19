package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.TransactionInformation;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.FundTransferTestFixture;
import org.nonprofitbookkeeping.service.TransactionCommand;
import org.nonprofitbookkeeping.service.TransactionEntryService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SclxJournalInformationRoundTripTest
{
    @TempDir Path directory;

    @Test
    void sourceInformationRoundTripsThroughPreviewCommitRestartAndReplayWithoutClearance() throws Exception
    {
        Path file = directory.resolve("information.sclx");
        var information = new TransactionInformation("Transfer-000042", LocalDate.of(2026, 2, 6), LocalDate.of(2026, 1, 30));
        try (Jpa source = new Jpa(directory.resolve("source")))
        {
            var f = FundTransferTestFixture.seed(source);
            new TransactionEntryService(source).enter(new TransactionCommand(LocalDate.of(2026, 2, 1), null,
                    "Information export", null, List.of(FundTransferTestFixture.line(f.bank(), f.source(), "10", "0"),
                    FundTransferTestFixture.line(f.income(), f.source(), "0", "10")), List.of(), information));
            var document = new SclxCoreSnapshotQueryService(source, () -> "DEFAULT").query(Instant.EPOCH);
            byte[] bytes = new SclxJsonSerializer().serialize(document);
            assertArrayEquals(bytes, new SclxJsonSerializer().serialize(document));
            Files.write(file, bytes);
            assertEquals(1, new ObjectMapper().readTree(bytes).path("extensions").path("scaJakartaH2")
                    .path("journalInformation").path("transactions").size());
        }
        Path database = directory.resolve("target");
        try (Jpa target = new Jpa(database))
        {
            ObjectNode fractional = (ObjectNode) new ObjectMapper().readTree(file.toFile());
            for (var transaction : fractional.path("transactions"))
            {
                if (!"Information export".equals(transaction.path("description").asText())) continue;
                for (var line : transaction.path("lines"))
                {
                    var mutable = (ObjectNode) line;
                    if (new java.math.BigDecimal(line.path("debit").asText("0")).signum() > 0) mutable.put("debit", new java.math.BigDecimal("10.001"));
                    if (new java.math.BigDecimal(line.path("credit").asText("0")).signum() > 0) mutable.put("credit", new java.math.BigDecimal("10.001"));
                }
            }
            Path fractionalFile = directory.resolve("fractional.sclx");
            new ObjectMapper().writeValue(fractionalFile.toFile(), fractional);
            var blocked = new SclxImportPreviewService(target, () -> "DEFAULT").preview(fractionalFile);
            assertTrue(blocked.hasBlockingErrors());
            assertTrue(blocked.operation().messages().stream().anyMatch(message -> message.code().equals("SCLX_LEDGER_PRECISION")));
            var preview = new SclxImportPreviewService(target, () -> "DEFAULT").preview(file);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertTrue(new SclxImportCommitService(target, () -> "DEFAULT").commit(file, preview, "tester", true, true).committed());
        }
        try (Jpa target = new Jpa(database))
        {
            var service = new TransactionEntryService(target);
            var saved = service.search(null, null, "Information export", 100).get(0);
            assertEquals(information, saved.information());
            assertTrue(saved.lines().stream().noneMatch(line -> line.bankCleared() || line.bankClearedOn() != null));
            var preview = new SclxImportPreviewService(target, () -> "DEFAULT").preview(file);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertTrue(new SclxImportCommitService(target, () -> "DEFAULT").commit(file, preview, "tester", true, true).committed());
            assertEquals(2, service.search(null, null, "", 100).size());
            ObjectNode changed = (ObjectNode) new ObjectMapper().readTree(file.toFile());
            ((ObjectNode) changed.path("extensions").path("scaJakartaH2").path("journalInformation").path("transactions").get(0))
                    .put("transferOrCheckReference", "Changed-source-reference");
            Path changedFile = directory.resolve("changed.sclx");
            new ObjectMapper().writeValue(changedFile.toFile(), changed);
            var conflicts = new SclxImportPreviewService(target, () -> "DEFAULT").preview(changedFile);
            assertTrue(conflicts.operation().items().stream().anyMatch(item -> item.entityType().equals("TRANSACTION")
                    && item.identityMatch() == InterchangeIdentityMatch.CONFLICT));
            var keep = conflicts.operation().items().stream().filter(item -> item.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                    .map(item -> new SclxImportConflictSelection(item.entityType(), item.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
            var resolved = new SclxImportPreviewService(target, () -> "DEFAULT").preview(changedFile, List.of(), keep);
            assertFalse(resolved.hasBlockingErrors(), () -> resolved.operation().messages().toString());
            assertTrue(new SclxImportCommitService(target, () -> "DEFAULT").commit(changedFile, resolved, "tester", true, true).committed());
            assertEquals(information, service.load(saved.id()).information(), "Keep target must retain complete header facts");
            var exported = new SclxCoreSnapshotQueryService(target, () -> "DEFAULT").query(Instant.EPOCH);
            var roundTrip = new ObjectMapper().readTree(new SclxJsonSerializer().serialize(exported));
            var fact = roundTrip.path("extensions").path("scaJakartaH2").path("journalInformation").path("transactions").get(0);
            assertEquals("Transfer-000042", fact.path("transferOrCheckReference").asText());
            assertEquals("2026-02-06", fact.path("statementDate").asText());
        }
    }
}
