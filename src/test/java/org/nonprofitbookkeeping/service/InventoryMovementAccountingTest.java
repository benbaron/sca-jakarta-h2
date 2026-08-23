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
                    item.id(), command(InventoryMovement.MovementType.ISSUE, "1.0000", "Late failure"));

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
                            "Not confirmed")));
            InventoryService.MovementPreview preview = service.previewMovement(
                    zeroValue.id(), new InventoryMovementCommand(
                            InventoryMovement.MovementType.RECEIPT,
                            BigDecimal.ONE,
                            MOVEMENT_DATE,
                            null,
                            true,
                            "Confirmed nonfinancial"));
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
                            "Inside finalized range")));
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
                type, new BigDecimal(quantity), MOVEMENT_DATE, OFFSET_ACCOUNT_ID, false, notes);
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
