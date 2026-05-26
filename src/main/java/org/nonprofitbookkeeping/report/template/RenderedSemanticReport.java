package org.nonprofitbookkeeping.report.template;

/** Rendered preview/export payload produced from a semantic report template. */
public record RenderedSemanticReport(String text, String csv)
{
}
