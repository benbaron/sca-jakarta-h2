package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SclxCloseReviewPayloadTest
{
    @TempDir Path directory;
    static final ObjectMapper JSON = new ObjectMapper();
    static final LocalDate START = LocalDate.of(2026, 6, 1), END = LocalDate.of(2026, 6, 30);
    static final String OPAQUE = " \nSource plan 42: {legacy: not JSON}; 未検証 \n ";

    Path source() throws Exception
    {
        Path db = directory.resolve("source"), file = directory.resolve("source.sclx");
        try (Jpa jpa = new Jpa(db))
        {
            var review = PeriodCloseTestFixture.readyReview(jpa, db, "DEFAULT", START, END);
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                AuditEvent event = em.createQuery("from AuditEvent e where e.portableId=:id", AuditEvent.class)
                        .setParameter("id", review.id()).getSingleResult();
                event.setAfterValue(" \n" + JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(event.getAfterValue())) + "\n ");
                event.setBeforeValue(OPAQUE); event.setEntityId("source-local-review-42");
                var unknown = new AuditEvent(); unknown.setCompany(event.getCompany()); unknown.setActor("Historical actor");
                unknown.setActionType("LEGACY_REVIEW"); unknown.setEntityType("CloseReview"); unknown.setSummary("Opaque history");
                unknown.setAfterValue(OPAQUE); unknown.setBeforeValue(OPAQUE); unknown.setReason(" source reason ");
                em.persist(unknown); em.getTransaction().commit();
            }
            var document = new SclxCoreSnapshotQueryService(jpa).query(Instant.EPOCH);
            byte[] bytes = new SclxJsonSerializer().serialize(document);
            assertArrayEquals(bytes, new SclxJsonSerializer().serialize(document));
            Files.write(file, bytes);
        }
        return file;
    }

    @Test
    void preservesExactSupportedAndOpaqueSourceTextThroughRemappingConflictRestartAndRetry() throws Exception
    {
        Path file = source(), db = directory.resolve("target");
        ObjectNode root = (ObjectNode)JSON.readTree(file.toFile());
        ObjectNode expected = review(root); String raw = expected.path("afterValue").asText();
        UUID id = UUID.fromString(expected.path("auditEventId").asText().replaceFirst(".*:", ""));
        long count;
        try (Jpa jpa = new Jpa(db))
        {
            try (var em = jpa.em())
            {
                em.getTransaction().begin(); var c = new Company(); c.setCode("COPY"); c.setDisplayName("Copy"); em.persist(c); em.getTransaction().commit();
            }
            commit(jpa, "COPY", file);
            try (var em = jpa.em())
            {
                var event = em.createQuery("from AuditEvent e where e.portableId=:id", AuditEvent.class).setParameter("id", id).getSingleResult();
                assertEquals(raw, event.getAfterValue()); assertEquals(OPAQUE, event.getBeforeValue());
                assertEquals("source-local-review-42", event.getEntityId()); assertEquals("COPY", event.getCompany().getCode());
                assertEquals(expected.path("actor").asText(), event.getActor());
                assertEquals(Instant.parse(expected.path("occurredAt").asText()), event.getOccurredAt());
                var opaque = em.createQuery("from AuditEvent e where e.actionType='LEGACY_REVIEW'", AuditEvent.class).getSingleResult();
                assertEquals(OPAQUE, opaque.getAfterValue()); assertEquals(OPAQUE, opaque.getBeforeValue()); assertEquals(" source reason ", opaque.getReason());
            }
            assertEquals(CloseReviewPayloadCodec.decode(raw), evidence(jpa, db, "COPY").history(START, END).get(0).payload());
            assertTrue(evidence(jpa, db, "DEFAULT").history(START, END).isEmpty());
            var changed = root.deepCopy(); var payload = (ObjectNode)JSON.readTree(raw); payload.put("note", "Different source note");
            review(changed).put("afterValue", payload.toString()); Path conflictFile = directory.resolve("conflict.sclx"); JSON.writeValue(conflictFile.toFile(), changed);
            var previews = new SclxImportPreviewService(jpa, () -> "COPY"); var conflict = previews.preview(conflictFile);
            assertTrue(conflict.hasBlockingErrors());
            var keep = conflict.operation().items().stream().filter(i -> i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                    .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
            assertEquals(1, keep.size());
            var resolved = previews.preview(conflictFile, List.of(), keep); assertFalse(resolved.hasBlockingErrors(), resolved.operation().messages().toString());
            assertTrue(new SclxImportCommitService(jpa, () -> "COPY").commit(conflictFile, resolved, "Importer", true, true).committed());
            assertEquals(CloseReviewPayloadCodec.decode(raw), evidence(jpa, db, "COPY").history(START, END).get(0).payload());
            count = count(jpa, "AuditEvent");
        }
        try (Jpa jpa = new Jpa(db))
        {
            commit(jpa, "COPY", file); assertEquals(count, count(jpa, "AuditEvent"));
            assertEquals(CloseReviewPayloadCodec.decode(raw), evidence(jpa, db, "COPY").history(START, END).get(0).payload());
        }
    }

    @Test
    void malformedAndUnsupportedReviewsBlockPreviewAndCommitBeforeBusinessWrites() throws Exception
    {
        Path file = source(); ObjectNode original = (ObjectNode)JSON.readTree(file.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("invalid")))
        {
            for (String kind : List.of("version", "type", "missing", "null", "extra", "date", "reference", "reason", "duplicate", "trailing", "broken"))
            {
                ObjectNode root = original.deepCopy(), row = review(root); var payload = (ObjectNode)JSON.readTree(row.path("afterValue").asText());
                switch (kind)
                {
                    case "version" -> payload.put("version", 2);
                    case "type" -> payload.put("attested", "true");
                    case "missing" -> payload.remove("dataRevision");
                    case "extra" -> payload.put("approved", true);
                    case "date" -> payload.put("end", "2025-01-01");
                    case "reference" -> payload.withArray("references").removeAll();
                    case "reason" -> ((ObjectNode)payload.path("warningReasons")).put("f".repeat(64), "Unknown");
                }
                String value = payload.toString();
                if (kind.equals("duplicate")) value = value.replace("\"version\":1", "\"version\":1,\"version\":1");
                if (kind.equals("trailing")) value += " {}";
                if (kind.equals("broken")) value = "not JSON";
                if (kind.equals("null")) row.putNull("afterValue"); else row.put("afterValue", value);
                Path input = directory.resolve(kind + ".sclx"); JSON.writeValue(input.toFile(), root);
                var preview = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(input);
                assertTrue(preview.hasBlockingErrors(), kind);
                assertTrue(preview.operation().messages().stream().anyMatch(m -> m.code().equals("SCLX_AUDIT_HISTORY_INVALID")), kind);
                assertThrows(IllegalStateException.class, () -> new SclxImportCommitService(jpa, () -> "DEFAULT").commit(input, preview, "Importer", true, true), kind);
                assertEquals(0, count(jpa, "AuditEvent")); assertEquals(0, count(jpa, "Txn")); assertEquals(0, count(jpa, "InterchangeIdentity"));
            }
        }
    }

    @Test
    void directImportValidatesWholeBatchAndCorruptedStoredReviewCannotExport() throws Exception
    {
        Path file = source(); String valid = review((ObjectNode)JSON.readTree(file.toFile())).path("afterValue").asText();
        try (Jpa jpa = new Jpa(directory.resolve("direct")); var em = jpa.em())
        {
            em.getTransaction().begin(); var owner = new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT");
            var good = fact("good", valid); var bad = fact("bad", "{}");
            assertThrows(IllegalArgumentException.class, () -> new AuditHistoryService(jpa, () -> "DEFAULT").importForInterchange(em, owner, List.of(good, bad), Map.of()));
            assertEquals(0L, em.createQuery("select count(e) from AuditEvent e", Long.class).getSingleResult(), "No valid prefix may persist before validation completes");
            em.getTransaction().commit();
        }
        Path db = directory.resolve("source"), destination = directory.resolve("bad-export.sclx");
        try (Jpa jpa = new Jpa(db); var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createQuery("update AuditEvent e set e.afterValue='{}' where e.actionType='CLOSE_REVIEW_RECORDED'").executeUpdate();
            em.getTransaction().commit();
            assertThrows(IllegalArgumentException.class, () -> new SclxFileExportService(new SclxCoreSnapshotQueryService(jpa), () -> db)
                    .export(new SclxExportRequest(destination, Instant.EPOCH, false)));
            assertFalse(Files.exists(destination));
            assertThrows(IllegalStateException.class, () -> evidence(jpa, db, "DEFAULT").history(START, END));
        }
    }

    @Test
    void viewerDeniedLateFailureRollsBackAndChangedFileCannotUseOldPreview() throws Exception
    {
        Path file = source(); List<Integer> writes = new ArrayList<>();
        try (Jpa probe = new Jpa(directory.resolve("probe")))
        {
            var preview = new SclxImportPreviewService(probe, () -> "DEFAULT").preview(file);
            assertTrue(new SclxImportCommitService(probe, () -> "DEFAULT", writes::add).commit(file, preview, "Importer", true, true).committed());
        }
        int late = writes.get(writes.size() - 2);
        try (Jpa jpa = new Jpa(directory.resolve("rollback")))
        {
            var preview = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file);
            var viewer = new AuthenticatedUserSession(9001, "viewer", "Viewer", "DEFAULT", Set.of(ReservedSecurityRole.VIEWER), Instant.EPOCH, Instant.EPOCH);
            var guard = new AuthorizationGuard(jpa, () -> Optional.of(viewer));
            assertThrows(AuthorizationException.class, () -> new SclxImportCommitService(jpa, () -> "DEFAULT", guard).commit(file, preview, "Importer", true, true));
            var reached = new java.util.concurrent.atomic.AtomicBoolean();
            var failed = new SclxImportCommitService(jpa, () -> "DEFAULT", n ->
            {
                if (n >= late) { reached.set(true); throw new IllegalStateException("Synthetic late failure"); }
            }).commit(file, preview, "Importer", true, true);
            assertFalse(failed.committed()); assertTrue(reached.get());
            assertEquals(0, count(jpa, "AuditEvent")); assertEquals(0, count(jpa, "Txn")); assertEquals(0, count(jpa, "InterchangeIdentity"));
            var root = (ObjectNode)JSON.readTree(file.toFile()); review(root).put("afterValue", "{}"); JSON.writeValue(file.toFile(), root);
            assertThrows(IllegalStateException.class, () -> new SclxImportCommitService(jpa, () -> "DEFAULT").commit(file, preview, "Importer", true, true));
            assertEquals(0, count(jpa, "AuditEvent"));
        }
    }

    static AuditHistoryService.AuditEventImport fact(String key, String value)
    {
        return new AuditHistoryService.AuditEventImport(key, UUID.randomUUID(), Instant.EPOCH, "actor", "CLOSE_REVIEW_RECORDED", "CloseReview", "42", "Historical review", null, value, null);
    }

    static ObjectNode review(ObjectNode root)
    {
        for (var event : root.path("extensions").path("scaJakartaH2").path("auditHistory").path("events"))
            if (event.path("actionType").asText().equals("CLOSE_REVIEW_RECORDED")) return (ObjectNode)event;
        throw new AssertionError("No source review");
    }

    static void commit(Jpa jpa, String company, Path file)
    {
        var preview = new SclxImportPreviewService(jpa, () -> company).preview(file);
        assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
        assertTrue(new SclxImportCommitService(jpa, () -> company).commit(file, preview, "Importer", true, true).committed());
    }

    static CloseReviewEvidenceService evidence(Jpa jpa, Path db, String company)
    {
        return new CloseReviewEvidenceService(jpa, () -> company, () -> new CoreAccountingPacketService(() -> db, () -> company, null), null);
    }

    static long count(Jpa jpa, String entity)
    {
        try (var em = jpa.em()) { return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult(); }
    }
}
