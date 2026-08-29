package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.report.ReportDefinition;

/** Company-owned defaults that apply only when a new Report Library is opened. */
public record CompanyReportingDefaults(
        ReportDefinition defaultReport,
        FinancialReportExportFormat defaultExportFormat)
{
    public CompanyReportingDefaults
    {
        defaultReport = defaultReport == null ? ReportDefinition.TRIAL_BALANCE : defaultReport;
        defaultExportFormat = defaultExportFormat == null
                ? FinancialReportExportFormat.TEXT
                : defaultExportFormat;
    }

    public static CompanyReportingDefaults defaults()
    {
        return new CompanyReportingDefaults(
                ReportDefinition.TRIAL_BALANCE,
                FinancialReportExportFormat.TEXT);
    }
}
