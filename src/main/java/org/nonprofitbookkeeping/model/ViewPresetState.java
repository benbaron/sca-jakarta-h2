package org.nonprofitbookkeeping.model;

/**
 * Persisted view preset metadata for restoring panel and date range.
 */
public record ViewPresetState(String name,
                              String panelId,
                              String startDateIso,
                              String endDateIso)
{
}
