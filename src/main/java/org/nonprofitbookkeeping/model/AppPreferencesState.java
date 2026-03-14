package org.nonprofitbookkeeping.model;

/**
 * Persisted user preferences and shell state.
 */
public record AppPreferencesState(UiThemePreference themePreference,
                                  boolean useNativeWindowDecorations,
                                  boolean rememberWindowState,
                                  UserPrivilegeLevel defaultPrivilege)
{
}
