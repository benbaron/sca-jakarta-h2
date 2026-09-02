package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodCloseAuthorizationIntegrationTest
{
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-02T02:30:00Z"), ZoneOffset.UTC);

    @Test
    void viewerCannotCloseOrReopenWhileReadsRemainAvailable(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("period-close-viewer-authorization")))
        {
            UserAdminService users = initializeSecurity(jpa);
            long viewerUserId = reservedUserId(users, ReservedSecurityRole.VIEWER);
            PeriodCloseRangeService setup = new PeriodCloseRangeService(jpa);
            PeriodCloseRangeView existing = setup.closeRange(
                    "DEFAULT",
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    "CALCULATED",
                    "setup",
                    "March close");

            PeriodCloseRangeService service = guardedService(
                    jpa,
                    () -> Optional.of(session(
                            viewerUserId,
                            "DEFAULT",
                            Set.of(ReservedSecurityRole.VIEWER))));

            assertEquals(existing.id(), service.loadRange(existing.id()).id());
            assertEquals(1, service.listRanges("DEFAULT").size());
            assertEquals(1, service.listEvents("DEFAULT").size());
            assertTrue(service.findClosedRange("DEFAULT", LocalDate.of(2026, 3, 15)).isPresent());

            assertThrows(AuthorizationException.class, () -> service.closeRange(
                    "DEFAULT", null, null, null, null, null));
            assertThrows(AuthorizationException.class, () -> service.reopenRange(
                    existing.id(), null, null, null, false));

            assertEquals(1L, rangeCount(jpa));
            assertEquals(1L, closeEventCount(jpa));
            assertEquals(1L, periodAuditCount(jpa));
            assertEquals("CLOSED", service.loadRange(existing.id()).status());
            assertEquals(2L, authorizationDenialCount(jpa));
        }
    }

    @Test
    void bookkeepingRolesAndUnionCanMutateWithImmediateSessionChanges(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("period-close-role-authorization")))
        {
            UserAdminService users = initializeSecurity(jpa);
            long adminUserId = reservedUserId(users, ReservedSecurityRole.ADMIN);
            long managerUserId = reservedUserId(users, ReservedSecurityRole.MANAGER);
            long accountantUserId = reservedUserId(users, ReservedSecurityRole.ACCOUNTANT);
            long viewerUserId = reservedUserId(users, ReservedSecurityRole.VIEWER);

            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(
                    Optional.of(session(
                            accountantUserId,
                            "DEFAULT",
                            Set.of(ReservedSecurityRole.ACCOUNTANT))));
            PeriodCloseRangeService service = guardedService(jpa, current::get);

            PeriodCloseRangeView march = service.closeRange(
                    "DEFAULT",
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    "CALCULATED",
                    "accountant",
                    "March close");

            current.set(Optional.of(session(
                    managerUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.MANAGER))));
            PeriodCloseRangeView april = service.closeRange(
                    "DEFAULT",
                    LocalDate.of(2026, 4, 1),
                    LocalDate.of(2026, 4, 30),
                    "CALCULATED",
                    "manager",
                    "April close");

            current.set(Optional.of(session(
                    adminUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.ADMIN))));
            assertEquals("REOPENED", service.reopenRange(
                    march.id(),
                    "admin",
                    "March correction",
                    ClosedPeriodPolicy.WARN_AND_REOPEN,
                    false).status());

            current.set(Optional.of(session(
                    accountantUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.ACCOUNTANT))));
            assertEquals("REOPENED", service.reopenRange(
                    april.id(),
                    "union-accountant",
                    "April correction",
                    ClosedPeriodPolicy.REQUIRE_REASON,
                    true).status());

            current.set(Optional.of(session(
                    viewerUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.VIEWER))));
            assertThrows(AuthorizationException.class, () -> service.closeRange(
                    "DEFAULT",
                    LocalDate.of(2026, 5, 1),
                    LocalDate.of(2026, 5, 31),
                    "CALCULATED",
                    "viewer",
                    null));

            assertEquals(2L, rangeCount(jpa));
            assertEquals(4L, closeEventCount(jpa));
            assertEquals(4L, periodAuditCount(jpa));
            assertFalse(service.findClosedRange("DEFAULT", LocalDate.of(2026, 3, 15)).isPresent());
            assertFalse(service.findClosedRange("DEFAULT", LocalDate.of(2026, 4, 15)).isPresent());
            assertEquals(1L, authorizationDenialCount(jpa));
        }
    }

    @Test
    void absentAndWrongCompanySessionsFailClosedWhileInterchangeSeamRemainsOuterGoverned(
            @TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("period-close-session-boundary-authorization")))
        {
            seedCompany(jpa, "OTHER", "Other Company");
            UserAdminService users = initializeSecurity(jpa);
            long accountantUserId = reservedUserId(users, ReservedSecurityRole.ACCOUNTANT);

            PeriodCloseRangeService setup = new PeriodCloseRangeService(jpa);
            PeriodCloseRangeView otherRange = setup.closeRange(
                    "OTHER",
                    LocalDate.of(2026, 2, 1),
                    LocalDate.of(2026, 2, 28),
                    "CUSTOM",
                    "setup",
                    null);

            AtomicReference<Optional<AuthenticatedUserSession>> current =
                    new AtomicReference<>(Optional.empty());
            PeriodCloseRangeService service = guardedService(jpa, current::get);

            assertThrows(AuthorizationException.class, () -> service.closeRange(
                    "DEFAULT",
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    "CALCULATED",
                    "nobody",
                    null));

            current.set(Optional.of(session(
                    accountantUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.ACCOUNTANT))));
            assertThrows(AuthorizationException.class, () -> service.reopenRange(
                    otherRange.id(),
                    "accountant",
                    "wrong company",
                    ClosedPeriodPolicy.WARN_AND_REOPEN,
                    false));
            assertEquals("CLOSED", setup.loadRange(otherRange.id()).status());

            AuthorizationGuard outerGuard = new AuthorizationGuard(
                    jpa,
                    () -> Optional.of(session(
                            accountantUserId,
                            "DEFAULT",
                            Set.of(ReservedSecurityRole.ACCOUNTANT))),
                    CLOCK);
            outerGuard.require(
                    ApplicationPermission.BOOKKEEPING_WRITE,
                    "DEFAULT",
                    "commit governed period-close import");

            PeriodCloseRangeService importService = guardedService(jpa, () -> Optional.empty());
            UUID rangeId = UUID.fromString("23000000-0000-0000-0000-000000000001");
            UUID eventId = UUID.fromString("23000000-0000-0000-0000-000000000002");
            Instant closedAt = Instant.parse("2026-01-31T23:00:00Z");
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company company = em.createQuery(
                                "select c from Company c where c.code = 'DEFAULT'", Company.class)
                        .getSingleResult();
                var imported = importService.importForInterchange(
                        em,
                        company,
                        List.of(new PeriodCloseRangeService.RangeImport(
                                "range-jan",
                                rangeId,
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 1, 31),
                                "CALCULATED",
                                "CLOSED",
                                closedAt,
                                "source-treasurer",
                                "January close",
                                null,
                                null,
                                null)),
                        List.of(new PeriodCloseRangeService.EventImport(
                                "event-jan-close",
                                eventId,
                                "range-jan",
                                "CLOSED",
                                "source-treasurer",
                                "January close",
                                closedAt)),
                        Map.of(),
                        Map.of());
                assertEquals(rangeId, imported.ranges().get("range-jan"));
                assertEquals(eventId, imported.events().get("event-jan-close"));
                em.getTransaction().commit();
            }

            assertEquals(2L, rangeCount(jpa));
            assertEquals(2L, closeEventCount(jpa));
            assertEquals(2L, authorizationDenialCount(jpa));
        }
    }

    private static UserAdminService initializeSecurity(Jpa jpa)
    {
        new SecurityBootstrapService(jpa, CLOCK).initializeIfUnambiguous();
        return new UserAdminService(jpa, () -> "DEFAULT", CLOCK, () -> { });
    }

    private static PeriodCloseRangeService guardedService(
            Jpa jpa,
            Supplier<Optional<AuthenticatedUserSession>> currentSession)
    {
        return new PeriodCloseRangeService(
                jpa,
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
        Instant now = Instant.parse("2026-09-02T02:30:00Z");
        return new AuthenticatedUserSession(
                userId,
                "operator",
                "Operator",
                companyCode,
                roles,
                now,
                now);
    }

    private static void seedCompany(Jpa jpa, String code, String displayName)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode(code);
            company.setDisplayName(displayName);
            company.setDefaultCurrency("USD");
            em.persist(company);
            em.getTransaction().commit();
        }
    }

    private static long rangeCount(Jpa jpa)
    {
        return nativeCount(jpa, "select count(*) from period_close_range");
    }

    private static long closeEventCount(Jpa jpa)
    {
        return nativeCount(jpa, "select count(*) from period_close_event");
    }

    private static long periodAuditCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "select count(a) from AuditEvent a where a.entityType = 'PeriodCloseRange'",
                            Long.class)
                    .getSingleResult();
        }
    }

    private static long authorizationDenialCount(Jpa jpa)
    {
        return nativeCount(
                jpa,
                "select count(*) from security_event where action_type = 'AUTHORIZATION_DENIED'");
    }

    private static long nativeCount(Jpa jpa, String sql)
    {
        try (EntityManager em = jpa.em())
        {
            Number count = (Number) em.createNativeQuery(sql).getSingleResult();
            return count.longValue();
        }
    }
}
