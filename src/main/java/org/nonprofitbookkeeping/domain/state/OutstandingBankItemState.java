package org.nonprofitbookkeeping.domain.state;

public enum OutstandingBankItemState
{
    RECORDED,
    UNCLEARED,
    CARRIED_FORWARD,
    CLEARED,
    VOIDED,
    STALE_WRITTEN_OFF
}
