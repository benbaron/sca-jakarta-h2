package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.InventoryMovement;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import org.nonprofitbookkeeping.interchange.sclx.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryMovementAccountingTest
{
    private static final String COMPANY = "SCA";
    private static final long CHART_ID = 31_001L;
    private static final long COMPANY_ID = 31_001L;
    private static final long FUND_ID = 31_001L;
    private static final long INVENTORY_ACCOUNT_ID = 31_001L;
    private static final long OFFSET_ACCOUNT_ID = 31_002L;
    private static final long BANK_OFFSET_ACCOUNT_ID = 31_003L;
    private static final LocalDate MOVEMENT_DATE = LocalDate.of(2026, 5, 15);

    @Test
    void previewIsNonMutatingAndConfirmedFinancialMovementCommitsOneAtomicOperation(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-atomic-success")))
        {
            seed(jpa);
            InventoryService service = service(jpa, COMPANY);
            InventoryItemView item = service.create(itemCommand("Food boxes", new BigDecimal("2.3456")));
            Counts before = counts(jpa);

            InventoryService.MovementPreview preview = service.previewMovement(
                    item.id(), command(InventoryMovement.MovementType.RECEIPT, "3.0000", "Shipment"));

            assertEquals(before, counts(jpa));
            assertTrue(preview.financial());
            assertEquals(new BigDecimal("7.0368"), preview.extendedValue());
            assertEquals(new BigDecimal("3.0000"), preview.quantityAfter());
            assertEquals(2, preview.transactionCommand().lines().size());

            InventoryMovementView movement = service.recordMovement(preview, "treasurer");

            Counts after = counts(jpa);
            assertEquals(before.items(), after.items());
            assertEquals(before.movements() + 1, after.movements());
            assertEquals(before.txns() + 1, after.txns());
            assertEquals(before.splits() + 2, after.splits());
            assertEquals(before.audits() + 2, after.audits());
            assertNotNull(movement.transactionId());
            assertEquals(new BigDecimal("3.0000"), service.load(item.id()).quantity());
            assertEquals(movement.id(), service.recordMovement(preview, "treasurer").id());
            assertEquals(after, counts(jpa));
        }
    }

    @Test
    void stalePreviewAndInjectedLateFailureLeaveNoPartialQuantityOrLedger(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-atomic-failure")))
        {
            seed(jpa);
            InventoryService normal = service(jpa, COMPANY);
            InventoryItemView item = normal.create(itemCommand("Blankets", new BigDecimal("5.0000")));
            InventoryService.MovementPreview stale = normal.previewMovement(
                    item.id(), command(InventoryMovement.MovementType.RECEIPT, "2.0000", "First preview"));
            normal.recordMovement(normal.previewMovement(
                    item.id(), command(InventoryMovement.MovementType.RECEIPT, "1.0000", "Winner")), "treasurer");
            Counts afterWinner = counts(jpa);

            assertThrows(IllegalStateException.class, () -> normal.recordMovement(stale, "treasurer"));
            assertEquals(afterWinner, counts(jpa));

            seedTags(jpa);
            InventoryService failing = new InventoryService(
                    jpa,
                    new TransactionEntryService(jpa, () -> COMPANY),
                    new TransactionCorrectionService(jpa, () -> COMPANY),
                    () -> COMPANY,
                    UUID::randomUUID,
                    UUID::randomUUID,
                    (em, ignoredItem, ignoredTransaction, ignoredPreview) -> {
                        em.flush();
                        throw new IllegalStateException("injected inventory late failure");
                    });
            InventoryService.MovementPreview lateFailure = failing.previewMovement(
                    item.id(), new InventoryMovementCommand(InventoryMovement.MovementType.ISSUE, BigDecimal.ONE,
                            MOVEMENT_DATE, OFFSET_ACCOUNT_ID, false, "Late failure", 31001L, 31001L, false));

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class, () -> failing.recordMovement(lateFailure, "treasurer"));
            assertTrue(failure.getMessage().contains("injected inventory late failure"));
            assertEquals(afterWinner, counts(jpa));
            assertEquals(new BigDecimal("1.0000"), normal.load(item.id()).quantity());
        }
    }

    @Test
    void companyCloseNegativeAndZeroValuePoliciesAreEnforced(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-policies")))
        {
            seed(jpa);
            InventoryService service = service(jpa, COMPANY);
            InventoryItemView valued = service.create(itemCommand("Cups", new BigDecimal("1.0000")));
            assertThrows(IllegalArgumentException.class, () -> service.previewMovement(
                    valued.id(), command(InventoryMovement.MovementType.ISSUE, "1.0000", "Negative")));

            InventoryService otherCompany = service(jpa, "DEFAULT");
            assertThrows(IllegalStateException.class, () -> otherCompany.previewMovement(
                    valued.id(), command(InventoryMovement.MovementType.RECEIPT, "1.0000", "Other")));

            new PeriodCloseRangeService(jpa).closeRange(
                    COMPANY, MOVEMENT_DATE, MOVEMENT_DATE, "CUSTOM", "treasurer", "Inventory close test");
            assertThrows(ClosedPeriodRangeException.class, () -> service.previewMovement(
                    valued.id(), command(InventoryMovement.MovementType.RECEIPT, "1.0000", "Closed")));
        }

        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-nonfinancial")))
        {
            seed(jpa);
            InventoryService service = service(jpa, COMPANY);
            InventoryItemView zeroValue = service.create(zeroValueItemCommand("Donated signs"));
            assertThrows(IllegalArgumentException.class, () -> service.previewMovement(
                    zeroValue.id(), new InventoryMovementCommand(
                            InventoryMovement.MovementType.RECEIPT,
                            BigDecimal.ONE,
                            MOVEMENT_DATE,
                            null,
                            false,
                            "Not confirmed", null, null, true)));
            InventoryService.MovementPreview preview = service.previewMovement(
                    zeroValue.id(), new InventoryMovementCommand(
                            InventoryMovement.MovementType.RECEIPT,
                            BigDecimal.ONE,
                            MOVEMENT_DATE,
                            null,
                            true,
                            "Confirmed nonfinancial", null, null, true));
            InventoryMovementView movement = service.recordMovement(preview, "custodian");
            assertNull(movement.transactionId());
            assertEquals(0L, count(jpa, "txn"));
        }

        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-finalized-reconciliation")))
        {
            seed(jpa);
            insertFinalizedBankRange(jpa);
            InventoryService service = service(jpa, COMPANY);
            InventoryItemView item = service.create(itemCommand("Festival stock", BigDecimal.ONE));
            assertThrows(IllegalStateException.class, () -> service.previewMovement(
                    item.id(), new InventoryMovementCommand(
                            InventoryMovement.MovementType.RECEIPT,
                            BigDecimal.ONE,
                            MOVEMENT_DATE,
                            BANK_OFFSET_ACCOUNT_ID,
                            false,
                            "Inside finalized range", null, null, true)));
        }
    }

    @Test
    void financialMovementCorrectionUsesCanonicalReversalAndSurvivesRestart(@TempDir Path tempDir)
    {
        Path database = tempDir.resolve("inventory-reversal-restart");
        long itemId;
        long originalTransactionId;
        try (Jpa jpa = new Jpa(database))
        {
            seed(jpa);
            InventoryService service = service(jpa, COMPANY);
            InventoryItemView item = service.create(itemCommand("Meal kits", new BigDecimal("4.0000")));
            itemId = item.id();
            InventoryMovementView original = service.recordMovement(
                    service.previewMovement(item.id(), command(
                            InventoryMovement.MovementType.RECEIPT, "2.0000", "Incorrect receipt")),
                    "treasurer");
            originalTransactionId = original.transactionId();

            InventoryService.MovementReversalPreview preview = service.previewMovementReversal(
                    original.id(), MOVEMENT_DATE.plusDays(1), "Receipt entered in error");
            InventoryMovementView reversal = service.reverseMovement(preview, "treasurer");

            assertNotNull(reversal.transactionId());
            assertEquals(new BigDecimal("0.0000"), service.load(item.id()).quantity());
            assertThrows(IllegalStateException.class, () -> service.previewMovementReversal(
                    original.id(), MOVEMENT_DATE.plusDays(2), "Duplicate reversal"));
            try (EntityManager em = jpa.em())
            {
                assertEquals("REVERSED", em.createNativeQuery("select status from txn where id = ?")
                        .setParameter(1, originalTransactionId)
                        .getSingleResult());
                assertEquals(originalTransactionId, ((Number) em.createNativeQuery(
                                "select reversal_of_txn_id from txn where id = ?")
                        .setParameter(1, reversal.transactionId())
                        .getSingleResult()).longValue());
            }
        }

        try (Jpa reopened = new Jpa(database))
        {
            InventoryService service = service(reopened, COMPANY);
            assertEquals(new BigDecimal("0.0000"), service.load(itemId).quantity());
            assertEquals(2, service.listMovements(COMPANY).size());
            assertEquals(2L, count(reopened, "txn"));
            assertEquals(4L, count(reopened, "txn_split"));
        }
    }

    @Test
    void eventCostBudgetReversalAndSclxRoundTripUseCanonicalSplits(@TempDir Path tempDir) throws Exception
    {
        Path sourceDatabase = tempDir.resolve("event-cost-source");
        Path exchange = tempDir.resolve("event-cost.sclx");
        long itemId;
        try (Jpa jpa = new Jpa(sourceDatabase))
        {
            seed(jpa);
            seedTags(jpa);
            InventoryService inventory = service(jpa, COMPANY);
            var item = inventory.create(itemCommand("Event stock", new BigDecimal("20")));
            itemId = item.id();
            inventory.recordMovement(inventory.previewMovement(item.id(), new InventoryMovementCommand(
                    InventoryMovement.MovementType.RECEIPT, BigDecimal.TEN, MOVEMENT_DATE,
                    BANK_OFFSET_ACCOUNT_ID, false, "Stock received", null, null, true)), "tester");
            TransactionEntryService journal = new TransactionEntryService(jpa, () -> COMPANY);
            journal.enter(new TransactionCommand(MOVEMENT_DATE, null, "Event sales", null, List.of(
                    new TransactionLineCommand(BANK_OFFSET_ACCOUNT_ID, FUND_ID, null, null, null,
                            new BigDecimal("500"), BigDecimal.ZERO, false, null),
                    new TransactionLineCommand(31004L, FUND_ID, null, 31001L, null,
                            BigDecimal.ZERO, new BigDecimal("500"), false, null))));
            var preview = inventory.previewMovement(item.id(), new InventoryMovementCommand(
                    InventoryMovement.MovementType.ISSUE, BigDecimal.TEN, MOVEMENT_DATE,
                    OFFSET_ACCOUNT_ID, false, "Event cost", 31001L, 31001L, false));
            assertEquals("FAIR — Autumn Fair", preview.eventLabel());
            assertNull(preview.transactionCommand().lines().get(0).activityId());
            assertNull(preview.transactionCommand().lines().get(0).budgetCategoryId());
            assertEquals(31001L, preview.transactionCommand().lines().get(1).activityId());
            assertEquals(31001L, preview.transactionCommand().lines().get(1).budgetCategoryId());
            var movement = inventory.recordMovement(preview, "tester");
            assertEquals("FAIR — Autumn Fair", movement.events());
            assertEquals("COST — Event Cost", movement.budgetCategories());
            assertEquals(movement.id(), inventory.recordMovement(preview, "tester").id());
            var totals = new EventAccountingQueryService(jpa, () -> COMPANY)
                    .workspace(31001L, MOVEMENT_DATE, MOVEMENT_DATE, FUND_ID).summary();
            assertEquals(new BigDecimal("500.0000"), totals.income());
            assertEquals(new BigDecimal("200.0000"), totals.expenses());
            assertEquals(new BigDecimal("300.0000"), totals.net());
            BudgetPlanService budgets = new BudgetPlanService(jpa, () -> COMPANY);
            var plan = budgets.createDraft(new BudgetPlanCommand("Event budget", 2026, "v1",
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), ""));
            budgets.replaceDraftLines(plan.id(), List.of(new BudgetLineCommand(31001L, FUND_ID, null, new BigDecimal("200"), "")));
            budgets.activate(plan.id());
            assertEquals(new BigDecimal("200.0000"), budgets.activeVariance(MOVEMENT_DATE).stream()
                    .filter(row -> "COST".equals(row.budgetCategoryCode())).findFirst().orElseThrow().actual());
            assertEquals(new BigDecimal("0.0000"), inventory.load(item.id()).quantity());
            var reversal = inventory.reverseMovement(inventory.previewMovementReversal(
                    movement.id(), MOVEMENT_DATE.plusDays(1), "Return unused stock"), "tester");
            assertEquals(movement.events(), reversal.events());
            assertEquals(movement.budgetCategories(), reversal.budgetCategories());
            assertEquals(new BigDecimal("10.0000"), inventory.load(item.id()).quantity());
            assertEquals(new BigDecimal("0.0000"), new EventAccountingQueryService(jpa, () -> COMPANY)
                    .workspace(31001L, MOVEMENT_DATE, MOVEMENT_DATE.plusDays(1), FUND_ID).summary().expenses());
            assertEquals(new BigDecimal("0.0000"), budgets.activeVariance(MOVEMENT_DATE.plusDays(1)).stream()
                    .filter(row -> "COST".equals(row.budgetCategoryCode())).findFirst().orElseThrow().actual());
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("update activity set name = 'Historic Fair', is_active = false where id = 31001").executeUpdate();
                em.getTransaction().commit();
            }
            new SclxFileExportService(new SclxCoreSnapshotQueryService(jpa, () -> COMPANY), () -> sourceDatabase)
                    .export(new SclxExportRequest(exchange, java.time.Instant.now(), false));
        }
        try (Jpa reopened = new Jpa(sourceDatabase))
        {
            assertEquals(new BigDecimal("10.0000"), service(reopened, COMPANY).load(itemId).quantity());
            assertEquals(2, service(reopened, COMPANY).listMovements(COMPANY).stream()
                    .filter(row -> "FAIR — Historic Fair".equals(row.events())).count());
        }
        try (Jpa target = new Jpa(tempDir.resolve("event-cost-target")))
        {
            try (EntityManager em = target.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("insert into company (id, code, display_name) values (31001, 'SCA', 'Target')").executeUpdate();
                em.getTransaction().commit();
            }
            var previews = new SclxImportPreviewService(target, () -> COMPANY);
            var preview = previews.preview(exchange);
            assertTrue(!preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            var importer = new SclxImportCommitService(target, () -> COMPANY);
            var result = importer.commit(exchange, preview, "tester");
            assertTrue(result.committed(), result.toString());
            assertEquals(3, service(target, COMPANY).listMovements(COMPANY).size());
            // SCLX preserves category codes; a new target initializes the category name from its code.
            assertEquals(2, service(target, COMPANY).listMovements(COMPANY).stream()
                    .filter(row -> "FAIR — Historic Fair".equals(row.events()) && "COST — COST".equals(row.budgetCategories())).count(),
                    () -> service(target, COMPANY).listMovements(COMPANY).toString());
            assertEquals(new BigDecimal("200.0000"), new BudgetPlanService(target, () -> COMPANY)
                    .activeVariance(MOVEMENT_DATE).stream().filter(row -> "COST".equals(row.budgetCategoryCode()))
                    .findFirst().orElseThrow().actual());
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var changed = mapper.readTree(java.nio.file.Files.readAllBytes(exchange));
            ((com.fasterxml.jackson.databind.node.ObjectNode) changed.path("extensions").path("scaJakartaH2")
                    .path("transactionBudgets").path("lines").get(0)).put("categoryCode", "DIFFERENT");
            Path changedExchange = tempDir.resolve("changed-budget.sclx");
            mapper.writeValue(changedExchange.toFile(), changed);
            var conflict = previews.preview(changedExchange);
            assertTrue(conflict.operation().items().stream().anyMatch(row ->
                    row.identityMatch() == org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch.CONFLICT));
            assertTrue(importer.commit(exchange, previews.preview(exchange), "tester").committed());
            assertEquals(3, service(target, COMPANY).listMovements(COMPANY).size());
        }
    }

    @Test
    void attributionRequiresExplicitChoiceAndRevalidatesBeforeAtomicCommit(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("tag-policies")))
        {
            seed(jpa);
            seedTags(jpa);
            var inventory = service(jpa, COMPANY);
            var item = inventory.create(itemCommand("Stock", BigDecimal.TEN));
            var missing = new InventoryMovementCommand(InventoryMovement.MovementType.RECEIPT, BigDecimal.ONE,
                    MOVEMENT_DATE, OFFSET_ACCOUNT_ID, false, "Missing choice", null, null, false);
            assertThrows(IllegalArgumentException.class, () -> inventory.previewMovement(item.id(), missing));
            var both = new InventoryMovementCommand(InventoryMovement.MovementType.RECEIPT, BigDecimal.ONE,
                    MOVEMENT_DATE, OFFSET_ACCOUNT_ID, false, "Conflicting choice", 31001L, null, true);
            assertThrows(IllegalArgumentException.class, () -> inventory.previewMovement(item.id(), both));
            var tagged = new InventoryMovementCommand(InventoryMovement.MovementType.RECEIPT, BigDecimal.ONE,
                    MOVEMENT_DATE, OFFSET_ACCOUNT_ID, false, "Tagged", 31001L, 31001L, false);
            var preview = inventory.previewMovement(item.id(), tagged);
            Counts before = counts(jpa);
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("update activity set is_active = false where id = 31001").executeUpdate();
                em.getTransaction().commit();
            }
            assertThrows(IllegalStateException.class, () -> inventory.recordMovement(preview, "tester"));
            assertEquals(before, counts(jpa));
            assertEquals(new BigDecimal("0.0000"), inventory.load(item.id()).quantity());
            var zero = inventory.create(zeroValueItemCommand("Zero value"));
            assertThrows(IllegalArgumentException.class, () -> inventory.previewMovement(zero.id(),
                    new InventoryMovementCommand(InventoryMovement.MovementType.RECEIPT, BigDecimal.ONE,
                            MOVEMENT_DATE, null, true, "No ledger", 31001L, null, false)));
        }
    }

    @Test
    void attributionRejectsForeignAndIneligibleReferencesWithoutWrites(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("tag-eligibility")))
        {
            seed(jpa);
            seedTags(jpa);
            var inventory = service(jpa, COMPANY);
            var item = inventory.create(itemCommand("Stock", BigDecimal.TEN));
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("insert into company (id, code, display_name) values (32001, 'OTHER', 'Other')").executeUpdate();
                em.createNativeQuery("insert into activity (id, company_id, code, name) values (32001, 32001, 'FOREIGN', 'Foreign')").executeUpdate();
                em.createNativeQuery("insert into budget_category (id, company_id, code, name) values (32001, 32001, 'FOREIGN', 'Foreign')").executeUpdate();
                em.getTransaction().commit();
            }
            Counts before = counts(jpa);
            for (var command : List.of(
                    new InventoryMovementCommand(InventoryMovement.MovementType.RECEIPT, BigDecimal.ONE,
                            MOVEMENT_DATE, OFFSET_ACCOUNT_ID, false, "Foreign Event", 32001L, null, false),
                    new InventoryMovementCommand(InventoryMovement.MovementType.RECEIPT, BigDecimal.ONE,
                            MOVEMENT_DATE, OFFSET_ACCOUNT_ID, false, "Foreign Budget", null, 32001L, true)))
            {
                assertThrows(CompanyOwnershipException.class, () -> inventory.previewMovement(item.id(), command));
            }
            var command = new InventoryMovementCommand(InventoryMovement.MovementType.RECEIPT, BigDecimal.ONE,
                    MOVEMENT_DATE, OFFSET_ACCOUNT_ID, false, "Independent Budget", null, 31001L, true);
            var preview = inventory.previewMovement(item.id(), command);
            assertEquals("Non-event", preview.eventLabel());
            for (String restriction : List.of("is_active = false", "is_active = true, effective_from = DATE '2026-06-01'",
                    "effective_from = null, effective_to = DATE '2026-04-30'"))
            {
                try (EntityManager em = jpa.em())
                {
                    em.getTransaction().begin();
                    em.createNativeQuery("update budget_category set " + restriction + " where id = 31001").executeUpdate();
                    em.getTransaction().commit();
                }
                assertThrows(IllegalStateException.class, () -> inventory.previewMovement(item.id(), command));
                assertThrows(IllegalStateException.class, () -> inventory.recordMovement(preview, "tester"));
                assertEquals(before, counts(jpa));
                assertEquals(new BigDecimal("0.0000"), inventory.load(item.id()).quantity());
            }
        }
    }

    private static void seedTags(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("insert into activity (id, company_id, code, name, is_active) values (31001, 31001, 'FAIR', 'Autumn Fair', true)").executeUpdate();
            em.createNativeQuery("insert into budget_category (id, company_id, code, name, is_active) values (31001, 31001, 'COST', 'Event Cost', true)").executeUpdate();
            em.createNativeQuery("insert into account (id, chart_id, code, name, account_type, normal_balance) values (31004, 31001, '4000', 'Event Income', 'INCOME', 'CREDIT')").executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static InventoryService service(Jpa jpa, String companyCode)
    {
        return new InventoryService(
                jpa,
                new TransactionEntryService(jpa, () -> companyCode),
                new TransactionCorrectionService(jpa, () -> companyCode),
                () -> companyCode);
    }

    private static InventoryMovementCommand command(
            InventoryMovement.MovementType type,
            String quantity,
            String notes)
    {
        return new InventoryMovementCommand(
                type, new BigDecimal(quantity), MOVEMENT_DATE, OFFSET_ACCOUNT_ID, false, notes, null, null, true);
    }

    private static InventoryItemCommand itemCommand(String name, BigDecimal unitValue)
    {
        return new InventoryItemCommand(
                COMPANY, INVENTORY_ACCOUNT_ID, FUND_ID, name, "Supplies", BigDecimal.ZERO, "each",
                unitValue, LocalDate.of(2026, 1, 1), "", "", InventoryItem.Condition.GOOD,
                InventoryItem.Status.ACTIVE, "");
    }

    private static InventoryItemCommand zeroValueItemCommand(String name)
    {
        return itemCommand(name, BigDecimal.ZERO);
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("insert into chart_of_accounts (id, name, version, status) values (?, 'Inventory Chart', '1', 'ACTIVE')")
                    .setParameter(1, CHART_ID).executeUpdate();
            em.createNativeQuery("insert into company (id, code, display_name, active_chart_of_accounts_id) values (?, ?, 'SCA Branch', ?)")
                    .setParameter(1, COMPANY_ID).setParameter(2, COMPANY).setParameter(3, CHART_ID).executeUpdate();
            em.createNativeQuery("update chart_of_accounts set company_id = ? where id = ?")
                    .setParameter(1, COMPANY_ID).setParameter(2, CHART_ID).executeUpdate();
            em.createNativeQuery("insert into fund (id, company_id, code, name, fund_type) values (?, ?, 'GENERAL', 'General', 'UNRESTRICTED')")
                    .setParameter(1, FUND_ID).setParameter(2, COMPANY_ID).executeUpdate();
            em.createNativeQuery("insert into account (id, chart_id, code, name, account_type, subtype, normal_balance) values (?, ?, '1300', 'Inventory', 'ASSET', 'INVENTORY', 'DEBIT')")
                    .setParameter(1, INVENTORY_ACCOUNT_ID).setParameter(2, CHART_ID).executeUpdate();
            em.createNativeQuery("insert into account (id, chart_id, code, name, account_type, normal_balance) values (?, ?, '5000', 'Inventory expense', 'EXPENSE', 'DEBIT')")
                    .setParameter(1, OFFSET_ACCOUNT_ID).setParameter(2, CHART_ID).executeUpdate();
            em.createNativeQuery("insert into account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) values (?, ?, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .setParameter(1, BANK_OFFSET_ACCOUNT_ID).setParameter(2, CHART_ID).executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void insertFinalizedBankRange(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("""
                    insert into company_bank_account
                        (id, company_id, name, account_type, account_id)
                    values (31003, ?, 'Checking', 'CHECKING', ?)
                    """)
                    .setParameter(1, COMPANY_ID)
                    .setParameter(2, BANK_OFFSET_ACCOUNT_ID)
                    .executeUpdate();
            em.createNativeQuery("""
                    insert into bank_reconciliation_session
                        (id, company_id, bank_account_id, statement_start_date, statement_end_date,
                         mismatch_policy, status)
                    values (31003, ?, 31003, ?, ?, 'WARN_ONLY', 'FINALIZED')
                    """)
                    .setParameter(1, COMPANY_ID)
                    .setParameter(2, java.sql.Date.valueOf(MOVEMENT_DATE.minusDays(5)))
                    .setParameter(3, java.sql.Date.valueOf(MOVEMENT_DATE.plusDays(5)))
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static Counts counts(Jpa jpa)
    {
        return new Counts(
                count(jpa, "inventory_item"),
                count(jpa, "inventory_movement"),
                count(jpa, "txn"),
                count(jpa, "txn_split"),
                count(jpa, "audit_event"));
    }

    private static long count(Jpa jpa, String table)
    {
        try (EntityManager em = jpa.em())
        {
            return ((Number) em.createNativeQuery("select count(*) from " + table).getSingleResult()).longValue();
        }
    }

    private record Counts(long items, long movements, long txns, long splits, long audits)
    {
    }
}
