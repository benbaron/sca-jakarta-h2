package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Reads historical review facts without rewriting source text or granting close authority. */
public final class CloseReviewPayloadCodec
{
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8)
                    .maxStringLength(CloseReviewEvidenceService.MAX_PAYLOAD).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private CloseReviewPayloadCodec() { }

    /** Unknown audit operations remain opaque; only the established pair has this contract. */
    public static void validateAudit(String action, String entity, String afterValue)
    {
        if (CloseReviewEvidenceService.ACTION.equals(action) && CloseReviewEvidenceService.ENTITY.equals(entity))
        {
            decode(afterValue);
        }
    }

    public static CloseReviewEvidenceService.Payload decode(String encoded)
    {
        try
        {
            require(encoded != null && encoded.length() <= CloseReviewEvidenceService.MAX_PAYLOAD
                    && encoded.getBytes(StandardCharsets.UTF_8).length <= CloseReviewEvidenceService.MAX_PAYLOAD,
                    "afterValue must be present and fit the one-MiB audit capacity");
            JsonNode root = JSON.readTree(encoded);
            fields(root, Set.of("version", "commandFingerprint", "start", "end", "dataRevision", "snapshotSha256",
                    "capturedAt", "findings", "references", "warningReasons", "note", "attested"), "payload");
            require(root.path("version").isIntegralNumber() && root.path("version").canConvertToInt()
                    && root.path("version").intValue() == 1, "version must be integer 1");
            hash(root, "commandFingerprint"); hash(root, "dataRevision"); hash(root, "snapshotSha256");
            LocalDate start = LocalDate.parse(text(root, "start"));
            LocalDate end = LocalDate.parse(text(root, "end"));
            require(!end.isBefore(start), "end must not precede start");
            Instant.parse(text(root, "capturedAt"));
            require(root.path("attested").isBoolean() && root.path("attested").booleanValue(), "attested must be boolean true");
            bounded(root, "note", 1000, false);

            Set<String> findingIds = new HashSet<>(), warnings = new HashSet<>();
            JsonNode findings = array(root, "findings");
            for (JsonNode finding : findings)
            {
                fields(finding, Set.of("id", "severity", "area", "reference", "detail", "destination"), "finding");
                String id = hash(finding, "id");
                require(findingIds.add(id), "duplicate finding identity");
                var severity = AccountingReadiness.Severity.valueOf(text(finding, "severity"));
                AccountingReadiness.Destination.valueOf(text(finding, "destination"));
                text(finding, "area"); text(finding, "reference"); text(finding, "detail");
                if (severity == AccountingReadiness.Severity.WARNING) warnings.add(id);
            }
            JsonNode references = array(root, "references");
            require(!references.isEmpty() && references.size() <= 100, "record between one and 100 references");
            Set<String> identities = new HashSet<>();
            for (JsonNode reference : references)
            {
                fields(reference, Set.of("kind", "reference", "description"), "reference");
                var kind = CloseReviewEvidenceService.ReferenceKind.valueOf(text(reference, "kind"));
                String value = bounded(reference, "reference", 1000, true);
                String description = bounded(reference, "description", 1000, false);
                var normalized = new CloseReviewEvidenceService.Reference(kind, value, description);
                require(identities.add(kind + "\n" + normalized.reference()), "duplicate reference kind/text");
            }
            JsonNode reasons = root.path("warningReasons");
            require(reasons.isObject() && reasons.size() <= 10000, "warningReasons must be an object with at most 10000 entries");
            var names = reasons.fieldNames();
            while (names.hasNext())
            {
                String id = names.next();
                require(warnings.contains(id), "exception reasons may name only retained WARNING findings");
                bounded(reasons, id, 1000, true);
            }
            return JSON.treeToValue(root, CloseReviewEvidenceService.Payload.class);
        }
        catch (IOException | RuntimeException ex)
        {
            throw new IllegalArgumentException("Invalid close-review evidence: " + ex.getMessage(), ex);
        }
    }

    private static void fields(JsonNode node, Set<String> expected, String label)
    {
        require(node != null && node.isObject(), label + " must be an object");
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(expected), label + " must contain exactly " + expected);
    }

    private static JsonNode array(JsonNode node, String name)
    {
        JsonNode value = node.path(name);
        require(value.isArray(), name + " must be an array");
        return value;
    }

    private static String hash(JsonNode node, String name)
    {
        String value = text(node, name);
        require(value.matches("[0-9a-f]{64}"), name + " must be a lowercase SHA-256 value");
        return value;
    }

    private static String bounded(JsonNode node, String name, int maximum, boolean required)
    {
        String value = text(node, name);
        require(value.length() <= maximum && (!required || !value.isBlank()), name + " exceeds its text bounds");
        return value;
    }

    private static String text(JsonNode node, String name)
    {
        require(node.path(name).isTextual(), name + " must be text");
        return node.path(name).textValue();
    }

    private static void require(boolean valid, String message)
    {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
