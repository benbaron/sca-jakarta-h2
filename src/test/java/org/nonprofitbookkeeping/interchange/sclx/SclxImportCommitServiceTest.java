package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetDepreciationRun;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.InventoryMovement;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.BudgetLine;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.CounterpartyKind;
import org.nonprofitbookkeeping.model.ImportIssue;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.model.TxnSupplementalLine;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.PeriodCloseEventView;
import org.nonprofitbookkeeping.service.PeriodCloseRangeService;
import org.nonprofitbookkeeping.service.PeriodCloseRangeView;
import org.nonprofitbookkeeping.service.TransactionEntryService;
import org.nonprofitbookkeeping.service.TransactionView;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxImportCommitServiceTest
{
    private static final String TARGET = "SCLX_TARGET";
    private static final UUID TRANSACTION_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID FIXED_ASSET_UUID = UUID.fromString("22222222-3333-4444-5555-666666666666");
    private static final UUID DEPRECIATION_RUN_UUID = UUID.fromString("33333333-4444-5555-6666-777777777777");
    private static final UUID INVENTORY_ITEM_UUID = UUID.fromString("44444444-5555-6666-7777-888888888888");
    private static final UUID INVENTORY_MOVEMENT_UUID = UUID.fromString("55555555-6666-7777-8888-999999999999");
    private static final UUID BANK_UUID = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");
    private static final UUID BANK_ACCOUNT_UUID = UUID.fromString("77777777-8888-9999-aaaa-bbbbbbbbbbbb");
    private static final UUID IMPORT_BATCH_UUID = UUID.fromString("88888888-9999-aaaa-bbbb-cccccccccccc");
    private static final UUID STATEMENT_LINE_UUID = UUID.fromString("99999999-aaaa-bbbb-cccc-dddddddddddd");
    private static final UUID IMPORT_ISSUE_UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-ffffffffffff");
    private static final UUID RECONCILIATION_SESSION_UUID = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");
    private static final UUID RECONCILIATION_MATCH_UUID = UUID.fromString("cccccccc-dddd-eeee-ffff-000000000000");
    private static final UUID PERIOD_CLOSE_RANGE_UUID = UUID.fromString("dddddddd-eeee-ffff-0000-111111111111");
    private static final UUID PERIOD_CLOSE_EVENT_UUID = UUID.fromString("eeeeeeee-ffff-0000-1111-222222222222");
    private static final UUID PERIOD_REOPEN_EVENT_UUID = UUID.fromString("ffffffff-0000-1111-2222-333333333333");
    private static final UUID AUDIT_EVENT_UUID = UUID.fromString("12345678-90ab-cdef-1234-567890abcdef");
    private static final UUID REVERSAL_TRANSACTION_UUID = UUID.fromString("23456789-0abc-def1-2345-67890abcdef1");
    private static final UUID REPLACEMENT_TRANSACTION_UUID = UUID.fromString("34567890-abcd-ef12-3456-7890abcdef12");

    @Test
    void importsNormalizedDonorDocumentAndPreservesOmittedTargetSettings(@TempDir Path tempDir)
    {
        Path source = Path.of("src/test/resources/compatibility/sclx/donor-sclx-1.3.json");
        try (Jpa jpa = new Jpa(tempDir.resolve("donor-compatibility")))
        {
            seedEmptyTarget(jpa);
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company target = company(em);
                target.setDefaultCurrency("EUR");
                target.setFiscalYearStartMonth(4);
                target.setFiscalYearStartDay(15);
                em.getTransaction().commit();
            }

            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());

            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, preview, "tester");

            assertTrue(result.committed(), () -> result.messages().toString());
            try (EntityManager em = jpa.em())
            {
                Company target = company(em);
                assertEquals("Donor Test", target.getDisplayName());
                assertEquals("EUR", target.getDefaultCurrency());
                assertEquals(4, target.getFiscalYearStartMonth());
                assertEquals(15, target.getFiscalYearStartDay());
                assertEquals(2L, count(em, "select count(a) from Account a"));
                assertEquals(1L, count(em, "select count(f) from Fund f"));
                assertEquals(0L, count(em, "select count(p) from BudgetPlan p"));
                assertEquals(1L, count(em, "select count(c) from Counterparty c"));

                UUID donorTransactionId = UUID.nameUUIDFromBytes(
                        "SCLX:txn-donor-1".getBytes(StandardCharsets.UTF_8));
                Txn imported = transaction(em, donorTransactionId);
                assertEquals("ENTERED", imported.getStatus());
                assertEquals("Donation [Reference: deposit 1]", imported.getMemo());
                assertEquals("Donor Counterparty", imported.getPayee().getDisplayName());
                List<TxnSplit> splits = em.createQuery(
                                "select s from TxnSplit s join fetch s.fund where s.txn = :txn", TxnSplit.class)
                        .setParameter("txn", imported)
                        .getResultList();
                assertEquals(2, splits.size());
                assertTrue(splits.stream().allMatch(split -> "General Fund".equals(split.getFund().getName())));
            }
        }
    }

    @Test
    void reusesNativeCounterpartyAndRecordsMissingSourceIdentity(@TempDir Path tempDir)
    {
        Path source = Path.of("src/test/resources/compatibility/sclx/donor-sclx-1.3.json");
        UUID portableId = SclxNativePortableIdentity.portableUuid("person-donor");
        try (Jpa jpa = new Jpa(tempDir.resolve("native-counterparty-reuse")))
        {
            seedEmptyTarget(jpa);
            Long existingId;
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Counterparty counterparty = new Counterparty();
                counterparty.setCompany(company(em));
                counterparty.setPortableId(portableId);
                counterparty.setDisplayName("Donor Counterparty");
                counterparty.setKind(CounterpartyKind.OTHER);
                counterparty.setActive(true);
                em.persist(counterparty);
                em.getTransaction().commit();
                existingId = counterparty.getId();
            }

            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            SclxImportEntityPreview counterpartyPreview = preview.operation().items().stream()
                    .filter(item -> item.entityType().equals("COUNTERPARTY"))
                    .findFirst().orElseThrow();
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertEquals(InterchangeIdentityMatch.NEW, counterpartyPreview.identityMatch());
            assertEquals(String.valueOf(existingId), counterpartyPreview.localEntityId());

            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, preview, "tester", false, true);

            assertTrue(result.committed(), () -> result.messages().toString());
            try (EntityManager em = jpa.em())
            {
                assertEquals(1L, count(em, "select count(c) from Counterparty c"));
                assertEquals(existingId, em.createQuery(
                                "select c.id from Counterparty c where c.portableId = :portableId", Long.class)
                        .setParameter("portableId", portableId)
                        .getSingleResult());
                assertEquals(1L, em.createQuery("""
                                select count(i) from InterchangeIdentity i
                                where i.company.code = :companyCode
                                  and i.formatCode = 'SCLX'
                                  and i.sourceSystem = 'org-donor-fixture'
                                  and i.entityType = 'COUNTERPARTY'
                                  and i.externalId = 'person-donor'
                                """, Long.class)
                        .setParameter("companyCode", TARGET)
                        .getSingleResult());
            }
        }
    }

    @Test
    void appliesSourceWinnerToNativeCounterpartyWithoutPriorSourceIdentity(@TempDir Path tempDir)
    {
        Path source = Path.of("src/test/resources/compatibility/sclx/donor-sclx-1.3.json");
        UUID portableId = SclxNativePortableIdentity.portableUuid("person-donor");
        try (Jpa jpa = new Jpa(tempDir.resolve("native-counterparty-source-winner")))
        {
            seedEmptyTarget(jpa);
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Counterparty counterparty = new Counterparty();
                counterparty.setCompany(company(em));
                counterparty.setPortableId(portableId);
                counterparty.setDisplayName("Old Donor Name");
                counterparty.setKind(CounterpartyKind.OTHER);
                counterparty.setActive(true);
                em.persist(counterparty);
                em.getTransaction().commit();
            }

            SclxImportPreviewService previewService = new SclxImportPreviewService(jpa, () -> TARGET);
            SclxImportPreview blocked = previewService.preview(source);
            assertTrue(blocked.hasBlockingErrors());
            SclxImportPreview resolved = previewService.preview(
                    source,
                    List.of(),
                    List.of(new SclxImportConflictSelection(
                            "COUNTERPARTY", "person-donor", SclxImportConflictChoice.TAKE_SOURCE)));
            assertFalse(resolved.hasBlockingErrors(), () -> resolved.operation().messages().toString());

            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, resolved, "tester", false, true);

            assertTrue(result.committed(), () -> result.messages().toString());
            try (EntityManager em = jpa.em())
            {
                Counterparty counterparty = em.createQuery(
                                "from Counterparty c where c.portableId = :portableId", Counterparty.class)
                        .setParameter("portableId", portableId)
                        .getSingleResult();
                assertEquals("Donor Counterparty", counterparty.getDisplayName());
                assertEquals(1L, count(em, "select count(c) from Counterparty c"));
                assertEquals(1L, em.createQuery("""
                                select count(i) from InterchangeIdentity i
                                where i.company.code = :companyCode
                                  and i.formatCode = 'SCLX'
                                  and i.sourceSystem = 'org-donor-fixture'
                                  and i.entityType = 'COUNTERPARTY'
                                  and i.externalId = 'person-donor'
                                """, Long.class)
                        .setParameter("companyCode", TARGET)
                        .getSingleResult());
            }
        }
    }

    @Test
    void commitReappliesApprovedRecordDropsToTheExactDonorSource(@TempDir Path tempDir) throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode donor = (ObjectNode) mapper.readTree(Files.readString(Path.of(
                "src/test/resources/compatibility/sclx/donor-sclx-1.3.json")));
        donor.putArray("assets").addObject()
                .put("assetId", "legacy-trailer")
                .put("description", "Trailer without canonical depreciation fields");
        Path source = tempDir.resolve("donor-with-legacy-asset.sclx");
        Files.writeString(source, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(donor));

        try (Jpa jpa = new Jpa(tempDir.resolve("donor-disposition")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreviewService previews = new SclxImportPreviewService(jpa, () -> TARGET);
            SclxImportPreview blocked = previews.preview(source);
            InterchangeValidationMessage unsupported = blocked.operation().messages().stream()
                    .filter(message -> message.code().equals("SCLX_DONOR_UNSUPPORTED_SECTION"))
                    .findFirst()
                    .orElseThrow();
            SclxImportDispositionSelection drop = new SclxImportDispositionSelection(
                    unsupported.code(), unsupported.path(), SclxImportDisposition.DROP_RECORD);
            SclxImportPreview corrected = previews.preview(
                    source, List.of(), List.of(), List.of(drop));

            assertFalse(corrected.hasBlockingErrors(), () -> corrected.operation().messages().toString());
            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, corrected, "tester");

            assertTrue(result.committed(), () -> result.messages().toString());
            try (EntityManager em = jpa.em())
            {
                assertEquals(1L, count(em, "select count(t) from Txn t"));
                assertEquals(0L, count(em, "select count(a) from FixedAsset a"));
            }
        }
    }

    @Test
    void importsCorrectionRelationshipsAtomicallyAndReimportIsIdempotent(@TempDir Path tempDir) throws Exception
    {
        Path source = writeCorrectionSource(tempDir.resolve("corrections.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("corrections")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreviewService previews = new SclxImportPreviewService(jpa, () -> TARGET);
            SclxImportPreview preview = previews.preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertEquals(26L, preview.operation().counts().created());

            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, preview, "tester");

            assertTrue(result.committed(), () -> result.messages().toString());
            assertEquals(26L, result.counts().created());
            try (EntityManager em = jpa.em())
            {
                Txn original = transaction(em, TRANSACTION_UUID);
                Txn reversal = transaction(em, REVERSAL_TRANSACTION_UUID);
                Txn replacement = transaction(em, REPLACEMENT_TRANSACTION_UUID);
                assertEquals("REVERSED", original.getStatus());
                assertEquals(original.getId(), reversal.getReversalOf().getId());
                assertEquals(original.getId(), replacement.getReplacementFor().getId());
                assertEquals(3L, count(em, "select count(t) from Txn t"));
                assertEquals(26L, count(em,
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
                assertEquals(1L, count(em,
                        "select count(a) from AuditEvent a where a.actionType = 'SCLX_IMPORTED'"));
            }

            Path roundTrip = tempDir.resolve("corrections-round-trip.sclx");
            new SclxFileExportService(
                    new SclxCoreSnapshotQueryService(jpa, () -> TARGET),
                    () -> tempDir.resolve("active-target.mv.db"))
                    .export(new SclxExportRequest(roundTrip, Instant.parse("2026-08-02T12:00:00Z"), false));
            JsonNode exported = new ObjectMapper().readTree(roundTrip.toFile());
            assertEquals(3, exported.path("transactions").size());
            String targetOriginal = SclxPortableIdentity.transaction(TARGET, TRANSACTION_UUID.toString());
            String targetReversal = SclxPortableIdentity.transaction(
                    TARGET, REVERSAL_TRANSACTION_UUID.toString());
            String targetReplacement = SclxPortableIdentity.transaction(
                    TARGET, REPLACEMENT_TRANSACTION_UUID.toString());
            assertCorrection(exported, targetReversal, "REVERSAL", targetOriginal);
            assertCorrection(exported, targetReplacement, "REPLACEMENT", targetOriginal);
            assertEquals("REVERSED", transaction(exported, targetOriginal).path("status").textValue());
            assertTrue(exported.path("budgets").size() > 0);
            assertTrue(exported.path("extensions").path("scaJakartaH2").path("fixedAssets").size() > 0);
            assertTrue(exported.path("extensions").path("scaJakartaH2").path("inventory")
                    .path("items").size() > 0);

            SclxImportPreview secondPreview = previews.preview(source);
            assertFalse(secondPreview.hasBlockingErrors(), () -> secondPreview.operation().messages().toString());
            assertEquals(26L, secondPreview.operation().counts().identical());
            SclxImportResult second = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, secondPreview, "tester");
            assertTrue(second.committed());
            assertEquals(0L, second.counts().created());
            try (EntityManager em = jpa.em())
            {
                assertEquals(3L, count(em, "select count(t) from Txn t"));
                assertEquals(1L, count(em,
                        "select count(a) from AuditEvent a where a.actionType = 'SCLX_IMPORTED'"));
            }
        }
    }

    @Test
    void malformedCorrectionIsRejectedBeforeMutation(@TempDir Path tempDir) throws Exception
    {
        Path source = writeCorrectionSource(tempDir.resolve("corrections-invalid.sclx"));
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode malformed = (ObjectNode) mapper.readTree(source.toFile());
        boolean changed = false;
        for (JsonNode transaction : malformed.path("transactions"))
        {
            if (transaction.hasNonNull("correctionOfTransactionId"))
            {
                ((ObjectNode) transaction).put(
                        "correctionOfTransactionId", "transaction:SOURCE:missing");
                changed = true;
                break;
            }
        }
        assertTrue(changed, "correction fixture must contain a relationship to invalidate");
        mapper.writerWithDefaultPrettyPrinter().writeValue(source.toFile(), malformed);

        try (Jpa jpa = new Jpa(tempDir.resolve("corrections-invalid")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new SclxImportCommitService(jpa, () -> TARGET)
                            .commit(source, preview, "tester"));

            assertTrue(failure.getMessage().contains("does not resolve"));
            try (EntityManager em = jpa.em())
            {
                assertEquals("Empty Target", company(em).getDisplayName());
                assertEquals(0L, count(em, "select count(t) from Txn t"));
                assertEquals(0L, count(em, "select count(i) from InterchangeIdentity i"));
            }
        }
    }

    @Test
    void failureAfterCorrectionWriteRollsBackCompleteGraph(@TempDir Path tempDir) throws Exception
    {
        Path source = writeCorrectionSource(tempDir.resolve("corrections-rollback.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("corrections-rollback")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            SclxImportCommitService service = new SclxImportCommitService(
                    jpa, () -> TARGET, writes -> {
                        if (writes == 17)
                        {
                            throw new IllegalStateException("injected after correction write");
                        }
                    });

            SclxImportResult result = service.commit(source, preview, "tester");

            assertTrue(result.rolledBack());
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, count(em, "select count(t) from Txn t"));
                assertEquals(0L, count(em, "select count(a) from AuditEvent a"));
                assertEquals(0L, count(em, "select count(i) from InterchangeIdentity i"));
            }
        }
    }

    @Test
    void importsFactualAuditHistoryAtomicallyAndReimportIsIdempotent(@TempDir Path tempDir) throws Exception
    {
        Path source = writeAuditHistorySource(tempDir.resolve("audit-history.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("audit-history")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreviewService previews = new SclxImportPreviewService(jpa, () -> TARGET);
            SclxImportPreview preview = previews.preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertEquals(21L, preview.operation().counts().created());

            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, preview, "tester");

            assertTrue(result.committed(), () -> result.messages().toString());
            assertEquals(21L, result.counts().created());
            try (EntityManager em = jpa.em())
            {
                AuditEvent imported = em.createQuery(
                                "from AuditEvent a where a.portableId = :portableId", AuditEvent.class)
                        .setParameter("portableId", AUDIT_EVENT_UUID)
                        .getSingleResult();
                assertEquals(TARGET, imported.getCompany().getCode());
                assertEquals(Instant.parse("2026-06-15T14:30:00Z"), imported.getOccurredAt());
                assertEquals("source-treasurer", imported.getActor());
                assertEquals("TRANSACTION_UPDATED", imported.getActionType());
                assertEquals("Transaction", imported.getEntityType());
                assertEquals("17", imported.getEntityId());
                assertEquals("Corrected source transaction", imported.getSummary());
                assertEquals("old memo", imported.getBeforeValue());
                assertEquals("new memo", imported.getAfterValue());
                assertEquals("source correction", imported.getReason());
                assertEquals(21L, count(em,
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
                assertEquals(1L, count(em,
                        "select count(a) from AuditEvent a "
                                + "where a.actionType = 'SCLX_IMPORTED'"));
            }

            SclxImportPreview secondPreview = previews.preview(source);
            assertFalse(secondPreview.hasBlockingErrors(), () -> secondPreview.operation().messages().toString());
            assertEquals(21L, secondPreview.operation().counts().identical());
            SclxImportResult second = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, secondPreview, "tester");
            assertTrue(second.committed());
            assertEquals(0L, second.counts().created());
            try (EntityManager em = jpa.em())
            {
                assertEquals(1L, em.createQuery(
                                "select count(a) from AuditEvent a where a.portableId = :portableId", Long.class)
                        .setParameter("portableId", AUDIT_EVENT_UUID)
                        .getSingleResult());
                assertEquals(1L, count(em,
                        "select count(a) from AuditEvent a "
                                + "where a.actionType = 'SCLX_IMPORTED'"));
            }
        }
    }

    @Test
    void failureAfterAuditHistoryWriteRollsBackCompleteGraph(@TempDir Path tempDir) throws Exception
    {
        Path source = writeAuditHistorySource(tempDir.resolve("audit-history-rollback.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("audit-history-rollback")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            SclxImportCommitService service = new SclxImportCommitService(
                    jpa, () -> TARGET, writes -> {
                        if (writes == 19)
                        {
                            throw new IllegalStateException("injected after audit-history write");
                        }
                    });

            SclxImportResult result = service.commit(source, preview, "tester");

            assertTrue(result.rolledBack());
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, count(em, "select count(a) from AuditEvent a"));
                assertEquals(0L, count(em, "select count(t) from Txn t"));
                assertEquals(0L, count(em,
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
            }
        }
    }

    @Test
    void rejectsMalformedAuditHistoryBeforeMutation(@TempDir Path tempDir) throws Exception
    {
        Path source = writeAuditHistorySource(tempDir.resolve("audit-history-malformed.sclx"));
        String json = Files.readString(source);
        String marker = "\"actor\": \"source-treasurer\"";
        assertTrue(json.contains(marker), "audit-history fixture actor marker must exist");
        Files.writeString(source, json.replace(marker, "\"actor\": \"" + "a".repeat(201) + "\""));

        try (Jpa jpa = new Jpa(tempDir.resolve("audit-history-malformed")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new SclxImportCommitService(jpa, () -> TARGET)
                            .commit(source, preview, "tester"));

            assertTrue(failure.getMessage().contains("actor exceeds 200"));
            try (EntityManager em = jpa.em())
            {
                assertEquals("Empty Target", company(em).getDisplayName());
                assertEquals(0L, count(em, "select count(a) from AuditEvent a"));
                assertEquals(0L, count(em, "select count(a) from Account a"));
            }
        }
    }

    @Test
    void previewAllowsTargetContainingOnlyAuditHistory(@TempDir Path tempDir) throws Exception
    {
        Path source = writeSource(tempDir.resolve("audit-only-target.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("audit-only-target")))
        {
            seedEmptyTarget(jpa);
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                AuditEvent existing = new AuditEvent();
                existing.setCompany(company(em));
                existing.setActor("tester");
                existing.setActionType("EXISTING_FACT");
                existing.setEntityType("Company");
                existing.setSummary("Existing factual audit history");
                em.persist(existing);
                em.getTransaction().commit();
            }

            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);

            assertTrue(preview.targetPopulated());
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertFalse(preview.operation().messages().stream()
                    .anyMatch(message -> message.code().equals(
                            "SCLX_OPERATIONAL_DATA_MERGE_UNSUPPORTED")));

            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, preview, "tester", false, true);
            assertTrue(result.committed(), () -> result.messages().toString());
            try (EntityManager em = jpa.em())
            {
                assertEquals(1L, count(em, "select count(c) from BudgetCategory c"));
                assertEquals(1L, count(em, "select count(t) from Txn t"));
                assertEquals(1L, count(em,
                        "select count(a) from AuditEvent a where a.actionType = 'EXISTING_FACT'"));
                assertEquals(1L, count(em,
                        "select count(a) from AuditEvent a where a.actionType = 'SCLX_IMPORTED'"));
            }
        }
    }

    @Test
    void initializesMissingChartForPopulatedTarget(@TempDir Path tempDir) throws Exception
    {
        Path source = writeSource(tempDir.resolve("populated-target-without-chart.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("populated-target-without-chart")))
        {
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company target = new Company();
                target.setCode(TARGET);
                target.setDisplayName("Target Without Chart");
                target.setDefaultCurrency("USD");
                em.persist(target);

                AuditEvent existing = new AuditEvent();
                existing.setCompany(target);
                existing.setActor("tester");
                existing.setActionType("EXISTING_FACT");
                existing.setEntityType("Company");
                existing.setSummary("Existing factual audit history");
                em.persist(existing);
                em.getTransaction().commit();
            }

            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            assertTrue(preview.targetPopulated());
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());

            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, preview, "tester", false, true);

            assertTrue(result.committed(), () -> result.messages().toString());
            try (EntityManager em = jpa.em())
            {
                Company target = company(em);
                assertEquals("Target Without Chart", target.getDisplayName());
                assertEquals("Portable Chart", target.getActiveChartOfAccounts().getName());
                assertEquals("2026", target.getActiveChartOfAccounts().getVersion());
                assertEquals(5L, count(em, "select count(a) from Account a"));
                assertEquals(1L, count(em, "select count(t) from Txn t"));
            }
        }
    }

    @Test
    void importsByReusingCompatibleActivityAssignedToCurrentTarget(@TempDir Path tempDir) throws Exception
    {
        Path source = writeSource(tempDir.resolve("assigned-activity-target.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("assigned-activity-target")))
        {
            seedEmptyTarget(jpa);
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Activity activity = new Activity();
                activity.setCompany(company(em));
                activity.setCode("EVENT");
                activity.setName("Portable Event");
                activity.setActive(true);
                em.persist(activity);
                em.getTransaction().commit();
            }

            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);

            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertTrue(preview.operation().messages().stream().anyMatch(message ->
                    message.code().equals("SCLX_TARGET_ACTIVITY_REUSED")));

            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, preview, "tester", true, true);

            assertTrue(result.committed(), () -> result.messages().toString());
            try (EntityManager em = jpa.em())
            {
                assertEquals(1L, count(em, "select count(a) from Activity a"));
                assertEquals(1L, count(em,
                        "select count(i) from InterchangeIdentity i where i.entityType = 'ACTIVITY'"));
                AuditEvent audit = em.createQuery(
                                "from AuditEvent a where a.actionType = 'SCLX_IMPORTED'", AuditEvent.class)
                        .getSingleResult();
                assertTrue(audit.getAfterValue().contains("targetReuses=ACTIVITY:"));
            }
        }
    }

    @Test
    void appliesPerRecordSourceWinnerForConflictingActivity(@TempDir Path tempDir) throws Exception
    {
        Path source = writeSource(tempDir.resolve("activity-source-winner.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("activity-source-winner")))
        {
            seedEmptyTarget(jpa);
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Activity activity = new Activity();
                activity.setCompany(company(em));
                activity.setCode("EVENT");
                activity.setName("Target activity name");
                activity.setActive(false);
                em.persist(activity);
                em.getTransaction().commit();
            }

            SclxImportPreview initial = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            SclxImportEntityPreview activity = initial.operation().items().stream()
                    .filter(item -> item.entityType().equals("ACTIVITY"))
                    .findFirst().orElseThrow();
            assertTrue(initial.hasBlockingErrors());

            SclxImportPreview resolved = new SclxImportPreviewService(jpa, () -> TARGET).preview(
                    source,
                    List.of(),
                    List.of(new SclxImportConflictSelection(
                            activity.entityType(), activity.externalId(),
                            SclxImportConflictChoice.TAKE_SOURCE)));
            assertFalse(resolved.hasBlockingErrors(), () -> resolved.operation().messages().toString());

            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, resolved, "tester", true, true);
            assertTrue(result.committed(), () -> result.messages().toString());
            try (EntityManager em = jpa.em())
            {
                Activity imported = em.createQuery("from Activity a", Activity.class).getSingleResult();
                assertEquals("Portable Event", imported.getName());
                assertTrue(imported.isActive());
                assertEquals(1L, count(em,
                        "select count(i) from InterchangeIdentity i where i.entityType = 'ACTIVITY'"));
            }
        }
    }

    @Test
    void importsIntoExistingCompatibleChartAndFundsWithoutReplacingTargetSettings(@TempDir Path tempDir)
            throws Exception
    {
        Path source = writeSource(tempDir.resolve("existing-company.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("existing-company")))
        {
            seedEmptyTarget(jpa);
            seedExistingChartAndFund(jpa);
            SclxImportPreviewService previews = new SclxImportPreviewService(jpa, () -> TARGET);
            SclxImportPreview preview = previews.preview(source);

            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertTrue(preview.targetPopulated());
            assertEquals(SclxAccountMode.MAPPED, preview.recommendedAccountMode());
            assertEquals(6L, preview.mappings().stream()
                    .filter(mapping -> mapping.resolution()
                            == SclxImportMappingRequirement.Resolution.MAPPED)
                    .count());
            IllegalStateException approval = assertThrows(IllegalStateException.class,
                    () -> new SclxImportCommitService(jpa, () -> TARGET)
                            .commit(source, preview, "tester"));
            assertTrue(approval.getMessage().contains("Approve the displayed"));

            IllegalStateException existingApproval = assertThrows(IllegalStateException.class,
                    () -> new SclxImportCommitService(jpa, () -> TARGET)
                            .commit(source, preview, "tester", true));
            assertTrue(existingApproval.getMessage().contains("existing company"));

            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, preview, "tester", true, true);

            assertTrue(result.committed(), () -> result.messages().toString());
            assertEquals(preview.sectionCounts().totalEntities() - 7L, result.counts().created());
            try (EntityManager em = jpa.em())
            {
                Company target = company(em);
                assertEquals("Empty Target", target.getDisplayName());
                assertEquals("USD", target.getDefaultCurrency());
                assertEquals("Empty Chart", target.getActiveChartOfAccounts().getName());
                assertEquals("EMPTY", target.getActiveChartOfAccounts().getVersion());
                assertEquals(5L, count(em, "select count(a) from Account a"));
                assertEquals(1L, count(em, "select count(f) from Fund f"));
                assertEquals(1L, count(em, "select count(t) from Txn t"));
                assertEquals(1L, count(em, "select count(a) from AuditEvent a where a.actionType = 'SCLX_IMPORTED'"));
                AuditEvent importAudit = em.createQuery(
                                "from AuditEvent a where a.actionType = 'SCLX_IMPORTED'", AuditEvent.class)
                        .getSingleResult();
                assertTrue(importAudit.getAfterValue().contains("mapped=6"));
                assertTrue(importAudit.getAfterValue().contains("ACCOUNT:1000->1000(MAPPED)"));
                assertEquals(preview.sectionCounts().totalEntities(), count(em,
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
            }
            List<TransactionView> journalTransactions = new TransactionEntryService(jpa, () -> TARGET)
                    .search(null, null, null, 500);
            assertEquals(1, journalTransactions.size());
            assertEquals("Purchase supplies", journalTransactions.get(0).memo());

            SclxImportPreview secondPreview = previews.preview(source);
            assertFalse(secondPreview.hasBlockingErrors(),
                    () -> secondPreview.operation().messages().toString());
            assertEquals(secondPreview.sectionCounts().totalEntities(),
                    secondPreview.operation().counts().identical());
            assertEquals(SclxAccountMode.AS_IS, secondPreview.recommendedAccountMode());
            SclxImportResult second = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, secondPreview, "tester");
            assertTrue(second.committed());
            assertEquals(0L, second.counts().created());
        }
    }

    @Test
    void importsPeriodCloseFactsAtomicallyAndReimportIsIdempotent(@TempDir Path tempDir) throws Exception
    {
        Path source = writePeriodCloseSource(tempDir.resolve("period-close.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("period-close")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreviewService previews = new SclxImportPreviewService(jpa, () -> TARGET);
            SclxImportPreview preview = previews.preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());

            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, preview, "tester");

            assertTrue(result.committed(), () -> result.messages().toString());
            assertEquals(23L, result.counts().created());
            PeriodCloseRangeService closeService = new PeriodCloseRangeService(jpa);
            PeriodCloseRangeView range = closeService.listRanges(TARGET).get(0);
            assertEquals(PERIOD_CLOSE_RANGE_UUID, range.id());
            assertEquals(LocalDate.of(2026, 1, 1), range.startDate());
            assertEquals(LocalDate.of(2026, 3, 31), range.endDate());
            assertEquals("REOPENED", range.status());
            assertEquals(Instant.parse("2026-04-10T12:00:00Z"), range.reopenedAt());
            assertEquals("closer", range.closedBy());
            assertEquals("reopener", range.reopenedBy());
            java.util.List<PeriodCloseEventView> events = closeService.listEvents(TARGET);
            assertEquals(2, events.size());
            assertTrue(events.stream().anyMatch(event -> event.id().equals(PERIOD_CLOSE_EVENT_UUID)
                    && event.eventType().equals("CLOSED")));
            assertTrue(events.stream().anyMatch(event -> event.id().equals(PERIOD_REOPEN_EVENT_UUID)
                    && event.eventType().equals("REOPENED")));
            try (EntityManager em = jpa.em())
            {
                assertEquals(23L, count(em,
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
                assertEquals(1L, count(em,
                        "select count(a) from AuditEvent a where a.actionType = 'SCLX_IMPORTED'"));
            }

            SclxImportPreview secondPreview = previews.preview(source);
            assertEquals(23L, secondPreview.operation().counts().identical());
            SclxImportResult second = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, secondPreview, "tester");
            assertTrue(second.committed());
            assertEquals(0L, second.counts().created());
            assertEquals(1, closeService.listRanges(TARGET).size());
            assertEquals(2, closeService.listEvents(TARGET).size());
        }
    }

    @Test
    void failureAfterPeriodCloseWriteRollsBackCompleteGraph(@TempDir Path tempDir) throws Exception
    {
        Path source = writePeriodCloseSource(tempDir.resolve("period-close-rollback.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("period-close-rollback")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            SclxImportCommitService service = new SclxImportCommitService(
                    jpa, () -> TARGET, writes -> {
                        if (writes == 20)
                        {
                            throw new IllegalStateException("injected after period-close write");
                        }
                    });

            SclxImportResult result = service.commit(source, preview, "tester");

            assertTrue(result.rolledBack());
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, nativeCount(em, "period_close_range"));
                assertEquals(0L, nativeCount(em, "period_close_event"));
                assertEquals(0L, count(em, "select count(t) from Txn t"));
                assertEquals(0L, count(em,
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
            }
        }
    }

    @Test
    void rejectsMismatchedPeriodCloseEventBeforeMutation(@TempDir Path tempDir) throws Exception
    {
        Path source = writePeriodCloseSource(tempDir.resolve("period-close-mismatch.sclx"));
        String json = Files.readString(source);
        String marker = "\"actor\": \"reopener\"";
        int eventActor = json.lastIndexOf(marker);
        assertTrue(eventActor >= 0, "period-close event fixture marker must exist");
        Files.writeString(source, json.substring(0, eventActor)
                + "\"actor\": \"different-actor\""
                + json.substring(eventActor + marker.length()));

        try (Jpa jpa = new Jpa(tempDir.resolve("period-close-mismatch")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new SclxImportCommitService(jpa, () -> TARGET)
                            .commit(source, preview, "tester"));

            assertTrue(failure.getMessage().contains("does not match range facts"));
            try (EntityManager em = jpa.em())
            {
                assertEquals("Empty Target", company(em).getDisplayName());
                assertEquals(0L, nativeCount(em, "period_close_range"));
                assertEquals(0L, count(em, "select count(a) from Account a"));
            }
        }
    }

    @Test
    void importsBankingAndReconciliationFactsAtomically(@TempDir Path tempDir) throws Exception
    {
        Path source = writeBankingSource(tempDir.resolve("banking.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("banking")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreviewService previews = new SclxImportPreviewService(jpa, () -> TARGET);
            SclxImportPreview preview = previews.preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());

            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, preview, "tester");

            assertTrue(result.committed(), () -> result.messages().toString());
            assertEquals(27L, result.counts().created());
            try (EntityManager em = jpa.em())
            {
                Bank bank = em.createQuery("from Bank b", Bank.class).getSingleResult();
                assertEquals(BANK_UUID, bank.getPortableId());
                assertEquals("Portable Credit Union", bank.getName());
                CompanyBankAccount bankAccount = em.createQuery(
                        "from CompanyBankAccount a", CompanyBankAccount.class).getSingleResult();
                assertEquals(BANK_ACCOUNT_UUID, bankAccount.getPortableId());
                assertEquals("1000", bankAccount.getAccount().getCode());
                BankImportBatch batch = em.createQuery(
                        "from BankImportBatch b", BankImportBatch.class).getSingleResult();
                assertEquals(IMPORT_BATCH_UUID, batch.getPortableId());
                assertEquals(Instant.parse("2026-07-20T10:00:00Z"), batch.getImportedAt());
                BankStatementLine statementLine = em.createQuery(
                        "from BankStatementLine l", BankStatementLine.class).getSingleResult();
                assertEquals(STATEMENT_LINE_UUID, statementLine.getPortableId());
                assertEquals(BankStatementLine.Status.MATCHED, statementLine.getStatus());
                ImportIssue issue = em.createQuery("from ImportIssue i", ImportIssue.class).getSingleResult();
                assertEquals(IMPORT_ISSUE_UUID, issue.getPortableId());
                TxnSplit bankLine = em.createQuery(
                        "from TxnSplit s where s.account.code = '1000'", TxnSplit.class).getSingleResult();
                assertTrue(bankLine.isBankCleared());
                assertEquals(statementLine.getId(), bankLine.getMatchedBankStatementLine().getId());
                assertEquals(1L, nativeCount(em, "bank_reconciliation_session"));
                assertEquals(1L, nativeCount(em, "bank_reconciliation_match"));
                assertEquals(27L, count(em,
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
            }

            SclxImportPreview secondPreview = previews.preview(source);
            assertEquals(27L, secondPreview.operation().counts().identical());
            SclxImportResult second = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, secondPreview, "tester");
            assertTrue(second.committed());
            assertEquals(0L, second.counts().created());
        }
    }

    @Test
    void failureAfterReconciliationWriteRollsBackBankingGraph(@TempDir Path tempDir) throws Exception
    {
        Path source = writeBankingSource(tempDir.resolve("banking-rollback.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("banking-rollback")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            SclxImportCommitService service = new SclxImportCommitService(
                    jpa, () -> TARGET, writes -> {
                        if (writes == 23)
                        {
                            throw new IllegalStateException("injected after reconciliation write");
                        }
                    });

            SclxImportResult result = service.commit(source, preview, "tester");

            assertTrue(result.rolledBack());
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, count(em, "select count(b) from Bank b"));
                assertEquals(0L, count(em, "select count(a) from CompanyBankAccount a"));
                assertEquals(0L, count(em, "select count(b) from BankImportBatch b"));
                assertEquals(0L, count(em, "select count(l) from BankStatementLine l"));
                assertEquals(0L, count(em, "select count(i) from ImportIssue i"));
                assertEquals(0L, nativeCount(em, "bank_reconciliation_session"));
                assertEquals(0L, nativeCount(em, "bank_reconciliation_match"));
                assertEquals(0L, count(em,
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
            }
        }
    }

    @Test
    void rejectsUnresolvedReconciliationTransactionLineBeforeMutation(@TempDir Path tempDir) throws Exception
    {
        Path source = writeBankingSource(tempDir.resolve("banking-unresolved-line.sclx"));
        String transaction = SclxPortableIdentity.transaction("SOURCE", TRANSACTION_UUID.toString());
        String bankLine = SclxPortableIdentity.transactionLine(transaction, 2);
        String marker = "\"lineId\": \"" + bankLine + "\"";
        String json = Files.readString(source);
        int markerIndex = json.lastIndexOf(marker);
        assertTrue(markerIndex >= 0, "reconciliation transaction-line fixture marker must exist");
        Files.writeString(source, json.substring(0, markerIndex)
                + "\"lineId\": \"sclx:transaction-line:missing\""
                + json.substring(markerIndex + marker.length()));

        try (Jpa jpa = new Jpa(tempDir.resolve("banking-unresolved-line")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new SclxImportCommitService(jpa, () -> TARGET)
                            .commit(source, preview, "tester"));

            assertTrue(failure.getMessage().contains("reconciliation.matches[].lineId does not resolve"));
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, count(em, "select count(b) from Bank b"));
                assertEquals(0L, count(em,
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
            }
        }
    }

    @Test
    void commitsCoreGraphAtomicallyAndReimportIsIdempotent(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("core-import");
        Path source = writeSource(tempDir.resolve("core.sclx"));
        try (Jpa jpa = new Jpa(database))
        {
            seedEmptyTarget(jpa);
            SclxImportPreviewService previews = new SclxImportPreviewService(jpa, () -> TARGET);
            SclxImportCommitService service = new SclxImportCommitService(jpa, () -> TARGET);

            SclxImportPreview firstPreview = previews.preview(source);
            assertFalse(firstPreview.hasBlockingErrors(), () -> firstPreview.operation().messages().toString());
            SclxImportResult first = service.commit(source, firstPreview, "tester");

            assertTrue(first.committed());
            assertFalse(first.rolledBack());
            assertEquals(20L, first.counts().created());
            try (EntityManager em = jpa.em())
            {
                Company company = company(em);
                assertEquals("Portable Source Company", company.getDisplayName());
                assertEquals("CAD", company.getDefaultCurrency());
                assertEquals(4, company.getFiscalYearStartMonth());
                assertEquals(1, company.getFiscalYearStartDay());
                assertEquals("Portable Chart", company.getActiveChartOfAccounts().getName());
                assertEquals(5L, count(em, "select count(a) from Account a"));
                assertEquals(1L, count(em, "select count(f) from Fund f"));
                assertEquals(1L, count(em, "select count(c) from BudgetCategory c"));
                assertEquals(1L, count(em, "select count(p) from BudgetPlan p"));
                assertEquals(1L, count(em, "select count(l) from BudgetLine l"));
                assertEquals(1L, count(em, "select count(t) from Txn t"));
                assertEquals(2L, count(em, "select count(s) from TxnSplit s"));
                assertEquals(1L, count(em, "select count(a) from Activity a"));
                assertEquals(1L, count(em, "select count(c) from Counterparty c"));
                assertEquals(1L, count(em, "select count(m) from Merchant m"));
                assertEquals(1L, count(em, "select count(s) from TxnSupplementalLine s"));
                assertEquals(20L, count(em, "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
                BudgetPlan budget = em.createQuery("from BudgetPlan p", BudgetPlan.class).getSingleResult();
                assertEquals("FY2026 Approved", budget.getName());
                assertEquals(2026, budget.getFiscalYear());
                assertEquals("APPROVED", budget.getVersionCode());
                assertEquals(BudgetPlan.Status.ACTIVE, budget.getStatus());
                BudgetLine budgetLine = em.createQuery("from BudgetLine l", BudgetLine.class).getSingleResult();
                assertEquals("PROGRAM", budgetLine.getBudgetCategory().getCode());
                assertEquals("PROGRAM", budgetLine.getBudgetCategory().getName());
                assertEquals("GENERAL", budgetLine.getFund().getCode());
                assertEquals(YearMonth.of(2026, 7), budgetLine.getPeriodMonth());
                assertEquals(new BigDecimal("125.0000"), budgetLine.getAmount());
                Txn transaction = em.createQuery("from Txn t", Txn.class).getSingleResult();
                assertEquals(TRANSACTION_UUID, transaction.getPortableId());
                assertEquals("Portable Payee", transaction.getPayee().getDisplayName());
                TxnSplit enrichedLine = em.createQuery(
                                "from TxnSplit s where s.merchant is not null", TxnSplit.class)
                        .getSingleResult();
                assertEquals("EVENT", enrichedLine.getActivity().getCode());
                assertEquals("Portable Merchant", enrichedLine.getMerchant().getName());
                TxnSupplementalLine supplemental = em.createQuery(
                                "from TxnSupplementalLine s", TxnSupplementalLine.class)
                        .getSingleResult();
                assertEquals(7, supplemental.getLineOrder());
                assertEquals("PAYABLE", supplemental.getKind());
                FixedAsset fixedAsset = em.createQuery("from FixedAsset a", FixedAsset.class).getSingleResult();
                assertEquals(FIXED_ASSET_UUID, fixedAsset.getPortableId());
                assertEquals("Portable Equipment", fixedAsset.getName());
                assertEquals(Instant.parse("2026-01-15T10:00:00Z"), fixedAsset.getCreatedAt());
                assertEquals(Instant.parse("2026-07-31T11:00:00Z"), fixedAsset.getUpdatedAt());
                FixedAssetDepreciationRun depreciationRun = em.createQuery(
                                "from FixedAssetDepreciationRun r", FixedAssetDepreciationRun.class)
                        .getSingleResult();
                assertEquals(DEPRECIATION_RUN_UUID, depreciationRun.getPortableId());
                assertEquals(fixedAsset.getId(), depreciationRun.getFixedAsset().getId());
                assertEquals(transaction.getId(), depreciationRun.getTransaction().getId());
                assertEquals(new BigDecimal("25.0000"), depreciationRun.getDepreciationAmount());
                InventoryItem inventoryItem = em.createQuery(
                                "from InventoryItem i", InventoryItem.class)
                        .getSingleResult();
                assertEquals(INVENTORY_ITEM_UUID, inventoryItem.getPortableId());
                assertEquals("Portable Regalia", inventoryItem.getName());
                assertEquals(new BigDecimal("3.0000"), inventoryItem.getQuantity());
                assertEquals(Instant.parse("2026-02-01T10:00:00Z"), inventoryItem.getCreatedAt());
                assertEquals(Instant.parse("2026-07-31T11:30:00Z"), inventoryItem.getUpdatedAt());
                InventoryMovement inventoryMovement = em.createQuery(
                                "from InventoryMovement m", InventoryMovement.class)
                        .getSingleResult();
                assertEquals(INVENTORY_MOVEMENT_UUID, inventoryMovement.getPortableId());
                assertEquals(inventoryItem.getId(), inventoryMovement.getInventoryItem().getId());
                assertEquals(transaction.getId(), inventoryMovement.getTransaction().getId());
                assertEquals(new BigDecimal("3.0000"), inventoryMovement.getQuantityChange());
                assertEquals(new BigDecimal("3.0000"), inventoryMovement.getResultingQuantity());
                assertEquals(1L, count(em,
                        "select count(a) from AuditEvent a "
                                + "where a.actionType = 'SCLX_IMPORTED'"));
            }

            SclxImportPreview secondPreview = previews.preview(source);
            assertFalse(secondPreview.hasBlockingErrors(), () -> secondPreview.operation().messages().toString());
            assertEquals(20L, secondPreview.operation().counts().identical());
            SclxImportResult second = service.commit(source, secondPreview, "tester");

            assertTrue(second.committed());
            assertEquals(0L, second.counts().created());
            assertEquals(20L, second.counts().identical());
            try (EntityManager em = jpa.em())
            {
                assertEquals(1L, count(em, "select count(t) from Txn t"));
                assertEquals(2L, count(em, "select count(s) from TxnSplit s"));
                assertEquals(1L, count(em, "select count(a) from FixedAsset a"));
                assertEquals(1L, count(em, "select count(r) from FixedAssetDepreciationRun r"));
                assertEquals(1L, count(em, "select count(i) from InventoryItem i"));
                assertEquals(1L, count(em, "select count(m) from InventoryMovement m"));
                assertEquals(20L, count(em, "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
            }
        }
    }

    @Test
    void importsNewRecordWhileReusingIdenticalOperationalGraph(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("incremental-import");
        Path source = writeAuditHistorySource(tempDir.resolve("incremental.sclx"));
        try (Jpa jpa = new Jpa(database))
        {
            seedEmptyTarget(jpa);
            SclxImportPreviewService previews = new SclxImportPreviewService(jpa, () -> TARGET);
            SclxImportCommitService service = new SclxImportCommitService(jpa, () -> TARGET);
            SclxImportPreview firstPreview = previews.preview(source);
            assertTrue(service.commit(source, firstPreview, "tester").committed());

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = (ObjectNode) mapper.readTree(source.toFile());
            ArrayNode events = (ArrayNode) root.path("extensions").path("scaJakartaH2")
                    .path("auditHistory").path("events");
            ObjectNode additional = events.get(0).deepCopy();
            UUID additionalId = UUID.fromString("abcdef12-3456-7890-abcd-ef1234567890");
            additional.put("auditEventId", SclxPortableIdentity.auditEvent("SOURCE", additionalId.toString()));
            additional.put("summary", "Later portable audit fact");
            events.add(additional);
            mapper.writerWithDefaultPrettyPrinter().writeValue(source.toFile(), root);

            SclxImportPreview incremental = previews.preview(source);
            assertFalse(incremental.hasBlockingErrors(), () -> incremental.operation().messages().toString());
            assertEquals(21L, incremental.operation().counts().identical());
            assertEquals(1L, incremental.operation().counts().created());

            SclxImportResult result = service.commit(source, incremental, "tester", true, true);
            assertTrue(result.committed(), () -> result.messages().toString());
            assertEquals(1L, result.counts().created());
            try (EntityManager em = jpa.em())
            {
                assertEquals(1L, count(em, "select count(t) from Txn t"));
                assertEquals(1L, count(em, "select count(a) from FixedAsset a"));
                assertEquals(1L, count(em, "select count(i) from InventoryItem i"));
                assertEquals(1L, em.createQuery(
                                "select count(a) from AuditEvent a where a.portableId = :id", Long.class)
                        .setParameter("id", AUDIT_EVENT_UUID)
                        .getSingleResult());
                assertEquals(1L, em.createQuery(
                                "select count(a) from AuditEvent a where a.portableId = :id", Long.class)
                        .setParameter("id", additionalId)
                        .getSingleResult());
                assertEquals(22L, count(em,
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
            }
        }
    }

    @Test
    void lateFailureRollsBackProfileMastersTransactionsIdentitiesAndAudit(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("rollback");
        Path source = writeSource(tempDir.resolve("rollback.sclx"));
        try (Jpa jpa = new Jpa(database))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            SclxImportCommitService service = new SclxImportCommitService(
                    jpa,
                    () -> TARGET,
                    writes -> {
                        if (writes == 17)
                        {
                            throw new IllegalStateException("injected late failure");
                        }
                    });

            SclxImportResult result = service.commit(source, preview, "tester");

            assertFalse(result.committed());
            assertTrue(result.rolledBack());
            assertTrue(result.messages().stream()
                    .anyMatch(message -> message.code().equals("SCLX_COMMIT_ROLLED_BACK")));
            try (EntityManager em = jpa.em())
            {
                Company company = company(em);
                assertEquals("Empty Target", company.getDisplayName());
                assertEquals("USD", company.getDefaultCurrency());
                assertEquals("Empty Chart", company.getActiveChartOfAccounts().getName());
                assertEquals(0L, count(em, "select count(a) from Account a"));
                assertEquals(0L, count(em, "select count(f) from Fund f"));
                assertEquals(0L, count(em, "select count(c) from BudgetCategory c"));
                assertEquals(0L, count(em, "select count(p) from BudgetPlan p"));
                assertEquals(0L, count(em, "select count(l) from BudgetLine l"));
                assertEquals(0L, count(em, "select count(t) from Txn t"));
                assertEquals(0L, count(em, "select count(a) from Activity a"));
                assertEquals(0L, count(em, "select count(c) from Counterparty c"));
                assertEquals(0L, count(em, "select count(m) from Merchant m"));
                assertEquals(0L, count(em, "select count(s) from TxnSupplementalLine s"));
                assertEquals(0L, count(em, "select count(a) from FixedAsset a"));
                assertEquals(0L, count(em, "select count(r) from FixedAssetDepreciationRun r"));
                assertEquals(0L, count(em, "select count(i) from InventoryItem i"));
                assertEquals(0L, count(em, "select count(m) from InventoryMovement m"));
                assertEquals(0L, count(em, "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
                assertEquals(0L, count(em,
                        "select count(a) from AuditEvent a "
                                + "where a.actionType = 'SCLX_IMPORTED'"));
            }
        }
    }

    @Test
    void rejectsBudgetAccountRelationBeforeMutation(@TempDir Path tempDir) throws Exception
    {
        Path source = writeSource(tempDir.resolve("account-bearing-budget.sclx"));
        String accountId = SclxPortableIdentity.account("SOURCE", "6100");
        Files.writeString(source, Files.readString(source).replace(
                "\"categoryCode\": \"PROGRAM\",",
                "\"accountId\": \"" + accountId + "\",\n"
                        + "                          \"categoryCode\": \"PROGRAM\","));
        try (Jpa jpa = new Jpa(tempDir.resolve("account-bearing-budget")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            SclxImportCommitService service = new SclxImportCommitService(jpa, () -> TARGET);

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> service.commit(source, preview, "tester"));

            assertTrue(failure.getMessage().contains("cannot be preserved"));
            try (EntityManager em = jpa.em())
            {
                assertEquals("Empty Target", company(em).getDisplayName());
                assertEquals(0L, count(em, "select count(a) from Account a"));
                assertEquals(0L, count(em, "select count(p) from BudgetPlan p"));
                assertEquals(0L, count(em, "select count(i) from InterchangeIdentity i"));
            }
        }
    }

    @Test
    void previewAllowsTargetContainingUnrelatedBudgetCategory(@TempDir Path tempDir) throws Exception
    {
        Path source = writeSource(tempDir.resolve("budget-category-target.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-category-target")))
        {
            seedEmptyTarget(jpa);
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                BudgetCategory category = new BudgetCategory();
                category.setCompany(company(em));
                category.setCode("EXISTING");
                category.setName("Existing Category");
                em.persist(category);
                em.getTransaction().commit();
            }

            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);

            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertFalse(preview.operation().messages().stream()
                    .anyMatch(message -> message.code().equals(
                            "SCLX_OPERATIONAL_DATA_MERGE_UNSUPPORTED")));

            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, preview, "tester", false, true);
            assertTrue(result.committed(), () -> result.messages().toString());
            try (EntityManager em = jpa.em())
            {
                assertEquals(2L, count(em, "select count(c) from BudgetCategory c"));
                assertEquals(1L, count(em, "select count(t) from Txn t"));
            }
        }
    }

    @Test
    void previewAllowsTargetContainingUnrelatedPeriodCloseHistory(@TempDir Path tempDir) throws Exception
    {
        Path source = writeSource(tempDir.resolve("period-close-target.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("period-close-target")))
        {
            seedEmptyTarget(jpa);
            new PeriodCloseRangeService(jpa).closeRange(
                    TARGET,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31),
                    "CALCULATED",
                    "tester",
                    "Existing close history");

            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);

            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertFalse(preview.operation().messages().stream()
                    .anyMatch(message -> message.code().equals(
                            "SCLX_OPERATIONAL_DATA_MERGE_UNSUPPORTED")));
        }
    }

    @Test
    void rejectsInvalidFixedAssetBeforeMutation(@TempDir Path tempDir) throws Exception
    {
        Path source = writeSource(tempDir.resolve("invalid-fixed-asset.sclx"));
        Files.writeString(source, Files.readString(source).replace(
                "\"usefulLifeMonths\": 60,",
                "\"usefulLifeMonths\": 48,"));
        try (Jpa jpa = new Jpa(tempDir.resolve("invalid-fixed-asset")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> new SclxImportCommitService(jpa, () -> TARGET)
                            .commit(source, preview, "tester"));

            assertTrue(failure.getMessage().contains("usefulLifeMonths"));
            try (EntityManager em = jpa.em())
            {
                assertEquals("Empty Target", company(em).getDisplayName());
                assertEquals(0L, count(em, "select count(a) from Account a"));
                assertEquals(0L, count(em, "select count(a) from FixedAsset a"));
                assertEquals(0L, count(em, "select count(i) from InterchangeIdentity i"));
            }
        }
    }

    @Test
    void rejectsUnresolvedInventoryMovementBeforeMutation(@TempDir Path tempDir) throws Exception
    {
        Path source = writeSource(tempDir.resolve("invalid-inventory.sclx"));
        String itemId = SclxPortableIdentity.inventoryItem("SOURCE", INVENTORY_ITEM_UUID.toString());
        String original = Files.readString(source);
        String itemReference = "\"itemId\": \"" + itemId + "\"";
        int movementReference = original.lastIndexOf(itemReference);
        assertTrue(movementReference >= 0, "inventory movement fixture mutation must apply");
        String changed = original.substring(0, movementReference)
                + "\"itemId\": \"inventory-item:SOURCE:missing\""
                + original.substring(movementReference + itemReference.length());
        Files.writeString(source, changed);
        try (Jpa jpa = new Jpa(tempDir.resolve("invalid-inventory")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> new SclxImportCommitService(jpa, () -> TARGET)
                            .commit(source, preview, "tester"));

            assertTrue(failure.getMessage().contains("does not resolve"));
            try (EntityManager em = jpa.em())
            {
                assertEquals("Empty Target", company(em).getDisplayName());
                assertEquals(0L, count(em, "select count(a) from Account a"));
                assertEquals(0L, count(em, "select count(i) from InventoryItem i"));
                assertEquals(0L, count(em, "select count(i) from InterchangeIdentity i"));
            }
        }
    }

    private static void seedEmptyTarget(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode(TARGET);
            company.setDisplayName("Empty Target");
            company.setDefaultCurrency("USD");
            em.persist(company);

            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName("Empty Chart");
            chart.setVersion("EMPTY");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            company.setActiveChartOfAccounts(chart);
            em.getTransaction().commit();
        }
    }

    private static void seedExistingChartAndFund(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = company(em);
            ChartOfAccounts chart = company.getActiveChartOfAccounts();
            addAccount(em, chart, "1000", "Cash", AccountType.BANK,
                    AccountSubtype.CASH, NormalBalance.DEBIT);
            addAccount(em, chart, "1500", "Equipment", AccountType.ASSET,
                    AccountSubtype.FIXED_ASSET, NormalBalance.DEBIT);
            addAccount(em, chart, "1590", "Accumulated Depreciation", AccountType.ASSET,
                    AccountSubtype.FIXED_ASSET, NormalBalance.CREDIT);
            addAccount(em, chart, "1600", "Inventory", AccountType.ASSET,
                    AccountSubtype.INVENTORY, NormalBalance.DEBIT);
            addAccount(em, chart, "6100", "Supplies", AccountType.EXPENSE,
                    null, NormalBalance.DEBIT);
            Fund fund = new Fund();
            fund.setCompany(company);
            fund.setCode("GENERAL");
            fund.setName("Existing General Fund");
            fund.setFundType(FundType.UNRESTRICTED);
            fund.setActive(true);
            em.persist(fund);
            em.getTransaction().commit();
        }
    }

    private static void addAccount(
            EntityManager em,
            ChartOfAccounts chart,
            String code,
            String name,
            AccountType type,
            AccountSubtype subtype,
            NormalBalance balance)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setSubtype(subtype);
        account.setNormalBalance(balance);
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setPosting(true);
        account.setActive(true);
        em.persist(account);
    }

    private static Company company(EntityManager em)
    {
        return em.createQuery("""
                select c from Company c
                left join fetch c.activeChartOfAccounts
                where c.code = :code
                """, Company.class)
                .setParameter("code", TARGET)
                .getSingleResult();
    }

    private static long count(EntityManager em, String jpql)
    {
        return em.createQuery(jpql, Long.class).getSingleResult();
    }

    private static Txn transaction(EntityManager em, UUID portableId)
    {
        return em.createQuery(
                        "from Txn t where t.portableId = :portableId", Txn.class)
                .setParameter("portableId", portableId)
                .getSingleResult();
    }

    private static JsonNode transaction(JsonNode root, String transactionId)
    {
        for (JsonNode transaction : root.path("transactions"))
        {
            if (transactionId.equals(transaction.path("transactionId").textValue()))
            {
                return transaction;
            }
        }
        throw new AssertionError("Missing exported transaction " + transactionId);
    }

    private static void assertCorrection(
            JsonNode root,
            String transactionId,
            String correctionType,
            String correctedTransactionId)
    {
        JsonNode transaction = transaction(root, transactionId);
        assertEquals(correctionType, transaction.path("correctionType").textValue());
        assertEquals(correctedTransactionId, transaction.path("correctionOfTransactionId").textValue());
    }

    private static long nativeCount(EntityManager em, String table)
    {
        return ((Number) em.createNativeQuery("select count(*) from " + table)
                .getSingleResult()).longValue();
    }

    private static Path writeCorrectionSource(Path target) throws Exception
    {
        writeSource(target);
        String original = SclxPortableIdentity.transaction("SOURCE", TRANSACTION_UUID.toString());
        String reversal = SclxPortableIdentity.transaction(
                "SOURCE", REVERSAL_TRANSACTION_UUID.toString());
        String replacement = SclxPortableIdentity.transaction(
                "SOURCE", REPLACEMENT_TRANSACTION_UUID.toString());
        String cash = SclxPortableIdentity.account("SOURCE", "1000");
        String expense = SclxPortableIdentity.account("SOURCE", "6100");
        String fund = SclxPortableIdentity.fund("SOURCE", "GENERAL");
        String reversalDebit = SclxPortableIdentity.transactionLine(reversal, 1);
        String reversalCredit = SclxPortableIdentity.transactionLine(reversal, 2);
        String replacementDebit = SclxPortableIdentity.transactionLine(replacement, 1);
        String replacementCredit = SclxPortableIdentity.transactionLine(replacement, 2);
        String additionalTransactions = """
                [
                    {
                      "transactionId": "%s",
                      "transactionDate": "2026-07-16",
                      "description": "Reverse purchase supplies",
                      "status": "ENTERED",
                      "correctionType": "REVERSAL",
                      "correctionOfTransactionId": "%s",
                      "lines": [
                        {
                          "lineId": "%s",
                          "accountId": "%s",
                          "fundId": "%s",
                          "debit": "25.00",
                          "credit": "0"
                        },
                        {
                          "lineId": "%s",
                          "accountId": "%s",
                          "fundId": "%s",
                          "debit": "0",
                          "credit": "25.00"
                        }
                      ]
                    },
                    {
                      "transactionId": "%s",
                      "transactionDate": "2026-07-16",
                      "description": "Replacement purchase supplies",
                      "status": "ENTERED",
                      "correctionType": "REPLACEMENT",
                      "correctionOfTransactionId": "%s",
                      "lines": [
                        {
                          "lineId": "%s",
                          "accountId": "%s",
                          "fundId": "%s",
                          "debit": "30.00",
                          "credit": "0"
                        },
                        {
                          "lineId": "%s",
                          "accountId": "%s",
                          "fundId": "%s",
                          "debit": "0",
                          "credit": "30.00"
                        }
                      ]
                    }
                  ]
                """.formatted(
                reversal, original,
                reversalDebit, cash, fund,
                reversalCredit, expense, fund,
                replacement, original,
                replacementDebit, expense, fund,
                replacementCredit, cash, fund);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(target.toFile());
        ArrayNode transactions = (ArrayNode) root.path("transactions");
        ((ObjectNode) transactions.get(0)).put("status", "REVERSED");
        JsonNode additions = mapper.readTree(additionalTransactions);
        additions.forEach(transactions::add);
        mapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), root);
        return target;
    }

    private static Path writePeriodCloseSource(Path target) throws Exception
    {
        writeSource(target);
        String range = SclxPortableIdentity.periodCloseRange(
                "SOURCE", PERIOD_CLOSE_RANGE_UUID.toString());
        String closedEvent = SclxPortableIdentity.periodCloseEvent(
                "SOURCE", PERIOD_CLOSE_EVENT_UUID.toString());
        String reopenedEvent = SclxPortableIdentity.periodCloseEvent(
                "SOURCE", PERIOD_REOPEN_EVENT_UUID.toString());
        String periodClose = """
                      "periodClose": {
                        "version": 1,
                        "ranges": [
                          {
                            "rangeId": "%s",
                            "startDate": "2026-01-01",
                            "endDate": "2026-03-31",
                            "rangeKind": "CALCULATED",
                            "status": "REOPENED",
                            "closedAt": "2026-04-05T10:00:00Z",
                            "closedBy": "closer",
                            "closeReason": "Quarter complete",
                            "reopenedAt": "2026-04-10T12:00:00Z",
                            "reopenedBy": "reopener",
                            "reopenReason": "Correction required"
                          }
                        ],
                        "events": [
                          {
                            "eventId": "%s",
                            "rangeId": "%s",
                            "eventType": "CLOSED",
                            "actor": "closer",
                            "reason": "Quarter complete",
                            "eventAt": "2026-04-05T10:00:00Z"
                          },
                          {
                            "eventId": "%s",
                            "rangeId": "%s",
                            "eventType": "REOPENED",
                            "actor": "reopener",
                            "reason": "Correction required",
                            "eventAt": "2026-04-10T12:00:00Z"
                          }
                        ]
                      },
                    """.formatted(range, closedEvent, range, reopenedEvent, range);
        String source = Files.readString(target);
        String marker = "\"fixedAssets\": {";
        assertTrue(source.contains(marker), "period-close fixture insertion marker must exist");
        Files.writeString(target, source.replace(marker, periodClose + marker));
        return target;
    }

    private static Path writeAuditHistorySource(Path target) throws Exception
    {
        writeSource(target);
        String auditEvent = SclxPortableIdentity.auditEvent("SOURCE", AUDIT_EVENT_UUID.toString());
        String auditHistory = """
                      "auditHistory": {
                        "version": 1,
                        "events": [
                          {
                            "auditEventId": "%s",
                            "occurredAt": "2026-06-15T14:30:00Z",
                            "actor": "source-treasurer",
                            "actionType": "TRANSACTION_UPDATED",
                            "entityType": "Transaction",
                            "entityId": "17",
                            "summary": "Corrected source transaction",
                            "beforeValue": "old memo",
                            "afterValue": "new memo",
                            "reason": "source correction"
                          }
                        ]
                      },
                    """.formatted(auditEvent);
        String source = Files.readString(target);
        String marker = "\"fixedAssets\": {";
        assertTrue(source.contains(marker), "audit-history fixture insertion marker must exist");
        Files.writeString(target, source.replace(marker, auditHistory + marker));
        return target;
    }

    private static Path writeBankingSource(Path target) throws Exception
    {
        writeSource(target);
        String cash = SclxPortableIdentity.account("SOURCE", "1000");
        String transaction = SclxPortableIdentity.transaction("SOURCE", TRANSACTION_UUID.toString());
        String bankLine = SclxPortableIdentity.transactionLine(transaction, 2);
        String bank = SclxPortableIdentity.bank("SOURCE", BANK_UUID.toString());
        String bankAccount = SclxPortableIdentity.bankAccount("SOURCE", BANK_ACCOUNT_UUID.toString());
        String batch = SclxPortableIdentity.bankImportBatch("SOURCE", IMPORT_BATCH_UUID.toString());
        String statementLine = SclxPortableIdentity.bankStatementLine("SOURCE", STATEMENT_LINE_UUID.toString());
        String issue = SclxPortableIdentity.bankImportIssue("SOURCE", IMPORT_ISSUE_UUID.toString());
        String session = SclxPortableIdentity.reconciliationSession(
                "SOURCE", RECONCILIATION_SESSION_UUID.toString());
        String match = SclxPortableIdentity.reconciliationMatch(
                "SOURCE", RECONCILIATION_MATCH_UUID.toString());
        String banking = """
                      "bankConfiguration": {
                        "banks": [
                          {
                            "bankId": "%s",
                            "name": "Portable Credit Union",
                            "routingNumber": "123456789",
                            "address": "1 Portable Way",
                            "website": null,
                            "contactName": "Bank Contact",
                            "contactPhone": null,
                            "contactEmail": "contact@example.invalid",
                            "notes": "Imported bank",
                            "active": true
                          }
                        ],
                        "accounts": [
                          {
                            "bankAccountId": "%s",
                            "bankId": "%s",
                            "ledgerAccountId": "%s",
                            "name": "Portable Checking",
                            "nickname": "Checking",
                            "institutionName": "Portable Credit Union",
                            "accountType": "BANK",
                            "lastFour": "1234",
                            "maskedAccountNumber": "****1234",
                            "openingDate": "2026-01-01",
                            "statementImportFormat": "OFX",
                            "ofxBankId": "123456789",
                            "ofxAccountId": "PORTABLE-1234",
                            "openingBalance": "0.0000",
                            "active": true,
                            "notes": "Imported configured account"
                          }
                        ]
                      },
                      "bankStatementFacts": {
                        "importBatches": [
                          {
                            "importBatchId": "%s",
                            "bankAccountId": "%s",
                            "sourceName": "portable.ofx",
                            "sourceHash": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                            "sourceFormat": "OFX",
                            "status": "ACCEPTED",
                            "importedAt": "2026-07-20T10:00:00Z",
                            "completedAt": "2026-07-20T10:05:00Z",
                            "totalLineCount": 1,
                            "acceptedLineCount": 1,
                            "rejectedLineCount": 0,
                            "issueCount": 1,
                            "notes": "Imported reviewed batch"
                          }
                        ],
                        "statementLines": [
                          {
                            "statementLineId": "%s",
                            "importBatchId": "%s",
                            "bankAccountId": "%s",
                            "sourceRowNumber": 1,
                            "sourceTransactionId": "FITID-1",
                            "deterministicFingerprint": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "statementAccountIdentifier": "PORTABLE-1234",
                            "transactionDate": "2026-07-15",
                            "postedDate": "2026-07-16",
                            "amount": "-25.0000",
                            "transactionType": "DEBIT",
                            "name": "Portable Merchant",
                            "memo": "Purchase supplies",
                            "checkNumber": null,
                            "reference": "REF-1",
                            "status": "MATCHED",
                            "dispositionNote": "Matched during source review",
                            "acceptedTransactionId": null,
                            "matchedTransactionId": "%s"
                          }
                        ],
                        "issues": [
                          {
                            "issueId": "%s",
                            "importBatchId": "%s",
                            "statementLineId": "%s",
                            "sourceRowNumber": 1,
                            "severity": "WARNING",
                            "code": "PORTABLE_WARNING",
                            "message": "Portable review warning",
                            "createdAt": "2026-07-20T10:01:00Z"
                          }
                        ],
                        "transactionLineClearance": [
                          {
                            "lineId": "%s",
                            "bankCleared": true,
                            "bankClearedOn": "2026-07-16",
                            "statementLineId": "%s"
                          }
                        ]
                      },
                      "reconciliation": {
                        "sessions": [
                          {
                            "reconciliationSessionId": "%s",
                            "bankAccountId": "%s",
                            "statementStartDate": "2026-07-01",
                            "statementEndDate": "2026-07-31",
                            "statementEndingBalance": "-25.0000",
                            "mismatchPolicy": "WARN_ONLY",
                            "status": "FINALIZED",
                            "notes": "Imported reconciliation",
                            "beginningBalance": "0.0000",
                            "bookBalanceAll": "-25.0000",
                            "bookBalanceCleared": "-25.0000",
                            "differenceAmount": "0.0000",
                            "createdAt": "2026-07-31T12:00:00Z",
                            "updatedAt": "2026-07-31T12:05:00Z"
                          }
                        ],
                        "matches": [
                          {
                            "reconciliationMatchId": "%s",
                            "reconciliationSessionId": "%s",
                            "statementLineId": "%s",
                            "lineId": "%s",
                            "matchStatus": "MATCHED",
                            "resolutionNote": "Exact portable match",
                            "createdAt": "2026-07-31T12:01:00Z",
                            "updatedAt": "2026-07-31T12:02:00Z"
                          }
                        ]
                      },
                      "fixedAssets": {
                """.formatted(
                bank, bankAccount, bank, cash,
                batch, bankAccount,
                statementLine, batch, bankAccount, transaction,
                issue, batch, statementLine,
                bankLine, statementLine,
                session, bankAccount,
                match, session, statementLine, bankLine);
        String source = Files.readString(target);
        String marker = "\"fixedAssets\": {";
        assertTrue(source.contains(marker), "banking fixture insertion marker must exist");
        Files.writeString(target, source.replace(marker, banking));
        return target;
    }

    private static Path writeSource(Path target) throws Exception
    {
        String organizationId = SclxPortableIdentity.organization("SOURCE");
        String cash = SclxPortableIdentity.account("SOURCE", "1000");
        String asset = SclxPortableIdentity.account("SOURCE", "1500");
        String accumulated = SclxPortableIdentity.account("SOURCE", "1590");
        String inventoryAccount = SclxPortableIdentity.account("SOURCE", "1600");
        String expense = SclxPortableIdentity.account("SOURCE", "6100");
        String fund = SclxPortableIdentity.fund("SOURCE", "GENERAL");
        String budget = SclxPortableIdentity.budget("SOURCE", 2026, "APPROVED");
        String budgetLine = SclxPortableIdentity.budgetLine(
                budget, "PROGRAM", null, fund, "2026-07");
        String transaction = SclxPortableIdentity.transaction("SOURCE", TRANSACTION_UUID.toString());
        String debitLine = SclxPortableIdentity.transactionLine(transaction, 1);
        String creditLine = SclxPortableIdentity.transactionLine(transaction, 2);
        String activity = SclxPortableIdentity.activity("SOURCE", "EVENT");
        String counterparty = SclxPortableIdentity.counterparty(
                "SOURCE", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String merchant = SclxPortableIdentity.merchant(
                "SOURCE", "99999999-8888-7777-6666-555555555555");
        String supplemental = SclxPortableIdentity.supplementalDetail(transaction, 1);
        String fixedAsset = SclxPortableIdentity.fixedAsset("SOURCE", FIXED_ASSET_UUID.toString());
        String depreciationRun = SclxPortableIdentity.fixedAssetDepreciationRun(
                "SOURCE", DEPRECIATION_RUN_UUID.toString());
        String inventoryItem = SclxPortableIdentity.inventoryItem(
                "SOURCE", INVENTORY_ITEM_UUID.toString());
        String inventoryMovement = SclxPortableIdentity.inventoryMovement(
                "SOURCE", INVENTORY_MOVEMENT_UUID.toString());
        Files.writeString(target, """
                {
                  "format": "SCLX",
                  "version": "1.3",
                  "exportedAt": "2026-07-31T12:00:00Z",
                  "organization": {
                    "organizationId": "%s",
                    "code": "SOURCE",
                    "name": "Portable Source Company",
                    "baseCurrency": "CAD",
                    "fiscalYearStart": "2026-04-01"
                  },
                  "chartOfAccounts": [
                    {
                      "accountId": "%s",
                      "code": "1000",
                      "name": "Cash",
                      "type": "BANK",
                      "subtype": "CASH",
                      "increaseSide": "DEBIT",
                      "currency": "CAD",
                      "openingBalance": "0.00",
                      "posting": true,
                      "active": true
                    },
                    {
                      "accountId": "%s",
                      "code": "1500",
                      "name": "Equipment",
                      "type": "ASSET",
                      "subtype": "FIXED_ASSET",
                      "increaseSide": "DEBIT",
                      "currency": "CAD",
                      "openingBalance": "0.00",
                      "posting": true,
                      "active": true
                    },
                    {
                      "accountId": "%s",
                      "code": "1590",
                      "name": "Accumulated Depreciation",
                      "type": "ASSET",
                      "subtype": "FIXED_ASSET",
                      "increaseSide": "CREDIT",
                      "currency": "CAD",
                      "openingBalance": "0.00",
                      "posting": true,
                      "active": true
                    },
                    {
                      "accountId": "%s",
                      "code": "1600",
                      "name": "Inventory",
                      "type": "ASSET",
                      "subtype": "INVENTORY",
                      "increaseSide": "DEBIT",
                      "currency": "CAD",
                      "openingBalance": "0.00",
                      "posting": true,
                      "active": true
                    },
                    {
                      "accountId": "%s",
                      "code": "6100",
                      "name": "Supplies",
                      "type": "EXPENSE",
                      "increaseSide": "DEBIT",
                      "currency": "CAD",
                      "openingBalance": "0.00",
                      "posting": true,
                      "active": true
                    }
                  ],
                  "funds": [
                    {
                      "fundId": "%s",
                      "code": "GENERAL",
                      "name": "General Fund",
                      "type": "UNRESTRICTED",
                      "active": true
                    }
                  ],
                  "budgets": [
                    {
                      "budgetId": "%s",
                      "name": "FY2026 Approved",
                      "fiscalYear": 2026,
                      "version": "APPROVED",
                      "active": true,
                      "lines": [
                        {
                          "lineId": "%s",
                          "fundId": "%s",
                          "categoryCode": "PROGRAM",
                          "periodMonth": "2026-07",
                          "amount": "125.0000"
                        }
                      ]
                    }
                  ],
                  "transactions": [
                    {
                      "transactionId": "%s",
                      "transactionDate": "2026-07-15",
                      "description": "Purchase supplies",
                      "status": "ENTERED",
                      "lines": [
                        {
                          "lineId": "%s",
                          "accountId": "%s",
                          "fundId": "%s",
                          "activityId": "%s",
                          "counterpartyId": "%s",
                          "debit": "25.00",
                          "credit": "0"
                        },
                        {
                          "lineId": "%s",
                          "accountId": "%s",
                          "fundId": "%s",
                          "activityId": "%s",
                          "counterpartyId": "%s",
                          "debit": "0",
                          "credit": "25.00"
                        }
                      ]
                    }
                  ],
                  "extensions": {
                    "version": 1,
                    "scaJakartaH2": {
                      "activeChartName": "Portable Chart",
                      "activeChartVersion": "2026",
                      "activities": [
                        {
                          "activityId": "%s",
                          "code": "EVENT",
                          "name": "Portable Event",
                          "active": true
                        }
                      ],
                      "counterparties": {
                        "counterparties": [
                          {
                            "counterpartyId": "%s",
                            "displayName": "Portable Payee",
                            "kind": "ORG",
                            "email": "payee@example.invalid",
                            "phone": null,
                            "notes": "Imported payee",
                            "active": true
                          }
                        ],
                        "merchants": [
                          {
                            "merchantId": "%s",
                            "name": "Portable Merchant",
                            "notes": null,
                            "active": true
                          }
                        ],
                        "transactionLineMerchants": [
                          {
                            "lineId": "%s",
                            "merchantId": "%s"
                          }
                        ]
                      },
                      "supplementalDetails": [
                        {
                          "supplementalDetailId": "%s",
                          "transactionId": "%s",
                          "lineOrder": 7,
                          "kind": "PAYABLE",
                          "entryRef": "AP-1",
                          "counterparty": "Portable Payee",
                          "description": "Portable payable detail",
                          "reference": null,
                          "amount": "25.00",
                          "dueDate": "2026-08-15",
                          "startDate": null,
                          "endDate": null,
                          "notes": "Imported detail"
                        }
                      ],
                      "fixedAssets": {
                        "version": 1,
                        "assets": [
                          {
                            "assetId": "%s",
                            "name": "Portable Equipment",
                            "acquisitionDate": "2026-01-15",
                            "acquisitionCost": "1800.0000",
                            "salvageValue": "300.0000",
                            "usefulLifeMonths": 60,
                            "depreciationMethod": "STRAIGHT_LINE",
                            "openingAccumulatedDepreciation": "0.0000",
                            "status": "ACTIVE",
                            "notes": "Imported fixed asset",
                            "assetAccountId": "%s",
                            "accumulatedDepreciationAccountId": "%s",
                            "depreciationExpenseAccountId": "%s",
                            "fundId": "%s",
                            "createdAt": "2026-01-15T10:00:00Z",
                            "updatedAt": "2026-07-31T11:00:00Z"
                          }
                        ],
                        "depreciationRuns": [
                          {
                            "depreciationRunId": "%s",
                            "assetId": "%s",
                            "runDate": "2026-07-15",
                            "depreciationAmount": "25.0000",
                            "transactionId": "%s",
                            "notes": "Imported completed run",
                            "createdAt": "2026-07-15T12:00:00Z"
                          }
                        ]
                      },
                      "inventory": {
                        "version": 1,
                        "items": [
                          {
                            "itemId": "%s",
                            "name": "Portable Regalia",
                            "itemType": "Regalia",
                            "quantity": "3.0000",
                            "unit": "pieces",
                            "unitValue": "125.0000",
                            "acquisitionDate": "2026-02-01",
                            "custodian": "Quartermaster",
                            "storageLocation": "Locker A",
                            "condition": "GOOD",
                            "status": "ACTIVE",
                            "notes": "Imported inventory item",
                            "inventoryAccountId": "%s",
                            "fundId": "%s",
                            "createdAt": "2026-02-01T10:00:00Z",
                            "updatedAt": "2026-07-31T11:30:00Z"
                          }
                        ],
                        "movements": [
                          {
                            "movementId": "%s",
                            "itemId": "%s",
                            "movementDate": "2026-07-15",
                            "movementType": "RECEIPT",
                            "quantityChange": "3.0000",
                            "resultingQuantity": "3.0000",
                            "unitValue": "125.0000",
                            "transactionId": "%s",
                            "notes": "Imported receipt",
                            "createdAt": "2026-07-15T12:30:00Z"
                          }
                        ]
                      }
                    }
                  }
                }
                """.formatted(
                organizationId,
                cash,
                asset,
                accumulated,
                inventoryAccount,
                expense,
                fund,
                budget,
                budgetLine,
                fund,
                transaction,
                debitLine,
                expense,
                fund,
                activity,
                counterparty,
                creditLine,
                cash,
                fund,
                activity,
                counterparty,
                activity,
                counterparty,
                merchant,
                debitLine,
                merchant,
                supplemental,
                transaction,
                fixedAsset,
                asset,
                accumulated,
                expense,
                fund,
                depreciationRun,
                fixedAsset,
                transaction,
                inventoryItem,
                inventoryAccount,
                fund,
                inventoryMovement,
                inventoryItem,
                transaction));
        return target;
    }
}
