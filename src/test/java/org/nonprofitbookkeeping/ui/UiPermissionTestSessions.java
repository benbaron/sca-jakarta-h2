package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.ReservedSecurityRole;

import java.time.Instant;
import java.util.Set;

final class UiPermissionTestSessions
{
    private UiPermissionTestSessions()
    {
    }

    static AuthenticatedUserSession admin()
    {
        return session(Set.of(ReservedSecurityRole.ADMIN));
    }

    static AuthenticatedUserSession manager()
    {
        return session(Set.of(ReservedSecurityRole.MANAGER));
    }

    static AuthenticatedUserSession viewer()
    {
        return session(Set.of(ReservedSecurityRole.VIEWER));
    }

    static AuthenticatedUserSession session(Set<ReservedSecurityRole> roles)
    {
        Instant now = Instant.parse("2026-09-03T18:00:00Z");
        return new AuthenticatedUserSession(
                9001L,
                "operator",
                "Operator",
                "DEFAULT",
                roles,
                now,
                now);
    }
}
