package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.InventoryMovement;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryAuthorizationIntegrationTest
{
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-01T22:30:00Z"), ZoneOffset.UTC);
    private static final long CHART_ID = 21_001L;
    private static final long FUND_ID = 21_001L;
    private static final long INVENTORY_ACCOUNT_ID = 21_001L;
    private static final long CASH_ACCOUNT_ID = 21_002L;

    @Test
    void viewerCannotMutateInventoryAndAuthorizationRunsBeforeValidation(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-viewer-authorization")))
        {
            UserAdminService users = initializeSecurity(jpa);
            long viewerUserId = reservedUserId(users, ReservedSecurityRole.VIEWER);
            seedMasterData(jpa);

            InventoryService setup = unguardedService(jpa);
            InventoryItemView item = setup.create(itemCommand("Serving Trays", BigDecimal.ZERO));
            InventoryMovementView originalMovement = setup.recordMovement(
                    item.id(), receiptCommand(new BigDecimal("1.0000"), LocalDate.of(2026, 2, 1)));
            InventoryService.MovementReversalPreview reversalPreview = setup.previewMovementReversal(
                    originalMovement.id(), LocalDate.of(2026, 2, 2), "authorization reversal preview");

            long itemCountBefore = inventoryItemCount(jpa);
            long movementCountBefore = inventoryMovementCount(jpa);
            long transactionCountBefore = transactionCount(jpa);
            long auditCountBefore = auditCount(jpa);

            Supplier<Optional<AuthenticatedUserSession>> viewerSession = () -> Optional.of(
                    session(viewerUserId, "DEFAULT", Set.of(ReservedSecurityRole.VIEWER)));
            InventoryService service = guardedService(jpa, viewerSession);

            assertEquals("Serving Trays", service.load(item.id()).name());
            assertEquals(1, service.listItems("DEFAULT").size());
            assertEquals(1, service.listMovements("DEFAULT").size());
            assertNotNull(service.previewMovement(
                    item.id(), receiptCommand(new BigDecimal("1.0000"), LocalDate.of(2026, 2, 3))));
            assertNotNull(service.previewMovementReversal(
                    originalMovement.id(), LocalDate.of(2026, 2, 2), "read-only preview"));

            assertThrows(AuthorizationException.class, () -> service.create(null));
            assertThrows(AuthorizationException.class, () -> service.update(item.id(), null));
            assertThrows(AuthorizationException.class,
                    () -> service.changeStatus(item.id(), null, null, null));
            assertThrows(AuthorizationException.class,
                    () -> service.recordMovement((InventoryService.MovementPreview) null, null));
            assertThrows(AuthorizationException.class,
                    () -> service.recordMovement(item.id(), null));
            assertThrows(AuthorizationException.class,
                    () -> service.reverseMovement((InventoryService.MovementReversalPreview) null, null));

            assertEquals(itemCountBefore, inventoryItemCount(jpa));
            assertEquals(movementCountBefore, inventoryMovementCount(jpa));
            assertEquals(transactionCountBefore, transactionCount(jpa));
            assertEquals(auditCountBefore, auditCount(jpa));
            assertEquals(6L, authorizationDenialCount(jpa));
            assertNotNull(reversalPreview);
        }
    }

    @Test
    void bookkeepingRolesCanMutateAndSessionChangesTakeEffectImmediately(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-role-authorization")))
        {
            UserAdminService users = initializeSecurity(jpa);
            long adminUserId = reservedUserId(users, ReservedSecurityRole.ADMIN);
            long managerUserId = reservedUserId(users, ReservedSecurityRole.MANAGER);
            long accountantUserId = reservedUserId(users, ReservedSecurityRole.ACCOUNTANT);
            long viewerUserId = reservedUserId(users, ReservedSecurityRole.VIEWER);
            seedMasterData(jpa);

            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(
                    Optional.of(session(
                            accountantUserId,
                            "DEFAULT",
                            Set.of(ReservedSecurityRole.ACCOUNTANT))));
            InventoryService service = guardedService(jpa, current::get);

            InventoryItemView item = service.create(itemCommand("Serving Trays", BigDecimal.ZERO));
            assertNotNull(item.id());

            current.set(Optional.of(session(
                    managerUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.MANAGER))));
            InventoryItemView updated = service.update(
                    item.id(), itemCommand("Updated Serving Trays", BigDecimal.ZERO));
            assertEquals("Updated Serving Trays", updated.name());
            assertEquals(InventoryItem.Status.INACTIVE,
                    service.changeStatus(
                            item.id(), InventoryItem.Status.INACTIVE,
                            "manager", "maintenance").status());
            assertEquals(InventoryItem.Status.ACTIVE,
                    service.changeStatus(
                            item.id(), InventoryItem.Status.ACTIVE,
                            "manager", "returned to inventory").status());

            current.set(Optional.of(session(
                    adminUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.ADMIN))));
            InventoryMovementView movement = service.recordMovement(
                    item.id(), receiptCommand(new BigDecimal("2.0000"), LocalDate.of(2026, 2, 1)));
            assertNotNull(movement.id());
            assertNotNull(movement.transactionId());
            assertEquals(new BigDecimal("2.0000"), service.load(item.id()).quantity());

            current.set(Optional.of(session(
                    accountantUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.ACCOUNTANT))));
            InventoryService.MovementReversalPreview reversalPreview = service.previewMovementReversal(
                    movement.id(), LocalDate.of(2026, 2, 2), "reverse receipt");
            InventoryMovementView reversal = service.reverseMovement(reversalPreview, "accountant");
            assertNotNull(reversal.transactionId());
            assertEquals(new BigDecimal("0.0000"), service.load(item.id()).quantity());

            current.set(Optional.of(session(
                    accountantUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.ACCOUNTANT))));
            InventoryItemView unionItem = service.create(itemCommand("Union Item", BigDecimal.ZERO));
            assertNotNull(unionItem.id());

            current.set(Optional.of(session(
                    viewerUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.VIEWER))));
            assertThrows(AuthorizationException.class,
                    () -> service.update(item.id(), itemCommand("Viewer denied", BigDecimal.ZERO)));
            assertEquals("Updated Serving Trays", service.load(item.id()).name());
            assertEquals(1L, authorizationDenialCount(jpa));
        }
    }

    @Test
    void absentAndWrongCompanySessionsFailClosedWhileImportSeamsRemainOuterGoverned(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("inventory-session-boundary-authorization")))
        {
            UserAdminService users = initializeSecurity(jpa);
            long adminUserId = reservedUserId(users, ReservedSecurityRole.ADMIN);
            seedMasterData(jpa);

            InventoryItemView original = unguardedService(jpa).create(
                    itemCommand("Original", BigDecimal.ZERO));
            AtomicReference<Optional<AuthenticatedUserSession>> current =
                    new AtomicReference<>(Optional.empty());
            InventoryService service = guardedService(jpa, current::get);

            assertThrows(AuthorizationException.class,
                    () -> service.update(original.id(), itemCommand("No session", BigDecimal.ZERO)));

            current.set(Optional.of(session(
                    adminUserId,
                    "OTHER",
                    Set.of(ReservedSecurityRole.ADMIN))));
            assertThrows(AuthorizationException.class,
                    () -> service.changeStatus(
                            original.id(), InventoryItem.Status.INACTIVE,
                            "admin", "wrong company"));

            assertEquals("Original", service.load(original.id()).name());
            assertEquals(InventoryItem.Status.ACTIVE, service.load(original.id()).status());
            assertEquals(2L, authorizationDenialCount(jpa));

            current.set(Optional.empty());
            Long importedItemId;
            Long importedMovementId;
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company company = new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT");
                InventoryItem imported = service.createForImport(
                        em,
                        company,
                        itemCommand("Imported Item", new BigDecimal("3.0000")),
                        UUID.fromString("21000000-0000-0000-0000-000000000001"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"));
                em.flush();
                InventoryMovement importedMovement = service.recordMovementForImport(
                        em,
                        company,
                        imported,
                        LocalDate.of(2026, 1, 1),
                        InventoryMovement.MovementType.RECEIPT,
                        new BigDecimal("3.0000"),
                        new BigDecimal("3.0000"),
                        new BigDecimal("5.0000"),
                        null,
                        "Imported opening inventory",
                        UUID.fromString("21000000-0000-0000-0000-000000000002"),
                        Instant.parse("2026-01-01T00:00:00Z"));
                em.flush();
                importedItemId = imported.getId();
                importedMovementId = importedMovement.getId();
                em.getTransaction().commit();
            }

            assertNotNull(importedItemId);
            assertNotNull(importedMovementId);
            assertEquals("Imported Item", service.load(importedItemId).name());
            assertEquals(new BigDecimal("3.0000"), service.load(importedItemId).quantity());
            assertEquals(1L, inventoryMovementCount(jpa));
            assertEquals(2L, authorizationDenialCount(jpa));
        }
    }

    private static UserAdminService initializeSecurity(Jpa jpa)
    {
        new AuthenticationService(jpa).initializeSecurityIfUnambiguous();
        return new UserAdminService(jpa, () -> "DEFAULT", CLOCK, () -> { });
    }

    private static InventoryService unguardedService(Jpa jpa)
    {
        Supplier<String> company = () -> "DEFAULT";
        return new InventoryService(
                jpa,
                new TransactionEntryService(jpa, company),
                new TransactionCorrectionService(jpa, company),
                company);
    }

    private static InventoryService guardedService(
            Jpa jpa,
            Supplier<Optional<AuthenticatedUserSession>> currentSession)
    {
        Supplier<String> company = () -> "DEFAULT";
        return new InventoryService(
                jpa,
                new TransactionEntryService(jpa, company),
                new TransactionCorrectionService(jpa, company),
                company,
                new AuthorizationGuard(jpa, currentSession, CLOCK));
    }

    private static long reservedUserId(UserAdminService service, ReservedSecurityRole role)
    {
        return service.listUsers().stream()
                .filter(user -> role.name().equalsIgnoreCase(user.getUsername()))
                .map(AppUser::getId)
                .findFirst()
                .orElseThrow();
    }

    private static AuthenticatedUserSession session(
            long userId,
            String companyCode,
            Set<ReservedSecurityRole> roles)
    {
        Instant now = Instant.parse("2026-09-01T22:30:00Z");
        return new AuthenticatedUserSession(
                userId,
                "operator",
                "Operator",
                companyCode,
                roles,
                now,
                now);
    }

    private static InventoryItemCommand itemCommand(String name, BigDecimal quantity)
    {
        return new InventoryItemCommand(
                "DEFAULT",
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
                "Authorization test inventory item");
    }

    private static InventoryMovementCommand receiptCommand(BigDecimal quantity, LocalDate date)
    {
        return new InventoryMovementCommand(
                InventoryMovement.MovementType.RECEIPT,
                quantity,
                date,
                CASH_ACCOUNT_ID,
                false,
                "Receive inventory");
    }

    private static void seedMasterData(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Long companyId = em.createQuery(
                            "select c.id from Company c where c.code = 'DEFAULT'", Long.class)
                    .getSingleResult();
            em.createNativeQuery(
                            "INSERT INTO chart_of_accounts (id, company_id, name, version, status) "
                                    + "VALUES (?, ?, 'Inventory Authorization Test', '1', 'ACTIVE')")
                    .setParameter(1, CHART_ID)
                    .setParameter(2, companyId)
                    .executeUpdate();
            em.createNativeQuery(
                            "UPDATE company SET active_chart_of_accounts_id = ? WHERE id = ?")
                    .setParameter(1, CHART_ID)
                    .setParameter(2, companyId)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO fund (id, company_id, code, name, fund_type) "
                                    + "VALUES (?, ?, 'OPERATING', 'Operating', 'UNRESTRICTED')")
                    .setParameter(1, FUND_ID)
                    .setParameter(2, companyId)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) "
                                    + "VALUES (?, ?, '1300', 'Inventory', 'ASSET', 'INVENTORY', 'DEBIT')")
                    .setParameter(1, INVENTORY_ACCOUNT_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) "
                                    + "VALUES (?, ?, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .setParameter(1, CASH_ACCOUNT_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static long inventoryItemCount(Jpa jpa)
    {
        return count(jpa, "select count(i) from InventoryItem i");
    }

    private static long inventoryMovementCount(Jpa jpa)
    {
        return count(jpa, "select count(m) from InventoryMovement m");
    }

    private static long transactionCount(Jpa jpa)
    {
        return count(jpa, "select count(t) from Txn t");
    }

    private static long auditCount(Jpa jpa)
    {
        return count(jpa, "select count(a) from AuditEvent a");
    }

    private static long count(Jpa jpa, String query)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(query, Long.class).getSingleResult();
        }
    }

    private static long authorizationDenialCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            Number count = (Number) em.createNativeQuery(
                            "select count(*) from security_event where action_type = 'AUTHORIZATION_DENIED'")
                    .getSingleResult();
            return count.longValue();
        }
    }
}
