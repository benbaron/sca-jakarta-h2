package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.report.ReportTableModel;

/**
 * Adapter contract for binary report exports.
 */
public interface FinancialReportExportAdapter
{
    FinancialReportExportFormat format();

    byte[] render(String reportName, String textPreview, String csvBody);

    default byte[] render(
            String reportName,
            String textPreview,
            String csvBody,
            ReportTableModel tableModel)
    {
        return render(reportName, textPreview, csvBody);
    }
}
