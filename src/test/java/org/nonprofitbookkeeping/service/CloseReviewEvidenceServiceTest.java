package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.DatabaseTransferService;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.*;

class CloseReviewEvidenceServiceTest
{
    @TempDir Path directory;

    @Test
    void recordsBlockedSnapshotWithReferencesReasonsAndActorAndPreservesRetryRestartPacketAndBackup() throws Exception
    {
        Path db = directory.resolve("source");
        CloseReviewEvidenceService.Command command;
        CloseReviewEvidenceService.Review saved;
        try (Jpa jpa = new Jpa(db))
        {
            var f = seed(jpa);
            f.entry(jpa).enter(f.receipt(UUID.randomUUID(), f.recipient(), f.event()));
            var service = service(jpa, db, manager(jpa));
            var preview = service.preview(DATE, DATE);
            String warning = preview.findings().stream().filter(row -> row.severity() == AccountingReadiness.Severity.WARNING).findFirst().orElseThrow().id();
            command = command(preview, UUID.randomUUID(), Map.of(warning, "Expected timing; source retained externally"));
            long txns = count(jpa, "Txn"), audits = count(jpa, "AuditEvent");
            saved = service.save(command);
            assertEquals("reviewer", saved.actor());
            assertTrue(saved.blockers() > 0, "Recording review must retain blockers rather than waive them");
            assertEquals(command.warningReasons(), saved.payload().warningReasons());
            assertEquals(command.references(), saved.payload().references());
            assertEquals(saved, service.save(command));
            assertEquals(audits + 1, count(jpa, "AuditEvent"));
            assertEquals(txns, count(jpa, "Txn"));
            assertEquals(0L, nativeCount(jpa, "period_close_range"));
            assertEquals(preview.dataRevision(), service.preview(DATE, DATE).dataRevision(), "Saving a factual review must not invalidate its assessed accounting content");
            assertEquals(List.of(saved), service.history(DATE, DATE));
            assertTrue(service.history(DATE.minusDays(1), DATE).isEmpty());
            var other = new CloseReviewEvidenceService(jpa, () -> "DEFAULT", () -> new CoreAccountingPacketService(() -> db, () -> "DEFAULT", null), null);
            assertTrue(other.history(DATE, DATE).isEmpty());
            var packet = new CoreAccountingPacketService(() -> db, () -> COMPANY, null).export(DATE, DATE, directory.resolve("packet.zip"));
            try (var zip = new ZipFile(packet.destination().toFile()))
            {
                String headers = read(zip, "Review Attestations.csv");
                assertTrue(headers.contains(saved.id().toString()));
                assertTrue(headers.contains("reviewer"));
                assertTrue(headers.contains("true"));
                assertTrue(read(zip, "Review References.csv").contains("external/receipt-1"));
                assertTrue(read(zip, "Review Exception Reasons.csv").contains(command.warningReasons().get(warning)));
                assertTrue(read(zip, "Recorded Review Findings.csv").contains("BLOCKER"));
                assertEquals(preview.dataRevision(), new ObjectMapper().readTree(zip.getInputStream(zip.getEntry("manifest.json"))
                        ).get("dataRevision").asText());
            }
            var transfer = new DatabaseTransferService(() -> db, ignored -> { });
            var backup = transfer.backUpDatabase(directory.resolve("backup.zip"));
            transfer.restoreDatabaseCopy(backup.backupFile(), directory.resolve("restored"));
            try (Jpa restored = new Jpa(directory.resolve("restored")))
            {
                assertEquals(saved, service(restored, directory.resolve("restored"), null).history(DATE, DATE).get(0));
            }
            f.entry(jpa).enter(new TransactionCommand(DATE, null, "Changed after recorded review", null,
                    List.of(f.line(f.cash(), "2", "0", f.event()), f.line(f.income(), "0", "2", f.event()))));
            assertNotEquals(saved.payload().dataRevision(), service.preview(DATE, DATE).dataRevision());
            assertEquals(saved, service.save(command), "Exact retry returns original historical facts even after accounting changes");
        }
        try (Jpa jpa = new Jpa(db))
        {
            assertEquals(saved, service(jpa, db, manager(jpa)).save(command));
            assertEquals(List.of(saved), service(jpa, db, null).history(DATE, DATE));
        }
    }

    @Test
    void rejectsStaleUnknownBlockerReasonsAndConflictingOrForeignRequestIdentities() throws Exception
    {
        Path db = directory.resolve("guards");
        try (Jpa jpa = new Jpa(db))
        {
            var f = seed(jpa);
            var service = service(jpa, db, null);
            var preview = service.preview(DATE, DATE);
            String blocker = preview.findings().stream().filter(row -> row.severity() == AccountingReadiness.Severity.BLOCKER).findFirst().orElseThrow().id();
            assertThrows(IllegalArgumentException.class, () -> service.save(command(preview, UUID.randomUUID(), Map.of(blocker, "Cannot waive"))));
            assertThrows(IllegalArgumentException.class, () -> service.save(command(preview, UUID.randomUUID(), Map.of("0".repeat(64), "Unknown"))));
            var cmd = command(preview, UUID.randomUUID(), Map.of());
            service.save(cmd);
            var changed = new CloseReviewEvidenceService.Command(cmd.requestId(), DATE, DATE, preview.dataRevision(), cmd.references(), Map.of(), "Different", true);
            assertThrows(IllegalStateException.class, () -> service.save(changed));
            UUID collision = UUID.randomUUID();
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                var audit = new AuditEvent(); audit.initializeOperationIdentity(collision); audit.setActor("existing");
                audit.setActionType("OTHER"); audit.setEntityType("OtherOperation"); audit.setSummary("Existing operation identity");
                em.persist(audit); em.getTransaction().commit();
            }
            assertThrows(IllegalStateException.class, () -> service.save(command(preview, collision, Map.of())));
            f.entry(jpa).enter(new TransactionCommand(DATE, null, "Stale", null,
                    List.of(f.line(f.cash(), "1", "0", f.event()), f.line(f.income(), "0", "1", f.event()))));
            assertThrows(IllegalStateException.class, () -> service.save(command(preview, UUID.randomUUID(), Map.of())));
            assertEquals(1, service.history(DATE, DATE).size());
        }
    }

    @Test
    void viewerCannotSaveAndInjectedFailureRollsBackWholeAuditFact() throws Exception
    {
        Path db = directory.resolve("rollback");
        try (Jpa jpa = new Jpa(db))
        {
            seed(jpa);
            var base = service(jpa, db, null);
            var cmd = command(base.preview(DATE, DATE), UUID.randomUUID(), Map.of());
            var viewer = new AuthorizationGuard(jpa, () -> Optional.of(session(ReservedSecurityRole.VIEWER)));
            assertThrows(AuthorizationException.class, () -> service(jpa, db, viewer).save(cmd));
            var failing = new CloseReviewEvidenceService(jpa, () -> COMPANY,
                    () -> new CoreAccountingPacketService(() -> db, () -> COMPANY, null), null,
                    event -> { throw new IllegalStateException("Synthetic after-persist failure"); });
            long audits = count(jpa, "AuditEvent");
            assertThrows(IllegalStateException.class, () -> failing.save(cmd));
            assertEquals(audits, count(jpa, "AuditEvent"));
            assertTrue(base.history(DATE, DATE).isEmpty());
            assertEquals(cmd.requestId(), base.save(cmd).id());
        }
    }

    @Test
    void genericAuditInterchangePreservesStructuredReviewAsHistoricalFacts() throws Exception
    {
        Path source = directory.resolve("portable-source"), target = directory.resolve("portable-target");
        AuditHistoryService.AuditEventImport fact;
        CloseReviewEvidenceService.Review saved;
        try (Jpa jpa = new Jpa(source))
        {
            seed(jpa); var service = service(jpa, source, null);
            saved = service.save(command(service.preview(DATE, DATE), UUID.randomUUID(), Map.of()));
            try (var em = jpa.em())
            {
                var event = em.createQuery("from AuditEvent e where e.portableId = :id", AuditEvent.class).setParameter("id", saved.id()).getSingleResult();
                fact = new AuditHistoryService.AuditEventImport("source-review", event.getPortableId(), event.getOccurredAt(), event.getActor(),
                        event.getActionType(), event.getEntityType(), event.getEntityId(), event.getSummary(), event.getBeforeValue(), event.getAfterValue(), event.getReason());
            }
        }
        try (Jpa jpa = new Jpa(target); var em = jpa.em())
        {
            em.getTransaction().begin();
            Company owner = new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT");
            new AuditHistoryService(jpa, () -> "DEFAULT").importForInterchange(em, owner, List.of(fact), Map.of());
            em.getTransaction().commit();
            var recorded = CloseReviewEvidenceService.readHistory(jpa, "DEFAULT", DATE, DATE, 100).get(0);
            assertEquals("DEFAULT", recorded.companyCode());
            assertEquals(saved.payload(), recorded.payload());
            assertEquals(saved.actor(), recorded.actor());
            assertEquals(saved.recordedAt(), recorded.recordedAt());
        }
    }

    @Test
    void validatesExplicitAttestationBoundedReferencesAndWholePayloadCapacity()
    {
        var reference = new CloseReviewEvidenceService.Reference(CloseReviewEvidenceService.ReferenceKind.RECEIPT, " external/ref ", " Description ");
        assertEquals("external/ref", reference.reference());
        assertThrows(IllegalArgumentException.class, () -> new CloseReviewEvidenceService.Reference(reference.kind(), " ", ""));
        assertThrows(IllegalArgumentException.class, () -> new CloseReviewEvidenceService.Reference(reference.kind(), "x".repeat(1001), ""));
        assertThrows(IllegalArgumentException.class, () -> new CloseReviewEvidenceService.Command(UUID.randomUUID(), DATE, DATE, "a".repeat(64), List.of(reference), Map.of(), "", false));
        assertThrows(IllegalArgumentException.class, () -> new CloseReviewEvidenceService.Command(UUID.randomUUID(), DATE, DATE, "a".repeat(64), List.of(reference, reference), Map.of(), "", true));
        var large = java.util.stream.IntStream.range(0, 6000).mapToObj(i -> new AccountingReadiness.Finding("id" + i,
                AccountingReadiness.Severity.WARNING, "Area", "ref", "€".repeat(200), AccountingReadiness.Destination.REPORTS)).toList();
        var payload = new CloseReviewEvidenceService.Payload(1, "fingerprint", DATE.toString(), DATE.toString(), "revision", "snapshot", Instant.EPOCH.toString(),
                large, List.of(reference), Map.of(), "", true);
        assertThrows(IllegalArgumentException.class, () -> CloseReviewEvidenceService.encodePayload(payload));
    }

    private static CloseReviewEvidenceService.Command command(AccountingReadiness preview, UUID id, Map<String, String> reasons)
    {
        return new CloseReviewEvidenceService.Command(id, preview.start(), preview.end(), preview.dataRevision(),
                List.of(new CloseReviewEvidenceService.Reference(CloseReviewEvidenceService.ReferenceKind.RECEIPT, "external/receipt-1", "Retained externally"),
                        new CloseReviewEvidenceService.Reference(CloseReviewEvidenceService.ReferenceKind.EXTERNAL_REVIEW, "external/review-1", "Recorded self-attestation only")), reasons, "Factual review", true);
    }

    private static CloseReviewEvidenceService service(Jpa jpa, Path db, AuthorizationGuard guard)
    {
        return new CloseReviewEvidenceService(jpa, () -> COMPANY, () -> new CoreAccountingPacketService(() -> db, () -> COMPANY, guard), guard);
    }

    private static AuthorizationGuard manager(Jpa jpa)
    {
        return new AuthorizationGuard(jpa, () -> Optional.of(session(ReservedSecurityRole.MANAGER)));
    }

    private static AuthenticatedUserSession session(ReservedSecurityRole role)
    {
        return new AuthenticatedUserSession(9001, "reviewer", "Reviewer", COMPANY, Set.of(role), Instant.EPOCH, Instant.EPOCH);
    }

    private static long count(Jpa jpa, String type)
    {
        try (var em = jpa.em()) { return em.createQuery("select count(e) from " + type + " e", Long.class).getSingleResult(); }
    }

    private static long nativeCount(Jpa jpa, String table)
    {
        try (var em = jpa.em()) { return ((Number) em.createNativeQuery("select count(*) from " + table).getSingleResult()).longValue(); }
    }

    private static String read(ZipFile zip, String name) throws Exception
    {
        return new String(zip.getInputStream(zip.getEntry(name)).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
