package org.nonprofitbookkeeping.service;

/** Copies a revision-bound definition graph into the current company, with zero openings. */
public record ChartCloneCommand(long sourceChartId, String sourceRevision, String name, String version)
{
}
