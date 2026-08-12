package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppRole;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.UserCompanyRole;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAdminServiceLifecycleTest
{
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void stableIdUserAndRoleEditsAreAuditedAndSurviveRestart(@TempDir Path tempDir)
    {
        Path database = tempDir.resolve("user-role-stable-id");
        Long userId;
        Long roleId;
        try (Jpa jpa = new Jpa(database))
        {
            UserAdminService service = service(jpa, "DEFAULT");
            AppUser user = service.saveUser(new AppUserCommand(
                    null, "Treasurer", "First Treasurer", "first@example.test", true, "tester"));
            AppRole role = service.saveRole(new AppRoleCommand(
                    null, "REVIEWER", "Reviewer", "Initial description", true, "tester"));
            userId = user.getId();
            roleId = role.getId();

            AppUser updatedUser = service.saveUser(new AppUserCommand(
                    userId, "Exchequer", "Branch Exchequer", "exchequer@example.test", true, "tester"));
            AppRole updatedRole = service.saveRole(new AppRoleCommand(
                    roleId, "FINANCIAL_REVIEWER", "Financial Reviewer", "Reviews factual records", true, "tester"));

            assertEquals(userId, updatedUser.getId());
            assertEquals(roleId, updatedRole.getId());
            assertEquals(1L, service.listUsers().stream().filter(value -> value.getId().equals(userId)).count());
            assertEquals(1L, service.listRoles().stream().filter(value -> value.getId().equals(roleId)).count());
            assertAuditActions(jpa, "APP_USER_CREATED", "APP_USER_UPDATED", "APP_ROLE_CREATED", "APP_ROLE_UPDATED");
        }

        try (Jpa jpa = new Jpa(database))
        {
            UserAdminService service = service(jpa, "DEFAULT");
            assertEquals("exchequer", service.listUsers().stream()
                    .filter(value -> value.getId().equals(userId)).findFirst().orElseThrow().getUsername());
            assertEquals("FINANCIAL_REVIEWER", service.listRoles().stream()
                    .filter(value -> value.getId().equals(roleId)).findFirst().orElseThrow().getCode());
        }
    }

    @Test
    void assignmentEndAndReassignmentRetainDistinctHistoryAndProtectRole(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("assignment-history")))
        {
            UserAdminService service = service(jpa, "DEFAULT");
            AppUser user = service.saveUser(new AppUserCommand(
                    null, "officer", "Branch Officer", null, true, "tester"));
            AppRole role = service.saveRole(new AppRoleCommand(
                    null, "OFFICER", "Officer", null, true, "tester"));

            UserCompanyRole first = service.assignRole(new UserRoleAssignmentCommand(
                    user.getId(), role.getId(), LocalDate.of(2026, 1, 1), "tester"));
            assertThrows(IllegalStateException.class, () -> service.assignRole(new UserRoleAssignmentCommand(
                    user.getId(), role.getId(), LocalDate.of(2026, 2, 1), "tester")));
            assertThrows(IllegalStateException.class, () -> service.saveRole(new AppRoleCommand(
                    role.getId(), role.getCode(), role.getName(), role.getDescription(), false, "tester")));
            assertThrows(IllegalStateException.class, () -> service.saveUser(new AppUserCommand(
                    user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), false, "tester")));

            UserCompanyRole ended = service.endAssignment(new UserRoleAssignmentEndCommand(
                    first.getId(), LocalDate.of(2026, 3, 31), false, "Term completed", "tester"));
            assertFalse(ended.isActive());
            assertEquals(LocalDate.of(2026, 3, 31), ended.getEndDate());

            UserCompanyRole second = service.assignRole(new UserRoleAssignmentCommand(
                    user.getId(), role.getId(), LocalDate.of(2026, 4, 1), "tester"));
            assertNotEquals(first.getId(), second.getId());
            assertEquals(2, service.listAssignments().size());
            assertEquals(1, service.roleUsage(role.getId()).activeAssignments());
            assertEquals(1, service.roleUsage(role.getId()).historicalAssignments());

            assertThrows(IllegalArgumentException.class, () -> service.endAssignment(
                    new UserRoleAssignmentEndCommand(second.getId(), LocalDate.of(2026, 8, 11), true, "", "tester")));
            service.endAssignment(new UserRoleAssignmentEndCommand(
                    second.getId(), LocalDate.of(2026, 8, 11), true, "Officer removed", "tester"));
            AppRole inactiveRole = service.saveRole(new AppRoleCommand(
                    role.getId(), role.getCode(), role.getName(), role.getDescription(), false, "tester"));
            AppUser inactiveUser = service.saveUser(new AppUserCommand(
                    user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), false, "tester"));
            assertFalse(inactiveRole.isActive());
            assertFalse(inactiveUser.isActive());
            assertEquals(2, service.roleUsage(role.getId()).historicalAssignments());
            assertAuditActions(jpa, "USER_ROLE_ASSIGNED", "USER_ROLE_ENDED", "USER_ROLE_REVOKED",
                    "APP_ROLE_DEACTIVATED", "APP_USER_DEACTIVATED");
        }
    }

    @Test
    void activeCompanyScopeRejectsCrossCompanyMutationAndReloadsExactHistory(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("assignment-company-isolation")))
        {
            new CompanyAdminService(jpa).createCompany("OTHER", "Other Company");
            UserAdminService defaultService = service(jpa, "DEFAULT");
            UserAdminService otherService = service(jpa, "OTHER");
            AppUser user = defaultService.saveUser(new AppUserCommand(
                    null, "shared", "Shared User", null, true, "tester"));
            AppRole role = defaultService.saveRole(new AppRoleCommand(
                    null, "SHARED", "Shared Role", null, true, "tester"));
            UserCompanyRole defaultAssignment = defaultService.assignRole(new UserRoleAssignmentCommand(
                    user.getId(), role.getId(), LocalDate.of(2026, 1, 1), "tester"));
            UserCompanyRole otherAssignment = otherService.assignRole(new UserRoleAssignmentCommand(
                    user.getId(), role.getId(), LocalDate.of(2026, 1, 1), "tester"));

            assertEquals(defaultAssignment.getId(), defaultService.listAssignments().get(0).getId());
            assertEquals(otherAssignment.getId(), otherService.listAssignments().get(0).getId());
            assertThrows(IllegalArgumentException.class, () -> defaultService.endAssignment(
                    new UserRoleAssignmentEndCommand(
                            otherAssignment.getId(), LocalDate.of(2026, 8, 11), false, "wrong company", "tester")));
            assertTrue(otherService.listAssignments().get(0).isActive());
        }
    }

    @Test
    void lateFailureRollsBackRoleAndAuditTogether(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("user-admin-late-failure")))
        {
            UserAdminService failing = new UserAdminService(
                    jpa, () -> "DEFAULT", CLOCK,
                    () -> { throw new IllegalStateException("injected user-admin late failure"); });

            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> failing.saveRole(
                    new AppRoleCommand(null, "ROLLBACK", "Rollback", null, true, "tester")));
            assertTrue(failure.getMessage().contains("injected user-admin late failure"));
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, em.createQuery(
                                "select count(r) from AppRole r where r.code = 'ROLLBACK'", Long.class)
                        .getSingleResult());
                assertEquals(0L, em.createQuery(
                                "select count(a) from AuditEvent a where a.actionType = 'APP_ROLE_CREATED' and a.afterValue like '%ROLLBACK%'",
                                Long.class)
                        .getSingleResult());
            }
        }
    }

    private static UserAdminService service(Jpa jpa, String companyCode)
    {
        return new UserAdminService(jpa, () -> companyCode, CLOCK, () -> { });
    }

    private static void assertAuditActions(Jpa jpa, String... actions)
    {
        try (EntityManager em = jpa.em())
        {
            for (String action : actions)
            {
                assertTrue(em.createQuery(
                                "select count(a) from AuditEvent a where a.actionType = :action", Long.class)
                        .setParameter("action", action)
                        .getSingleResult() > 0, action);
            }
        }
    }
}
