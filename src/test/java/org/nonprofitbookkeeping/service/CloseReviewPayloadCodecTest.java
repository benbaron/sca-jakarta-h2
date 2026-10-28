package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class CloseReviewPayloadCodecTest
{
    static final ObjectMapper JSON = new ObjectMapper();
    static final String WARNING = "a".repeat(64), BLOCKER = "b".repeat(64);

    static ObjectNode valid()
    {
        return JSON.valueToTree(new CloseReviewEvidenceService.Payload(1, "c".repeat(64), "2026-06-01", "2026-06-30",
                "d".repeat(64), "e".repeat(64), "2026-07-01T12:00:00.123456789Z",
                List.of(new AccountingReadiness.Finding(WARNING, AccountingReadiness.Severity.WARNING, "Bank", "Source bank 42",
                                "Earlier outstanding item", AccountingReadiness.Destination.RECONCILIATION),
                        new AccountingReadiness.Finding(BLOCKER, AccountingReadiness.Severity.BLOCKER, "Budget", "Source plan 17",
                                "Missing source facts", AccountingReadiness.Destination.BUDGET)),
                List.of(new CloseReviewEvidenceService.Reference(CloseReviewEvidenceService.ReferenceKind.OTHER,
                        "external/源-42", "Operator supplied")), Map.of(WARNING, "Timing; keep source pointer 42"), "Historical only", true));
    }

    @Test
    void supportedHistoricalFactsRemainTypedWithoutTargetReinterpretation() throws Exception
    {
        String text = " \n" + JSON.writerWithDefaultPrettyPrinter().writeValueAsString(valid()) + "\n ";
        var payload = CloseReviewPayloadCodec.decode(text);
        assertEquals("Source bank 42", payload.findings().get(0).reference());
        assertEquals(2, payload.findings().size());
        assertEquals(Map.of(WARNING, "Timing; keep source pointer 42"), payload.warningReasons());
        assertEquals("2026-07-01T12:00:00.123456789Z", payload.capturedAt());
        var noReasons = valid(); noReasons.set("warningReasons", JSON.createObjectNode());
        assertTrue(CloseReviewPayloadCodec.decode(noReasons.toString()).warningReasons().isEmpty(), "Factual review need not satisfy close policy");
    }

    @Test
    void rejectsMalformedVersionsShapesTypesAndHistoricalRelationships()
    {
        Map<String, Consumer<ObjectNode>> changes = new java.util.LinkedHashMap<>();
        changes.put("unsupported", p -> p.put("version", 2));
        changes.put("overflow", p -> p.put("version", 4294967297L));
        changes.put("string version", p -> p.put("version", "1"));
        changes.put("float version", p -> p.put("version", 1.0));
        changes.put("missing", p -> p.remove("findings"));
        changes.put("extra", p -> p.put("approved", true));
        changes.put("null list", p -> p.putNull("references"));
        changes.put("null element", p -> p.withArray("findings").addNull());
        changes.put("numeric hash", p -> p.put("dataRevision", 12));
        changes.put("bad hash", p -> p.put("snapshotSha256", "not-a-hash"));
        changes.put("fingerprint", p -> p.put("commandFingerprint", "f".repeat(63)));
        changes.put("reversed dates", p -> p.put("end", "2026-05-31"));
        changes.put("bad date", p -> p.put("start", "June"));
        changes.put("bad instant", p -> p.put("capturedAt", "tomorrow"));
        changes.put("string attestation", p -> p.put("attested", "true"));
        changes.put("false attestation", p -> p.put("attested", false));
        changes.put("note capacity", p -> p.put("note", "x".repeat(1001)));
        changes.put("null note", p -> p.putNull("note"));
        changes.put("no refs", p -> p.withArray("references").removeAll());
        changes.put("too many refs", p -> { var ref = p.withArray("references").get(0); for (int i=0;i<100;i++) p.withArray("references").add(ref); });
        changes.put("duplicate refs", p -> p.withArray("references").add(p.withArray("references").get(0)));
        changes.put("blank ref", p -> ((ObjectNode)p.path("references").get(0)).put("reference", "  "));
        changes.put("ref kind", p -> ((ObjectNode)p.path("references").get(0)).put("kind", "APPROVAL"));
        changes.put("ref field", p -> ((ObjectNode)p.path("references").get(0)).put("extra", "x"));
        changes.put("ref capacity", p -> ((ObjectNode)p.path("references").get(0)).put("description", "x".repeat(1001)));
        changes.put("severity", p -> ((ObjectNode)p.path("findings").get(0)).put("severity", "APPROVED"));
        changes.put("destination", p -> ((ObjectNode)p.path("findings").get(0)).put("destination", "APPROVALS"));
        changes.put("duplicate findings", p -> p.withArray("findings").add(p.withArray("findings").get(0)));
        changes.put("finding identity", p -> ((ObjectNode)p.path("findings").get(0)).put("id", "42"));
        changes.put("finding type", p -> ((ObjectNode)p.path("findings").get(0)).put("detail", 42));
        changes.put("unknown reason", p -> ((ObjectNode)p.path("warningReasons")).put("f".repeat(64), "Unknown"));
        changes.put("blocker reason", p -> ((ObjectNode)p.path("warningReasons")).put(BLOCKER, "Waiver"));
        changes.put("blank reason", p -> ((ObjectNode)p.path("warningReasons")).put(WARNING, " "));
        changes.put("reason capacity", p -> ((ObjectNode)p.path("warningReasons")).put(WARNING, "x".repeat(1001)));
        changes.put("reason type", p -> p.put("warningReasons", "x"));
        changes.forEach((name, change) ->
        {
            ObjectNode payload = valid(); change.accept(payload);
            assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadCodec.decode(payload.toString()), name);
        });
        for (String invalid : List.of("null", "[]", "{}", "broken", valid() + " {}", valid().toString().replace("\"version\":1", "\"version\":1,\"version\":1")))
            assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadCodec.decode(invalid));
        assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadCodec.decode(null));
    }

    @Test
    void enforcesUtf8CapacityAndLeavesUnrecognizedAuditTextOpaque()
    {
        // Under the UTF-16 limit but over one MiB in UTF-8, without imposing per-finding policy limits.
        var payload = valid(); ((ObjectNode)payload.path("findings").get(0)).put("detail", "€".repeat(350000));
        String encoded = payload.toString(); assertTrue(encoded.length() < CloseReviewEvidenceService.MAX_PAYLOAD);
        assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadCodec.decode(encoded));
        assertDoesNotThrow(() -> CloseReviewPayloadCodec.validateAudit("LEGACY", "CloseReview", "not JSON"));
        assertDoesNotThrow(() -> CloseReviewPayloadCodec.validateAudit("CLOSE_REVIEW_RECORDED", "Other", null));
        assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadCodec.validateAudit("CLOSE_REVIEW_RECORDED", "CloseReview", "not JSON"));
    }
}
