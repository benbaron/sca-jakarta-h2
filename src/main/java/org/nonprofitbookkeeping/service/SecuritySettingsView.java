package org.nonprofitbookkeeping.service;

/** Database-global authentication settings. Zero timeout means disabled. */
public record SecuritySettingsView(
        int inactivityTimeoutMinutes,
        boolean adminRecoveryPending,
        boolean bootstrapInitialized)
{
}
