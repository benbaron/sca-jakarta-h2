package org.nonprofitbookkeeping.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.nonprofitbookkeeping.report.template.SemanticReportValueSet;

/** Result generated from one immutable ReportRequest. */
public record ReportResult(
        ReportRequest request,
        String text,
        String csv,
        JsonNode semanticTemplate,
        SemanticReportValueSet semanticValues,
        ReportTableModel tableModel)
{
    public ReportResult(
            ReportRequest request,
            String text,
            String csv,
            JsonNode semanticTemplate,
            SemanticReportValueSet semanticValues)
    {
        this(request, text, csv, semanticTemplate, semanticValues, null);
    }

    public ReportResult
    {
        if (request == null)
        {
            throw new IllegalArgumentException("request is required.");
        }
        text = text == null ? "" : text;
        csv = csv == null ? "" : csv;
        if ((semanticTemplate == null) != (semanticValues == null))
        {
            throw new IllegalArgumentException("Semantic template and values must be supplied together.");
        }
        if (semanticTemplate != null && tableModel != null)
        {
            throw new IllegalArgumentException(
                    "A report preview cannot use semantic and core table models together.");
        }
    }

    public boolean semantic()
    {
        return semanticTemplate != null;
    }

    public boolean tabular()
    {
        return tableModel != null;
    }
}
