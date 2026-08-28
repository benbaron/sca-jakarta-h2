package org.nonprofitbookkeeping.ui;

/**
 * Owns application-wide desktop session state independently of any JavaFX shell implementation.
 */
public final class ApplicationSessionContext
{
    private static final UiSessionState SESSION_STATE = new UiSessionState();

    private ApplicationSessionContext()
    {
    }

    static UiSessionState sharedSessionState()
    {
        return SESSION_STATE;
    }
}
