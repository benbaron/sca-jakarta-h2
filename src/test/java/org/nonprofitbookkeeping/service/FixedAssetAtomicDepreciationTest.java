package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedAssetAtomicDepreciationTest
{
    private static final Scope SCA = new Scope(10_001L, 10_001L, 10_001L, 10_001L, 10_002L, 10_003L, 10_004L, "SCA");
    private static final Scope OTHER = new Scope(20_001L, 20_001L, 20_001L, 20_001L, 20_002L, 20_003L, 20_004L, "OTHER");
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 4, 30);

    @Test
    void successfulRunCommitsOneCompleteAuthoritativeOperation(@TempDir Path tempDir)
    {
        UUID transactionPortableId = UUID.randomUUID();
        UUID runPortableId = UUID.randomUUID();
        try (Jpa jpa = new Jpa(tempDir.resolve("success")))
        {
            seedScope(jpa, SCA);
            FixedAssetView asset = service(jpa, SCA.code()).create(assetCommand(SCA, "Trailer"));
            Counts before = counts(jpa);

            DepreciationRunView run = service(
                    jpa,
                    SCA.code(),
                    transactionPortableId,
                    runPortableId,
                    "treasurer",
                    noOpHook())
                    .runMonthlyDepreciation(asset.id(), RUN_DATE, "April depreciation");

            Counts after = counts(jpa);
            assertEquals(before.txns() + 1, after.txns());
            assertEquals(before.splits() + 2, after.splits());
            assertEquals(before.runs() + 1, after.runs());
            assertEquals(before.audits() + 1, after.audits());
            assertEquals(before.txnPortableIds() + 1, after.txnPortableIds());
            assertEquals(before.runPortableIds() + 1, after.runPortableIds());
            assertNotNull(run.id());
            assertNotNull(run.transactionId());
            assertEquals(new BigDecimal("10.0000"), run.depreciationAmount());

            try (EntityManager em = jpa.em())
            {
                assertEquals(transactionPortableId, em.createQuery(
                                "select t.portableId from Txn t where t.id = :id", UUID.class)
                        .setParameter("id", run.transactionId())
                        .getSingleResult());
                assertEquals(runPortableId, em.createQuery(
                                "select r.portableId from FixedAssetDepreciationRun r where r.id = :id", UUID.class)
                        .setParameter("id", run.id())
                        .getSingleResult());
                assertEquals(2L, number(em, "select count(*) from txn_split where txn_id = ?", run.transactionId()));
                assertEquals(1L, number(em, "select count(*) from audit_event where action_type = 'TRANSACTION_ENTERED' and entity_id = ?",
                        String.valueOf(run.transactionId())));
                assertEquals("Monthly fixed-asset depreciation", em.createNativeQuery(
                                "select reason from audit_event where action_type = 'TRANSACTION_ENTERED' and entity_id = ?")
                        .setParameter(1, String.valueOf(run.transactionId()))
                        .getSingleResult());
            }
        }
    }

    @Test
    void duplicateSameAssetAndDateAddsNoDurableRecords(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("duplicate")))
        {
            seedScope(jpa, SCA);
            FixedAssetService service = service(jpa, SCA.code());
            FixedAssetView asset = service.create(assetCommand(SCA, "Trailer"));
            service.runMonthlyDepreciation(asset.id(), RUN_DATE, "first");
            Counts beforeDuplicate = counts(jpa);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> service.runMonthlyDepreciation(asset.id(), RUN_DATE, "duplicate"));

            assertTrue(failure.getMessage().contains("already exists"));
            assertEquals(beforeDuplicate, counts(jpa));
        }
    }

    @Test
    void injectedFailureAfterCanonicalPersistenceRollsBackEverything(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("late-failure")))
        {
            seedScope(jpa, SCA);
            FixedAssetView asset = service(jpa, SCA.code()).create(assetCommand(SCA, "Trailer"));
            Counts before = counts(jpa);
            FixedAssetService failing = service(
                    jpa,
                    SCA.code(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "treasurer",
                    (em, ignoredAsset, ignoredTxn, ignoredDate, ignoredAmount, ignoredPortableId) -> {
                        em.flush();
                        throw new IllegalStateException("injected late persistence failure");
                    });

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> failing.runMonthlyDepreciation(asset.id(), RUN_DATE, "late failure"));

            assertTrue(failure.getMessage().contains("injected late persistence failure"));
            assertEquals(before, counts(jpa));
        }
    }

    @Test
    void runPortableIdentityFailureRollsBackTransactionSplitsAuditAndRun(@TempDir Path tempDir)
    {
        UUID runPortableId = UUID.randomUUID();
        try (Jpa jpa = new Jpa(tempDir.resolve("run-identity-failure")))
        {
            seedScope(jpa, SCA);
            FixedAssetView asset = service(jpa, SCA.code()).create(assetCommand(SCA, "Trailer"));
            Counts before = counts(jpa);
            FixedAssetService failing = service(
                    jpa,
                    SCA.code(),
                    UUID.randomUUID(),
                    runPortableId,
                    "treasurer",
                    (em, fixedAsset, transaction, ignoredDate, amount, portableId) -> em.createNativeQuery("""
                            insert into fixed_asset_depreciation_run
                                (portable_id, fixed_asset_id, run_date, depreciation_amount, transaction_id, notes)
                            values (cast(? as uuid), ?, ?, ?, ?, 'identity collision')
                            """)
                            .setParameter(1, portableId.toString())
                            .setParameter(2, fixedAsset.getId())
                            .setParameter(3, java.sql.Date.valueOf(RUN_DATE.minusMonths(1)))
                            .setParameter(4, amount)
                            .setParameter(5, transaction.getId())
                            .executeUpdate());

            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> failing.runMonthlyDepreciation(asset.id(), RUN_DATE, "identity failure"));

            assertTrue(deepMessage(failure).toLowerCase().contains("unique")
                    || deepMessage(failure).toLowerCase().contains("portable"));
            assertEquals(before, counts(jpa));
        }
    }

    @Test
    void factualAuditPersistenceFailureRollsBackEntireOperation(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("audit-failure")))
        {
            seedScope(jpa, SCA);
            FixedAssetView asset = service(jpa, SCA.code()).create(assetCommand(SCA, "Trailer"));
            Counts before = counts(jpa);
            FixedAssetService failing = service(
                    jpa,
                    SCA.code(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "A".repeat(201),
                    noOpHook());

            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> failing.runMonthlyDepreciation(asset.id(), RUN_DATE, "audit failure"));

            assertTrue(deepMessage(failure).toLowerCase().contains("actor")
                    || deepMessage(failure).toLowerCase().contains("value too long")
                    || deepMessage(failure).toLowerCase().contains("200"));
            assertEquals(before, counts(jpa));
        }
    }

    @Test
    void databasePeriodConstraintRaceCannotLeaveOrphanTransaction(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("constraint-race")))
        {
            seedScope(jpa, SCA);
            assertDepreciationRunConstraint(jpa);
            FixedAssetView asset = service(jpa, SCA.code()).create(assetCommand(SCA, "Trailer"));
            Counts before = counts(jpa);
            FixedAssetService racing = service(
                    jpa,
                    SCA.code(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "treasurer",
                    (em, fixedAsset, transaction, date, amount, ignoredPortableId) -> em.createNativeQuery("""
                            insert into fixed_asset_depreciation_run
                                (portable_id, fixed_asset_id, run_date, depreciation_amount, transaction_id, notes)
                            values (random_uuid(), ?, ?, ?, ?, 'simulated concurrent winner')
                            """)
                            .setParameter(1, fixedAsset.getId())
                            .setParameter(2, java.sql.Date.valueOf(date))
                            .setParameter(3, amount)
                            .setParameter(4, transaction.getId())
                            .executeUpdate());

            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> racing.runMonthlyDepreciation(asset.id(), RUN_DATE, "constraint race"));

            assertTrue(deepMessage(failure).toLowerCase().contains("unique")
                    || deepMessage(failure).contains("UQ_FIXED_ASSET_DEP_RUN_PERIOD"));
            assertEquals(before, counts(jpa));
        }
    }

    @Test
    void multiCompanyAssetOwnershipCannotBeBypassed(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("company-isolation")))
        {
            seedScope(jpa, SCA);
            seedScope(jpa, OTHER);
            FixedAssetService service = service(jpa, SCA.code());
            FixedAssetView otherAsset = service.create(assetCommand(OTHER, "Other trailer"));
            Counts before = counts(jpa);

            CompanyOwnershipException failure = assertThrows(CompanyOwnershipException.class,
                    () -> service.runMonthlyDepreciation(otherAsset.id(), RUN_DATE, "wrong company"));

            assertTrue(failure.getMessage().contains("not SCA"));
            assertEquals(before, counts(jpa));
        }
    }

    @Test
    void closedPeriodProtectionCannotBeBypassed(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("closed-period")))
        {
            seedScope(jpa, SCA);
            FixedAssetService service = service(jpa, SCA.code());
            FixedAssetView asset = service.create(assetCommand(SCA, "Trailer"));
            new PeriodCloseRangeService(jpa).closeRange(
                    SCA.code(), RUN_DATE, RUN_DATE, "CALCULATED", "treasurer", "month closed");
            Counts before = counts(jpa);

            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> service.runMonthlyDepreciation(asset.id(), RUN_DATE, "closed"));

            assertTrue(deepMessage(failure).toLowerCase().contains("closed"));
            assertEquals(before, counts(jpa));
        }
    }

    @Test
    void completedReconciliationProtectionCannotBeBypassed(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("completed-reconciliation")))
        {
            seedScope(jpa, SCA);
            FixedAssetService service = service(jpa, SCA.code());
            FixedAssetView asset = service.create(assetCommand(SCA, "Trailer"));
            DepreciationRunView completed = service.runMonthlyDepreciation(asset.id(), RUN_DATE, "completed");
            protectWithCompletedReconciliation(jpa, completed.transactionId());
            Counts before = counts(jpa);

            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> service.runMonthlyDepreciation(asset.id(), RUN_DATE, "protected"));

            assertTrue(deepMessage(failure).toLowerCase().contains("reconciliation"));
            assertEquals(before, counts(jpa));
        }
    }

    @Test
    void finalizedReconciliationProtectionCannotBeBypassed(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("finalized-reconciliation")))
        {
            seedScope(jpa, SCA);
            FixedAssetService service = service(jpa, SCA.code());
            FixedAssetView asset = service.create(assetCommand(SCA, "Trailer"));
            DepreciationRunView completed = service.runMonthlyDepreciation(asset.id(), RUN_DATE, "completed");
            protectWithFinalizedReconciliation(jpa, completed.transactionId());
            Counts before = counts(jpa);

            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> service.runMonthlyDepreciation(asset.id(), RUN_DATE, "protected"));

            assertTrue(deepMessage(failure).toLowerCase().contains("reconciliation"));
            assertEquals(before, counts(jpa));
        }
    }

    @Test
    void restartAfterLateFailureShowsNoPartialDepreciationActivity(@TempDir Path tempDir)
    {
        Path database = tempDir.resolve("restart-rollback");
        Counts expected;
        long assetId;
        try (Jpa jpa = new Jpa(database))
        {
            seedScope(jpa, SCA);
            assetId = service(jpa, SCA.code()).create(assetCommand(SCA, "Trailer")).id();
            expected = counts(jpa);
            FixedAssetService failing = service(
                    jpa,
                    SCA.code(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "treasurer",
                    (em, ignoredAsset, ignoredTxn, ignoredDate, ignoredAmount, ignoredPortableId) -> {
                        em.flush();
                        throw new IllegalStateException("restart failure");
                    });
            assertThrows(IllegalStateException.class,
                    () -> failing.runMonthlyDepreciation(assetId, RUN_DATE, "restart failure"));
        }

        try (Jpa reopened = new Jpa(database))
        {
            assertEquals(expected, counts(reopened));
            assertTrue(service(reopened, SCA.code()).listDepreciationRuns(SCA.code()).isEmpty());
        }
    }

    private static FixedAssetService service(Jpa jpa, String companyCode)
    {
        return new FixedAssetService(
                jpa,
                new TransactionEntryService(jpa, () -> companyCode),
                () -> companyCode);
    }

    private static FixedAssetService service(
            Jpa jpa,
            String companyCode,
            UUID transactionPortableId,
            UUID runPortableId,
            String actor,
            FixedAssetService.DepreciationWriteHook hook)
    {
        return new FixedAssetService(
                jpa,
                new TransactionEntryService(jpa, () -> companyCode),
                () -> companyCode,
                () -> transactionPortableId,
                () -> runPortableId,
                () -> actor,
                hook);
    }

    private static FixedAssetService.DepreciationWriteHook noOpHook()
    {
        return (em, asset, transaction, runDate, amount, runPortableId) -> { };
    }

    private static FixedAssetCommand assetCommand(Scope scope, String name)
    {
        return new FixedAssetCommand(
                scope.code(),
                scope.assetAccountId(),
                scope.accumulatedAccountId(),
                scope.expenseAccountId(),
                scope.fundId(),
                name,
                LocalDate.of(2026, 1, 1),
                new BigDecimal("840.0000"),
                BigDecimal.ZERO,
                84,
                FixedAsset.DepreciationMethod.STRAIGHT_LINE,
                BigDecimal.ZERO,
                FixedAsset.Status.ACTIVE,
                "Test asset");
    }

    private static void seedScope(Jpa jpa, Scope scope)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("insert into chart_of_accounts (id, name, version, status) values (?, ?, '1', 'ACTIVE')")
                    .setParameter(1, scope.chartId())
                    .setParameter(2, scope.code() + " Chart")
                    .executeUpdate();
            em.createNativeQuery("insert into company (id, code, display_name, active_chart_of_accounts_id) values (?, ?, ?, ?)")
                    .setParameter(1, scope.companyId())
                    .setParameter(2, scope.code())
                    .setParameter(3, scope.code() + " Branch")
                    .setParameter(4, scope.chartId())
                    .executeUpdate();
            em.createNativeQuery("update chart_of_accounts set company_id = ? where id = ?")
                    .setParameter(1, scope.companyId())
                    .setParameter(2, scope.chartId())
                    .executeUpdate();
            em.createNativeQuery("insert into fund (id, company_id, code, name, fund_type) values (?, ?, 'OPERATING', 'Operating', 'UNRESTRICTED')")
                    .setParameter(1, scope.fundId())
                    .setParameter(2, scope.companyId())
                    .executeUpdate();
            em.createNativeQuery("insert into account (id, chart_id, code, name, account_type, subtype, normal_balance) values (?, ?, '1500', 'Equipment', 'ASSET', 'FIXED_ASSET', 'DEBIT')")
                    .setParameter(1, scope.assetAccountId())
                    .setParameter(2, scope.chartId())
                    .executeUpdate();
            em.createNativeQuery("insert into account (id, chart_id, code, name, account_type, subtype, normal_balance) values (?, ?, '1590', 'Accumulated Depreciation', 'ASSET', 'FIXED_ASSET', 'CREDIT')")
                    .setParameter(1, scope.accumulatedAccountId())
                    .setParameter(2, scope.chartId())
                    .executeUpdate();
            em.createNativeQuery("insert into account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) values (?, ?, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .setParameter(1, scope.checkingAccountId())
                    .setParameter(2, scope.chartId())
                    .executeUpdate();
            em.createNativeQuery("insert into account (id, chart_id, code, name, account_type, normal_balance) values (?, ?, '6100', 'Depreciation Expense', 'EXPENSE', 'DEBIT')")
                    .setParameter(1, scope.expenseAccountId())
                    .setParameter(2, scope.chartId())
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void protectWithCompletedReconciliation(Jpa jpa, long transactionId)
    {
        UUID runId = UUID.randomUUID();
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("""
                    insert into reconciliation_run
                        (id, group_code, statement_ending_on, bank_format, imported_transaction_count, status)
                    values (cast(? as uuid), 'SCA', ?, 'OFX', 1, 'COMPLETED')
                    """)
                    .setParameter(1, runId.toString())
                    .setParameter(2, java.sql.Date.valueOf(RUN_DATE))
                    .executeUpdate();
            em.createNativeQuery("""
                    insert into txn_reconciliation_protection
                        (txn_id, reconciliation_run_id, protected_by, notes)
                    values (?, cast(? as uuid), 'treasurer', 'test protection')
                    """)
                    .setParameter(1, transactionId)
                    .setParameter(2, runId.toString())
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void protectWithFinalizedReconciliation(Jpa jpa, long transactionId)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            long splitId = number(em, "select min(id) from txn_split where txn_id = ?", transactionId);
            em.createNativeQuery("""
                    insert into company_bank_account
                        (id, company_id, name, institution_name, account_type, last_four)
                    values (30001, ?, 'Checking', 'Test Bank', 'CHECKING', '1234')
                    """)
                    .setParameter(1, SCA.companyId())
                    .executeUpdate();
            em.createNativeQuery("""
                    insert into bank_reconciliation_session
                        (id, company_id, bank_account_id, statement_start_date, statement_end_date,
                         mismatch_policy, status)
                    values (30001, ?, 30001, ?, ?, 'WARN_ONLY', 'FINALIZED')
                    """)
                    .setParameter(1, SCA.companyId())
                    .setParameter(2, java.sql.Date.valueOf(RUN_DATE.withDayOfMonth(1)))
                    .setParameter(3, java.sql.Date.valueOf(RUN_DATE))
                    .executeUpdate();
            em.createNativeQuery("""
                    insert into bank_reconciliation_match
                        (session_id, txn_split_id, match_status, resolution_note)
                    values (30001, ?, 'MATCHED', 'test protection')
                    """)
                    .setParameter(1, splitId)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void assertDepreciationRunConstraint(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            long count = ((Number) em.createNativeQuery("""
                    select count(*)
                    from information_schema.table_constraints
                    where upper(table_name) = 'FIXED_ASSET_DEPRECIATION_RUN'
                      and upper(constraint_name) = 'UQ_FIXED_ASSET_DEP_RUN_PERIOD'
                      and constraint_type = 'UNIQUE'
                    """).getSingleResult()).longValue();
            assertEquals(1L, count);
        }
    }

    private static Counts counts(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return new Counts(
                    number(em, "select count(*) from txn"),
                    number(em, "select count(*) from txn_split"),
                    number(em, "select count(*) from fixed_asset_depreciation_run"),
                    number(em, "select count(*) from audit_event"),
                    number(em, "select count(*) from txn where portable_id is not null"),
                    number(em, "select count(*) from fixed_asset_depreciation_run where portable_id is not null"));
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
        if (value == null)
        {
            return 0L;
        }
        return ((Number) value).longValue();
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

    private record Counts(long txns, long splits, long runs, long audits, long txnPortableIds, long runPortableIds)
    {
    }

    private record Scope(
            long companyId,
            long chartId,
            long fundId,
            long assetAccountId,
            long accumulatedAccountId,
            long checkingAccountId,
            long expenseAccountId,
            String code)
    {
    }
}
