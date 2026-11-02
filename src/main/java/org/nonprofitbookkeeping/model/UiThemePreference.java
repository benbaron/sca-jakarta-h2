package org.nonprofitbookkeeping.model;

/**
 * Supported persisted visual themes.
 */
public enum UiThemePreference
{
    /** Retained persisted value; now uses AtlantaFX Primer Light. */
    LIGHT("Primer Light"),
    /** Retained persisted value; now uses AtlantaFX Primer Dark. */
    DARK("Primer Dark"),
    SYSTEM_DEFAULT("System Default (JavaFX)"),
    NORD_LIGHT("Nord Light"),
    NORD_DARK("Nord Dark"),
    CUPERTINO_LIGHT("Cupertino Light"),
    CUPERTINO_DARK("Cupertino Dark"),
    DRACULA("Dracula");

    private final String displayName;

    UiThemePreference(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
