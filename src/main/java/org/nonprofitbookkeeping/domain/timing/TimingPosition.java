package org.nonprofitbookkeeping.domain.timing;

/**
 * Relative timing perspective for either bank settlement or budget recognition.
 */
public enum TimingPosition
{
    PREVIOUSLY,
    NOW,
    FUTURE,
    NONE
}
