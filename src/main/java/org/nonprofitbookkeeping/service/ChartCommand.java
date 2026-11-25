package org.nonprofitbookkeeping.service;

/** Creates a DRAFT chart or edits the exact chart's metadata/lifecycle. */
public record ChartCommand(Long id, String name, String version, boolean retired, String expectedRevision)
{
}
