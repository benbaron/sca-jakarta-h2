package org.nonprofitbookkeeping.service;

/**
 * Adapter contract for binary report exports.
 */
public interface FinancialReportExportAdapter
{
    FinancialReportExportFormat format();

    byte[] render(String reportName, String textPreview, String csvBody);
}
