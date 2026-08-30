package org.nonprofitbookkeeping.service;

import java.util.EnumSet;
import java.util.Set;

/** Fixed application permission policy for the reserved P20 security roles. */
public final class AuthorizationPolicy
{
    private AuthorizationPolicy()
    {
    }

    public static Set<ApplicationPermission> permissions(Set<ReservedSecurityRole> roles)
    {
        EnumSet<ApplicationPermission> result = EnumSet.noneOf(ApplicationPermission.class);
        if (roles == null || roles.isEmpty())
        {
            return Set.of();
        }
        for (ReservedSecurityRole role : roles)
        {
            if (role == null)
            {
                continue;
            }
            switch (role)
            {
                case ADMIN -> result.addAll(EnumSet.allOf(ApplicationPermission.class));
                case MANAGER -> result.addAll(EnumSet.of(
                        ApplicationPermission.BOOKKEEPING_WRITE,
                        ApplicationPermission.COMPANY_ADMIN,
                        ApplicationPermission.EXPORT,
                        ApplicationPermission.UI_PREFERENCE_WRITE));
                case ACCOUNTANT -> result.addAll(EnumSet.of(
                        ApplicationPermission.BOOKKEEPING_WRITE,
                        ApplicationPermission.EXPORT,
                        ApplicationPermission.UI_PREFERENCE_WRITE));
                case VIEWER -> result.addAll(EnumSet.of(
                        ApplicationPermission.EXPORT,
                        ApplicationPermission.UI_PREFERENCE_WRITE));
            }
        }
        return result.isEmpty() ? Set.of() : Set.copyOf(result);
    }

    public static boolean allows(Set<ReservedSecurityRole> roles, ApplicationPermission permission)
    {
        return permission != null && permissions(roles).contains(permission);
    }
}
