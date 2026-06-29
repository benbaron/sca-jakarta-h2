package org.nonprofitbookkeeping.model;

/**
 * Defines how the application handles attempts to change a closed period.
 */
public enum ClosedPeriodPolicy
{
    WARN_AND_REOPEN,
    REQUIRE_REASON,
    REQUIRE_FORMAL_ADJUSTMENT
}
