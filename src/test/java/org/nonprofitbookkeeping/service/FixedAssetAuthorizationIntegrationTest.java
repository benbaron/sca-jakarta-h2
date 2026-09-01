package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FixedAssetAuthorizationIntegrationTest
{
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-01T18:00:00Z"), ZoneOffset.UTC);
    private static final long CHART_ID = 20_001L;
    private static final long FUND_ID = 20_001L;
    private static final long ASSET_ACCOUNT_ID = 20_001L;
    private static final long ACCUMULATED_DEPRECIATION_ACCOUNT_ID = 20_002L;
    private static final long DEPRECIATION_EXPENSE_ACCOUNT_ID = 20_003L;

    @Test
    void viewerCannotMutateFixedAssetsAndAuthorizationRunsBeforeValidation(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fixed-asset-viewer-authorization")))
        {
            UserAdminService users = initializeSecurity(jpa);
            long viewerUserId = reservedUserId(users, ReservedSecurityRole.VIEWER);
            seedMasterData(jpa);

            FixedAssetService setup = unguardedService(jpa);
            FixedAssetView asset = setup.create(assetCommand("Trailer"));
            long assetCountBefore = fixedAssetCount(jpa);
            long runCountBefore = depreciationRunCount(jpa);
            long lifecycleCountBefore = lifecycleEventCount(jpa);
            long transactionCountBefore = transactionCount(jpa);
            long auditCountBefore = auditCount(jpa);

            Supplier<Optional<AuthenticatedUserSession>> viewerSession = () -> Optional.of(
                    session(viewerUserId, "DEFAULT", Set.of(ReservedSecurityRole.VIEWER)));
            FixedAssetService service = guardedService(jpa, viewerSession);

            assertEquals("Trailer", service.load(asset.id()).name());
            assertThrows(AuthorizationException.class, () -> service.create(null));
            assertThrows(AuthorizationException.class, () -> service.update(asset.id(), null));
            assertThrows(AuthorizationException.class,
                    () -> service.changeStatus(asset.id(), null, null, null));
            assertThrows(AuthorizationException.class,
                    () -> service.runMonthlyDepreciation(asset.id(), null, null));
            assertThrows(AuthorizationException.class,
                    () -> service.recordLifecycleEvent(null, null));
            assertThrows(AuthorizationException.class,
                    () -> service.reverseLifecycleEvent(null, null));

            assertEquals(assetCountBefore, fixedAssetCount(jpa));
            assertEquals(runCountBefore, depreciationRunCount(jpa));
            assertEquals(lifecycleCountBefore, lifecycleEventCount(jpa));
            assertEquals(transactionCountBefore, transactionCount(jpa));
            assertEquals(auditCountBefore, auditCount(jpa));
            assertEquals(6L, authorizationDenialCount(jpa));
        }
    }

    @Test
    void bookkeepingRolesCanMutateAndSessionChangesTakeEffectImmediately(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fixed-asset-role-authorization")))
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
            FixedAssetService service = guardedService(jpa, current::get);

            FixedAssetView asset = service.create(assetCommand("Trailer"));
            assertNotNull(asset.id());

            current.set(Optional.of(session(
                    managerUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.MANAGER))));
            FixedAssetView updated = service.update(asset.id(), assetCommand("Updated trailer"));
            assertEquals("Updated trailer", updated.name());
            service.changeStatus(asset.id(), FixedAsset.Status.INACTIVE, "manager", "maintenance");
            service.changeStatus(asset.id(), FixedAsset.Status.ACTIVE, "manager", "returned to service");

            current.set(Optional.of(session(
                    adminUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.ADMIN))));
            DepreciationRunView run = service.runMonthlyDepreciation(
                    asset.id(), LocalDate.of(2026, 4, 30), "April depreciation");
            assertNotNull(run.id());
            assertNotNull(run.transactionId());

            current.set(Optional.of(session(
                    accountantUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.ACCOUNTANT))));
            FixedAssetView unionAsset = service.create(assetCommand("Union asset"));
            assertNotNull(unionAsset.id());

            current.set(Optional.of(session(
                    viewerUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.VIEWER))));
            assertThrows(AuthorizationException.class,
                    () -> service.update(asset.id(), assetCommand("Viewer denied")));
            assertEquals("Updated trailer", service.load(asset.id()).name());
            assertEquals(1L, authorizationDenialCount(jpa));
        }
    }

    @Test
    void absentAndWrongCompanySessionsFailClosedWhileImportSeamsRemainOuterGoverned(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fixed-asset-session-boundary-authorization")))
        {
            UserAdminService users = initializeSecurity(jpa);
            long adminUserId = reservedUserId(users, ReservedSecurityRole.ADMIN);
            seedMasterData(jpa);

            FixedAssetView original = unguardedService(jpa).create(assetCommand("Original"));
            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(Optional.empty());
            FixedAssetService service = guardedService(jpa, current::get);

            assertThrows(AuthorizationException.class,
                    () -> service.update(original.id(), assetCommand("No session")));

            current.set(Optional.of(session(
                    adminUserId,
                    "OTHER",
                    Set.of(ReservedSecurityRole.ADMIN))));
            assertThrows(AuthorizationException.class,
                    () -> service.changeStatus(
                            original.id(), FixedAsset.Status.INACTIVE, "admin", "wrong company"));

            assertEquals("Original", service.load(original.id()).name());
            assertEquals(FixedAsset.Status.ACTIVE, service.load(original.id()).status());
            assertEquals(2L, authorizationDenialCount(jpa));

            current.set(Optional.empty());
            Long importedAssetId;
            Long importedRunId;
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company company = new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT");
                FixedAsset imported = service.createForImport(
                        em,
                        company,
                        assetCommand("Imported asset"),
                        UUID.fromString("20000000-0000-0000-0000-000000000001"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"));
                em.flush();

                TransactionEntryService entry = new TransactionEntryService(jpa, () -> "DEFAULT");
                Txn transaction = entry.enter(
                        em,
                        company,
                        depreciationTransactionCommand(),
                        UUID.fromString("20000000-0000-0000-0000-000000000002"),
                        "outer-import",
                        "Imported completed depreciation run");
                var run = service.recordCompletedRunForImport(
                        em,
                        company,
                        imported,
                        LocalDate.of(2026, 4, 30),
                        new BigDecimal("10.0000"),
                        transaction,
                        "Imported April depreciation",
                        UUID.fromString("20000000-0000-0000-0000-000000000003"),
                        Instant.parse("2026-04-30T00:00:00Z"));
                em.flush();
                importedAssetId = imported.getId();
                importedRunId = run.getId();
                em.getTransaction().commit();
            }

            assertNotNull(importedAssetId);
            assertNotNull(importedRunId);
            assertEquals("Imported asset", service.load(importedAssetId).name());
            assertEquals(1L, depreciationRunCount(jpa));
            assertEquals(2L, authorizationDenialCount(jpa));
        }
    }

    private static UserAdminService initializeSecurity(Jpa jpa)
    {
        new AuthenticationService(jpa).initializeSecurityIfUnambiguous();
        return new UserAdminService(jpa, () -> "DEFAULT", CLOCK, () -> { });
    }

    private static FixedAssetService unguardedService(Jpa jpa)
    {
        Supplier<String> company = () -> "DEFAULT";
        return new FixedAssetService(jpa, new TransactionEntryService(jpa, company), company);
    }

    private static FixedAssetService guardedService(
            Jpa jpa,
            Supplier<Optional<AuthenticatedUserSession>> currentSession)
    {
        Supplier<String> company = () -> "DEFAULT";
        return new FixedAssetService(
                jpa,
                new TransactionEntryService(jpa, company),
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
        Instant now = Instant.parse("2026-09-01T18:00:00Z");
        return new AuthenticatedUserSession(
                userId,
                "operator",
                "Operator",
                companyCode,
                roles,
                now,
                now);
    }

    private static FixedAssetCommand assetCommand(String name)
    {
        return new FixedAssetCommand(
                "DEFAULT",
                ASSET_ACCOUNT_ID,
                ACCUMULATED_DEPRECIATION_ACCOUNT_ID,
                DEPRECIATION_EXPENSE_ACCOUNT_ID,
                FUND_ID,
                name,
                LocalDate.of(2026, 1, 1),
                new BigDecimal("1200.0000"),
                BigDecimal.ZERO,
                36,
                FixedAsset.DepreciationMethod.STRAIGHT_LINE,
                BigDecimal.ZERO,
                FixedAsset.Status.ACTIVE,
                "Test asset");
    }

    private static TransactionCommand depreciationTransactionCommand()
    {
        BigDecimal amount = new BigDecimal("10.0000");
        return new TransactionCommand(
                LocalDate.of(2026, 4, 30),
                null,
                "Imported depreciation",
                null,
                List.of(
                        new TransactionLineCommand(
                                DEPRECIATION_EXPENSE_ACCOUNT_ID,
                                FUND_ID,
                                null,
                                null,
                                null,
                                amount,
                                BigDecimal.ZERO,
                                false,
                                "expense"),
                        new TransactionLineCommand(
                                ACCUMULATED_DEPRECIATION_ACCOUNT_ID,
                                FUND_ID,
                                null,
                                null,
                                null,
                                BigDecimal.ZERO,
                                amount,
                                false,
                                "accumulated depreciation")));
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
                                    + "VALUES (?, ?, 'Authorization Test', '1', 'ACTIVE')")
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
                                    + "VALUES (?, ?, '1500', 'Equipment', 'ASSET', 'FIXED_ASSET', 'DEBIT')")
                    .setParameter(1, ASSET_ACCOUNT_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) "
                                    + "VALUES (?, ?, '1590', 'Accumulated Depreciation', 'ASSET', 'FIXED_ASSET', 'CREDIT')")
                    .setParameter(1, ACCUMULATED_DEPRECIATION_ACCOUNT_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) "
                                    + "VALUES (?, ?, '6100', 'Depreciation Expense', 'EXPENSE', 'DEBIT')")
                    .setParameter(1, DEPRECIATION_EXPENSE_ACCOUNT_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static long fixedAssetCount(Jpa jpa)
    {
        return count(jpa, "select count(a) from FixedAsset a");
    }

    private static long depreciationRunCount(Jpa jpa)
    {
        return count(jpa, "select count(r) from FixedAssetDepreciationRun r");
    }

    private static long lifecycleEventCount(Jpa jpa)
    {
        return count(jpa, "select count(e) from FixedAssetLifecycleEvent e");
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
