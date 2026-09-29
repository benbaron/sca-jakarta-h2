package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.nonprofitbookkeeping.interchange.sclx.*;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.SupplementalOpenItemQueryServiceTest.*;

class SclxLifecycleRoundTripTest
{
    private static final LocalDate OPEN = LocalDate.of(2026, 1, 10);
    private static final LocalDate PAID = LocalDate.of(2026, 2, 10);
    private static final LocalDate REVERSED = LocalDate.of(2026, 3, 5);

    @ParameterizedTest
    @EnumSource(SupplementalOpenItemQueryService.Kind.class)
    void preservesPartialAndCorrectedHistoryForEveryKind(SupplementalOpenItemQueryService.Kind kind, @TempDir Path dir) throws Exception
    {
        try (Jpa source = new Jpa(dir.resolve("source")); Jpa target = new Jpa(dir.resolve("target")))
        {
            seed(source);
            empty(target);
            var entry = new TransactionEntryService(source, () -> "TEST");
            UUID item = UUID.randomUUID();
            var opening = enterIncrease(entry, kind, item, OPEN, new BigDecimal("100"));
            var payment = enterDecrease(entry, kind, item, PAID, new BigDecimal("25"));
            Path file = export(source, dir.resolve("partial.sclx"));
            try (Jpa partial = new Jpa(dir.resolve("partial")))
            {
                empty(partial);
                importFile(partial, file, false);
                assertBalance(partial, kind, item, PAID, "75.0000");
            }
            new TransactionCorrectionService(source, () -> "TEST").reverse(payment.id(), REVERSED, "tester", "undo payment", false);
            new TransactionCorrectionService(source, () -> "TEST").reverse(opening.id(), REVERSED.plusDays(1), "tester", "replace opening", true);
            file = export(source, dir.resolve("corrected.sclx"));
            var mapper = new ObjectMapper();
            ObjectNode root = (ObjectNode) mapper.readTree(file.toFile());
            ArrayNode reversed = mapper.createArrayNode();
            for (int i = root.path("transactions").size() - 1; i >= 0; i--) reversed.add(root.path("transactions").get(i));
            root.set("transactions", reversed);
            mapper.writeValue(file.toFile(), root);
            importFile(target, file, false);
            assertBalance(target, kind, item, OPEN, "100.0000");
            assertBalance(target, kind, item, PAID, "75.0000");
            assertBalance(target, kind, item, REVERSED, "100.0000");
            importFile(target, file, false);
            assertBalance(target, kind, item, REVERSED.plusDays(1), "100.0000");
            assertEquals(5, new TransactionEntryService(target, () -> "TEST").search(null, null, null, 100).size());
            ObjectNode reexport = (ObjectNode) mapper.readTree(export(target, dir.resolve("again.sclx")).toFile());
            assertEquals(root.path("extensions").path("scaJakartaH2").path("supplementalDetails"),
                    reexport.path("extensions").path("scaJakartaH2").path("supplementalDetails"));
        }
    }

    @Test
    void blocksMalformedLinksAndKeepsLegacyAcknowledgment(@TempDir Path dir) throws Exception
    {
        try (Jpa source = new Jpa(dir.resolve("source")); Jpa target = new Jpa(dir.resolve("target")))
        {
            seed(source);
            empty(target);
            enterIncrease(new TransactionEntryService(source, () -> "TEST"), SupplementalOpenItemQueryService.Kind.RECEIVABLE,
                    UUID.randomUUID(), OPEN, new BigDecimal("100"));
            Path file = export(source, dir.resolve("linked.sclx"));
            var mapper = new ObjectMapper();
            ObjectNode original = (ObjectNode) mapper.readTree(file.toFile());
            for (String field : new String[] {"version", "transactionLineId", "itemId", "itemEffect"})
            {
                ObjectNode bad = original.deepCopy();
                ObjectNode link = (ObjectNode) bad.path("extensions").path("scaJakartaH2").path("supplementalDetails").get(0).path("lifecycle");
                if (field.equals("version")) link.put(field, 2); else link.put(field, "bad-reference");
                mapper.writeValue(file.toFile(), bad);
                assertTrue(new SclxImportPreviewService(target, () -> "TEST").preview(file).hasBlockingErrors(), field);
            }
            ObjectNode legacy = original.deepCopy();
            ((ObjectNode) legacy.path("extensions").path("scaJakartaH2").path("supplementalDetails").get(0)).remove("lifecycle");
            mapper.writeValue(file.toFile(), legacy);
            var preview = new SclxImportPreviewService(target, () -> "TEST").preview(file);
            assertTrue(preview.operation().messages().stream().anyMatch(m -> m.code().equals("SCLX_UNALLOCATED_CONTROL_LINE")));
            assertFalse(new SclxImportCommitService(target, () -> "TEST").commit(file, preview, "tester", false, false, false).committed());
            importFile(target, file, true);
            assertFalse(new SupplementalOpenItemQueryService(target, () -> "TEST").query(SupplementalOpenItemQueryService.Kind.RECEIVABLE, OPEN).ready());
        }
    }

    @Test
    void excessApplicationsRollBackTheEntireImportedBatch(@TempDir Path dir) throws Exception
    {
        try (Jpa source = new Jpa(dir.resolve("source")); Jpa target = new Jpa(dir.resolve("target")))
        {
            seed(source);
            empty(target);
            var entry = new TransactionEntryService(source, () -> "TEST");
            UUID item = UUID.randomUUID();
            var kind = SupplementalOpenItemQueryService.Kind.PAYABLE;
            enterIncrease(entry, kind, item, OPEN, new BigDecimal("100"));
            enterDecrease(entry, kind, item, PAID, new BigDecimal("25"));
            enterDecrease(entry, kind, item, PAID.plusDays(1), new BigDecimal("25"));
            Path file = export(source, dir.resolve("excess.sclx"));
            var mapper = new ObjectMapper();
            ObjectNode root = (ObjectNode) mapper.readTree(file.toFile());
            for (var txn : root.path("transactions"))
            {
                if (txn.path("transactionDate").asText().equals(OPEN.toString())) continue;
                for (var line : txn.path("lines"))
                {
                    for (String field : new String[] {"debit", "credit"})
                    {
                        if (new BigDecimal(line.path(field).asText()).signum() > 0) ((ObjectNode) line).put(field, "60.0000");
                    }
                }
            }
            for (var detail : root.path("extensions").path("scaJakartaH2").path("supplementalDetails"))
            {
                if (detail.path("lifecycle").path("itemEffect").asText().equals("DECREASE")) ((ObjectNode) detail).put("amount", "60.0000");
            }
            mapper.writeValue(file.toFile(), root);
            var preview = new SclxImportPreviewService(target, () -> "TEST").preview(file);
            assertFalse(preview.hasBlockingErrors());
            var result = new SclxImportCommitService(target, () -> "TEST").commit(file, preview, "tester", true, true, false);
            assertTrue(result.rolledBack());
            assertTrue(result.messages().stream().anyMatch(m -> m.message().contains("over-applied")));
            try (var em = target.em())
            {
                assertEquals(0L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(a) from Account a", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(i) from InterchangeIdentity i", Long.class).getSingleResult());
            }
        }
    }

    @Test
    void changedLifecycleRequiresResolutionAndForeignTransactionIsRejected(@TempDir Path dir) throws Exception
    {
        try (Jpa source = new Jpa(dir.resolve("source")); Jpa target = new Jpa(dir.resolve("target")))
        {
            seed(source);
            empty(target);
            var entry = new TransactionEntryService(source, () -> "TEST");
            enterIncrease(entry, SupplementalOpenItemQueryService.Kind.RECEIVABLE, UUID.randomUUID(), OPEN, new BigDecimal("100"));
            Path file = export(source, dir.resolve("conflict.sclx"));
            importFile(target, file, false);
            var mapper = new ObjectMapper();
            ObjectNode root = (ObjectNode) mapper.readTree(file.toFile());
            ((ObjectNode) root.path("extensions").path("scaJakartaH2").path("supplementalDetails").get(0).path("lifecycle"))
                    .put("itemId", UUID.randomUUID().toString());
            mapper.writeValue(file.toFile(), root);
            var preview = new SclxImportPreviewService(target, () -> "TEST").preview(file);
            assertTrue(preview.hasBlockingErrors());
            assertTrue(preview.operation().items().stream().anyMatch(i -> i.entityType().equals("SUPPLEMENTAL_DETAIL")
                    && i.identityMatch() == org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch.CONFLICT));
            try (var em = target.em())
            {
                em.getTransaction().begin();
                Company other = new Company();
                other.setCode("OTHER");
                other.setDisplayName("Other");
                em.persist(other);
                em.getTransaction().commit();
            }
            assertTrue(new SclxImportPreviewService(target, () -> "OTHER").preview(file).hasBlockingErrors());
            assertEquals(1, new TransactionEntryService(target, () -> "TEST").search(null, null, null, 100).size());
        }
    }

    @Test
    void repeatedControlAccountsAndDoubleDigitLineOrdinalsRetainInverseCoverage(@TempDir Path dir) throws Exception
    {
        try (Jpa source = new Jpa(dir.resolve("source")); Jpa target = new Jpa(dir.resolve("target")))
        {
            seed(source);
            empty(target);
            UUID item = UUID.randomUUID();
            var lines = new java.util.ArrayList<TransactionLineCommand>();
            var details = new java.util.ArrayList<TransactionSupplementalLineCommand>();
            for (int i = 0; i < 12; i++)
            {
                lines.add(new TransactionLineCommand(1001L, 1001L, null, null, null,
                        BigDecimal.valueOf(i + 1), BigDecimal.ZERO, false, null));
                details.add(new TransactionSupplementalLineCommand("RECEIVABLE", null, null, "Part " + i,
                        null, BigDecimal.valueOf(i + 1), null, null, null, null, i, item, "INCREASE", i));
            }
            lines.add(new TransactionLineCommand(1003L, 1001L, null, null, null, BigDecimal.ZERO,
                    new BigDecimal("78"), false, null));
            var opening = new TransactionEntryService(source, () -> "TEST").enter(new TransactionCommand(OPEN, null,
                    "Multiple control splits", null, lines, details));
            new TransactionCorrectionService(source, () -> "TEST").reverse(opening.id(), REVERSED, "tester", "cancel", false);
            Path file = export(source, dir.resolve("many-lines.sclx"));
            importFile(target, file, false);
            assertBalance(target, SupplementalOpenItemQueryService.Kind.RECEIVABLE, item, OPEN, "78.0000");
            assertBalance(target, SupplementalOpenItemQueryService.Kind.RECEIVABLE, item, REVERSED, "0.0000");
            var mapper = new ObjectMapper();
            assertEquals(mapper.readTree(file.toFile()).path("transactions"),
                    mapper.readTree(export(target, dir.resolve("many-lines-again.sclx")).toFile()).path("transactions"));
        }
    }

    @Test
    void inverseOfExistingLegacySourceStillRequiresAcknowledgment(@TempDir Path dir)
    {
        try (Jpa jpa = new Jpa(dir.resolve("legacy-inverse")))
        {
            seed(jpa);
            var entry = new TransactionEntryService(jpa, () -> "TEST");
            var opening = enterIncrease(entry, SupplementalOpenItemQueryService.Kind.RECEIVABLE,
                    UUID.randomUUID(), OPEN, new BigDecimal("100"));
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.createQuery("delete from TxnSupplementalLine").executeUpdate();
                em.getTransaction().commit();
            }
            var inverse = new TransactionCommand(REVERSED, null, "Preserved inverse", null, java.util.List.of(
                    new TransactionLineCommand(1001L, 1001L, null, null, null, BigDecimal.ZERO, new BigDecimal("100"), false, null),
                    new TransactionLineCommand(1003L, 1001L, null, null, null, new BigDecimal("100"), BigDecimal.ZERO, false, null)));
            for (boolean acknowledged : new boolean[] {false, true})
            {
                try (var em = jpa.em())
                {
                    em.getTransaction().begin();
                    Company company = em.find(Company.class, 100L);
                    Runnable write = () -> entry.importSclxHistory(em, company, "tester", "a".repeat(64), "legacy.sclx", acknowledged, writer ->
                    {
                        var txn = writer.enter(inverse, UUID.randomUUID());
                        var original = em.find(org.nonprofitbookkeeping.model.Txn.class, opening.id());
                        original.setStatus("REVERSED");
                        new TransactionCorrectionService(jpa, () -> "TEST").restoreRelationshipForImport(em, company, txn, "REVERSAL", original);
                        return txn;
                    });
                    if (acknowledged)
                    {
                        write.run();
                        em.getTransaction().commit();
                    }
                    else
                    {
                        assertTrue(assertThrows(PostingException.class, write::run).getMessage().contains("requires supplemental allocations"));
                        em.getTransaction().rollback();
                    }
                }
            }
            assertEquals(2, entry.search(null, null, null, 100).size());
            assertFalse(new SupplementalOpenItemQueryService(jpa, () -> "TEST")
                    .query(SupplementalOpenItemQueryService.Kind.RECEIVABLE, REVERSED).ready());
        }
    }

    private static void assertBalance(Jpa jpa, SupplementalOpenItemQueryService.Kind kind, UUID item, LocalDate date, String amount)
    {
        var result = new SupplementalOpenItemQueryService(jpa, () -> "TEST").query(kind, date);
        assertEquals(new BigDecimal(amount), result.openAmount());
        assertEquals(item, result.authoritativeRows().get(0).itemId());
        assertTrue(result.ready(), () -> result.toString());
    }

    private static Path export(Jpa jpa, Path file) throws Exception
    {
        var snapshot = new SclxCoreSnapshotQueryService(jpa, () -> "TEST").query(Instant.parse("2026-04-01T00:00:00Z"));
        Files.write(file, new SclxJsonSerializer().serialize(snapshot));
        return file;
    }

    private static void importFile(Jpa jpa, Path file, boolean legacy)
    {
        var preview = new SclxImportPreviewService(jpa, () -> "TEST").preview(file);
        assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
        if (!legacy) assertTrue(preview.operation().messages().stream().noneMatch(m -> m.code().equals("SCLX_UNALLOCATED_CONTROL_LINE")));
        var result = new SclxImportCommitService(jpa, () -> "TEST").commit(file, preview, "tester", true, true, legacy);
        assertTrue(result.committed(), () -> result.messages().toString());
    }

    private static void empty(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode("TEST");
            company.setDisplayName("Test");
            company.setDefaultCurrency("USD");
            em.persist(company);
            em.getTransaction().commit();
        }
    }
}
