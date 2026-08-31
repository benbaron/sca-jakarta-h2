package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppRole;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.UserCompanyRole;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAdminAuthorizationIntegrationTest
{
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-31T16:00:00Z"), ZoneOffset.UTC);

    @Test
    void viewerAccountantAndManagerCannotUseAnyUserAdminMutation(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("user-admin-read-only-authorization")))
        {
            UserAdminService setup = unguarded(jpa, "DEFAULT");
            AppUser user = setup.saveUser(new AppUserCommand(
                    null, "security-subject", "Security Subject", null, true, "setup"));
            AppRole role = setup.saveRole(new AppRoleCommand(
                    null, "SECURITY_SUBJECT_ROLE", "Security Subject Role", null, true, "setup"));
            UserCompanyRole assignment = setup.assignRole(new UserRoleAssignmentCommand(
                    user.getId(), role.getId(), LocalDate.of(2026, 1, 1), "setup"));

            long usersBefore = userCount(jpa);
            long rolesBefore = roleCount(jpa);
            long assignmentsBefore = assignmentCount(jpa);

            UserAdminService viewer = guarded(
                    jpa,
                    "DEFAULT",
                    () -> Optional.of(session(
                            reservedUserId(setup, ReservedSecurityRole.VIEWER),
                            ReservedSecurityRole.VIEWER)));
            assertThrows(AuthorizationException.class, () -> viewer.saveUser(new AppUserCommand(
                    null, "viewer-denied", "Viewer Denied", null, true, "viewer")));

            UserAdminService accountant = guarded(
                    jpa,
                    "DEFAULT",
                    () -> Optional.of(session(
                            reservedUserId(setup, ReservedSecurityRole.ACCOUNTANT),
                            ReservedSecurityRole.ACCOUNTANT)));
            assertThrows(AuthorizationException.class, () -> accountant.saveRole(new AppRoleCommand(
                    null, "ACCOUNTANT_DENIED", "Accountant Denied", null, true, "accountant")));

            UserAdminService manager = guarded(
                    jpa,
                    "DEFAULT",
                    () -> Optional.of(session(
                            reservedUserId(setup, ReservedSecurityRole.MANAGER),
                            ReservedSecurityRole.MANAGER)));
            assertThrows(AuthorizationException.class, () -> manager.assignRole(new UserRoleAssignmentCommand(
                    user.getId(), role.getId(), LocalDate.of(2026, 6, 1), "manager")));
            assertThrows(AuthorizationException.class, () -> manager.endAssignment(new UserRoleAssignmentEndCommand(
                    assignment.getId(), LocalDate.of(2026, 8, 31), false, "Denied", "manager")));

            assertEquals(usersBefore, userCount(jpa));
            assertEquals(rolesBefore, roleCount(jpa));
            assertEquals(assignmentsBefore, assignmentCount(jpa));
            assertTrue(assignmentActive(jpa, assignment.getId()));
            assertTrue(authorizationDenialCount(jpa) >= 4L);
        }
    }

    @Test
    void adminCanMaintainUsersRolesAndAssignmentsAndAuthorizationTracksCurrentSession(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("user-admin-session-switching")))
        {
            new CompanyAdminService(jpa).createCompany("OTHER", "Other Company");
            UserAdminService setup = unguarded(jpa, "DEFAULT");
            long adminUserId = reservedUserId(setup, ReservedSecurityRole.ADMIN);
            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(
                    Optional.of(session(adminUserId, "DEFAULT", Set.of(ReservedSecurityRole.MANAGER))));
            UserAdminService service = guarded(jpa, "DEFAULT", current::get);

            assertThrows(AuthorizationException.class, () -> service.saveUser(new AppUserCommand(
                    null, "manager-denied", "Manager Denied", null, true, "spoofed-manager")));

            current.set(Optional.of(session(adminUserId, "DEFAULT", Set.of(ReservedSecurityRole.ADMIN))));
            AppUser user = service.saveUser(new AppUserCommand(
                    null, "security-editor", "Security Editor", "security-editor@example.test", true, "admin"));
            AppRole role = service.saveRole(new AppRoleCommand(
                    null, "SECURITY_EDITOR", "Security Editor", "Maintained by ADMIN", true, "admin"));
            UserCompanyRole assignment = service.assignRole(new UserRoleAssignmentCommand(
                    user.getId(), role.getId(), LocalDate.of(2026, 8, 1), "admin"));
            UserCompanyRole ended = service.endAssignment(new UserRoleAssignmentEndCommand(
                    assignment.getId(), LocalDate.of(2026, 8, 31), false, "Term complete", "admin"));
            assertFalse(ended.isActive());

            current.set(Optional.of(session(
                    adminUserId,
                    "DEFAULT",
                    Set.of(
                            ReservedSecurityRole.VIEWER,
                            ReservedSecurityRole.ACCOUNTANT,
                            ReservedSecurityRole.MANAGER))));
            assertThrows(AuthorizationException.class, () -> service.saveRole(new AppRoleCommand(
                    null, "UNION_DENIED", "Union Denied", null, true, "non-admin-union")));

            current.set(Optional.of(session(adminUserId, "OTHER", Set.of(ReservedSecurityRole.ADMIN))));
            assertThrows(AuthorizationException.class, () -> service.saveUser(new AppUserCommand(
                    null, "wrong-company", "Wrong Company", null, true, "admin")));

            current.set(Optional.empty());
            assertThrows(AuthorizationException.class, () -> service.saveRole(new AppRoleCommand(
                    null, "NO_SESSION", "No Session", null, true, "none")));

            assertEquals(1L, setup.listUsers().stream()
                    .filter(value -> "security-editor".equals(value.getUsername()))
                    .count());
            assertEquals(1L, setup.listRoles().stream()
                    .filter(value -> "SECURITY_EDITOR".equals(value.getCode()))
                    .count());
            assertFalse(assignmentActive(jpa, assignment.getId()));
        }
    }

    @Test
    void authorizationDoesNotBypassReservedSecurityProtections(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("user-admin-reserved-protections")))
        {
            UserAdminService setup = unguarded(jpa, "DEFAULT");
            long adminUserId = reservedUserId(setup, ReservedSecurityRole.ADMIN);
            UserAdminService service = guarded(
                    jpa,
                    "DEFAULT",
                    () -> Optional.of(session(adminUserId, ReservedSecurityRole.ADMIN)));

            AppUser reservedAdmin = service.listUsers().stream()
                    .filter(value -> ReservedSecurityRole.ADMIN.name().equalsIgnoreCase(value.getUsername()))
                    .findFirst()
                    .orElseThrow();
            AppRole reservedAdminRole = service.listRoles().stream()
                    .filter(value -> ReservedSecurityRole.ADMIN.name().equalsIgnoreCase(value.getCode()))
                    .findFirst()
                    .orElseThrow();
            UserCompanyRole requiredAdminAssignment = service.listAssignments().stream()
                    .filter(UserCompanyRole::isRequiredSecurityAssignment)
                    .findFirst()
                    .orElseThrow();

            assertThrows(IllegalStateException.class, () -> service.saveUser(new AppUserCommand(
                    reservedAdmin.getId(),
                    "renamed-admin",
                    reservedAdmin.getDisplayName(),
                    reservedAdmin.getEmail(),
                    true,
                    "admin")));
            assertThrows(IllegalStateException.class, () -> service.saveRole(new AppRoleCommand(
                    reservedAdminRole.getId(),
                    reservedAdminRole.getCode(),
                    reservedAdminRole.getName(),
                    reservedAdminRole.getDescription(),
                    false,
                    "admin")));
            assertThrows(IllegalStateException.class, () -> service.endAssignment(new UserRoleAssignmentEndCommand(
                    requiredAdminAssignment.getId(),
                    LocalDate.of(2026, 8, 31),
                    false,
                    "Must remain assigned",
                    "admin")));
        }
    }

    @Test
    void authorizationDoesNotBypassOrdinaryAssignmentLifecycleProtections(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("user-admin-assignment-protections")))
        {
            UserAdminService setup = unguarded(jpa, "DEFAULT");
            long adminUserId = reservedUserId(setup, ReservedSecurityRole.ADMIN);
            UserAdminService service = guarded(
                    jpa,
                    "DEFAULT",
                    () -> Optional.of(session(adminUserId, ReservedSecurityRole.ADMIN)));

            AppUser user = service.saveUser(new AppUserCommand(
                    null, "lifecycle-user", "Lifecycle User", null, true, "admin"));
            AppRole role = service.saveRole(new AppRoleCommand(
                    null, "LIFECYCLE_ROLE", "Lifecycle Role", null, true, "admin"));
            assertEquals(
                    "LIFECYCLE_ROLE|null",
                    roleDatabaseState(jpa, role.getId()),
                    "saveRole returned an ID whose database row is not the custom unreserved role");
            service.assignRole(new UserRoleAssignmentCommand(
                    user.getId(), role.getId(), LocalDate.of(2026, 1, 1), "admin"));

            assertThrows(IllegalStateException.class, () -> service.assignRole(new UserRoleAssignmentCommand(
                    user.getId(), role.getId(), LocalDate.of(2026, 2, 1), "admin")));
            assertThrows(IllegalStateException.class, () -> service.saveUser(new AppUserCommand(
                    user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), false, "admin")));
            assertThrows(IllegalStateException.class, () -> service.saveRole(new AppRoleCommand(
                    role.getId(), role.getCode(), role.getName(), role.getDescription(), false, "admin")));
        }
    }

    private static UserAdminService unguarded(Jpa jpa, String companyCode)
    {
        return new UserAdminService(jpa, () -> companyCode, CLOCK, () -> { });
    }

    private static UserAdminService guarded(
            Jpa jpa,
            String companyCode,
            java.util.function.Supplier<Optional<AuthenticatedUserSession>> currentSession)
    {
        return new UserAdminService(
                jpa,
                () -> companyCode,
                CLOCK,
                () -> { },
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

    private static AuthenticatedUserSession session(long userId, ReservedSecurityRole role)
    {
        return session(userId, "DEFAULT", Set.of(role));
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

    private static long userCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(u) from AppUser u", Long.class).getSingleResult();
        }
    }

    private static long roleCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(r) from AppRole r", Long.class).getSingleResult();
        }
    }

    private static long assignmentCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(a) from UserCompanyRole a", Long.class).getSingleResult();
        }
    }

    private static boolean assignmentActive(Jpa jpa, long assignmentId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.find(UserCompanyRole.class, assignmentId).isActive();
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

    private static String roleDatabaseState(Jpa jpa, long roleId)
    {
        try (EntityManager em = jpa.em())
        {
            Object[] row = (Object[]) em.createNativeQuery(
                            "select code, reserved_security_code from app_role where id = :id")
                    .setParameter("id", roleId)
                    .getSingleResult();
            return row[0] + "|" + row[1];
        }
    }
}
