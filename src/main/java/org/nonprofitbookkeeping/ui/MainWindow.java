package org.nonprofitbookkeeping.ui;

/**
 * Compatibility facade for callers that have not yet moved to {@link ApplicationSessionContext}.
 *
 * <p>The production JavaFX shell is {@link ProductionWorkspaceWindow}. This class no longer owns
 * window chrome, navigation, search, date-range controls, view presets, or panel routing.</p>
 */
@Deprecated(forRemoval = false)
public final class MainWindow
{
    private MainWindow()
    {
    }

    static UiSessionState sharedSessionState()
    {
        return ApplicationSessionContext.sharedSessionState();
    }
}
