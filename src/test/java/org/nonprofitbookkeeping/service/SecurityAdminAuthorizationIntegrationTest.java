package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityAdminAuthorizationIntegrationTest
{
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-31T16:00:00Z"), ZoneOffset.UTC);

    @Test
    void viewerAccountantAndManagerCannotMutateSecurityAdministration(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("security-admin-read-only-authorization")))
        {
            UserAdminService setup = userAdmin(jpa, "DEFAULT");
            long adminUserId = reservedUserId(setup, ReservedSecurityRole.ADMIN);
            long viewerUserId = reservedUserId(setup, ReservedSecurityRole.VIEWER);
            long accountantUserId = reservedUserId(setup, ReservedSecurityRole.ACCOUNTANT);
            long managerUserId = reservedUserId(setup, ReservedSecurityRole.MANAGER);
            AppUser target = setup.saveUser(new AppUserCommand(
                    null, "credential-target", "Credential Target", null, true, "setup"));

            SecurityAdminService unguarded = unguardedSecurity(jpa);
            unguarded.setPassword(adminUserId, "DEFAULT", target.getId(), "initial-password".toCharArray());
            String credentialBefore = credentialHash(jpa, target.getId());

            SecurityAdminService viewer = guardedSecurity(
                    jpa,
                    () -> Optional.of(session(viewerUserId, "DEFAULT", Set.of(ReservedSecurityRole.VIEWER))));
            assertTrue(viewer.passwordConfigured(target.getId()));
            assertEquals(0, viewer.settings().inactivityTimeoutMinutes());
            assertThrows(AuthorizationException.class, () -> viewer.setPassword(
                    adminUserId, "DEFAULT", target.getId(), "viewer-change".toCharArray()));

            SecurityAdminService accountant = guardedSecurity(
                    jpa,
                    () -> Optional.of(session(
                            accountantUserId,
                            "DEFAULT",
                            Set.of(ReservedSecurityRole.ACCOUNTANT))));
            assertThrows(AuthorizationException.class, () -> accountant.clearPassword(
                    adminUserId, "DEFAULT", target.getId()));

            SecurityAdminService manager = guardedSecurity(
                    jpa,
                    () -> Optional.of(session(managerUserId, "DEFAULT", Set.of(ReservedSecurityRole.MANAGER))));
            assertThrows(AuthorizationException.class, () -> manager.setInactivityTimeoutMinutes(
                    adminUserId, "DEFAULT", 30));

            assertTrue(unguarded.passwordConfigured(target.getId()));
            assertEquals(credentialBefore, credentialHash(jpa, target.getId()));
            assertEquals(0, unguarded.settings().inactivityTimeoutMinutes());
            assertEquals(3L, authorizationDenialCount(jpa));
        }
    }

    @Test
    void adminCanMutateSecurityAdministrationAndAuthorizationTracksCurrentSession(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("security-admin-session-switching")))
        {
            new CompanyAdminService(jpa).createCompany("OTHER", "Other Company");
            UserAdminService setup = userAdmin(jpa, "DEFAULT");
            long adminUserId = reservedUserId(setup, ReservedSecurityRole.ADMIN);
            long managerUserId = reservedUserId(setup, ReservedSecurityRole.MANAGER);
            AppUser target = setup.saveUser(new AppUserCommand(
                    null, "session-target", "Session Target", null, true, "setup"));

            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(
                    Optional.of(session(managerUserId, "DEFAULT", Set.of(ReservedSecurityRole.MANAGER))));
            SecurityAdminService service = guardedSecurity(jpa, current::get);

            assertThrows(AuthorizationException.class, () -> service.setPassword(
                    adminUserId, "DEFAULT", target.getId(), "manager-denied".toCharArray()));

            current.set(Optional.of(session(adminUserId, "DEFAULT", Set.of(ReservedSecurityRole.ADMIN))));
            service.setPassword(adminUserId, "DEFAULT", target.getId(), "admin-password".toCharArray());
            assertTrue(service.passwordConfigured(target.getId()));
            assertEquals(45, service.setInactivityTimeoutMinutes(
                    adminUserId, "DEFAULT", 45).inactivityTimeoutMinutes());
            service.clearPassword(adminUserId, "DEFAULT", target.getId());
            assertFalse(service.passwordConfigured(target.getId()));

            current.set(Optional.of(session(
                    managerUserId,
                    "DEFAULT",
                    Set.of(
                            ReservedSecurityRole.VIEWER,
                            ReservedSecurityRole.ACCOUNTANT,
                            ReservedSecurityRole.MANAGER))));
            assertThrows(AuthorizationException.class, () -> service.setInactivityTimeoutMinutes(
                    adminUserId, "DEFAULT", 60));

            current.set(Optional.of(session(adminUserId, "OTHER", Set.of(ReservedSecurityRole.ADMIN))));
            assertThrows(AuthorizationException.class, () -> service.setInactivityTimeoutMinutes(
                    adminUserId, "DEFAULT", 60));

            current.set(Optional.empty());
            assertThrows(AuthorizationException.class, () -> service.setInactivityTimeoutMinutes(
                    adminUserId, "DEFAULT", 60));

            assertEquals(45, service.settings().inactivityTimeoutMinutes());
            assertEquals(4L, authorizationDenialCount(jpa));
        }
    }

    @Test
    void authorizationDoesNotBypassSingletonAdminOrInactiveTargetProtections(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("security-admin-domain-protections")))
        {
            UserAdminService setup = userAdmin(jpa, "DEFAULT");
            long adminUserId = reservedUserId(setup, ReservedSecurityRole.ADMIN);
            long managerUserId = reservedUserId(setup, ReservedSecurityRole.MANAGER);
            AppUser target = setup.saveUser(new AppUserCommand(
                    null, "inactive-target", "Inactive Target", null, true, "setup"));

            SecurityAdminService fabricatedAdmin = guardedSecurity(
                    jpa,
                    () -> Optional.of(session(managerUserId, "DEFAULT", Set.of(ReservedSecurityRole.ADMIN))));
            assertThrows(SecurityException.class, () -> fabricatedAdmin.setInactivityTimeoutMinutes(
                    managerUserId, "DEFAULT", 15));
            assertEquals(0, fabricatedAdmin.settings().inactivityTimeoutMinutes());

            setup.saveUser(new AppUserCommand(
                    target.getId(),
                    target.getUsername(),
                    target.getDisplayName(),
                    target.getEmail(),
                    false,
                    "setup"));
            SecurityAdminService admin = guardedSecurity(
                    jpa,
                    () -> Optional.of(session(adminUserId, "DEFAULT", Set.of(ReservedSecurityRole.ADMIN))));
            assertThrows(IllegalStateException.class, () -> admin.setPassword(
                    adminUserId, "DEFAULT", target.getId(), "inactive-denied".toCharArray()));
            assertFalse(admin.passwordConfigured(target.getId()));
        }
    }

    private static UserAdminService userAdmin(Jpa jpa, String companyCode)
    {
        new SecurityBootstrapService(jpa, CLOCK).initializeIfUnambiguous();
        return new UserAdminService(jpa, () -> companyCode, CLOCK, () -> { });
    }

    private static SecurityAdminService unguardedSecurity(Jpa jpa)
    {
        return new SecurityAdminService(jpa, CLOCK, testHasher());
    }

    private static SecurityAdminService guardedSecurity(
            Jpa jpa,
            java.util.function.Supplier<Optional<AuthenticatedUserSession>> currentSession)
    {
        return new SecurityAdminService(
                jpa,
                CLOCK,
                testHasher(),
                new AuthorizationGuard(jpa, currentSession, CLOCK));
    }

    private static PasswordHasher testHasher()
    {
        return new PasswordHasher(1, new SecureRandom());
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
        Instant now = Instant.parse("2026-08-31T16:00:00Z");
        return new AuthenticatedUserSession(
                userId,
                "operator",
                "Operator",
                companyCode,
                roles,
                now,
                now);
    }

    private static String credentialHash(Jpa jpa, long userId)
    {
        try (EntityManager em = jpa.em())
        {
            SecurityRepository.CredentialData credential = SecurityRepository.credential(em, userId);
            return credential == null ? null : credential.hashBase64();
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
