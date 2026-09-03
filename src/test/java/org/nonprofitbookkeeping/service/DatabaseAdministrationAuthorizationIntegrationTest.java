package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyOwnershipIssue;
import org.nonprofitbookkeeping.persistence.DatabaseTransferService;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseAdministrationAuthorizationIntegrationTest
{
    @Test
    void onlyAdminMayBackupRestoreAndActivateValidatedCopy(@TempDir Path tempDir) throws Exception
    {
        Path source = tempDir.resolve("database-admin-transfer");
        AtomicReference<Path> switched = new AtomicReference<>();
        try (Jpa jpa = new Jpa(source))
        {
            new SecurityBootstrapService(jpa).initializeIfUnambiguous();
            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(Optional.empty());
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get);
            DatabaseAdministrationService service = new DatabaseAdministrationService(
                    new DatabaseTransferService(() -> source, switched::set),
                    () -> guard);

            long denialsBefore = authorizationDenialCount(jpa);
            for (ReservedSecurityRole role : nonAdminRoles())
            {
                current.set(Optional.of(session(jpa, role)));
                Path deniedBackup = tempDir.resolve(role.name().toLowerCase() + "-backup.zip");
                assertThrows(AuthorizationException.class, () -> service.backUpDatabase(deniedBackup));
                assertFalse(Files.exists(deniedBackup));
                assertThrows(AuthorizationException.class, () -> service.restoreDatabaseCopy(
                        tempDir.resolve("missing.zip"),
                        tempDir.resolve(role.name().toLowerCase() + "-copy")));
                assertThrows(AuthorizationException.class,
                        () -> service.switchToValidatedCopy(validatedResult(tempDir, role.name().toLowerCase())));
                assertNull(switched.get());
            }

            current.set(Optional.empty());
            assertThrows(AuthorizationException.class,
                    () -> service.backUpDatabase(tempDir.resolve("anonymous-backup.zip")));
            assertEquals(denialsBefore + 10, authorizationDenialCount(jpa));

            current.set(Optional.of(session(jpa, ReservedSecurityRole.ADMIN)));
            Path backup = tempDir.resolve("admin-backup.zip");
            DatabaseTransferService.BackupResult backupResult = service.backUpDatabase(backup);
            assertTrue(Files.isRegularFile(backup));
            assertEquals(64, backupResult.sha256().length());

            Path restored = tempDir.resolve("admin-restored");
            DatabaseTransferService.RestoreResult restoreResult = service.restoreDatabaseCopy(backup, restored);
            assertTrue(restoreResult.validated());
            assertTrue(Files.isRegularFile(Path.of(restored + ".mv.db")));

            service.switchToValidatedCopy(restoreResult);
            assertEquals(restored.toAbsolutePath().normalize(), switched.get());
        }
    }

    @Test
    void ownershipRepairRequiresDatabaseAdminAndUsesAuthenticatedActor(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("database-admin-ownership")))
        {
            new SecurityBootstrapService(jpa).initializeIfUnambiguous();
            OwnershipFixture fixture = seedOwnershipIssue(jpa);
            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(Optional.empty());
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get);
            CompanyOwnershipService service = new CompanyOwnershipService(jpa, guard);

            long denialsBefore = authorizationDenialCount(jpa);
            for (ReservedSecurityRole role : nonAdminRoles())
            {
                current.set(Optional.of(session(jpa, role)));
                assertThrows(AuthorizationException.class, () -> service.assignOwner(
                        fixture.issueId(), fixture.companyId(), "", "Denied repair"));
                assertFalse(activityHasOwner(jpa, fixture.activityId()));
                assertFalse(issueResolved(jpa, fixture.issueId()));
            }
            assertEquals(denialsBefore + 3, authorizationDenialCount(jpa));

            current.set(Optional.of(session(jpa, ReservedSecurityRole.ADMIN)));
            CompanyOwnershipRepairResult result = service.assignOwner(
                    fixture.issueId(), fixture.companyId(), "spoofed-operator", "Validated repair");
            assertEquals("DEFAULT", result.companyCode());
            assertTrue(activityHasOwner(jpa, fixture.activityId()));
            assertTrue(issueResolved(jpa, fixture.issueId()));
            assertEquals("ADMIN", ownershipAuditActor(jpa, fixture.activityId()));
        }
    }

    @Test
    void sampleCompanyMutationRequiresDatabaseAdminAndTracksCurrentSession(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("database-admin-sample")))
        {
            new SecurityBootstrapService(jpa).initializeIfUnambiguous();
            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(Optional.empty());
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get);
            SampleCompanyService service = new SampleCompanyService(jpa, guard);

            for (ReservedSecurityRole role : nonAdminRoles())
            {
                current.set(Optional.of(session(jpa, role)));
                assertThrows(AuthorizationException.class, service::createOrRefresh);
                assertEquals(0L, sampleChartCount(jpa));
            }

            current.set(Optional.of(session(jpa, ReservedSecurityRole.ADMIN)));
            SampleCompanyService.SampleCompanySummary summary = service.createOrRefresh();
            assertNotNull(summary);
            assertEquals(1L, sampleChartCount(jpa));
            service.createOrRefresh();
            assertEquals(1L, sampleChartCount(jpa));

            current.set(Optional.of(session(jpa, ReservedSecurityRole.MANAGER)));
            assertThrows(AuthorizationException.class, service::createOrRefresh);
            assertEquals(1L, sampleChartCount(jpa));
        }
    }


    private static List<ReservedSecurityRole> nonAdminRoles()
    {
        return List.of(
                ReservedSecurityRole.VIEWER,
                ReservedSecurityRole.ACCOUNTANT,
                ReservedSecurityRole.MANAGER);
    }

    private static DatabaseTransferService.RestoreResult validatedResult(Path tempDir, String label)
    {
        return new DatabaseTransferService.RestoreResult(
                tempDir.resolve(label + "-unused.zip"),
                tempDir.resolve(label + "-unused-copy"),
                Instant.now(),
                Instant.now(),
                true,
                new DatabaseTransferService.DatabaseCounts(0, 0, 0),
                "hash");
    }

    private static AuthenticatedUserSession session(Jpa jpa, ReservedSecurityRole role)
    {
        long userId = reservedUserId(jpa, role.name());
        Instant now = Instant.parse("2026-09-03T22:00:00Z");
        return new AuthenticatedUserSession(
                userId,
                role.name(),
                role.name(),
                "DEFAULT",
                Set.of(role),
                now,
                now);
    }

    private static long reservedUserId(Jpa jpa, String username)
    {
        try (EntityManager em = jpa.em())
        {
            Number id = (Number) em.createNativeQuery(
                            "select id from app_user where upper(username) = :username")
                    .setParameter("username", username)
                    .getSingleResult();
            return id.longValue();
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

    private static OwnershipFixture seedOwnershipIssue(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = em.createQuery(
                            "from Company c where c.code = 'DEFAULT'", Company.class)
                    .getSingleResult();
            Activity activity = new Activity();
            activity.setCode("LEGACY-DB-ADMIN");
            activity.setName("Legacy Database Admin Activity");
            em.persist(activity);
            em.flush();

            CompanyOwnershipIssue issue = new CompanyOwnershipIssue();
            issue.setEntityType("ACTIVITY");
            issue.setEntityId(Long.toString(activity.getId()));
            issue.setIssueCode("UNRESOLVED_OWNER");
            issue.setCandidateCompanyCount(0);
            issue.setDetails("Activity has no deterministic company owner.");
            em.persist(issue);
            em.flush();
            OwnershipFixture fixture = new OwnershipFixture(issue.getId(), activity.getId(), company.getId());
            em.getTransaction().commit();
            return fixture;
        }
    }

    private static boolean activityHasOwner(Jpa jpa, long activityId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.find(Activity.class, activityId).getCompany() != null;
        }
    }

    private static boolean issueResolved(Jpa jpa, long issueId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.find(CompanyOwnershipIssue.class, issueId).getResolvedAt() != null;
        }
    }

    private static String ownershipAuditActor(Jpa jpa, long activityId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "select a from AuditEvent a where a.actionType = :action and a.entityType = :type "
                                    + "and a.entityId = :entityId order by a.id desc",
                            AuditEvent.class)
                    .setParameter("action", "COMPANY_OWNERSHIP_ASSIGNED")
                    .setParameter("type", "ACTIVITY")
                    .setParameter("entityId", Long.toString(activityId))
                    .setMaxResults(1)
                    .getSingleResult()
                    .getActor();
        }
    }

    private static long sampleChartCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "select count(c) from ChartOfAccounts c where c.name = :name", Long.class)
                    .setParameter("name", SampleCompanyService.SAMPLE_CHART_NAME)
                    .getSingleResult();
        }
    }

    private record OwnershipFixture(long issueId, long activityId, long companyId)
    {
    }
}
