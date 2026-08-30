package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationPolicyTest
{
    @Test
    void reservedRolesMapToTheAdoptedPermissionMatrix()
    {
        assertAllows(ReservedSecurityRole.ADMIN,
                ApplicationPermission.BOOKKEEPING_WRITE,
                ApplicationPermission.COMPANY_ADMIN,
                ApplicationPermission.SECURITY_ADMIN,
                ApplicationPermission.DATABASE_ADMIN,
                ApplicationPermission.EXPORT,
                ApplicationPermission.UI_PREFERENCE_WRITE);

        assertAllows(ReservedSecurityRole.MANAGER,
                ApplicationPermission.BOOKKEEPING_WRITE,
                ApplicationPermission.COMPANY_ADMIN,
                ApplicationPermission.EXPORT,
                ApplicationPermission.UI_PREFERENCE_WRITE);
        assertFalse(AuthorizationPolicy.allows(Set.of(ReservedSecurityRole.MANAGER),
                ApplicationPermission.SECURITY_ADMIN));
        assertFalse(AuthorizationPolicy.allows(Set.of(ReservedSecurityRole.MANAGER),
                ApplicationPermission.DATABASE_ADMIN));

        assertAllows(ReservedSecurityRole.ACCOUNTANT,
                ApplicationPermission.BOOKKEEPING_WRITE,
                ApplicationPermission.EXPORT,
                ApplicationPermission.UI_PREFERENCE_WRITE);
        assertFalse(AuthorizationPolicy.allows(Set.of(ReservedSecurityRole.ACCOUNTANT),
                ApplicationPermission.COMPANY_ADMIN));
        assertFalse(AuthorizationPolicy.allows(Set.of(ReservedSecurityRole.ACCOUNTANT),
                ApplicationPermission.SECURITY_ADMIN));

        assertAllows(ReservedSecurityRole.VIEWER,
                ApplicationPermission.EXPORT,
                ApplicationPermission.UI_PREFERENCE_WRITE);
        assertFalse(AuthorizationPolicy.allows(Set.of(ReservedSecurityRole.VIEWER),
                ApplicationPermission.BOOKKEEPING_WRITE));
        assertFalse(AuthorizationPolicy.allows(Set.of(ReservedSecurityRole.VIEWER),
                ApplicationPermission.COMPANY_ADMIN));
    }

    @Test
    void multipleNonAdminRolesUsePermissionUnionAndNoRolesGrantNothing()
    {
        Set<ReservedSecurityRole> roles = Set.of(ReservedSecurityRole.ACCOUNTANT, ReservedSecurityRole.VIEWER);
        assertTrue(AuthorizationPolicy.allows(roles, ApplicationPermission.BOOKKEEPING_WRITE));
        assertTrue(AuthorizationPolicy.allows(roles, ApplicationPermission.EXPORT));
        assertFalse(AuthorizationPolicy.allows(roles, ApplicationPermission.COMPANY_ADMIN));
        assertFalse(AuthorizationPolicy.allows(Set.of(), ApplicationPermission.EXPORT));
        assertFalse(AuthorizationPolicy.allows(null, ApplicationPermission.EXPORT));
    }

    private static void assertAllows(ReservedSecurityRole role, ApplicationPermission... permissions)
    {
        for (ApplicationPermission permission : permissions)
        {
            assertTrue(AuthorizationPolicy.allows(Set.of(role), permission),
                    () -> role + " should allow " + permission);
        }
    }
}
