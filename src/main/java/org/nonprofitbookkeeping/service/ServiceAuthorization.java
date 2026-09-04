package org.nonprofitbookkeeping.service;

/** Shared nullable guard adapter for production services with legacy/test constructors. */
final class ServiceAuthorization
{
    private ServiceAuthorization()
    {
    }

    static void require(
            AuthorizationGuard guard,
            ApplicationPermission permission,
            String companyCode,
            String operation)
    {
        if (guard != null)
        {
            guard.require(permission, companyCode, operation);
        }
    }

    static void require(
            AuthorizationGuard guard,
            ApplicationPermission permission,
            String operation)
    {
        if (guard != null)
        {
            guard.require(permission, operation);
        }
    }

    static String actor(
            AuthorizationGuard guard,
            ApplicationPermission permission,
            String companyCode,
            String operation,
            String fallbackActor)
    {
        if (guard == null)
        {
            return fallbackActor;
        }
        return guard.requireActor(permission, companyCode, operation);
    }

    static String actor(
            AuthorizationGuard guard,
            ApplicationPermission permission,
            String operation,
            String fallbackActor)
    {
        if (guard == null)
        {
            return fallbackActor;
        }
        return guard.require(permission, operation).username();
    }
}
