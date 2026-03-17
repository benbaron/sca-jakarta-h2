package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.FinancialReportExportFormat;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ReportLibraryPanelTest component.
 */
class ReportLibraryPanelTest
{
    @Test
    void buildReportExportFileName_normalizesName()
    {
        assertEquals("budget-vs-actual-2026-03-15.txt",
                ReportLibraryPanel.buildReportExportFileName("Budget vs Actual", LocalDate.of(2026, 3, 15), FinancialReportExportFormat.TEXT));
    }

    @Test
    void buildReportExportFileName_handlesNonWordNames()
    {
        assertEquals("report-2026-03-15.txt",
                ReportLibraryPanel.buildReportExportFileName("***", LocalDate.of(2026, 3, 15), FinancialReportExportFormat.TEXT));
    }

    @Test
    void buildReportExportFileName_supportsCsvExtension()
    {
        assertEquals("trial-balance-2026-03-15.csv",
                ReportLibraryPanel.buildReportExportFileName("Trial Balance", LocalDate.of(2026, 3, 15), FinancialReportExportFormat.CSV));
    }

    @Test
    void buildReportExportFileName_supportsPdfAndXlsxExtension()
    {
        assertEquals("trial-balance-2026-03-15.pdf",
                ReportLibraryPanel.buildReportExportFileName("Trial Balance", LocalDate.of(2026, 3, 15), FinancialReportExportFormat.PDF));
        assertEquals("trial-balance-2026-03-15.xlsx",
                ReportLibraryPanel.buildReportExportFileName("Trial Balance", LocalDate.of(2026, 3, 15), FinancialReportExportFormat.XLSX));
    }
}
