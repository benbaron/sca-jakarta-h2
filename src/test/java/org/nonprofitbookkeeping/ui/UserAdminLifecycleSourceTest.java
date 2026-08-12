package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAdminLifecycleSourceTest
{
    @Test
    void panelExposesRealStableLifecycleOperationsWithoutAuthorizationClaims() throws Exception
    {
        String panel = source("src/main/java/org/nonprofitbookkeeping/ui/UserAdminPanel.java");
        String service = source("src/main/java/org/nonprofitbookkeeping/service/UserAdminService.java");
        String migration = source(
                "src/main/resources/db/migration/V72__user_role_assignment_lifecycle.sql");

        assertTrue(panel.contains("new AppUserCommand("));
        assertTrue(panel.contains("new AppRoleCommand("));
        assertTrue(panel.contains("new UserRoleAssignmentCommand("));
        assertTrue(panel.contains("new UserRoleAssignmentEndCommand("));
        assertTrue(panel.contains("End Selected"));
        assertTrue(panel.contains("Revoke Selected"));
        assertTrue(panel.contains("do not authenticate a login or enforce permissions"));
        assertTrue(panel.contains("userAdminRolesSplit"));
        assertTrue(panel.contains("CompanySplitPaneStateBinder.bind"));
        assertFalse(panel.contains("Delete Role"));
        assertFalse(panel.contains("Delete Assignment"));

        assertTrue(service.contains("PESSIMISTIC_WRITE"));
        assertTrue(service.contains("rejectOverlappingAssignment"));
        assertTrue(service.contains("USER_ROLE_REVOKED"));
        assertTrue(service.contains("APP_ROLE_DEACTIVATED"));
        assertTrue(service.contains("where a.id = :id and a.company = :company"));
        assertTrue(migration.contains("uq_user_company_role_period"));
        assertTrue(migration.contains("ck_user_company_role_active_end"));
    }

    private static String source(String path) throws Exception
    {
        return Files.readString(Path.of(path));
    }
}
