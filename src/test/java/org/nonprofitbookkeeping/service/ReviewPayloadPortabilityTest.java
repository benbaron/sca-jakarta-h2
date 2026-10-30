package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.sclx.*;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ReviewPayloadPortabilityTest
{
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDate START = LocalDate.of(2026, 6, 1), END = LocalDate.of(2026, 6, 30);

    @Test
    void actualFilePreservesRawReviewAndOpaqueHistoricalTextAcrossRetryRestartAndCompanyRemapping() throws Exception
    {
        Path source = directory.resolve("source"), target = directory.resolve("target"), file = directory.resolve("history.sclx");
        CloseReviewEvidenceService.Review saved;
        UUID opaqueId = UUID.randomUUID();
        String raw;
        try (Jpa jpa = new Jpa(source))
        {
            saved = PeriodCloseTestFixture.readyReview(jpa, source, "DEFAULT", START, END);
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                AuditEvent review = em.createQuery("from AuditEvent where portableId=:id", AuditEvent.class).setParameter("id", saved.id()).getSingleResult();
                raw = " \n" + review.getAfterValue() + "\n ";
                review.setAfterValue(raw);
                var opaque = new AuditEvent(); opaque.initializeOperationIdentity(opaqueId); opaque.setCompany(review.getCompany());
                opaque.setActor("historical actor"); opaque.setActionType("FUTURE_HISTORY"); opaque.setEntityType("OpaqueSubject");
                opaque.setEntityId("  source:123  "); opaque.setSummary("Retained external subject text");
                opaque.setBeforeValue(""); opaque.setAfterValue(" \n{\"version\":999,\"raw\":\"ñ€\"}\n "); opaque.setReason("  \n ");
                em.persist(opaque); em.getTransaction().commit();
            }
            export(jpa, source, file);
            String exported = JSON.readTree(Files.readString(file)).path("extensions").path("scaJakartaH2").path("auditHistory").path("events")
                    .findValues("afterValue").stream().map(n -> n.asText()).filter(raw::equals).findFirst().orElseThrow();
            assertEquals(raw, exported);
        }
        try (Jpa jpa = new Jpa(target))
        {
            try (var em = jpa.em())
            {
                em.getTransaction().begin(); var company = new Company(); company.setCode("COPY"); company.setDisplayName("Synthetic copy");
                em.persist(company); em.getTransaction().commit();
            }
            var preview = new SclxImportPreviewService(jpa, () -> "COPY").preview(file);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            assertTrue(new SclxImportCommitService(jpa, () -> "COPY").commit(file, preview, "Importer", true, true).committed());
            var imported = PeriodCloseTestFixture.evidence(jpa, target, "COPY").history(START, END).stream().filter(r -> r.id().equals(saved.id())).findFirst().orElseThrow();
            assertEquals(saved.payload(), imported.payload());
            try (var em = jpa.em())
            {
                assertEquals(raw, em.createQuery("select e.afterValue from AuditEvent e where e.portableId=:id", String.class).setParameter("id", saved.id()).getSingleResult());
                var opaque = em.createQuery("from AuditEvent where portableId=:id", AuditEvent.class).setParameter("id", opaqueId).getSingleResult();
                assertEquals("", opaque.getBeforeValue()); assertEquals("  \n ", opaque.getReason());
                assertEquals("  source:123  ", opaque.getEntityId()); assertEquals(" \n{\"version\":999,\"raw\":\"ñ€\"}\n ", opaque.getAfterValue());
            }
        }
        try (Jpa jpa = new Jpa(target))
        {
            var before = counts(jpa);
            var preview = new SclxImportPreviewService(jpa, () -> "COPY").preview(file);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            assertTrue(new SclxImportCommitService(jpa, () -> "COPY").commit(file, preview, "Importer", true, true).committed());
            assertEquals(before, counts(jpa));
            assertTrue(PeriodCloseTestFixture.evidence(jpa, target, "DEFAULT").history(START, END).isEmpty());
            ObjectNode changed = (ObjectNode) JSON.readTree(Files.readString(file));
            ObjectNode event = reviewEvent(changed);
            ObjectNode payload = (ObjectNode) JSON.readTree(event.path("afterValue").asText());
            var facts = CloseReviewPayloadReader.read(payload.toString());
            var revised = new CloseReviewEvidenceService.Command(saved.id(), START, END, facts.dataRevision(),
                    facts.references(), facts.warningReasons(), "Changed retained note", true);
            payload.put("note", revised.note()); payload.put("commandFingerprint", CloseReviewEvidenceService.fingerprint(revised));
            event.put("afterValue", payload.toString());
            Path conflictFile = directory.resolve("changed.sclx"); JSON.writeValue(conflictFile.toFile(), changed);
            var previews = new SclxImportPreviewService(jpa, () -> "COPY");
            var conflict = previews.preview(conflictFile);
            assertTrue(conflict.hasBlockingErrors());
            assertTrue(conflict.operation().items().stream().anyMatch(i -> i.entityType().equals("AUDIT_EVENT")
                    && i.identityMatch() == InterchangeIdentityMatch.CONFLICT));
            var keep = conflict.operation().items().stream().filter(i -> i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                    .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
            var resolved = previews.preview(conflictFile, List.of(), keep);
            assertFalse(resolved.hasBlockingErrors(), resolved.operation().messages().toString());
            assertTrue(new SclxImportCommitService(jpa, () -> "COPY").commit(conflictFile, resolved, "Importer", true, true).committed());
            var afterKeep = new java.util.ArrayList<>(before);
            afterKeep.set(2, before.get(2) + 1); // Existing import operation receipt; retained review is unchanged.
            assertEquals(afterKeep, counts(jpa));
            try (var em = jpa.em())
            {
                assertEquals(2L, em.createQuery("select count(e) from AuditEvent e where e.actionType='SCLX_IMPORTED'", Long.class).getSingleResult());
            }
            assertEquals(saved.payload(), PeriodCloseTestFixture.evidence(jpa, target, "COPY").history(START, END)
                    .stream().filter(r -> r.id().equals(saved.id())).findFirst().orElseThrow().payload());
            // Keep target cannot make an unsupported recognized version safe, even on an existing identity.
            payload.put("version", 999); event.put("afterValue", payload.toString()); JSON.writeValue(conflictFile.toFile(), changed);
            assertTrue(previews.preview(conflictFile, List.of(), keep).hasBlockingErrors());
            assertEquals(afterKeep, counts(jpa));
        }
    }

    @Test
    void malformedRecognizedPayloadBlocksActualPreviewAndStaleFileCommitWithoutAnyBusinessWrites() throws Exception
    {
        Path source = directory.resolve("valid"), target = directory.resolve("empty"), file = directory.resolve("review.sclx");
        try (Jpa jpa = new Jpa(source))
        {
            PeriodCloseTestFixture.readyReview(jpa, source, "DEFAULT", START, END); export(jpa, source, file);
        }
        ObjectNode original = (ObjectNode) JSON.readTree(Files.readString(file));
        List<Consumer<ObjectNode>> invalid = List.of(n -> n.put("version", 999), n -> n.remove("attested"),
                n -> n.put("version", "1"), n -> n.put("attested", "true"), n -> n.put("end", "1900-01-01"),
                n -> n.putArray("references"), n -> n.put("commandFingerprint", "0".repeat(64)));
        try (Jpa jpa = new Jpa(target))
        {
            var before = counts(jpa);
            var approved = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file);
            assertFalse(approved.hasBlockingErrors(), approved.operation().messages().toString());
            for (var mutation : invalid)
            {
                ObjectNode changed = original.deepCopy(); var event = reviewEvent(changed);
                var payload = (ObjectNode) JSON.readTree(event.path("afterValue").asText()); mutation.accept(payload);
                event.put("afterValue", payload.toString()); JSON.writeValue(file.toFile(), changed);
                var preview = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file);
                assertTrue(preview.hasBlockingErrors());
                assertTrue(preview.operation().messages().stream().anyMatch(m -> m.code().equals("SCLX_AUDIT_HISTORY_INVALID")), preview.operation().messages().toString());
                assertEquals(before, counts(jpa), "Preview must be read-only");
                assertThrows(IllegalStateException.class, () -> new SclxImportCommitService(jpa, () -> "DEFAULT").commit(file, approved, "Importer", true, true));
                assertEquals(before, counts(jpa), "Rejected changed file must not partially import any section");
            }
            // Nested JSON is a string: outer strict duplicate detection cannot inspect it.
            ObjectNode duplicate = original.deepCopy(); var event = reviewEvent(duplicate);
            event.put("afterValue", event.path("afterValue").asText().replace("\"version\":1", "\"version\":1,\"version\":1"));
            JSON.writeValue(file.toFile(), duplicate);
            assertTrue(new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file).hasBlockingErrors());
            assertEquals(before, counts(jpa));
            ObjectNode unknown = original.deepCopy();
            reviewEvent(unknown).put("x".repeat(4000), "unsupported audit field");
            JSON.writeValue(file.toFile(), unknown);
            var bounded = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file);
            assertTrue(bounded.hasBlockingErrors());
            assertTrue(bounded.operation().messages().stream().anyMatch(message ->
                    message.code().equals("SCLX_AUDIT_HISTORY_INVALID") && message.message().length() <= 801));
            assertEquals(before, counts(jpa));
        }
    }

    @Test
    void directRestorationRejectsWholeBatchBeforePersistAndHistoryAndExportShareValidation() throws Exception
    {
        Path database = directory.resolve("direct");
        try (Jpa jpa = new Jpa(database); var em = jpa.em())
        {
            PeriodCloseTestFixture.readyReview(jpa, database, "DEFAULT", START, END);
            em.getTransaction().begin();
            var company = new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT");
            var valid = fact("generic", "OTHER", "Other", "not JSON");
            var invalid = fact("bad", "CLOSE_REVIEW_RECORDED", "CloseReview", "{\"version\":999}");
            long before = count(jpa, "AuditEvent");
            assertThrows(IllegalArgumentException.class, () -> new AuditHistoryService(jpa, () -> "DEFAULT")
                    .importForInterchange(em, company, List.of(valid, invalid), Map.of()));
            assertTrue(em.getTransaction().isActive(), "Restoration must not take over the caller transaction");
            assertEquals(before, em.createQuery("select count(e) from AuditEvent e", Long.class).getSingleResult());
            em.getTransaction().commit(); assertEquals(before, count(jpa, "AuditEvent"));
            em.getTransaction().begin();
            var malformed = new AuditEvent(); malformed.setCompany(company); malformed.setActor("source");
            malformed.setActionType("CLOSE_REVIEW_RECORDED"); malformed.setEntityType("CloseReview"); malformed.setSummary("Malformed legacy review");
            malformed.setAfterValue("{\"version\":999}"); em.persist(malformed); em.getTransaction().commit();
            assertThrows(IllegalStateException.class, () -> PeriodCloseTestFixture.evidence(jpa, database, "DEFAULT").history(START, END));
            Path rejected = directory.resolve("rejected.sclx");
            assertThrows(IllegalArgumentException.class, () -> export(jpa, database, rejected));
            assertFalse(Files.exists(rejected), "Rejected export leaves no misleading completed file");
        }
    }

    private static ObjectNode reviewEvent(ObjectNode root)
    {
        var events = (ArrayNode) root.path("extensions").path("scaJakartaH2").path("auditHistory").path("events");
        for (var event : events) if (event.path("actionType").asText().equals("CLOSE_REVIEW_RECORDED")) return (ObjectNode) event;
        throw new AssertionError("Missing real review");
    }

    private static AuditHistoryService.AuditEventImport fact(String id, String action, String entity, String after)
    {
        return new AuditHistoryService.AuditEventImport(id, UUID.randomUUID(), Instant.EPOCH, "source", action, entity, null, "Historical fact", null, after, null);
    }

    private static void export(Jpa jpa, Path source, Path file) throws Exception
    {
        new SclxFileExportService(new SclxCoreSnapshotQueryService(jpa), () -> source).export(new SclxExportRequest(file, Instant.EPOCH, false));
    }

    private static List<Long> counts(Jpa jpa)
    {
        return List.of(count(jpa, "Company"), count(jpa, "Txn"), count(jpa, "AuditEvent"), count(jpa, "InterchangeIdentity"), count(jpa, "BudgetPlan"));
    }

    private static long count(Jpa jpa, String type)
    {
        try (var em = jpa.em()) { return em.createQuery("select count(e) from " + type + " e", Long.class).getSingleResult(); }
    }
}
