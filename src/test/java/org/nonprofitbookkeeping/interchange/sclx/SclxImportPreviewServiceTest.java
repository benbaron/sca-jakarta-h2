package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxImportPreviewServiceTest
{
    @TempDir
    Path tempDir;

    @Test
    void previewsExactCountsMappingsAndIdempotentIdentityWithoutWriting() throws Exception
    {
        Path source = write(SclxJsonSerializerTest.document(), "complete.sclx");
        SclxImportTargetSnapshot empty = emptyTarget("TEST");
        SclxImportPreview first = service(empty).preview(source);

        assertFalse(first.hasBlockingErrors(), () -> first.operation().messages().toString());
        assertEquals(SclxAccountMode.AS_IS, first.recommendedAccountMode());
        assertEquals(1L, first.sectionCounts().count("organizations"));
        assertEquals(2L, first.sectionCounts().count("accounts"));
        assertEquals(1L, first.sectionCounts().count("fixedAssets"));
        assertEquals(1L, first.sectionCounts().count("depreciationRuns"));
        assertEquals(13L, first.sectionCounts().totalEntities());
        assertEquals(0L, first.sectionCounts().unsupportedSectionCount());
        assertEquals(13L, first.operation().counts().created());
        assertTrue(first.mappings().stream().allMatch(
                mapping -> mapping.resolution() == SclxImportMappingRequirement.Resolution.AS_IS));
        assertTrue(first.transactions().get(0).balanced());

        SclxImportEntityPreview transaction = first.operation().items().stream()
                .filter(item -> item.entityType().equals("TRANSACTION"))
                .findFirst()
                .orElseThrow();
        Map<SclxImportTargetSnapshot.ExternalIdentityKey, SclxImportTargetSnapshot.IdentityFact> identities =
                Map.of(new SclxImportTargetSnapshot.ExternalIdentityKey("TRANSACTION", transaction.externalId()),
                        new SclxImportTargetSnapshot.IdentityFact(transaction.normalizedContentHash(), "42"));
        SclxImportTargetSnapshot withIdentity = new SclxImportTargetSnapshot(
                empty.companyCode(), empty.companyName(), false, Map.of(), Map.of(), identities,
                List.of(), Set.of());
        SclxImportPreview second = service(withIdentity).preview(source);

        assertEquals(InterchangeIdentityMatch.IDENTICAL, second.operation().items().stream()
                .filter(item -> item.entityType().equals("TRANSACTION"))
                .findFirst().orElseThrow().identityMatch());
        assertEquals(1L, second.operation().counts().identical());
        assertEquals(12L, second.operation().counts().created());
    }

    @Test
    void blocksPopulatedMappingUnbalancedClosedAndFinalizedReconciliationConflicts() throws Exception
    {
        byte[] bytes = new SclxJsonSerializer().serialize(SclxJsonSerializerTest.document());
        JsonNode root = new ObjectMapper().readTree(bytes);
        ObjectNode debitLine = null;
        for (JsonNode line : root.path("transactions").get(0).path("lines"))
        {
            if (new BigDecimal(line.path("debit").asText()).signum() > 0)
            {
                debitLine = (ObjectNode) line;
                break;
            }
        }
        assertNotNull(debitLine, "fixture must contain a debit posting line");
        debitLine.put("debit", "0");
        Path source = tempDir.resolve("conflicts.sclx");
        Files.write(source, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(root));

        String transactionId = root.path("transactions").get(0).path("transactionId").textValue();
        Map<String, SclxImportTargetSnapshot.TargetAccount> accounts = Map.of(
                "1010", account("OTHER", "1010", "ASSET", "DEBIT"),
                "6100", account("OTHER", "6100", "EXPENSE", "DEBIT"));
        Map<String, SclxImportTargetSnapshot.TargetFund> funds = Map.of(
                "GENERAL", new SclxImportTargetSnapshot.TargetFund(
                        SclxPortableIdentity.fund("OTHER", "GENERAL"), "GENERAL", "UNRESTRICTED", true, "7"));
        Map<SclxImportTargetSnapshot.ExternalIdentityKey, SclxImportTargetSnapshot.IdentityFact> identities =
                Map.of(new SclxImportTargetSnapshot.ExternalIdentityKey("TRANSACTION", transactionId),
                        new SclxImportTargetSnapshot.IdentityFact("0".repeat(64), "42"));
        SclxImportTargetSnapshot target = new SclxImportTargetSnapshot(
                "OTHER", "Other Company", true, accounts, funds, identities,
                List.of(new SclxImportTargetSnapshot.ClosedRange(
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))),
                Set.of("42"));

        SclxImportPreview preview = service(target).preview(source);
        Set<String> codes = preview.operation().messages().stream()
                .map(message -> message.code())
                .collect(Collectors.toSet());

        assertTrue(preview.hasBlockingErrors());
        assertEquals(SclxAccountMode.MAPPED, preview.recommendedAccountMode());
        assertTrue(codes.contains("SCLX_POPULATED_TARGET_UNSUPPORTED"));
        assertTrue(codes.contains("SCLX_EXPLICIT_MAPPING_REQUIRED"));
        assertTrue(codes.contains("SCLX_BALANCING_ACCOUNT_REQUIRED"));
        assertTrue(codes.contains("SCLX_CLOSED_PERIOD_CONFLICT"));
        assertTrue(codes.contains("SCLX_FINALIZED_RECONCILIATION_CONFLICT"));
        assertTrue(codes.contains("SCLX_ZERO_VALUE_LINE_SKIPPED"));
        assertTrue(codes.contains("SCLX_EXTERNAL_ID_CONFLICT"));
        assertEquals(1, preview.transactions().get(0).postingLineCount());
        assertTrue(preview.transactions().get(0).requiresBalancingAccount());
        assertTrue(preview.transactions().get(0).closedPeriodConflict());
        assertTrue(preview.transactions().get(0).finalizedReconciliationConflict());
    }

    private SclxImportPreviewService service(SclxImportTargetSnapshot target)
    {
        return new SclxImportPreviewService(
                new SclxDocumentParser(), new SclxStructureValidator(),
                (companyCode, sourceSystem) -> target,
                target::companyCode);
    }

    private Path write(SclxExportDocument document, String name) throws Exception
    {
        Path source = tempDir.resolve(name);
        Files.write(source, new SclxJsonSerializer().serialize(document));
        return source;
    }

    private static SclxImportTargetSnapshot emptyTarget(String companyCode)
    {
        return new SclxImportTargetSnapshot(
                companyCode, "Test Company", false, Map.of(), Map.of(), Map.of(), List.of(), Set.of());
    }

    private static SclxImportTargetSnapshot.TargetAccount account(
            String companyCode, String code, String type, String side)
    {
        return new SclxImportTargetSnapshot.TargetAccount(
                SclxPortableIdentity.account(companyCode, code), code, type, side, true, true, code);
    }
}
