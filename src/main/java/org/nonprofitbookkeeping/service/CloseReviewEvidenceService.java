package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Immutable factual self-attestations about captured content, never a period-close authorization. */
public final class CloseReviewEvidenceService
{
    static final String ACTION = "CLOSE_REVIEW_RECORDED";
    static final String ENTITY = "CloseReview";
    static final int MAX_PAYLOAD = 1_048_576;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Jpa jpa;
    private final Supplier<String> company;
    private final Supplier<CoreAccountingPacketService> captures;
    private final AuthorizationGuard guard;
    private final Consumer<AuditEvent> afterPersist;

    public CloseReviewEvidenceService(Jpa jpa, Supplier<String> company,
            Supplier<CoreAccountingPacketService> captures, AuthorizationGuard guard)
    {
        this(jpa, company, captures, guard, event -> { });
    }

    CloseReviewEvidenceService(Jpa jpa, Supplier<String> company,
            Supplier<CoreAccountingPacketService> captures, AuthorizationGuard guard, Consumer<AuditEvent> afterPersist)
    {
        this.jpa = Objects.requireNonNull(jpa);
        this.company = Objects.requireNonNull(company);
        this.captures = Objects.requireNonNull(captures);
        this.guard = guard;
        this.afterPersist = Objects.requireNonNull(afterPersist);
    }

    public enum ReferenceKind { RECEIPT, EXTERNAL_REVIEW, COUNT_PACKET, OTHER }

    public record Reference(ReferenceKind kind, String reference, String description)
    {
        public Reference
        {
            Objects.requireNonNull(kind, "Reference kind is required");
            reference = text(reference, "Reference", 1000, true);
            description = text(description, "Reference description", 1000, false);
        }
    }

    public record Command(UUID requestId, LocalDate start, LocalDate end, String expectedRevision,
            List<Reference> references, Map<String, String> warningReasons, String note, boolean attested)
    {
        public Command
        {
            Objects.requireNonNull(requestId, "Request identity is required");
            Objects.requireNonNull(start, "Start is required");
            Objects.requireNonNull(end, "End is required");
            if (end.isBefore(start))
            {
                throw new IllegalArgumentException("End must not precede start");
            }
            if (expectedRevision == null || !expectedRevision.matches("[0-9a-f]{64}"))
            {
                throw new IllegalArgumentException("A valid preview content revision is required");
            }
            references = List.copyOf(Objects.requireNonNull(references));
            if (references.isEmpty() || references.size() > 100)
            {
                throw new IllegalArgumentException("Record between one and 100 external references");
            }
            long identities = references.stream().map(row -> row.kind() + "\n" + row.reference()).distinct().count();
            if (identities != references.size())
            {
                throw new IllegalArgumentException("Duplicate reference kind/text");
            }
            if (warningReasons.size() > 10000)
            {
                throw new IllegalArgumentException("Too many exception reasons");
            }
            Map<String, String> normalized = new java.util.TreeMap<>();
            warningReasons.forEach((id, reason) ->
            {
                if (id == null || !id.matches("[0-9a-f]{64}"))
                {
                    throw new IllegalArgumentException("Invalid warning identity");
                }
                normalized.put(id, text(reason, "Exception reason", 1000, true));
            });
            warningReasons = java.util.Collections.unmodifiableMap(normalized);
            note = text(note, "Review note", 1000, false);
            if (!attested)
            {
                throw new IllegalArgumentException("Explicit factual self-attestation is required");
            }
        }
    }

    /** Versioned audit payload; dates/instants remain ISO strings for generic audit/SCLX portability. */
    public record Payload(int version, String commandFingerprint, String start, String end, String dataRevision,
            String snapshotSha256, String capturedAt, List<AccountingReadiness.Finding> findings,
            List<Reference> references, Map<String, String> warningReasons, String note, boolean attested)
    {
        public Payload
        {
            findings = List.copyOf(findings);
            references = List.copyOf(references);
            warningReasons = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(warningReasons));
        }
    }

    public record Review(UUID id, String companyCode, Instant recordedAt, String actor, Payload payload)
    {
        public long blockers()
        {
            return payload.findings().stream().filter(row -> row.severity() == AccountingReadiness.Severity.BLOCKER).count();
        }
    }

    public AccountingReadiness preview(LocalDate start, LocalDate end) throws IOException
    {
        String code = code();
        ServiceAuthorization.require(guard, ApplicationPermission.EXPORT, code, "preview review evidence snapshot");
        var result = captures.get().previewReadiness(start, end);
        if (!code.equals(result.companyCode()))
        {
            throw new IllegalStateException("Captured review belongs to another company");
        }
        ServiceAuthorization.require(guard, ApplicationPermission.EXPORT, code, "return review evidence snapshot");
        return result;
    }

    public List<Review> history(LocalDate start, LocalDate end)
    {
        String code = code();
        ServiceAuthorization.require(guard, ApplicationPermission.EXPORT, code, "read review evidence history");
        var result = readHistory(jpa, code, start, end, CoreAccountingPacketService.MAX_LEDGER_ROWS).stream().limit(100).toList();
        ServiceAuthorization.require(guard, ApplicationPermission.EXPORT, code, "return review evidence history");
        return result;
    }

    public Review save(Command command) throws IOException
    {
        Objects.requireNonNull(command);
        String code = code();
        String actor = ServiceAuthorization.actor(guard, ApplicationPermission.BOOKKEEPING_WRITE, code,
                "save factual review evidence", "system");
        String fingerprint = fingerprint(command);
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company owner = new CompanyOwnershipService(jpa).requireCompany(em, code);
                em.lock(owner, LockModeType.PESSIMISTIC_WRITE);
                var existing = em.createQuery("from AuditEvent e left join fetch e.company where e.portableId = :id", AuditEvent.class)
                        .setParameter("id", command.requestId()).getResultStream().findFirst().orElse(null);
                if (existing != null)
                {
                    if (existing.getCompany() == null || !owner.getId().equals(existing.getCompany().getId()) || !ACTION.equals(existing.getActionType())
                            || !ENTITY.equals(existing.getEntityType()) || !fingerprint.equals(review(existing).payload().commandFingerprint()))
                    {
                        throw new IllegalStateException("Review request identity was already used with different facts or operation");
                    }
                    ServiceAuthorization.require(guard, ApplicationPermission.BOOKKEEPING_WRITE, code, "return recorded review evidence");
                    Review result = review(existing);
                    em.getTransaction().commit();
                    return result;
                }
                AccountingReadiness captured = preview(command.start(), command.end());
                if (!command.expectedRevision().equals(captured.dataRevision()))
                {
                    throw new IllegalStateException("Review preview is stale; preview again before saving");
                }
                for (String id : command.warningReasons().keySet())
                {
                    if (captured.findings().stream().noneMatch(row -> row.id().equals(id)
                            && row.severity() == AccountingReadiness.Severity.WARNING))
                    {
                        throw new IllegalArgumentException("Exception reasons may reference only warnings in this captured snapshot");
                    }
                }
                Payload payload = new Payload(1, fingerprint, command.start().toString(), command.end().toString(), captured.dataRevision(),
                        captured.snapshotSha256(), captured.capturedAt().toString(), captured.findings(), command.references(),
                        command.warningReasons(), command.note(), true);
                String encoded = encodePayload(payload);
                if (!actor.equals(ServiceAuthorization.actor(guard, ApplicationPermission.BOOKKEEPING_WRITE, code,
                        "record factual review evidence", "system")))
                {
                    throw new AuthorizationException(ApplicationPermission.BOOKKEEPING_WRITE, "Authenticated actor changed during review capture");
                }
                AuditEvent event = new AuditEvent();
                event.initializeOperationIdentity(command.requestId());
                event.setCompany(owner);
                event.setActor(actor);
                event.setActionType(ACTION);
                event.setEntityType(ENTITY);
                event.setEntityId(command.requestId().toString());
                event.setSummary("Recorded factual review for " + command.start() + " through " + command.end()
                        + "; " + captured.blockers() + " blocker(s); no close authorization");
                event.setAfterValue(encoded);
                event.setReason(command.note().isEmpty() ? "External-reference self-attestation; documents not independently verified" : command.note());
                em.persist(event);
                em.flush();
                em.refresh(event);
                afterPersist.accept(event);
                Review saved = review(event);
                em.getTransaction().commit();
                return saved;
            }
            catch (IOException | RuntimeException ex)
            {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw ex;
            }
        }
    }

    static List<Review> readHistory(Jpa jpa, String company, LocalDate start, LocalDate end, int capacity)
    {
        List<Review> result = new ArrayList<>();
        try (var em = jpa.em())
        {
            var events = em.createQuery("""
                    from AuditEvent e join fetch e.company c where c.code = :company
                    and e.actionType = :action and e.entityType = :entity order by e.occurredAt desc, e.id desc
                    """, AuditEvent.class).setParameter("company", company).setParameter("action", ACTION)
                    .setParameter("entity", ENTITY).setMaxResults(capacity + 1).getResultList();
            if (events.size() > capacity)
            {
                throw new IllegalArgumentException("Review history capacity exceeded");
            }
            for (var event : events)
            {
                Review review = review(event);
                if (review.payload().start().equals(start.toString()) && review.payload().end().equals(end.toString())) result.add(review);
            }
        }
        return List.copyOf(result);
    }

    static void appendPacket(Jpa jpa, AccountingReadiness assessed, int capacity, Map<String, String> reports)
    {
        List<Map<String, Object>> headers = new ArrayList<>(), references = new ArrayList<>(), findings = new ArrayList<>(), reasons = new ArrayList<>();
        for (Review review : readHistory(jpa, assessed.companyCode(), assessed.start(), assessed.end(), capacity))
        {
            Payload payload = review.payload();
            headers.add(PacketCsv.row("reviewId", review.id(), "recordedAt", review.recordedAt(), "recordedBy", review.actor(),
                    "start", payload.start(), "end", payload.end(), "dataRevision", payload.dataRevision(),
                    "snapshotSha256", payload.snapshotSha256(), "capturedAt", payload.capturedAt(),
                    "matchesAssessedContent", payload.dataRevision().equals(assessed.dataRevision()), "blockers", review.blockers(),
                    "referenceCount", payload.references().size(), "reasonCount", payload.warningReasons().size(), "note", payload.note(),
                    "basis", "Factual self-attestation; external documents not independently verified; no close authorization"));
            for (Reference reference : payload.references())
            {
                references.add(PacketCsv.row("reviewId", review.id(), "kind", reference.kind(), "reference", reference.reference(), "description", reference.description()));
            }
            for (var finding : payload.findings())
            {
                findings.add(PacketCsv.row("reviewId", review.id(), "findingId", finding.id(), "severity", finding.severity(),
                        "area", finding.area(), "reference", finding.reference(), "detail", finding.detail(), "destination", finding.destination()));
            }
            payload.warningReasons().forEach((id, reason) -> reasons.add(PacketCsv.row("reviewId", review.id(), "findingId", id, "reason", reason)));
        }
        reports.put("Review Attestations", PacketCsv.rows(List.of("reviewId", "recordedAt", "recordedBy", "start", "end", "dataRevision", "snapshotSha256",
                "capturedAt", "matchesAssessedContent", "blockers", "referenceCount", "reasonCount", "note", "basis"), headers));
        reports.put("Review References", PacketCsv.rows(List.of("reviewId", "kind", "reference", "description"), references));
        reports.put("Recorded Review Findings", PacketCsv.rows(List.of("reviewId", "findingId", "severity", "area", "reference", "detail", "destination"), findings));
        reports.put("Review Exception Reasons", PacketCsv.rows(List.of("reviewId", "findingId", "reason"), reasons));
    }

    private static Review review(AuditEvent event)
    {
        try
        {
            Payload payload = CloseReviewPayloadCodec.decode(event.getAfterValue());
            return new Review(event.getPortableId(), event.getCompany().getCode(), event.getOccurredAt(), event.getActor(), payload);
        }
        catch (RuntimeException ex)
        {
            throw new IllegalStateException("Cannot read retained review evidence " + event.getPortableId(), ex);
        }
    }

    private String code()
    {
        return text(company.get(), "Company", 80, true).toUpperCase(Locale.ROOT);
    }

    private static String fingerprint(Command command)
    {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("start", command.start().toString()); facts.put("end", command.end().toString());
        facts.put("revision", command.expectedRevision()); facts.put("references", command.references());
        facts.put("reasons", command.warningReasons()); facts.put("note", command.note()); facts.put("attested", command.attested());
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(facts).getBytes(StandardCharsets.UTF_8)));
        }
        catch (java.security.NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    private static String encode(Object value)
    {
        try
        {
            return JSON.writeValueAsString(value);
        }
        catch (JsonProcessingException ex)
        {
            throw new IllegalStateException("Cannot encode factual review evidence", ex);
        }
    }

    static String encodePayload(Payload payload)
    {
        String encoded = encode(payload);
        if (encoded.length() > MAX_PAYLOAD || encoded.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD)
        {
            throw new IllegalArgumentException("Review evidence exceeds the one-MiB audit capacity; no partial record saved");
        }
        return encoded;
    }

    private static String text(String value, String label, int maximum, boolean required)
    {
        String clean = value == null ? "" : value.strip();
        if ((required && clean.isEmpty()) || clean.length() > maximum)
        {
            throw new IllegalArgumentException(label + " must " + (required ? "be present and " : "") + "not exceed " + maximum + " characters");
        }
        return clean;
    }
}
