package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.service.CompanyOwnershipIssueView;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Instant;
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
        assertEquals(2L, first.sectionCounts().unsupportedSectionCount());
        assertEquals(13L, first.operation().counts().created());
        assertTrue(first.mappings().stream().allMatch(
                mapping -> mapping.resolution() == SclxImportMappingRequirement.Resolution.CREATE));
        assertTrue(first.transactions().get(0).balanced());

        SclxImportEntityPreview transaction = first.operation().items().stream()
                .filter(item -> item.entityType().equals("TRANSACTION"))
                .findFirst()
                .orElseThrow();
        Map<SclxImportTargetSnapshot.ExternalIdentityKey, SclxImportTargetSnapshot.IdentityFact> identities =
                Map.of(new SclxImportTargetSnapshot.ExternalIdentityKey("TRANSACTION", transaction.externalId()),
                        new SclxImportTargetSnapshot.IdentityFact(transaction.normalizedContentHash(), "42"));
        SclxImportTargetSnapshot withIdentity = new SclxImportTargetSnapshot(
                empty.companyCode(), empty.companyName(), false, false, Map.of(), Map.of(), identities,
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
                "OTHER", "Other Company", true, true, accounts, funds, identities,
                List.of(new SclxImportTargetSnapshot.ClosedRange(
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))),
                Set.of("42"));

        SclxImportPreview preview = service(target).preview(source);
        Set<String> codes = preview.operation().messages().stream()
                .map(message -> message.code())
                .collect(Collectors.toSet());

        assertTrue(preview.hasBlockingErrors());
        assertEquals(SclxAccountMode.MAPPED, preview.recommendedAccountMode());
        assertTrue(codes.contains("SCLX_MAPPING_APPROVAL_REQUIRED"));
        assertTrue(codes.contains("SCLX_BALANCING_ACCOUNT_REQUIRED"));
        assertTrue(codes.contains("SCLX_CLOSED_PERIOD_CONFLICT"));
        assertTrue(codes.contains("SCLX_FINALIZED_RECONCILIATION_CONFLICT"));
        assertTrue(codes.contains("SCLX_ZERO_VALUE_LINE_SKIPPED"));
        assertTrue(codes.contains("SCLX_CONFLICT_CHOICE_REQUIRED"));
        assertEquals(1, preview.transactions().get(0).postingLineCount());
        assertTrue(preview.transactions().get(0).requiresBalancingAccount());
        assertTrue(preview.transactions().get(0).closedPeriodConflict());
        assertTrue(preview.transactions().get(0).finalizedReconciliationConflict());
    }

    @Test
    void previewsDonorCompatibilityDecisionsAsExplicitNonBlockingWarnings()
    {
        Path source = Path.of("src/test/resources/compatibility/sclx/donor-sclx-1.3.json");

        SclxImportPreview preview = service(emptyTarget("TEST")).preview(source);
        Set<String> codes = preview.operation().messages().stream()
                .map(message -> message.code())
                .collect(Collectors.toSet());

        assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
        assertEquals(SclxAccountMode.AS_IS, preview.recommendedAccountMode());
        assertEquals(2L, preview.sectionCounts().count("accounts"));
        assertEquals(1L, preview.sectionCounts().count("counterparties"));
        assertEquals(0L, preview.sectionCounts().count("budgets"));
        assertEquals(0L, preview.sectionCounts().unsupportedSectionCount());
        assertEquals(8L, preview.sectionCounts().totalEntities());
        assertTrue(codes.contains("SCLX_DONOR_TARGET_SETTINGS_PRESERVED"));
        assertTrue(codes.contains("SCLX_DONOR_REFERENCES_PRESERVED"));
        assertTrue(codes.contains("SCLX_DONOR_COUNTERPARTY_LINKS_NORMALIZED"));
        assertTrue(codes.contains("SCLX_DONOR_BUDGET_REFERENCES_SKIPPED"));
        assertTrue(preview.transactions().get(0).balanced());
    }

    @Test
    void reusesIdenticalNativeCounterpartyWhenSourceIdentityRowIsMissing()
    {
        Path source = Path.of("src/test/resources/compatibility/sclx/donor-sclx-1.3.json");
        String externalId = "person-donor";
        SclxImportTargetSnapshot.NativePortableKey nativeKey =
                SclxNativePortableIdentity.key("COUNTERPARTY", externalId);
        ObjectNode canonicalCounterparty = new ObjectMapper().createObjectNode();
        canonicalCounterparty.put("displayName", "Donor Counterparty");
        canonicalCounterparty.put("kind", "OTHER");
        canonicalCounterparty.putNull("email");
        canonicalCounterparty.putNull("phone");
        canonicalCounterparty.putNull("notes");
        canonicalCounterparty.put("active", true);
        SclxImportTargetSnapshot target = new SclxImportTargetSnapshot(
                "TEST", "Test Company", true, true, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(nativeKey, new SclxImportTargetSnapshot.NativePortableFact(
                        "63", "TEST", SclxNativePortableIdentity.incomingFingerprint(
                                "COUNTERPARTY", canonicalCounterparty))),
                List.of(), Set.of());

        SclxImportPreview preview = service(target).preview(source);
        SclxImportEntityPreview counterparty = preview.operation().items().stream()
                .filter(item -> item.entityType().equals("COUNTERPARTY"))
                .findFirst().orElseThrow();

        assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
        assertEquals(InterchangeIdentityMatch.NEW, counterparty.identityMatch());
        assertEquals("63", counterparty.localEntityId());
        assertEquals(nativeKey.portableId().toString(), counterparty.nativePortableId());
        assertTrue(preview.operation().messages().stream().anyMatch(message ->
                message.code().equals("SCLX_NATIVE_PORTABLE_ID_REUSED")
                        && message.path().equals(counterparty.path())));
    }

    @Test
    void requiresRecordChoiceForDifferentNativeCounterpartyContent()
    {
        Path source = Path.of("src/test/resources/compatibility/sclx/donor-sclx-1.3.json");
        String externalId = "person-donor";
        SclxImportTargetSnapshot.NativePortableKey nativeKey =
                SclxNativePortableIdentity.key("COUNTERPARTY", externalId);
        SclxImportTargetSnapshot target = new SclxImportTargetSnapshot(
                "TEST", "Test Company", true, true, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(nativeKey, new SclxImportTargetSnapshot.NativePortableFact(
                        "63", "TEST", "0".repeat(64))),
                List.of(), Set.of());

        SclxImportPreview preview = service(target).preview(source);
        SclxImportEntityPreview counterparty = preview.operation().items().stream()
                .filter(item -> item.entityType().equals("COUNTERPARTY"))
                .findFirst().orElseThrow();

        assertTrue(preview.hasBlockingErrors());
        assertEquals(InterchangeIdentityMatch.CONFLICT, counterparty.identityMatch());
        assertTrue(counterparty.sourceChoiceAllowed());
        assertTrue(preview.operation().messages().stream().anyMatch(message ->
                message.code().equals("SCLX_CONFLICT_CHOICE_REQUIRED")
                        && message.path().equals(counterparty.path())));
    }

    @Test
    void blocksNativePortableIdentityOwnedByAnotherCompany()
    {
        Path source = Path.of("src/test/resources/compatibility/sclx/donor-sclx-1.3.json");
        String externalId = "person-donor";
        SclxImportTargetSnapshot.NativePortableKey nativeKey =
                SclxNativePortableIdentity.key("COUNTERPARTY", externalId);
        SclxImportTargetSnapshot target = new SclxImportTargetSnapshot(
                "TEST", "Test Company", true, false, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(nativeKey, new SclxImportTargetSnapshot.NativePortableFact(
                        "63", "OTHER", "0".repeat(64))),
                List.of(), Set.of());

        SclxImportPreview preview = service(target).preview(source);

        assertTrue(preview.hasBlockingErrors());
        assertTrue(preview.operation().messages().stream().anyMatch(message ->
                message.code().equals("SCLX_NATIVE_PORTABLE_ID_OTHER_COMPANY")
                        && message.path().contains("counterparties")));
    }

    @Test
    void dropsUnsupportedDonorRecordsIndividuallyAndCarriesChoicesIntoFreshPreview() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode donor = (ObjectNode) mapper.readTree(Files.readString(Path.of(
                "src/test/resources/compatibility/sclx/donor-sclx-1.3.json")));
        var assets = mapper.createArrayNode();
        assets.addObject().put("assetId", "asset-one").put("description", "Trailer");
        assets.addObject().put("assetId", "asset-two").put("description", "Tent");
        donor.set("assets", assets);
        Path source = tempDir.resolve("unsupported-donor-assets.sclx");
        Files.writeString(source, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(donor));
        SclxImportPreviewService service = service(emptyTarget("TEST"));

        SclxImportPreview blocked = service.preview(source);
        List<org.nonprofitbookkeeping.interchange.InterchangeValidationMessage> assetErrors =
                blocked.operation().messages().stream()
                        .filter(message -> message.code().equals("SCLX_DONOR_UNSUPPORTED_SECTION"))
                        .toList();
        assertEquals(List.of("$.assets[0]", "$.assets[1]"), assetErrors.stream()
                .map(message -> message.path()).toList());

        List<SclxImportDispositionSelection> dispositions = assetErrors.stream()
                .map(message -> new SclxImportDispositionSelection(
                        message.code(), message.path(), SclxImportDisposition.DROP_RECORD))
                .toList();
        SclxImportPreview corrected = service.preview(
                source, List.of(), List.of(), dispositions);

        assertFalse(corrected.hasBlockingErrors(), () -> corrected.operation().messages().toString());
        assertEquals(0L, corrected.sectionCounts().unsupportedSectionCount());
        assertEquals(dispositions, corrected.dispositions());
        assertEquals(2L, corrected.operation().messages().stream()
                .filter(message -> message.code().equals("SCLX_DISPOSITION_APPLIED"))
                .count());
    }

    @Test
    void ignoresOnlyAnExactNonblockingMessage() throws Exception
    {
        Path source = Path.of("src/test/resources/compatibility/sclx/donor-sclx-1.3.json");
        SclxImportPreviewService service = service(emptyTarget("TEST"));
        SclxImportPreview initial = service.preview(source);
        var warning = initial.operation().messages().stream()
                .filter(message -> message.code().equals("SCLX_DONOR_TARGET_SETTINGS_PRESERVED"))
                .findFirst().orElseThrow();

        SclxImportPreview ignored = service.preview(source, List.of(), List.of(), List.of(
                new SclxImportDispositionSelection(
                        warning.code(), warning.path(), SclxImportDisposition.IGNORE)));

        assertFalse(ignored.hasBlockingErrors(), () -> ignored.operation().messages().toString());
        assertFalse(ignored.operation().messages().stream()
                .anyMatch(message -> message.code().equals(warning.code())
                        && message.path().equals(warning.path())));
        assertTrue(ignored.operation().messages().stream()
                .anyMatch(message -> message.code().equals("SCLX_DISPOSITION_APPLIED")));

        ObjectNode donor = (ObjectNode) new ObjectMapper().readTree(Files.readString(source));
        donor.putArray("assets").addObject().put("assetId", "blocked-asset");
        Path blockedSource = tempDir.resolve("ignore-blocking.sclx");
        Files.writeString(blockedSource,
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(donor));
        SclxImportPreview blocked = service.preview(blockedSource);
        var blocker = blocked.operation().messages().stream()
                .filter(message -> message.code().equals("SCLX_DONOR_UNSUPPORTED_SECTION"))
                .findFirst().orElseThrow();
        SclxImportPreview rejected = service.preview(blockedSource, List.of(), List.of(), List.of(
                new SclxImportDispositionSelection(
                        blocker.code(), blocker.path(), SclxImportDisposition.IGNORE)));
        assertTrue(rejected.hasBlockingErrors());
        assertTrue(rejected.operation().messages().stream()
                .anyMatch(message -> message.code().equals("SCLX_DISPOSITION_UNSUPPORTED")));
    }

    @Test
    void suggestedCorrectionDropsUndatedNonpostingAnnotationRecord() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(
                new SclxJsonSerializer().serialize(SclxJsonSerializerTest.document()));
        ObjectNode annotation = root.withArray("transactions").addObject();
        annotation.put("transactionId", "workbook-annotation");
        annotation.put("description", "Workbook note only");
        annotation.put("status", "ENTERED");
        annotation.putArray("lines");
        Path source = tempDir.resolve("suggested-date-correction.sclx");
        Files.writeString(source,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        SclxImportPreviewService service = service(emptyTarget("TEST"));
        SclxImportPreview initial = service.preview(source);
        var missingDate = initial.operation().messages().stream()
                .filter(message -> message.code().equals("SCLX_DATE_REQUIRED"))
                .filter(message -> message.path().contains("transactionDate"))
                .findFirst().orElseThrow();

        SclxImportPreview corrected = service.preview(source, List.of(), List.of(), List.of(
                new SclxImportDispositionSelection(
                        missingDate.code(), missingDate.path(),
                        SclxImportDisposition.MAKE_SUGGESTED_CORRECTION)));

        assertFalse(corrected.hasBlockingErrors(), () -> corrected.operation().messages().toString());
        assertTrue(corrected.operation().messages().stream()
                .anyMatch(message -> message.code().equals("SCLX_DISPOSITION_APPLIED")));
        assertTrue(corrected.transactions().stream()
                .noneMatch(transaction -> transaction.transactionId().equals("workbook-annotation")));
    }

    @Test
    void surfacesEveryOpenOwnershipDiagnosticAsAnActionableBlockingPreviewError()
    {
        Path source = Path.of("src/test/resources/compatibility/sclx/donor-sclx-1.3.json");
        List<CompanyOwnershipIssueView> ownershipIssues = List.of(
                new CompanyOwnershipIssueView(
                        11L, "ACTIVITY", "1", "EVENT — Annual event", List.of(), "UNRESOLVED_OWNER", 0,
                        "Activity has no deterministic company owner.", Instant.parse("2026-08-14T00:00:00Z")),
                new CompanyOwnershipIssueView(
                        12L, "TXN_SPLIT", "7", "TXN_SPLIT 7", List.of("DEFAULT", "OTHER"),
                        "CROSS_COMPANY_REFERENCE", 2,
                        "Transaction split account belongs to another company.",
                        Instant.parse("2026-08-14T00:00:00Z")));
        SclxImportTargetSnapshot target = emptyTarget("TEST");
        SclxImportPreviewService service = new SclxImportPreviewService(
                new SclxDocumentParser(), new SclxStructureValidator(),
                (companyCode, sourceSystem) -> target,
                target::companyCode,
                () -> ownershipIssues);

        SclxImportPreview preview = service.preview(source);
        List<org.nonprofitbookkeeping.interchange.InterchangeValidationMessage> blockers =
                preview.operation().messages().stream()
                        .filter(message -> message.code().equals("SCLX_COMPANY_OWNERSHIP_UNRESOLVED"))
                        .toList();

        assertTrue(preview.hasBlockingErrors());
        assertEquals(2, blockers.size());
        assertTrue(blockers.stream().allMatch(message -> message.blocking()
                && message.message().contains("Resolution:")
                && message.message().contains("Administration -> Company Ownership Diagnostics")));
        assertTrue(blockers.stream().anyMatch(message ->
                message.path().equals("companyOwnership.ACTIVITY.1")
                        && message.message().contains("Assign to Import Company")));
        assertTrue(blockers.stream().anyMatch(message ->
                message.path().equals("companyOwnership.TXN_SPLIT.7")
                        && message.message().contains("will not guess")));
    }

    @Test
    void allowsExistingCompatibleChartAndFundsWithApprovedMappings() throws Exception
    {
        Path source = write(SclxJsonSerializerTest.document(), "existing-masters.sclx");
        JsonNode root = new ObjectMapper().readTree(source.toFile());
        Map<String, SclxImportTargetSnapshot.TargetAccount> accounts = new java.util.LinkedHashMap<>();
        for (JsonNode value : root.path("chartOfAccounts"))
        {
            String code = value.path("code").textValue();
            accounts.put(code, account("TARGET", code,
                    value.path("type").textValue(), value.path("increaseSide").textValue()));
        }
        Map<String, SclxImportTargetSnapshot.TargetFund> funds = new java.util.LinkedHashMap<>();
        for (JsonNode value : root.path("funds"))
        {
            String code = value.path("code").textValue();
            funds.put(code, new SclxImportTargetSnapshot.TargetFund(
                    SclxPortableIdentity.fund("TARGET", code), code,
                    value.path("type").textValue(), true, code));
        }
        SclxImportTargetSnapshot target = new SclxImportTargetSnapshot(
                "TARGET", "Existing Company", true, false, accounts, funds,
                Map.of(), List.of(), Set.of());

        SclxImportPreview preview = service(target).preview(source);
        Set<String> codes = preview.operation().messages().stream()
                .map(message -> message.code())
                .collect(Collectors.toSet());

        assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
        assertEquals(SclxAccountMode.MAPPED, preview.recommendedAccountMode());
        assertTrue(preview.mappings().stream().allMatch(mapping ->
                mapping.resolution() == SclxImportMappingRequirement.Resolution.MAPPED));
        assertTrue(codes.contains("SCLX_MAPPING_APPROVAL_REQUIRED"));
        assertFalse(codes.contains("SCLX_OPERATIONAL_DATA_MERGE_UNSUPPORTED"));
        assertEquals(preview.sectionCounts().totalEntities() - preview.mappings().size() - 1L,
                preview.operation().counts().created());
    }

    @Test
    void reusesCompatibleActivityAssignedToTheImportTarget() throws Exception
    {
        Path source = write(SclxJsonSerializerTest.document(), "assigned-activity.sclx");
        JsonNode activity = new ObjectMapper().readTree(source.toFile())
                .path("extensions").path("scaJakartaH2").path("activities").get(0);
        String code = activity.path("code").textValue();
        SclxImportTargetSnapshot target = new SclxImportTargetSnapshot(
                "TARGET", "Import Target", true, false, Map.of(), Map.of(),
                Map.of(code, new SclxImportTargetSnapshot.TargetActivity(
                        code, activity.path("name").textValue(), activity.path("active").booleanValue(), "91")),
                Map.of(), List.of(), Set.of());

        SclxImportPreview preview = service(target).preview(source);
        SclxImportEntityPreview activityPreview = preview.operation().items().stream()
                .filter(item -> item.entityType().equals("ACTIVITY"))
                .findFirst().orElseThrow();

        assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
        assertEquals(InterchangeIdentityMatch.NEW, activityPreview.identityMatch());
        assertEquals("91", activityPreview.localEntityId());
        assertEquals(1L, preview.operation().counts().updated());
        assertTrue(preview.operation().messages().stream().anyMatch(message ->
                message.code().equals("SCLX_TARGET_ACTIVITY_REUSED") && !message.blocking()));
        assertFalse(preview.operation().messages().stream().anyMatch(message ->
                message.code().equals("SCLX_OPERATIONAL_DATA_MERGE_UNSUPPORTED")));
    }

    @Test
    void blocksDifferentActivityUsingTheSameTargetCode() throws Exception
    {
        Path source = write(SclxJsonSerializerTest.document(), "activity-conflict.sclx");
        JsonNode activity = new ObjectMapper().readTree(source.toFile())
                .path("extensions").path("scaJakartaH2").path("activities").get(0);
        String code = activity.path("code").textValue();
        SclxImportTargetSnapshot target = new SclxImportTargetSnapshot(
                "TARGET", "Import Target", true, false, Map.of(), Map.of(),
                Map.of(code, new SclxImportTargetSnapshot.TargetActivity(
                        code, "Different activity", activity.path("active").booleanValue(), "91")),
                Map.of(), List.of(), Set.of());

        SclxImportPreview preview = service(target).preview(source);

        assertTrue(preview.hasBlockingErrors());
        assertTrue(preview.operation().messages().stream().anyMatch(message ->
                message.code().equals("SCLX_CONFLICT_CHOICE_REQUIRED") && message.blocking()));

        String externalId = activity.path("activityId").textValue();
        SclxImportPreview keepTarget = service(target).preview(source, List.of(), List.of(
                new SclxImportConflictSelection(
                        "ACTIVITY", externalId, SclxImportConflictChoice.KEEP_TARGET)));
        assertFalse(keepTarget.hasBlockingErrors(), () -> keepTarget.operation().messages().toString());
        assertEquals(SclxImportConflictChoice.KEEP_TARGET,
                keepTarget.operation().items().stream()
                        .filter(item -> item.entityType().equals("ACTIVITY"))
                        .findFirst().orElseThrow().conflictChoice());

        SclxImportPreview takeSource = service(target).preview(source, List.of(), List.of(
                new SclxImportConflictSelection(
                        "ACTIVITY", externalId, SclxImportConflictChoice.TAKE_SOURCE)));
        assertFalse(takeSource.hasBlockingErrors(), () -> takeSource.operation().messages().toString());
        assertEquals(1L, takeSource.operation().counts().updated());
    }

    @Test
    void selectedCompatibleTargetResolvesSameCodeAccountConflict() throws Exception
    {
        Path source = write(SclxJsonSerializerTest.document(), "selected-mapping.sclx");
        JsonNode sourceAccount = new ObjectMapper().readTree(source.toFile())
                .path("chartOfAccounts").get(0);
        String sourceId = sourceAccount.path("accountId").textValue();
        String sourceCode = sourceAccount.path("code").textValue();
        Map<String, SclxImportTargetSnapshot.TargetAccount> accounts = Map.of(
                sourceCode, account("TARGET", sourceCode, "LIABILITY", "CREDIT"),
                "ALT", account("TARGET", "ALT", sourceAccount.path("type").textValue(),
                        sourceAccount.path("increaseSide").textValue()));
        SclxImportTargetSnapshot target = new SclxImportTargetSnapshot(
                "TARGET", "Existing Company", true, false, accounts, Map.of(),
                Map.of(), List.of(), Set.of());
        SclxImportPreviewService service = service(target);

        SclxImportPreview initial = service.preview(source);
        assertTrue(initial.hasBlockingErrors());
        SclxImportMappingRequirement conflict = initial.mappings().stream()
                .filter(mapping -> mapping.sourceId().equals(sourceId))
                .findFirst().orElseThrow();
        assertEquals(SclxImportMappingRequirement.Resolution.CONFLICT, conflict.resolution());
        assertEquals(List.of("ALT"), conflict.compatibleTargetCodes());

        SclxImportPreview resolved = service.preview(source, List.of(
                new SclxImportMappingSelection(
                        SclxImportMappingRequirement.Kind.ACCOUNT, sourceId, "ALT")));

        assertFalse(resolved.hasBlockingErrors(), () -> resolved.operation().messages().toString());
        SclxImportMappingRequirement mapping = resolved.mappings().stream()
                .filter(value -> value.sourceId().equals(sourceId))
                .findFirst().orElseThrow();
        assertEquals(SclxImportMappingRequirement.Resolution.MAPPED, mapping.resolution());
        assertEquals("ALT", mapping.targetCode());

        String accountHash = resolved.operation().items().stream()
                .filter(value -> value.entityType().equals("ACCOUNT")
                        && value.externalId().equals(sourceId))
                .findFirst().orElseThrow().normalizedContentHash();
        SclxImportTargetSnapshot importedTarget = new SclxImportTargetSnapshot(
                target.companyCode(), target.companyName(), true, false,
                target.accountsByCode(), target.fundsByCode(),
                Map.of(new SclxImportTargetSnapshot.ExternalIdentityKey("ACCOUNT", sourceId),
                        new SclxImportTargetSnapshot.IdentityFact(accountHash, "ALT")),
                List.of(), Set.of());
        SclxImportPreview identical = service(importedTarget).preview(source);
        SclxImportMappingRequirement durableMapping = identical.mappings().stream()
                .filter(value -> value.sourceId().equals(sourceId))
                .findFirst().orElseThrow();
        assertEquals(SclxImportMappingRequirement.Resolution.AS_IS, durableMapping.resolution());
        assertEquals("ALT", durableMapping.targetCode());
    }

    @Test
    void createDefaultAlsoOffersCompatibleAlternateTarget() throws Exception
    {
        Path source = write(SclxJsonSerializerTest.document(), "create-alternate-mapping.sclx");
        JsonNode sourceAccount = new ObjectMapper().readTree(source.toFile())
                .path("chartOfAccounts").get(0);
        String sourceId = sourceAccount.path("accountId").textValue();
        String sourceCode = sourceAccount.path("code").textValue();
        SclxImportTargetSnapshot target = new SclxImportTargetSnapshot(
                "TARGET", "Existing Company", true, false,
                Map.of("ALT", account("TARGET", "ALT",
                        sourceAccount.path("type").textValue(),
                        sourceAccount.path("increaseSide").textValue())),
                Map.of(), Map.of(), List.of(), Set.of());
        SclxImportPreviewService service = service(target);

        SclxImportMappingRequirement create = service.preview(source).mappings().stream()
                .filter(mapping -> mapping.sourceId().equals(sourceId))
                .findFirst().orElseThrow();
        assertEquals(SclxImportMappingRequirement.Resolution.CREATE, create.resolution());
        assertEquals(sourceCode, create.targetCode());
        assertEquals(List.of("ALT"), create.compatibleTargetCodes());

        SclxImportMappingRequirement mapped = service.preview(source, List.of(
                        new SclxImportMappingSelection(
                                SclxImportMappingRequirement.Kind.ACCOUNT, sourceId, "ALT")))
                .mappings().stream()
                .filter(mapping -> mapping.sourceId().equals(sourceId))
                .findFirst().orElseThrow();
        assertEquals(SclxImportMappingRequirement.Resolution.MAPPED, mapped.resolution());
        assertEquals("ALT", mapped.targetCode());
    }

    @Test
    void blocksNonZeroDonorBudgetsInsteadOfDiscardingThem() throws Exception
    {
        String donor = Files.readString(Path.of(
                "src/test/resources/compatibility/sclx/donor-sclx-1.3.json"));
        Path source = tempDir.resolve("non-zero-donor-budget.sclx");
        Files.writeString(source, donor.replace(
                "\"budgetedAmount\": 0.00", "\"budgetedAmount\": 1.00"));

        SclxImportPreview preview = service(emptyTarget("TEST")).preview(source);

        assertTrue(preview.hasBlockingErrors());
        assertTrue(preview.operation().messages().stream().anyMatch(message ->
                message.code().equals("SCLX_DONOR_BUDGET_DATA_UNSUPPORTED") && message.blocking()));
    }

    @Test
    void blocksFundlessDonorLinesWhenGeneralFundCannotBeIdentified() throws Exception
    {
        String donor = Files.readString(Path.of(
                "src/test/resources/compatibility/sclx/donor-sclx-1.3.json"));
        Path source = tempDir.resolve("missing-general-fund.sclx");
        Files.writeString(source, donor.replace("General Fund", "Operating Fund"));

        SclxImportPreview preview = service(emptyTarget("TEST")).preview(source);

        assertTrue(preview.hasBlockingErrors());
        assertTrue(preview.operation().messages().stream().anyMatch(message ->
                message.code().equals("SCLX_DONOR_GENERAL_FUND_REQUIRED") && message.blocking()));
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
                companyCode, "Test Company", false, false, Map.of(), Map.of(), Map.of(), List.of(), Set.of());
    }

    private static SclxImportTargetSnapshot.TargetAccount account(
            String companyCode, String code, String type, String side)
    {
        return new SclxImportTargetSnapshot.TargetAccount(
                SclxPortableIdentity.account(companyCode, code), code, type,
                "BANK".equalsIgnoreCase(type) ? "CASH" : null, side, true, true, code);
    }
}
