package org.nonprofitbookkeeping.ui;

/**
 * Lightweight UI diagnostics for temporary desktop troubleshooting.
 */
final class UiDebug
{
    private UiDebug()
    {
    }

    static void log(String area, String message)
    {
        System.err.println("[NPBK][" + area + "] " + message);
    }
}
