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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Reads historical review facts without treating their findings or hashes as current authority. */
final class CloseReviewPayloadReader
{
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16)
                    .maxStringLength(CloseReviewEvidenceService.MAX_PAYLOAD).maxNumberLength(32).build())
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> FIELDS = Set.of("version", "commandFingerprint", "start", "end",
            "dataRevision", "snapshotSha256", "capturedAt", "findings", "references", "warningReasons", "note", "attested");

    private CloseReviewPayloadReader()
    {
    }

    static CloseReviewEvidenceService.Payload read(String encoded)
    {
        if (encoded == null || encoded.length() > CloseReviewEvidenceService.MAX_PAYLOAD
                || encoded.getBytes(StandardCharsets.UTF_8).length > CloseReviewEvidenceService.MAX_PAYLOAD)
        {
            throw invalid("payload is missing or exceeds one MiB");
        }
        try
        {
            JsonNode root = JSON.readTree(encoded);
            fields(root, FIELDS);
            JsonNode version = root.get("version");
            if (!version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() != 1)
            {
                throw invalid("version must be integer 1");
            }
            hash(root, "commandFingerprint"); hash(root, "dataRevision"); hash(root, "snapshotSha256");
            LocalDate start = LocalDate.parse(text(root, "start", 10, true));
            LocalDate end = LocalDate.parse(text(root, "end", 10, true));
            if (end.isBefore(start))
            {
                throw invalid("end must not precede start");
            }
            Instant.parse(text(root, "capturedAt", 40, true));
            if (!root.get("attested").isBoolean() || !root.get("attested").booleanValue())
            {
                throw invalid("attested must be true");
            }
            text(root, "note", 1000, false);
            JsonNode references = array(root, "references");
            if (references.isEmpty() || references.size() > 100)
            {
                throw invalid("one to 100 references are required");
            }
            Set<String> referenceIds = new HashSet<>();
            for (JsonNode reference : references)
            {
                fields(reference, Set.of("kind", "reference", "description"));
                var kind = CloseReviewEvidenceService.ReferenceKind.valueOf(text(reference, "kind", 40, true));
                String ref = text(reference, "reference", 1000, true);
                text(reference, "description", 1000, false);
                if (!referenceIds.add(kind + "\n" + ref.strip()))
                {
                    throw invalid("duplicate reference kind/text");
                }
            }
            Set<String> findingIds = new HashSet<>(), warnings = new HashSet<>();
            for (JsonNode finding : array(root, "findings"))
            {
                fields(finding, Set.of("id", "severity", "area", "reference", "detail", "destination"));
                String id = hash(finding, "id");
                if (!findingIds.add(id))
                {
                    throw invalid("duplicate finding identity");
                }
                var severity = AccountingReadiness.Severity.valueOf(text(finding, "severity", 40, true));
                AccountingReadiness.Destination.valueOf(text(finding, "destination", 40, true));
                text(finding, "area", CloseReviewEvidenceService.MAX_PAYLOAD, true);
                text(finding, "reference", CloseReviewEvidenceService.MAX_PAYLOAD, false);
                text(finding, "detail", CloseReviewEvidenceService.MAX_PAYLOAD, false);
                if (severity == AccountingReadiness.Severity.WARNING)
                {
                    warnings.add(id);
                }
            }
            JsonNode reasons = root.get("warningReasons");
            if (!reasons.isObject() || reasons.size() > 10000)
            {
                throw invalid("warningReasons must be a bounded object");
            }
            var ids = reasons.fieldNames();
            while (ids.hasNext())
            {
                String id = ids.next();
                if (!warnings.contains(id))
                {
                    throw invalid("exception reason must name a retained WARNING finding");
                }
                text(reasons, id, 1000, true);
            }
            var payload = JSON.treeToValue(root, CloseReviewEvidenceService.Payload.class);
            var command = new CloseReviewEvidenceService.Command(new UUID(0, 0), start, end, payload.dataRevision(),
                    payload.references(), payload.warningReasons(), payload.note(), payload.attested());
            if (!CloseReviewEvidenceService.fingerprint(command).equals(payload.commandFingerprint()))
            {
                throw invalid("commandFingerprint does not match retained command facts");
            }
            return payload;
        }
        catch (IOException | RuntimeException ex)
        {
            String detail = Objects.toString(ex.getMessage(), "invalid payload");
            if (detail.length() > 600)
            {
                detail = detail.substring(0, 600) + "…";
            }
            throw new IllegalArgumentException("Invalid retained close-review evidence: " + detail, ex);
        }
    }

    private static void fields(JsonNode value, Set<String> expected)
    {
        if (value == null || !value.isObject())
        {
            throw invalid("expected an object");
        }
        Set<String> present = new HashSet<>();
        value.fieldNames().forEachRemaining(present::add);
        if (!present.equals(expected))
        {
            throw invalid("unsupported or missing fields: expected " + expected);
        }
    }

    private static JsonNode array(JsonNode object, String field)
    {
        JsonNode value = object.get(field);
        if (!value.isArray())
        {
            throw invalid(field + " must be an array");
        }
        return value;
    }

    private static String text(JsonNode object, String field, int limit, boolean required)
    {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.textValue().length() > limit
                || (required && value.textValue().isBlank()))
        {
            throw invalid(field + " must be bounded text");
        }
        return value.textValue();
    }

    private static String hash(JsonNode object, String field)
    {
        String value = text(object, field, 64, true);
        if (!value.matches("[0-9a-f]{64}"))
        {
            throw invalid(field + " must be a SHA-256 hex value");
        }
        return value;
    }

    private static IllegalArgumentException invalid(String message)
    {
        return new IllegalArgumentException(message);
    }
}
