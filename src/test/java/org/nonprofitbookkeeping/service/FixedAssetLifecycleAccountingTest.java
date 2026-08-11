package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetLifecycleEvent;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedAssetLifecycleAccountingTest
{
    private static final long COMPANY_ID = 10_001L;
    private static final long CHART_ID = 10_001L;
    private static final long FUND_ID = 10_001L;
    private static final long ASSET_ACCOUNT_ID = 10_001L;
    private static final long ACCUMULATED_ACCOUNT_ID = 10_002L;
    private static final long BANK_ACCOUNT_ID = 10_003L;
    private static final long DEPRECIATION_EXPENSE_ID = 10_004L;
    private static final long GAIN_ACCOUNT_ID = 10_005L;
    private static final long LOSS_ACCOUNT_ID = 10_006L;
    private static final LocalDate EVENT_DATE = LocalDate.of(2026, 6, 30);

    @Test
    void saleCommitsExactGainAccountingStatusAuditAndIdempotentRetry(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("sale")))
        {
            seed(jpa);
            UUID transactionPortableId = UUID.randomUUID();
            UUID eventPortableId = UUID.randomUUID();
            FixedAssetService service = lifecycleService(
                    jpa, transactionPortableId, eventPortableId, UUID.randomUUID(), noOpHook());
            FixedAssetView asset = service.create(assetCommand(
                    "Trailer", "1000.0000", "200.0000", FixedAsset.Status.ACTIVE));
            Counts before = counts(jpa);

            FixedAssetService.LifecyclePreview preview = service.previewLifecycleEvent(
                    asset.id(), lifecycleCommand(
                            FixedAssetLifecycleEvent.EventType.SALE, "900.0000", "0.0000"));

            assertEquals(new BigDecimal("800.0000"), preview.carryingAmountBefore());
            assertEquals(new BigDecimal("100.0000"), preview.gainAmount());
            assertEquals(new BigDecimal("0.0000"), preview.lossAmount());
            assertEquals(FixedAsset.Status.DISPOSED, preview.assetStatusAfter());

            FixedAssetLifecycleEventView event = service.recordLifecycleEvent(preview, "treasurer");
            Counts committed = counts(jpa);
            assertEquals(before.txns() + 1, committed.txns());
            assertEquals(before.splits() + 4, committed.splits());
            assertEquals(before.events() + 1, committed.events());
            assertEquals(before.audits() + 2, committed.audits());
            assertEquals(FixedAsset.Status.DISPOSED, service.load(asset.id()).status());
            assertEquals(transactionPortableId, portableId(jpa, "txn", event.transactionId()));
            assertEquals(eventPortableId, portableId(jpa, "fixed_asset_lifecycle_event", event.id()));
            assertSigned(jpa, event.transactionId(), BANK_ACCOUNT_ID, "900.0000");
            assertSigned(jpa, event.transactionId(), ACCUMULATED_ACCOUNT_ID, "-200.0000");
            assertSigned(jpa, event.transactionId(), ASSET_ACCOUNT_ID, "-1000.0000");
            assertSigned(jpa, event.transactionId(), GAIN_ACCOUNT_ID, "100.0000");

            FixedAssetLifecycleEventView retry = service.recordLifecycleEvent(preview, "treasurer");
            assertEquals(event.id(), retry.id());
            assertEquals(committed, counts(jpa));

            FixedAssetCommand forbidden = assetCommand(
                    "Trailer", "1000.0000", "200.0000", FixedAsset.Status.DISPOSED);
            assertThrows(IllegalArgumentException.class, () -> service.update(asset.id(), forbidden));
            FixedAssetCommand directReactivation = assetCommand(
                    "Trailer", "1000.0000", "200.0000", FixedAsset.Status.ACTIVE);
            assertThrows(IllegalStateException.class,
                    () -> service.update(asset.id(), directReactivation));
        }
    }

    @Test
    void zeroProceedsFullyDepreciatedRetirementCreatesTwoNonzeroLines(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("retirement")))
        {
            seed(jpa);
            FixedAssetService service = lifecycleService(
                    jpa, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), noOpHook());
            FixedAssetView asset = service.create(assetCommand(
                    "Fully depreciated equipment", "1000.0000", "1000.0000", FixedAsset.Status.ACTIVE));

            FixedAssetService.LifecyclePreview preview = service.previewLifecycleEvent(
                    asset.id(), lifecycleCommand(
                            FixedAssetLifecycleEvent.EventType.RETIREMENT, "0.0000", "0.0000"));

            assertEquals(new BigDecimal("0.0000"), preview.carryingAmountBefore());
            assertEquals(2, preview.transactionCommand().lines().size());
            FixedAssetLifecycleEventView event = service.recordLifecycleEvent(preview, "treasurer");
            assertEquals(FixedAsset.Status.DISPOSED, service.load(asset.id()).status());
            assertEquals(2L, number(jpa, "select count(*) from txn_split where txn_id = ?", event.transactionId()));
            assertSigned(jpa, event.transactionId(), ACCUMULATED_ACCOUNT_ID, "-1000.0000");
            assertSigned(jpa, event.transactionId(), ASSET_ACCOUNT_ID, "-1000.0000");
        }
    }

    @Test
    void partialProceedsSaleRecognizesLossAndDomainReversalRestoresStatus(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("partial-proceeds-loss")))
        {
            seed(jpa);
            FixedAssetService service = lifecycleService(
                    jpa, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), noOpHook());
            FixedAssetView asset = service.create(assetCommand(
                    "Partially recovered equipment", "1000.0000", "200.0000",
                    FixedAsset.Status.ACTIVE));

            FixedAssetService.LifecyclePreview preview = service.previewLifecycleEvent(
                    asset.id(), lifecycleCommand(
                            FixedAssetLifecycleEvent.EventType.SALE, "500.0000", "0.0000"));

            assertEquals(new BigDecimal("800.0000"), preview.carryingAmountBefore());
            assertEquals(new BigDecimal("300.0000"), preview.lossAmount());
            assertEquals(new BigDecimal("0.0000"), preview.gainAmount());
            FixedAssetLifecycleEventView event = service.recordLifecycleEvent(preview, "treasurer");
            assertSigned(jpa, event.transactionId(), BANK_ACCOUNT_ID, "500.0000");
            assertSigned(jpa, event.transactionId(), LOSS_ACCOUNT_ID, "300.0000");
            assertEquals(FixedAsset.Status.DISPOSED, service.load(asset.id()).status());

            FixedAssetService.LifecycleReversalPreview reversal =
                    service.previewLifecycleReversal(
                            event.id(), EVENT_DATE.plusDays(1), "sale was entered in error");
            FixedAssetLifecycleEventView reversed = service.reverseLifecycleEvent(
                    reversal, "treasurer");

            assertNotNull(reversed.reversalTransactionId());
            assertEquals(FixedAsset.Status.ACTIVE, service.load(asset.id()).status());
            assertEquals("REVERSED", text(jpa,
                    "select status from txn where id = ?", event.transactionId()));
        }
    }

    @Test
    void finalizedBankReconciliationRangeBlocksSaleProceeds(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("finalized-proceeds")))
        {
            seed(jpa);
            protectProceedsDateWithFinalizedReconciliation(jpa);
            FixedAssetService service = lifecycleService(
                    jpa, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), noOpHook());
            FixedAssetView asset = service.create(assetCommand(
                    "Reconciliation-protected equipment", "1000.0000", "200.0000",
                    FixedAsset.Status.ACTIVE));
            Counts before = counts(jpa);

            IllegalStateException protectedRange = assertThrows(IllegalStateException.class,
                    () -> service.previewLifecycleEvent(
                            asset.id(), lifecycleCommand(
                                    FixedAssetLifecycleEvent.EventType.SALE,
                                    "500.0000", "0.0000")));

            assertTrue(protectedRange.getMessage().contains("finalized reconciliation"));
            assertEquals(before, counts(jpa));
            assertEquals(FixedAsset.Status.ACTIVE, service.load(asset.id()).status());
        }
    }

    @Test
    void lifecyclePreviewRejectsAssetOwnedByAnotherActiveCompany(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("company-isolation")))
        {
            seed(jpa);
            seedOtherCompany(jpa);
            FixedAssetService sca = lifecycleService(
                    jpa, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), noOpHook());
            FixedAssetView asset = sca.create(assetCommand(
                    "SCA-only equipment", "1000.0000", "200.0000", FixedAsset.Status.ACTIVE));
            FixedAssetService other = lifecycleService(
                    jpa, "OTHER", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), noOpHook());
            Counts before = counts(jpa);

            RuntimeException rejected = assertThrows(RuntimeException.class,
                    () -> other.previewLifecycleEvent(
                            asset.id(), lifecycleCommand(
                                    FixedAssetLifecycleEvent.EventType.RETIREMENT,
                                    "0.0000", "0.0000")));

            assertTrue(deepMessage(rejected).toLowerCase().contains("company"));
            assertEquals(before, counts(jpa));
            assertEquals(FixedAsset.Status.ACTIVE, sca.load(asset.id()).status());
        }
    }

    @Test
    void impairmentAndDomainReversalKeepAssetAndLedgerSynchronized(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("impairment-reversal")))
        {
            seed(jpa);
            UUID reversalPortableId = UUID.randomUUID();
            FixedAssetService service = lifecycleService(
                    jpa, UUID.randomUUID(), UUID.randomUUID(), reversalPortableId, noOpHook());
            FixedAssetView asset = service.create(assetCommand(
                    "Damaged equipment", "1000.0000", "0.0000", FixedAsset.Status.ACTIVE));
            FixedAssetService.LifecyclePreview preview = service.previewLifecycleEvent(
                    asset.id(), lifecycleCommand(
                            FixedAssetLifecycleEvent.EventType.IMPAIRMENT, "0.0000", "300.0000"));
            FixedAssetLifecycleEventView event = service.recordLifecycleEvent(preview, "treasurer");

            FixedAssetView impaired = service.load(asset.id());
            assertEquals(FixedAsset.Status.ACTIVE, impaired.status());
            assertEquals(new BigDecimal("300.0000"), impaired.accumulatedImpairment());
            assertEquals(new BigDecimal("700.0000"), impaired.currentBookValue());
            assertSigned(jpa, event.transactionId(), LOSS_ACCOUNT_ID, "300.0000");
            assertSigned(jpa, event.transactionId(), ACCUMULATED_ACCOUNT_ID, "300.0000");
            TransactionCorrectionService corrections =
                    new TransactionCorrectionService(jpa, () -> "SCA");
            assertThrows(IllegalStateException.class,
                    () -> corrections.directEdit(
                            event.transactionId(), EVENT_DATE, "wrong route", "wrong route", "treasurer"));
            assertThrows(IllegalStateException.class,
                    () -> corrections.delete(event.transactionId(), "treasurer", "wrong route"));
            assertThrows(IllegalStateException.class,
                    () -> corrections.reverse(event.transactionId(), EVENT_DATE.plusDays(1),
                            "treasurer", "wrong route", false));
            assertThrows(PostingException.class,
                    () -> new TransactionEntryService(jpa, () -> "SCA")
                            .update(event.transactionId(), preview.transactionCommand()));

            FixedAssetService.LifecycleReversalPreview reversalPreview =
                    service.previewLifecycleReversal(
                            event.id(), EVENT_DATE.plusDays(1), "damage assessment corrected");
            FixedAssetLifecycleEventView reversed = service.reverseLifecycleEvent(
                    reversalPreview, "treasurer");

            assertNotNull(reversed.reversalTransactionId());
            assertEquals(reversalPortableId,
                    portableId(jpa, "txn", reversed.reversalTransactionId()));
            Counts afterReversal = counts(jpa);
            FixedAssetLifecycleEventView reversalRetry = service.reverseLifecycleEvent(
                    reversalPreview, "treasurer");
            assertEquals(reversed.reversalTransactionId(), reversalRetry.reversalTransactionId());
            assertEquals(afterReversal, counts(jpa));
            FixedAssetView restored = service.load(asset.id());
            assertEquals(FixedAsset.Status.ACTIVE, restored.status());
            assertEquals(new BigDecimal("0.0000"), restored.accumulatedImpairment());
            assertEquals(new BigDecimal("1000.0000"), restored.currentBookValue());
            assertEquals("REVERSED", text(jpa, "select status from txn where id = ?", event.transactionId()));
            assertThrows(IllegalStateException.class,
                    () -> corrections.directEdit(
                            reversed.reversalTransactionId(), EVENT_DATE.plusDays(1),
                            "wrong route", "wrong route", "treasurer"));
            assertThrows(IllegalStateException.class,
                    () -> corrections.delete(
                            reversed.reversalTransactionId(), "treasurer", "wrong route"));
        }
    }

    @Test
    void closedPeriodAndStalePreviewCannotWriteLifecycleFacts(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("guards")))
        {
            seed(jpa);
            FixedAssetService service = lifecycleService(
                    jpa, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), noOpHook());
            FixedAssetView asset = service.create(assetCommand(
                    "Guarded equipment", "1000.0000", "0.0000", FixedAsset.Status.ACTIVE));
            FixedAssetService.LifecyclePreview preview = service.previewLifecycleEvent(
                    asset.id(), lifecycleCommand(
                            FixedAssetLifecycleEvent.EventType.SALE, "900.0000", "0.0000"));
            service.update(asset.id(), assetCommand(
                    "Renamed equipment", "1000.0000", "0.0000", FixedAsset.Status.ACTIVE));
            Counts beforeStale = counts(jpa);
            assertThrows(IllegalStateException.class,
                    () -> service.recordLifecycleEvent(preview, "treasurer"));
            assertEquals(beforeStale, counts(jpa));

            new PeriodCloseRangeService(jpa).closeRange(
                    "SCA", EVENT_DATE, EVENT_DATE, "CALCULATED", "treasurer", "closed");
            Counts beforeClosed = counts(jpa);
            RuntimeException closed = assertThrows(RuntimeException.class,
                    () -> service.previewLifecycleEvent(
                            asset.id(), lifecycleCommand(
                                    FixedAssetLifecycleEvent.EventType.RETIREMENT,
                                    "0.0000", "0.0000")));
            assertTrue(deepMessage(closed).toLowerCase().contains("closed"));
            assertEquals(beforeClosed, counts(jpa));
        }
    }

    @Test
    void backdatedLifecycleEventCannotPrecedeRecordedDepreciation(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("chronology-guard")))
        {
            seed(jpa);
            FixedAssetService service = lifecycleService(
                    jpa, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), noOpHook());
            FixedAssetView asset = service.create(assetCommand(
                    "Chronological equipment", "1000.0000", "0.0000",
                    FixedAsset.Status.ACTIVE));
            service.runMonthlyDepreciation(
                    asset.id(), EVENT_DATE.plusMonths(1), "later completed depreciation");
            Counts before = counts(jpa);

            IllegalStateException rejected = assertThrows(IllegalStateException.class,
                    () -> service.previewLifecycleEvent(
                            asset.id(), lifecycleCommand(
                                    FixedAssetLifecycleEvent.EventType.RETIREMENT,
                                    "0.0000", "0.0000")));

            assertTrue(rejected.getMessage().contains("precedes later fixed-asset accounting"));
            assertEquals(before, counts(jpa));
            assertEquals(FixedAsset.Status.ACTIVE, service.load(asset.id()).status());
        }
    }

    @Test
    void backdatedDepreciationCannotPrecedeRecordedImpairment(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("depreciation-chronology-guard")))
        {
            seed(jpa);
            FixedAssetService service = lifecycleService(
                    jpa, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), noOpHook());
            FixedAssetView asset = service.create(assetCommand(
                    "Impaired chronological equipment", "1000.0000", "0.0000",
                    FixedAsset.Status.ACTIVE));
            FixedAssetService.LifecyclePreview impairment = service.previewLifecycleEvent(
                    asset.id(), lifecycleCommand(
                            FixedAssetLifecycleEvent.EventType.IMPAIRMENT,
                            EVENT_DATE.plusMonths(1), "0.0000", "100.0000"));
            service.recordLifecycleEvent(impairment, "treasurer");
            Counts before = counts(jpa);

            IllegalStateException rejected = assertThrows(IllegalStateException.class,
                    () -> service.runMonthlyDepreciation(
                            asset.id(), EVENT_DATE, "backdated after impairment"));

            assertTrue(rejected.getMessage().contains(
                    "Depreciation run date precedes later fixed-asset lifecycle accounting"));
            assertEquals(before, counts(jpa));
        }
    }

    @Test
    void lateFailureRollsBackTransactionEventStatusAndAuditsAcrossRestart(@TempDir Path tempDir)
    {
        Path database = tempDir.resolve("late-rollback");
        long assetId;
        Counts expected;
        try (Jpa jpa = new Jpa(database))
        {
            seed(jpa);
            FixedAssetService service = lifecycleService(
                    jpa, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    (em, asset, transaction, preview) -> {
                        em.flush();
                        throw new IllegalStateException("injected lifecycle failure");
                    });
            assetId = service.create(assetCommand(
                    "Rollback equipment", "1000.0000", "0.0000", FixedAsset.Status.ACTIVE)).id();
            FixedAssetService.LifecyclePreview preview = service.previewLifecycleEvent(
                    assetId, lifecycleCommand(
                            FixedAssetLifecycleEvent.EventType.SALE, "800.0000", "0.0000"));
            expected = counts(jpa);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> service.recordLifecycleEvent(preview, "treasurer"));
            assertTrue(failure.getMessage().contains("injected lifecycle failure"));
            assertEquals(expected, counts(jpa));
            assertEquals(FixedAsset.Status.ACTIVE, service.load(assetId).status());
        }
        try (Jpa reopened = new Jpa(database))
        {
            FixedAssetService service = new FixedAssetService(
                    reopened, new TransactionEntryService(reopened, () -> "SCA"), () -> "SCA");
            assertEquals(expected, counts(reopened));
            assertEquals(FixedAsset.Status.ACTIVE, service.load(assetId).status());
            assertTrue(service.listLifecycleEvents("SCA").isEmpty());
        }
    }

    @Test
    void migrationCreatesLifecycleConstraintsAndIndexes(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("migration")); EntityManager em = jpa.em())
        {
            assertEquals(1L, number(em, """
                    select count(*) from information_schema.tables
                    where upper(table_name) = 'FIXED_ASSET_LIFECYCLE_EVENT'
                    """));
            assertEquals(1L, number(em, """
                    select count(*) from information_schema.table_constraints
                    where upper(table_name) = 'FIXED_ASSET_LIFECYCLE_EVENT'
                      and upper(constraint_name) = 'UQ_FIXED_ASSET_LIFECYCLE_TRANSACTION'
                      and constraint_type = 'UNIQUE'
                    """));
        }
    }

    private static FixedAssetService lifecycleService(
            Jpa jpa,
            UUID transactionPortableId,
            UUID eventPortableId,
            UUID reversalPortableId,
            FixedAssetService.LifecycleWriteHook hook)
    {
        return lifecycleService(
                jpa, "SCA", transactionPortableId, eventPortableId, reversalPortableId, hook);
    }

    private static FixedAssetService lifecycleService(
            Jpa jpa,
            String companyCode,
            UUID transactionPortableId,
            UUID eventPortableId,
            UUID reversalPortableId,
            FixedAssetService.LifecycleWriteHook hook)
    {
        return new FixedAssetService(
                jpa,
                new TransactionEntryService(jpa, () -> companyCode),
                new TransactionCorrectionService(jpa, () -> companyCode),
                () -> companyCode,
                () -> transactionPortableId,
                () -> eventPortableId,
                () -> reversalPortableId,
                hook);
    }

    private static FixedAssetService.LifecycleWriteHook noOpHook()
    {
        return (em, asset, transaction, preview) -> { };
    }

    private static FixedAssetLifecycleCommand lifecycleCommand(
            FixedAssetLifecycleEvent.EventType type,
            String proceeds,
            String impairment)
    {
        return lifecycleCommand(type, EVENT_DATE, proceeds, impairment);
    }

    private static FixedAssetLifecycleCommand lifecycleCommand(
            FixedAssetLifecycleEvent.EventType type,
            LocalDate eventDate,
            String proceeds,
            String impairment)
    {
        return new FixedAssetLifecycleCommand(
                type,
                eventDate,
                new BigDecimal(proceeds),
                new BigDecimal(impairment),
                BANK_ACCOUNT_ID,
                GAIN_ACCOUNT_ID,
                LOSS_ACCOUNT_ID,
                "test lifecycle event");
    }

    private static FixedAssetCommand assetCommand(
            String name,
            String cost,
            String openingDepreciation,
            FixedAsset.Status status)
    {
        return new FixedAssetCommand(
                "SCA",
                ASSET_ACCOUNT_ID,
                ACCUMULATED_ACCOUNT_ID,
                DEPRECIATION_EXPENSE_ID,
                FUND_ID,
                name,
                LocalDate.of(2026, 1, 1),
                new BigDecimal(cost),
                BigDecimal.ZERO,
                36,
                FixedAsset.DepreciationMethod.STRAIGHT_LINE,
                new BigDecimal(openingDepreciation),
                status,
                "test asset");
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("insert into chart_of_accounts (id, name, version, status) values (?, 'SCA Chart', '1', 'ACTIVE')")
                    .setParameter(1, CHART_ID).executeUpdate();
            em.createNativeQuery("insert into company (id, code, display_name, active_chart_of_accounts_id) values (?, 'SCA', 'SCA Branch', ?)")
                    .setParameter(1, COMPANY_ID).setParameter(2, CHART_ID).executeUpdate();
            em.createNativeQuery("update chart_of_accounts set company_id = ? where id = ?")
                    .setParameter(1, COMPANY_ID).setParameter(2, CHART_ID).executeUpdate();
            em.createNativeQuery("insert into fund (id, company_id, code, name, fund_type) values (?, ?, 'OPERATING', 'Operating', 'UNRESTRICTED')")
                    .setParameter(1, FUND_ID).setParameter(2, COMPANY_ID).executeUpdate();
            account(em, ASSET_ACCOUNT_ID, "1500", "Equipment", "ASSET", "FIXED_ASSET", "DEBIT");
            account(em, ACCUMULATED_ACCOUNT_ID, "1590", "Accumulated Depreciation", "ASSET", "FIXED_ASSET", "CREDIT");
            account(em, BANK_ACCOUNT_ID, "1000", "Checking", "BANK", "CASH", "DEBIT");
            account(em, DEPRECIATION_EXPENSE_ID, "6100", "Depreciation Expense", "EXPENSE", null, "DEBIT");
            account(em, GAIN_ACCOUNT_ID, "4900", "Gain on Asset Disposal", "INCOME", null, "CREDIT");
            account(em, LOSS_ACCOUNT_ID, "6200", "Loss on Asset Disposal", "EXPENSE", null, "DEBIT");
            em.getTransaction().commit();
        }
    }

    private static void account(
            EntityManager em,
            long id,
            String code,
            String name,
            String type,
            String subtype,
            String normalBalance)
    {
        em.createNativeQuery("""
                insert into account
                    (id, chart_id, code, name, account_type, subtype, normal_balance)
                values (?, ?, ?, ?, ?, ?, ?)
                """)
                .setParameter(1, id)
                .setParameter(2, CHART_ID)
                .setParameter(3, code)
                .setParameter(4, name)
                .setParameter(5, type)
                .setParameter(6, subtype)
                .setParameter(7, normalBalance)
                .executeUpdate();
    }

    private static void seedOtherCompany(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("""
                    insert into chart_of_accounts (id, name, version, status)
                    values (11001, 'Other Chart', '1', 'ACTIVE')
                    """).executeUpdate();
            em.createNativeQuery("""
                    insert into company (id, code, display_name, active_chart_of_accounts_id)
                    values (11001, 'OTHER', 'Other Branch', 11001)
                    """).executeUpdate();
            em.createNativeQuery("update chart_of_accounts set company_id = 11001 where id = 11001")
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void protectProceedsDateWithFinalizedReconciliation(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("""
                    insert into company_bank_account
                        (id, company_id, name, institution_name, account_type, last_four, account_id)
                    values (20001, ?, 'Checking', 'Test Bank', 'CHECKING', '1234', ?)
                    """)
                    .setParameter(1, COMPANY_ID)
                    .setParameter(2, BANK_ACCOUNT_ID)
                    .executeUpdate();
            em.createNativeQuery("""
                    insert into bank_reconciliation_session
                        (id, company_id, bank_account_id, statement_start_date, statement_end_date,
                         mismatch_policy, status)
                    values (20001, ?, 20001, ?, ?, 'WARN_ONLY', 'FINALIZED')
                    """)
                    .setParameter(1, COMPANY_ID)
                    .setParameter(2, java.sql.Date.valueOf(EVENT_DATE.withDayOfMonth(1)))
                    .setParameter(3, java.sql.Date.valueOf(EVENT_DATE))
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static Counts counts(Jpa jpa)
    {
        return new Counts(
                number(jpa, "select count(*) from txn"),
                number(jpa, "select count(*) from txn_split"),
                number(jpa, "select count(*) from fixed_asset_lifecycle_event"),
                number(jpa, "select count(*) from audit_event"));
    }

    private static void assertSigned(Jpa jpa, long transactionId, long accountId, String expected)
    {
        try (EntityManager em = jpa.em())
        {
            BigDecimal actual = (BigDecimal) em.createNativeQuery(
                            "select amount_signed from txn_split where txn_id = ? and account_id = ?")
                    .setParameter(1, transactionId)
                    .setParameter(2, accountId)
                    .getSingleResult();
            assertEquals(0, actual.compareTo(new BigDecimal(expected)));
        }
    }

    private static UUID portableId(Jpa jpa, String table, long id)
    {
        try (EntityManager em = jpa.em())
        {
            return switch (table)
            {
                case "txn" -> em.createQuery(
                                "select t.portableId from Txn t where t.id = :id", UUID.class)
                        .setParameter("id", id)
                        .getSingleResult();
                case "fixed_asset_lifecycle_event" -> em.createQuery(
                                "select e.portableId from FixedAssetLifecycleEvent e where e.id = :id",
                                UUID.class)
                        .setParameter("id", id)
                        .getSingleResult();
                default -> throw new IllegalArgumentException("Unsupported portable-id table: " + table);
            };
        }
    }

    private static String text(Jpa jpa, String sql, Object... parameters)
    {
        try (EntityManager em = jpa.em())
        {
            var query = em.createNativeQuery(sql);
            for (int i = 0; i < parameters.length; i++)
            {
                query.setParameter(i + 1, parameters[i]);
            }
            return String.valueOf(query.getSingleResult());
        }
    }

    private static long number(Jpa jpa, String sql, Object... parameters)
    {
        try (EntityManager em = jpa.em())
        {
            return number(em, sql, parameters);
        }
    }

    private static long number(EntityManager em, String sql, Object... parameters)
    {
        var query = em.createNativeQuery(sql);
        for (int i = 0; i < parameters.length; i++)
        {
            query.setParameter(i + 1, parameters[i]);
        }
        Object value = query.getSingleResult();
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static String deepMessage(Throwable failure)
    {
        String message = "";
        for (Throwable current = failure; current != null; current = current.getCause())
        {
            if (current.getMessage() != null && !current.getMessage().isBlank())
            {
                message = current.getMessage();
            }
        }
        return message;
    }

    private record Counts(long txns, long splits, long events, long audits)
    {
    }
}
