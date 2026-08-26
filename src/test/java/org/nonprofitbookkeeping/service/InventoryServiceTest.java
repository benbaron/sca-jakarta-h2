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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InventoryServiceTest
{
    private static final String COMPANY_CODE = "INVCO";
    private static final String FUND_CODE = "INVFUND";
    private static final long CHART_ID = 20_001L;
    private static final long COMPANY_ID = 20_001L;
    private static final long FUND_ID = 20_001L;
    private static final long INVENTORY_ACCOUNT_ID = 20_001L;
    private static final long CASH_ACCOUNT_ID = 20_002L;

    @Test
    public void valuedItemStartsAtZeroAndAwaitsGovernedReceipt(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-create")))
        {
            seedCompanyAccountsAndFund(jpa);
            InventoryService service = service(jpa);

            InventoryItemView item = service.create(itemCommand("Loaner Feast Kit", BigDecimal.ZERO));

            assertNotNull(item.id());
            assertEquals("Loaner Feast Kit", item.name());
            assertEquals(new BigDecimal("0.0000"), item.quantity());
            assertEquals(new BigDecimal("0.0000"), item.totalValue());
            assertEquals(1, service.listItems(COMPANY_CODE).size());
            assertEquals(0, service.listMovements(COMPANY_CODE).size());
            assertThrows(IllegalArgumentException.class,
                    () -> service.create(itemCommand("Ungoverned", new BigDecimal("3.0000"))));
        }
    }

    @Test
    public void receiptIssueAndAdjustmentMaintainMovementHistory(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-movement")))
        {
            seedCompanyAccountsAndFund(jpa);
            InventoryService service = service(jpa);
            InventoryItemView item = service.create(itemCommand("Serving Trays", BigDecimal.ZERO));

            service.recordMovement(item.id(), new InventoryMovementCommand(
                    InventoryMovement.MovementType.RECEIPT,
                    new BigDecimal("10.0000"),
                    LocalDate.of(2026, 2, 1),
                    CASH_ACCOUNT_ID,
                    false,
                    "Received ten trays"));
            service.recordMovement(item.id(), new InventoryMovementCommand(
                    InventoryMovement.MovementType.RECEIPT,
                    new BigDecimal("2.0000"),
                    LocalDate.of(2026, 2, 1),
                    CASH_ACCOUNT_ID,
                    false,
                    "Received two trays"));
            service.recordMovement(item.id(), new InventoryMovementCommand(
                    InventoryMovement.MovementType.ISSUE,
                    new BigDecimal("4.0000"),
                    LocalDate.of(2026, 2, 2),
                    CASH_ACCOUNT_ID,
                    false,
                    "Issued four trays"));
            InventoryMovementView adjustment = service.recordMovement(item.id(), new InventoryMovementCommand(
                    InventoryMovement.MovementType.ADJUSTMENT,
                    new BigDecimal("7.0000"),
                    LocalDate.of(2026, 2, 3),
                    CASH_ACCOUNT_ID,
                    false,
                    "Counted seven trays"));

            assertEquals(new BigDecimal("7.0000"), adjustment.resultingQuantity());
            assertEquals(new BigDecimal("7.0000"), service.load(item.id()).quantity());
            assertEquals(4, service.listMovements(COMPANY_CODE).size());
            assertNotNull(adjustment.transactionId());
        }
    }

    @Test
    public void movementCannotMakeQuantityNegative(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-negative")))
        {
            seedCompanyAccountsAndFund(jpa);
            InventoryService service = service(jpa);
            InventoryItemView item = service.create(itemCommand("Banners", BigDecimal.ZERO));
            service.recordMovement(item.id(), new InventoryMovementCommand(
                    InventoryMovement.MovementType.RECEIPT,
                    new BigDecimal("1.0000"),
                    LocalDate.of(2026, 1, 31),
                    CASH_ACCOUNT_ID,
                    false,
                    "One banner"));

            assertThrows(IllegalArgumentException.class, () -> service.recordMovement(item.id(), new InventoryMovementCommand(
                    InventoryMovement.MovementType.ISSUE,
                    new BigDecimal("2.0000"),
                    LocalDate.of(2026, 2, 1),
                    CASH_ACCOUNT_ID,
                    false,
                    "Too many issued")));
        }
    }

    @Test
    public void inventoryAccountMustUseInventorySubtype(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-validation")))
        {
            seedCompanyAccountsAndFund(jpa);
            InventoryService service = service(jpa);

            InventoryItemCommand bad = new InventoryItemCommand(
                    COMPANY_CODE,
                    CASH_ACCOUNT_ID,
                    FUND_ID,
                    "Invalid",
                    "Supplies",
                    new BigDecimal("1.0000"),
                    "each",
                    BigDecimal.ZERO,
                    LocalDate.of(2026, 1, 1),
                    "",
                    "",
                    InventoryItem.Condition.UNKNOWN,
                    InventoryItem.Status.ACTIVE,
                    "");

            assertThrows(IllegalArgumentException.class, () -> service.create(bad));
        }
    }

    @Test
    public void ordinaryUpdateCannotChangeLifecycleStatus(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-status-edit-guard")))
        {
            seedCompanyAccountsAndFund(jpa);
            InventoryService service = service(jpa);
            InventoryItemView item = service.create(itemCommand("Lifecycle Guard", BigDecimal.ZERO));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.update(item.id(), itemCommandWithStatus(
                            "Lifecycle Guard", BigDecimal.ZERO, InventoryItem.Status.INACTIVE)));

            assertEquals("Inventory status changes use the explicit lifecycle action", ex.getMessage());
            assertEquals(InventoryItem.Status.ACTIVE, service.load(item.id()).status());
        }
    }

    @Test
    public void lifecycleRequiresZeroQuantityAuditsTransitionsAndKeepsDisposedTerminal(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-status-lifecycle")))
        {
            seedCompanyAccountsAndFund(jpa);
            InventoryService service = service(jpa);
            InventoryItemView item = service.create(itemCommand("Lifecycle Item", BigDecimal.ZERO));

            assertEquals(InventoryItem.Status.INACTIVE,
                    service.changeStatus(item.id(), InventoryItem.Status.INACTIVE,
                            "tester", "temporarily out of service").status());
            assertEquals(InventoryItem.Status.ACTIVE,
                    service.changeStatus(item.id(), InventoryItem.Status.ACTIVE,
                            "tester", "returned to service").status());

            service.recordMovement(item.id(), new InventoryMovementCommand(
                    InventoryMovement.MovementType.RECEIPT,
                    new BigDecimal("2.0000"),
                    LocalDate.of(2026, 2, 1),
                    CASH_ACCOUNT_ID,
                    false,
                    "Receive two"));

            IllegalStateException quantityGuard = assertThrows(IllegalStateException.class,
                    () -> service.changeStatus(item.id(), InventoryItem.Status.INACTIVE,
                            "tester", "should fail"));
            assertEquals(
                    "Inventory item must have zero quantity before deactivation or disposal; use governed inventory movements first",
                    quantityGuard.getMessage());

            service.recordMovement(item.id(), new InventoryMovementCommand(
                    InventoryMovement.MovementType.ISSUE,
                    new BigDecimal("2.0000"),
                    LocalDate.of(2026, 2, 2),
                    CASH_ACCOUNT_ID,
                    false,
                    "Issue two"));
            InventoryItemView disposed = service.changeStatus(
                    item.id(), InventoryItem.Status.DISPOSED, "tester", "retired from inventory");
            assertEquals(InventoryItem.Status.DISPOSED, disposed.status());
            assertEquals(new BigDecimal("0.0000"), disposed.quantity());

            IllegalStateException terminal = assertThrows(IllegalStateException.class,
                    () -> service.changeStatus(item.id(), InventoryItem.Status.ACTIVE,
                            "tester", "attempt to restore"));
            assertEquals(
                    "Disposed inventory items are retained terminal history and cannot be reactivated or deactivated",
                    terminal.getMessage());

            try (EntityManager em = jpa.em())
            {
                Long auditCount = em.createQuery("""
                                select count(a) from AuditEvent a
                                where a.actionType = :action
                                  and a.entityType = :entityType
                                  and a.entityId = :entityId
                                """, Long.class)
                        .setParameter("action", "INVENTORY_ITEM_STATUS_CHANGED")
                        .setParameter("entityType", "InventoryItem")
                        .setParameter("entityId", Long.toString(item.id()))
                        .getSingleResult();
                assertEquals(3L, auditCount);
            }
        }
    }

    private static InventoryItemCommand itemCommandWithStatus(
            String name,
            BigDecimal quantity,
            InventoryItem.Status status)
    {
        InventoryItemCommand base = itemCommand(name, quantity);
        return new InventoryItemCommand(
                base.companyCode(), base.inventoryAccountId(), base.fundId(), base.name(), base.itemType(),
                base.quantity(), base.unit(), base.unitValue(), base.acquisitionDate(), base.custodian(),
                base.storageLocation(), base.condition(), status, base.notes());
    }

    private static InventoryItemCommand itemCommand(String name, BigDecimal quantity)
    {
        return new InventoryItemCommand(
                COMPANY_CODE,
                INVENTORY_ACCOUNT_ID,
                FUND_ID,
                name,
                "Feast Gear",
                quantity,
                "each",
                new BigDecimal("5.0000"),
                LocalDate.of(2026, 1, 1),
                "Quartermaster",
                "Storage Locker",
                InventoryItem.Condition.GOOD,
                InventoryItem.Status.ACTIVE,
                "Test inventory item");
    }

    private static InventoryService service(Jpa jpa)
    {
        return new InventoryService(
                jpa,
                new TransactionEntryService(jpa, () -> COMPANY_CODE),
                () -> COMPANY_CODE);
    }

    private static void seedCompanyAccountsAndFund(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (?, 'SCA Inventory Chart', '1', 'ACTIVE')")
                    .setParameter(1, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO company (id, code, display_name, active_chart_of_accounts_id) VALUES (?, ?, 'SCA Branch', ?)")
                    .setParameter(1, COMPANY_ID)
                    .setParameter(2, COMPANY_CODE)
                    .setParameter(3, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = ? WHERE id = ?")
                    .setParameter(1, COMPANY_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, company_id, code, name, fund_type) VALUES (?, ?, ?, 'Inventory Test Fund', 'UNRESTRICTED')")
                    .setParameter(1, FUND_ID)
                    .setParameter(2, COMPANY_ID)
                    .setParameter(3, FUND_CODE)
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (?, ?, '1300', 'Inventory', 'ASSET', 'INVENTORY', 'DEBIT')")
                    .setParameter(1, INVENTORY_ACCOUNT_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) VALUES (?, ?, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .setParameter(1, CASH_ACCOUNT_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }
}
