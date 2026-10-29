package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class CloseReviewPayloadReaderTest
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String WARNING = "a".repeat(64);
    private static final String BLOCKER = "b".repeat(64);

    static ObjectNode valid()
    {
        var command = new CloseReviewEvidenceService.Command(UUID.randomUUID(), LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30), "c".repeat(64),
                List.of(new CloseReviewEvidenceService.Reference(CloseReviewEvidenceService.ReferenceKind.RECEIPT,
                        "external/recibo-ñ", "Source-local evidence; not fetched")), Map.of(WARNING, "Expected timing"), "Historical note", true);
        var payload = new CloseReviewEvidenceService.Payload(1, CloseReviewEvidenceService.fingerprint(command),
                command.start().toString(), command.end().toString(), command.expectedRevision(), "d".repeat(64), Instant.EPOCH.toString(),
                List.of(new AccountingReadiness.Finding(WARNING, AccountingReadiness.Severity.WARNING,
                                "Bank", "source-bank:123", "Pending receipt", AccountingReadiness.Destination.RECONCILIATION),
                        new AccountingReadiness.Finding(BLOCKER, AccountingReadiness.Severity.BLOCKER,
                                "Ledger", "source-txn:456", "Retained blocker", AccountingReadiness.Destination.JOURNAL)),
                command.references(), command.warningReasons(), command.note(), true);
        return JSON.valueToTree(payload);
    }

    @Test
    void readsSupportedHistoricalFactsWithoutRecomputingSourceLocalFindings()
    {
        ObjectNode value = valid();
        var payload = CloseReviewPayloadReader.read(" \n" + value + "\n ");
        assertEquals("source-txn:456", payload.findings().get(1).reference());
        assertEquals(AccountingReadiness.Severity.BLOCKER, payload.findings().get(1).severity());
        assertEquals(Map.of(WARNING, "Expected timing"), payload.warningReasons());
        assertEquals("external/recibo-ñ", payload.references().get(0).reference());
        assertDoesNotThrow(() -> CloseReviewEvidenceService.validateRetainedAudit("UNRELATED_HISTORY", "Other", "{not JSON"));
        assertDoesNotThrow(() -> CloseReviewEvidenceService.validateRetainedAudit("FUTURE_ACTION", "Other", "{\"version\":999}"));
        assertThrows(IllegalArgumentException.class, () -> CloseReviewEvidenceService.validateRetainedAudit("CLOSE_REVIEW_RECORDED", "Other", value.toString()));
        assertThrows(IllegalArgumentException.class, () -> CloseReviewEvidenceService.validateRetainedAudit("OTHER", "CloseReview", value.toString()));
    }

    @Test
    void requiresEveryTypedVersionOneFieldAndRejectsUnknownMembers()
    {
        List<String> names = new ArrayList<>(); valid().fieldNames().forEachRemaining(names::add);
        for (String field : names)
        {
            ObjectNode missing = valid(); missing.remove(field);
            assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadReader.read(missing.toString()), "missing " + field);
            ObjectNode empty = valid(); empty.putNull(field);
            assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadReader.read(empty.toString()), "null " + field);
        }
        for (String json : List.of("null", "[]", "true", "123", "{}", "", "{not JSON"))
        {
            assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadReader.read(json), json);
        }
        assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadReader.read(null));
        ObjectNode extra = valid(); extra.put("currentApproval", true);
        assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadReader.read(extra.toString()));
    }

    @Test
    void rejectsCoercedTypesUnsupportedVersionsDatesHashesAndAlteredCommandFacts()
    {
        List<Consumer<ObjectNode>> invalid = List.of(
                n -> n.put("version", 2), n -> n.put("version", "1"), n -> n.put("version", 1.0),
                n -> n.put("version", 4294967297L), n -> n.put("attested", "true"), n -> n.put("attested", false),
                n -> n.put("note", 12), n -> n.put("note", "x".repeat(1001)), n -> n.put("note", "Changed after fingerprint"),
                n -> n.put("start", "2026-02-30"), n -> n.put("end", "2026-05-31"), n -> n.put("capturedAt", "yesterday"),
                n -> n.put("commandFingerprint", "x".repeat(64)), n -> n.put("commandFingerprint", "0".repeat(64)),
                n -> n.put("dataRevision", "abc"), n -> n.put("snapshotSha256", "D".repeat(64)),
                n -> n.put("findings", "[]"), n -> n.put("warningReasons", "{}"));
        for (var change : invalid) reject(change);
        String json = valid().toString();
        assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadReader.read(json + " {}"));
        assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadReader.read(json.replace("\"version\":1", "\"version\":1,\"version\":1")));
        assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadReader.read(json.replace("\"kind\":\"RECEIPT\"", "\"kind\":\"RECEIPT\",\"kind\":\"RECEIPT\"")));
    }

    @Test
    void rejectsIncompleteDuplicateReferencesAndInvalidFindingsAndReasons()
    {
        reject(n -> n.putArray("references"));
        reject(n -> ((ArrayNode) n.get("references")).add(n.get("references").get(0).deepCopy()));
        reject(n -> { var refs = n.withArray("references"); while (refs.size() < 101) refs.add(refs.get(0).deepCopy()); });
        for (String field : Set.of("kind", "reference", "description"))
        {
            reject(n -> ((ObjectNode) n.get("references").get(0)).remove(field));
        }
        reject(n -> ((ObjectNode) n.get("references").get(0)).put("reference", "  "));
        reject(n -> ((ObjectNode) n.get("references").get(0)).put("kind", "APPROVAL"));
        reject(n -> ((ObjectNode) n.get("references").get(0)).put("description", false));
        for (String field : Set.of("id", "severity", "area", "reference", "detail", "destination"))
        {
            reject(n -> ((ObjectNode) n.get("findings").get(0)).remove(field));
            reject(n -> ((ObjectNode) n.get("findings").get(0)).put(field, 12));
        }
        reject(n -> ((ObjectNode) n.get("findings").get(0)).put("severity", "APPROVED"));
        reject(n -> ((ObjectNode) n.get("findings").get(0)).put("destination", "UNKNOWN"));
        reject(n -> ((ArrayNode) n.get("findings")).add(n.get("findings").get(0).deepCopy()));
        reject(n -> ((ObjectNode) n.get("warningReasons")).put(BLOCKER, "Cannot excuse blocker"));
        reject(n -> ((ObjectNode) n.get("warningReasons")).put("f".repeat(64), "Unknown warning"));
        reject(n -> ((ObjectNode) n.get("warningReasons")).put(WARNING, "  "));
        reject(n -> ((ObjectNode) n.get("warningReasons")).put(WARNING, "x".repeat(1001)));
    }

    @Test
    void boundsUtf8BytesAndNestedJsonBeforeBinding()
    {
        String valid = valid().toString();
        String boundary = valid + " ".repeat(CloseReviewEvidenceService.MAX_PAYLOAD - valid.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertDoesNotThrow(() -> CloseReviewPayloadReader.read(boundary));
        assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadReader.read(boundary + " "));
        String oversized = " ".repeat(CloseReviewEvidenceService.MAX_PAYLOAD + 1);
        assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadReader.read(oversized));
        // Less than one MiB Java characters, more than one MiB UTF-8 bytes.
        String multibyte = "\"" + "€".repeat(400000) + "\"";
        assertTrue(multibyte.length() < CloseReviewEvidenceService.MAX_PAYLOAD);
        assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadReader.read(multibyte));
        assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadReader.read("[".repeat(30) + "0" + "]".repeat(30)));
        String key = "x".repeat(4000);
        var failure = assertThrows(IllegalArgumentException.class,
                () -> CloseReviewPayloadReader.read("{\"" + key + "\":0,\"" + key + "\":0}"));
        assertTrue(failure.getMessage().length() < 700, "Malformed member names must fit the Preview diagnostic capacity");
    }

    private static void reject(Consumer<ObjectNode> change)
    {
        ObjectNode value = valid(); change.accept(value);
        assertThrows(IllegalArgumentException.class, () -> CloseReviewPayloadReader.read(value.toString()), value::toString);
    }
}
