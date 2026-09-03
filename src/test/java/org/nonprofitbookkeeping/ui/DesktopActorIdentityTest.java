package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.ReservedSecurityRole;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DesktopActorIdentityTest
{

    @Test
    void currentPrefersAuthenticatedApplicationSessionUsername()
    {
        UiSessionState sessionState = ApplicationSessionContext.sharedSessionState();
        Optional<AuthenticatedUserSession> previous = sessionState.authenticatedUser();
        Instant now = Instant.parse("2026-09-03T18:00:00Z");
        try
        {
            sessionState.setAuthenticatedUser(new AuthenticatedUserSession(
                    42L,
                    "  operator  ",
                    "Operator",
                    "TEST",
                    Set.of(ReservedSecurityRole.VIEWER),
                    now,
                    now));

            assertEquals("operator", DesktopActorIdentity.current());
        }
        finally
        {
            sessionState.clearAuthenticatedUser();
            previous.ifPresent(sessionState::setAuthenticatedUser);
        }
    }

    @Test
    void usesTrimmedOperatingSystemUserWhenAvailable()
    {
        assertEquals("alex", DesktopActorIdentity.fromUserName("  alex  "));
    }

    @Test
    void usesNonSecurityFallbackWhenOperatingSystemUserIsUnavailable()
    {
        assertEquals("local-desktop", DesktopActorIdentity.fromUserName("  "));
        assertEquals("local-desktop", DesktopActorIdentity.fromUserName(null));
    }
}
