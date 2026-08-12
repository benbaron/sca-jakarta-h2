package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.InventoryMovement;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.report.AssetInventoryReportQueryService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P16-S17 closure scenario across one migrated file, two companies, and a restart boundary.
 *
 * <p>Focused service tests retain the exhaustive edge-case coverage for each authority. This
 * scenario proves those authorities can coexist in one production-shaped database without
 * leaking company scope or retaining partial writes after a late failure.</p>
 */
class P16EndToEndClosureTest
{
    private static final Scope ALPHA = new Scope(71_001L, 71_001L, 71_001L, 71_001L,
            71_002L, 71_003L, 71_004L, 71_005L, 71_006L, 71_007L, 71_001L, "ALPHA");
    private static final Scope BETA = new Scope(72_001L, 72_001L, 72_001L, 72_001L,
            72_002L, 72_003L, 72_004L, 72_005L, 72_006L, 72_007L, 72_001L, "BETA");
    private static final LocalDate ACTIVITY_DATE = LocalDate.of(2026, 6, 15);

    @Test
    void authoritiesRemainAtomicCompanyScopedAndDurableAcrossRestart(@TempDir Path tempDir)
            throws Exception
    {
        Path database = tempDir.resolve("p16-s17-closure");
        Path csv = tempDir.resolve("late-failure.csv");
        Files.writeString(csv, """
                code,name,account_type,normal_balance,parent_code
                9900,Temporary Parent,ASSET,DEBIT,
                9910,Temporary Child,ASSET,DEBIT,9900
                """);

        long alphaAssetId;
        long betaAssetId;
        long alphaItemId;
        long bankTransactionId;
        try (Jpa jpa = new Jpa(database))
        {
            seed(jpa, ALPHA);
            seed(jpa, BETA);

            CoaCsvImportService failingCoa = new CoaCsvImportService(jpa, ALPHA::code, writes ->
            {
                if (writes == 1)
                {
                    throw new IllegalStateException("S17 injected late COA failure");
                }
            });
            CoaCsvImportService.CoaCsvBatchCommitResult failedImport = failingCoa.commit(
                    failingCoa.preview(csv).confirmedCopy(), "architect");
            assertFalse(failedImport.committed());
            assertTrue(failedImport.rolledBack());
            assertEquals(0L, number(jpa,
                    "select count(*) from account where code in ('9900', '9910')"));
            assertEquals(0L, number(jpa,
                    "select count(*) from interchange_identity where format_code = 'COA_CSV'"));

            FixedAssetService alphaAssets = fixedAssets(jpa, ALPHA.code());
            FixedAssetView alphaAsset = alphaAssets.create(asset(ALPHA, "Alpha delivery trailer"));
            alphaAssetId = alphaAsset.id();
            alphaAssets.runMonthlyDepreciation(
                    alphaAsset.id(), ACTIVITY_DATE, "S17 monthly depreciation");
            FixedAssetView betaAsset = fixedAssets(jpa, BETA.code())
                    .create(asset(BETA, "Beta delivery trailer"));
            betaAssetId = betaAsset.id();

            InventoryService alphaInventory = inventory(jpa, ALPHA.code());
            InventoryItemView alphaItem = alphaInventory.create(item(ALPHA, "Alpha event kits"));
            alphaItemId = alphaItem.id();
            InventoryService.MovementPreview movement = alphaInventory.previewMovement(
                    alphaItem.id(), new InventoryMovementCommand(
                            InventoryMovement.MovementType.RECEIPT,
                            new BigDecimal("4.0000"),
                            ACTIVITY_DATE,
                            ALPHA.inventoryExpenseAccountId(),
                            false,
                            "S17 inventory receipt"));
            alphaInventory.recordMovement(movement, "architect");
            inventory(jpa, BETA.code()).create(item(BETA, "Beta event kits"));

            BudgetPlanService alphaBudgets = new BudgetPlanService(jpa, ALPHA::code);
            BudgetPlanView draft = alphaBudgets.createDraft(budget("closure-v1"));
            alphaBudgets.replaceDraftLines(draft.id(), List.of(new BudgetLineCommand(
                    ALPHA.budgetCategoryId(),
                    ALPHA.fundId(),
                    null,
                    new BigDecimal("1200.0000"),
                    "Annual program budget")));
            assertEquals(BudgetPlan.Status.ACTIVE, alphaBudgets.activate(draft.id()).status());
            assertTrue(new BudgetPlanService(jpa, BETA::code).activeForFiscalYear(2026).isEmpty());

            TransactionEntryService entries = new TransactionEntryService(jpa, ALPHA::code);
            TransactionView bankTransaction = entries.enter(new TransactionCommand(
                    ACTIVITY_DATE,
                    null,
                    "S17 bank transaction for reconciliation",
                    ALPHA.checkingAccountId(),
                    List.of(
                            line(ALPHA.checkingAccountId(), ALPHA.fundId(), "25.0000", true),
                            line(ALPHA.incomeAccountId(), ALPHA.fundId(), "25.0000", false))));
            bankTransactionId = bankTransaction.id();
            finalizeBankTransaction(jpa, ALPHA, bankTransaction);

            TransactionView finalized = entries.load(bankTransaction.id());
            assertEquals(TransactionView.ClearedState.CLEARED, finalized.clearedState());
            assertTrue(finalized.lines().stream()
                    .filter(TransactionView.Line::bankAccount)
                    .allMatch(line -> line.reconciliationSessionId() != null));
            assertThrows(IllegalStateException.class, () -> new TransactionCorrectionService(
                    jpa, ALPHA::code).reverse(
                    bankTransaction.id(), ACTIVITY_DATE.plusDays(1), "architect", "blocked", false));

            new PeriodCloseRangeService(jpa).closeRange(
                    ALPHA.code(),
                    ACTIVITY_DATE.plusDays(2),
                    ACTIVITY_DATE.plusDays(2),
                    "CUSTOM",
                    "architect",
                    "S17 closed-date protection");
            assertThrows(ClosedPeriodRangeException.class, () -> entries.enter(new TransactionCommand(
                    ACTIVITY_DATE.plusDays(2),
                    null,
                    "Must not enter in closed date",
                    null,
                    List.of(
                            line(ALPHA.expenseAccountId(), ALPHA.fundId(), "5.0000", true),
                            line(ALPHA.incomeAccountId(), ALPHA.fundId(), "5.0000", false)))));

            AtomicReference<String> activeCompany = new AtomicReference<>(ALPHA.code());
            AssetInventoryReportQueryService reports =
                    new AssetInventoryReportQueryService(jpa, activeCompany::get);
            assertEquals(1, reports.filterCatalog().assets().size());
            assertEquals(1, reports.filterCatalog().inventoryItems().size());
            activeCompany.set(BETA.code());
            assertEquals(1, reports.filterCatalog().assets().size());
            assertEquals(1, reports.filterCatalog().inventoryItems().size());

            assertTrue(new AuditHistoryService(jpa, ALPHA::code).listRecent(
                    AuditHistoryService.AuditHistoryFilter.empty(), 100).stream()
                    .anyMatch(row -> row.actionType().equals("TRANSACTION_ENTERED")));
            assertEquals(0L, number(jpa,
                    "select count(*) from txn where company_id = ?", BETA.companyId()));
        }

        try (Jpa reopened = new Jpa(database))
        {
            assertEquals(0L, number(reopened,
                    "select count(*) from account where code in ('9900', '9910')"));
            assertEquals(1, fixedAssets(reopened, ALPHA.code()).listDepreciationRuns(ALPHA.code()).size());
            assertEquals(alphaAssetId,
                    fixedAssets(reopened, ALPHA.code()).load(alphaAssetId).id());
            assertEquals(betaAssetId,
                    fixedAssets(reopened, BETA.code()).load(betaAssetId).id());
            assertEquals(new BigDecimal("4.0000"),
                    inventory(reopened, ALPHA.code()).load(alphaItemId).quantity());
            assertEquals(BudgetPlan.Status.ACTIVE,
                    new BudgetPlanService(reopened, ALPHA::code)
                            .activeForFiscalYear(2026).orElseThrow().status());
            assertEquals(TransactionView.ClearedState.CLEARED,
                    new TransactionEntryService(reopened, ALPHA::code)
                            .load(bankTransactionId).clearedState());
            assertTrue(new AuditHistoryService(reopened, ALPHA::code).listRecent(
                    AuditHistoryService.AuditHistoryFilter.empty(), 100).size() >= 4);
        }
    }

    private static FixedAssetService fixedAssets(Jpa jpa, String companyCode)
    {
        return new FixedAssetService(
                jpa,
                new TransactionEntryService(jpa, () -> companyCode),
                () -> companyCode);
    }

    private static InventoryService inventory(Jpa jpa, String companyCode)
    {
        return new InventoryService(
                jpa,
                new TransactionEntryService(jpa, () -> companyCode),
                new TransactionCorrectionService(jpa, () -> companyCode),
                () -> companyCode);
    }

    private static FixedAssetCommand asset(Scope scope, String name)
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
                "P16-S17 closure asset");
    }

    private static InventoryItemCommand item(Scope scope, String name)
    {
        return new InventoryItemCommand(
                scope.code(),
                scope.inventoryAccountId(),
                scope.fundId(),
                name,
                "Supplies",
                BigDecimal.ZERO,
                "each",
                new BigDecimal("3.0000"),
                LocalDate.of(2026, 1, 1),
                "",
                "",
                InventoryItem.Condition.GOOD,
                InventoryItem.Status.ACTIVE,
                "P16-S17 closure inventory");
    }

    private static BudgetPlanCommand budget(String version)
    {
        return new BudgetPlanCommand(
                "FY2026 " + version,
                2026,
                version,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "P16-S17 closure budget");
    }

    private static TransactionLineCommand line(long accountId, long fundId, String amount, boolean debit)
    {
        BigDecimal value = new BigDecimal(amount);
        return new TransactionLineCommand(
                accountId,
                fundId,
                null,
                null,
                null,
                debit ? value : BigDecimal.ZERO,
                debit ? BigDecimal.ZERO : value,
                false,
                null);
    }

    private static void finalizeBankTransaction(Jpa jpa, Scope scope, TransactionView transaction)
    {
        long splitId = transaction.lines().stream()
                .filter(line -> line.accountId() == scope.checkingAccountId())
                .findFirst()
                .orElseThrow()
                .id();
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("""
                    insert into company_bank_account
                        (id, company_id, name, account_type, account_id)
                    values (?, ?, 'Closure Checking', 'CHECKING', ?)
                    """)
                    .setParameter(1, scope.companyBankAccountId())
                    .setParameter(2, scope.companyId())
                    .setParameter(3, scope.checkingAccountId())
                    .executeUpdate();
            em.createNativeQuery("""
                    insert into bank_reconciliation_session
                        (id, company_id, bank_account_id, statement_start_date, statement_end_date,
                         mismatch_policy, status)
                    values (?, ?, ?, ?, ?, 'WARN_ONLY', 'FINALIZED')
                    """)
                    .setParameter(1, scope.companyBankAccountId())
                    .setParameter(2, scope.companyId())
                    .setParameter(3, scope.companyBankAccountId())
                    .setParameter(4, java.sql.Date.valueOf(ACTIVITY_DATE.withDayOfMonth(1)))
                    .setParameter(5, java.sql.Date.valueOf(ACTIVITY_DATE.withDayOfMonth(30)))
                    .executeUpdate();
            em.createNativeQuery("""
                    insert into bank_reconciliation_match
                        (session_id, txn_split_id, match_status, resolution_note)
                    values (?, ?, 'MATCHED', 'P16-S17 closure')
                    """)
                    .setParameter(1, scope.companyBankAccountId())
                    .setParameter(2, splitId)
                    .executeUpdate();
            em.createNativeQuery("""
                    update txn_split
                    set bank_cleared = true, bank_cleared_on = ?
                    where id = ?
                    """)
                    .setParameter(1, java.sql.Date.valueOf(ACTIVITY_DATE))
                    .setParameter(2, splitId)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void seed(Jpa jpa, Scope scope)
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
            em.createNativeQuery("insert into budget_category (id, company_id, code, name, is_active) values (?, ?, 'PROGRAM', 'Program Services', true)")
                    .setParameter(1, scope.budgetCategoryId())
                    .setParameter(2, scope.companyId())
                    .executeUpdate();
            account(em, scope.assetAccountId(), scope.chartId(), "1500", "Equipment", "ASSET", "FIXED_ASSET", "DEBIT");
            account(em, scope.accumulatedAccountId(), scope.chartId(), "1590", "Accumulated Depreciation", "ASSET", "FIXED_ASSET", "CREDIT");
            account(em, scope.checkingAccountId(), scope.chartId(), "1000", "Checking", "BANK", "CASH", "DEBIT");
            account(em, scope.expenseAccountId(), scope.chartId(), "6100", "Depreciation Expense", "EXPENSE", null, "DEBIT");
            account(em, scope.inventoryAccountId(), scope.chartId(), "1300", "Inventory", "ASSET", "INVENTORY", "DEBIT");
            account(em, scope.inventoryExpenseAccountId(), scope.chartId(), "5000", "Inventory Expense", "EXPENSE", null, "DEBIT");
            account(em, scope.incomeAccountId(), scope.chartId(), "4000", "Contributions", "INCOME", null, "CREDIT");
            em.getTransaction().commit();
        }
    }

    private static void account(
            EntityManager em,
            long id,
            long chartId,
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
                .setParameter(2, chartId)
                .setParameter(3, code)
                .setParameter(4, name)
                .setParameter(5, type)
                .setParameter(6, subtype)
                .setParameter(7, normalBalance)
                .executeUpdate();
    }

    private static long number(Jpa jpa, String sql, Object... parameters)
    {
        try (EntityManager em = jpa.em())
        {
            var query = em.createNativeQuery(sql);
            for (int index = 0; index < parameters.length; index++)
            {
                query.setParameter(index + 1, parameters[index]);
            }
            return ((Number) query.getSingleResult()).longValue();
        }
    }

    private record Scope(
            long companyId,
            long chartId,
            long fundId,
            long assetAccountId,
            long accumulatedAccountId,
            long checkingAccountId,
            long expenseAccountId,
            long inventoryAccountId,
            long inventoryExpenseAccountId,
            long incomeAccountId,
            long budgetCategoryId,
            String code)
    {
        long companyBankAccountId()
        {
            return companyId + 500;
        }
    }
}
