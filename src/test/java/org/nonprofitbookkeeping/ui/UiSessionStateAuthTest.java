package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UiSessionStateAuthTest component.
 */
public class UiSessionStateAuthTest
{
    @Test
    public void passwordLifecycle_loginLogoutBehavesAsExpected()
    {
        UiSessionState session = new UiSessionState();

        session.setPassword("secret");
        assertTrue(session.hasPassword());
        assertFalse(session.isLoggedIn());

        assertFalse(session.login("wrong"));
        assertFalse(session.isLoggedIn());

        assertTrue(session.login("secret"));
        assertTrue(session.isLoggedIn());

        session.logout();
        assertFalse(session.isLoggedIn());

        session.setPassword("");
        assertFalse(session.hasPassword());
        assertTrue(session.login(""));
        assertTrue(session.isLoggedIn());
    }
}
