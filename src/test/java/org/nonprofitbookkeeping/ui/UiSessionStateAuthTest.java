package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.ReservedSecurityRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the in-memory authenticated identity boundary; credential material never belongs here. */
public class UiSessionStateAuthTest
{
    @Test
    void freshSessionHasNoAuthenticatedIdentityEvenWithLegacyDefaultPrivilege()
    {
        UiSessionState session = new UiSessionState();

        assertEquals(UserPrivilegeLevel.ACCOUNTANT, session.preferences().defaultPrivilege());
        assertFalse(session.isAuthenticated());
        assertTrue(session.authenticatedUser().isEmpty());
    }

    @Test
    void authenticatedIdentityRoleSwitchActivityAndLogoutAreTrackedWithoutPasswords()
    {
        UiSessionState session = new UiSessionState();
        List<Optional<AuthenticatedUserSession>> changes = new ArrayList<>();
        session.onAuthenticationChanged(changes::add);

        Instant authenticatedAt = Instant.parse("2026-08-30T03:00:00Z");
        Instant initialActivity = Instant.parse("2026-08-30T03:01:00Z");
        AuthenticatedUserSession accountant = new AuthenticatedUserSession(
                41L,
                "accountant",
                "Accountant",
                "ALPHA",
                Set.of(ReservedSecurityRole.ACCOUNTANT),
                authenticatedAt,
                initialActivity);

        session.setAuthenticatedUser(accountant);

        assertTrue(session.isAuthenticated());
        assertEquals(accountant, session.authenticatedUser().orElseThrow());
        assertEquals(List.of(Optional.of(accountant)), changes);

        Instant laterActivity = Instant.parse("2026-08-30T03:05:00Z");
        session.touchAuthenticatedActivity(laterActivity);
        AuthenticatedUserSession touched = session.authenticatedUser().orElseThrow();
        assertEquals(accountant.userId(), touched.userId());
        assertEquals(accountant.username(), touched.username());
        assertEquals(accountant.companyCode(), touched.companyCode());
        assertEquals(accountant.effectiveRoles(), touched.effectiveRoles());
        assertEquals(authenticatedAt, touched.authenticatedAt());
        assertEquals(laterActivity, touched.lastActivityAt());
        assertEquals(1, changes.size(), "activity-only changes do not replace authenticated identity");

        AuthenticatedUserSession viewerInOtherCompany = touched.withCompany(
                "BETA",
                Set.of(ReservedSecurityRole.VIEWER),
                Instant.parse("2026-08-30T03:06:00Z"));
        session.setAuthenticatedUser(viewerInOtherCompany);

        AuthenticatedUserSession switched = session.authenticatedUser().orElseThrow();
        assertEquals("BETA", switched.companyCode());
        assertEquals(Set.of(ReservedSecurityRole.VIEWER), switched.effectiveRoles());
        assertFalse(switched.hasRole(ReservedSecurityRole.ACCOUNTANT));
        assertTrue(switched.hasRole(ReservedSecurityRole.VIEWER));
        assertEquals(2, changes.size());
        assertEquals(Optional.of(viewerInOtherCompany), changes.get(1));

        Optional<AuthenticatedUserSession> cleared = session.clearAuthenticatedUser();

        assertEquals(Optional.of(viewerInOtherCompany), cleared);
        assertFalse(session.isAuthenticated());
        assertTrue(session.authenticatedUser().isEmpty());
        assertEquals(3, changes.size());
        assertTrue(changes.get(2).isEmpty());
    }

    @Test
    void touchingActivityWhileLoggedOutIsANoOp()
    {
        UiSessionState session = new UiSessionState();

        session.touchAuthenticatedActivity(Instant.parse("2026-08-30T04:00:00Z"));

        assertFalse(session.isAuthenticated());
        assertTrue(session.authenticatedUser().isEmpty());
    }
}
