package org.nonprofitbookkeeping.model;

/**
 * Persisted user preferences and shell state.
 */
public record AppPreferencesState(UiThemePreference themePreference,
                                  boolean useNativeWindowDecorations,
                                  boolean rememberWindowState,
                                  UserPrivilegeLevel defaultPrivilege,
                                  CorrectionMethod correctionMethod,
                                  ClosedPeriodPolicy closedPeriodPolicy,
                                  boolean requireReopenReason,
                                  ReopenScope defaultReopenScope,
                                  boolean confirmEnteredTransactionDeletion)
{
    public AppPreferencesState(UiThemePreference themePreference,
                               boolean useNativeWindowDecorations,
                               boolean rememberWindowState,
                               UserPrivilegeLevel defaultPrivilege)
    {
        this(themePreference,
                useNativeWindowDecorations,
                rememberWindowState,
                defaultPrivilege,
                CorrectionMethod.DIRECT_EDIT,
                ClosedPeriodPolicy.WARN_AND_REOPEN,
                false,
                ReopenScope.UNTIL_MANUALLY_CLOSED,
                true);
    }
}
