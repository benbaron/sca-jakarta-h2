package org.nonprofitbookkeeping.ui;

/** Supplies the best available factual actor for local desktop audit commands. */
final class DesktopActorIdentity
{
    private static final String FALLBACK = "local-desktop";

    private DesktopActorIdentity()
    {
    }

    static String current()
    {
        return ApplicationSessionContext.sharedSessionState()
                .authenticatedUser()
                .map(session -> fromUserName(session.username()))
                .orElseGet(() -> fromUserName(System.getProperty("user.name")));
    }

    static String fromUserName(String userName)
    {
        if (userName == null || userName.isBlank())
        {
            return FALLBACK;
        }
        return userName.trim();
    }
}
