package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Strict, bounded, non-mutating reader for governed SCLX JSON documents. */
public final class SclxDocumentParser
{
    public static final long MAX_FILE_BYTES = 256L * 1024L * 1024L;
    public static final int MAX_NESTING_DEPTH = 32;
    public static final int MAX_STRING_LENGTH = 4 * 1024 * 1024;
    public static final int MAX_NUMBER_LENGTH = 128;

    private static final byte[] UTF8_BOM = new byte[] {
            (byte) 0xEF, (byte) 0xBB, (byte) 0xBF
    };

    private final ObjectMapper mapper;

    public SclxDocumentParser()
    {
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(MAX_NESTING_DEPTH)
                        .maxStringLength(MAX_STRING_LENGTH)
                        .maxNumberLength(MAX_NUMBER_LENGTH)
                        .build())
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        mapper = new ObjectMapper(factory)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    public SclxParsedDocument parse(Path source)
    {
        return parse(source, List.of());
    }

    public SclxParsedDocument parse(
            Path source,
            List<SclxImportDispositionSelection> dispositionSelections)
    {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(dispositionSelections, "dispositionSelections");
        Path normalized = source.toAbsolutePath().normalize();
        try
        {
            if (!Files.isRegularFile(normalized))
            {
                throw new IllegalArgumentException("SCLX source is not a regular file: " + normalized);
            }
            long size = Files.size(normalized);
            if (size > MAX_FILE_BYTES)
            {
                throw new IllegalArgumentException(
                        "SCLX source exceeds the supported maximum of " + MAX_FILE_BYTES + " bytes");
            }
            return parse(Files.readAllBytes(normalized), dispositionSelections);
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Could not read SCLX source: " + ex.getMessage(), ex);
        }
    }

    SclxParsedDocument parse(byte[] originalBytes)
    {
        return parse(originalBytes, List.of());
    }

    SclxParsedDocument parse(
            byte[] originalBytes,
            List<SclxImportDispositionSelection> dispositionSelections)
    {
        Objects.requireNonNull(originalBytes, "originalBytes");
        Objects.requireNonNull(dispositionSelections, "dispositionSelections");
        if (originalBytes.length > MAX_FILE_BYTES)
        {
            throw new IllegalArgumentException(
                    "SCLX source exceeds the supported maximum of " + MAX_FILE_BYTES + " bytes");
        }

        boolean bomStripped = startsWithBom(originalBytes);
        int offset = bomStripped ? UTF8_BOM.length : 0;
        String json = decodeStrictUtf8(originalBytes, offset);
        JsonNode root;
        try
        {
            root = mapper.readTree(json);
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Malformed SCLX JSON: " + ex.getMessage(), ex);
        }

        if (root == null || !root.isObject())
        {
            throw new IllegalArgumentException("SCLX root must be a JSON object");
        }
        JsonNode formatNode = root.get("format");
        if (formatNode == null || !formatNode.isTextual() || !"SCLX".equals(formatNode.textValue()))
        {
            throw new IllegalArgumentException("SCLX format must be the exact string SCLX");
        }
        JsonNode versionNode = root.get("version");
        if (versionNode == null || !versionNode.isTextual())
        {
            throw new IllegalArgumentException("SCLX version is required and must be a string");
        }
        SclxVersion version = SclxVersion.parseReadable(versionNode.textValue());
        ExportedAt exportedAt = parseExportedAt(root.get("exportedAt"));
        SclxDonorCompatibilityNormalizer.Normalization normalization =
                SclxDonorCompatibilityNormalizer.normalize(
                        (ObjectNode) root, exportedAt.value(), exportedAt.numeric());
        SclxImportDispositionApplier.Result dispositions =
                SclxImportDispositionApplier.apply(
                        normalization.root(), normalization.notices(), dispositionSelections);

        return new SclxParsedDocument(
                version,
                exportedAt.value(),
                dispositions.root(),
                originalBytes.length,
                sha256(originalBytes),
                bomStripped,
                dispositions.notices());
    }

    private static ExportedAt parseExportedAt(JsonNode node)
    {
        if (node == null || node.isNull())
        {
            return new ExportedAt(null, false);
        }
        if (node.isNumber())
        {
            try
            {
                BigDecimal seconds = node.decimalValue();
                if (seconds.stripTrailingZeros().scale() > 9)
                {
                    throw new ArithmeticException("more than nanosecond precision");
                }
                BigDecimal whole = seconds.setScale(0, RoundingMode.DOWN);
                long epochSecond = whole.longValueExact();
                long nanos = seconds.subtract(whole).movePointRight(9).longValueExact();
                return new ExportedAt(Instant.ofEpochSecond(epochSecond, nanos), true);
            }
            catch (ArithmeticException | DateTimeException ex)
            {
                throw new IllegalArgumentException(
                        "SCLX numeric exportedAt must be finite epoch seconds with nanosecond precision", ex);
            }
        }
        if (!node.isTextual())
        {
            throw new IllegalArgumentException("SCLX exportedAt must be an RFC 3339 string");
        }
        try
        {
            return new ExportedAt(Instant.parse(node.textValue()), false);
        }
        catch (DateTimeException ex)
        {
            throw new IllegalArgumentException("SCLX exportedAt must be an RFC 3339 UTC instant", ex);
        }
    }

    private record ExportedAt(Instant value, boolean numeric)
    {
    }

    private static String decodeStrictUtf8(byte[] bytes, int offset)
    {
        try
        {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        }
        catch (CharacterCodingException ex)
        {
            throw new IllegalArgumentException("SCLX input is not valid UTF-8", ex);
        }
    }

    private static boolean startsWithBom(byte[] bytes)
    {
        return bytes.length >= UTF8_BOM.length
                && bytes[0] == UTF8_BOM[0]
                && bytes[1] == UTF8_BOM[1]
                && bytes[2] == UTF8_BOM[2];
    }

    private static String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
