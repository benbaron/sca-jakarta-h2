package org.nonprofitbookkeeping.domain.state;

/**
 * Enumeration of OutstandingBankItemState values.
 */
public enum OutstandingBankItemState
{
    RECORDED,
    UNCLEARED,
    CARRIED_FORWARD,
    CLEARED,
    VOIDED,
    STALE_WRITTEN_OFF
}
