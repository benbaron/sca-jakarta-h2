package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

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
                ReportLibraryPanel.buildReportExportFileName("Budget vs Actual", LocalDate.of(2026, 3, 15)));
    }

    @Test
    void buildReportExportFileName_handlesNonWordNames()
    {
        assertEquals("report-2026-03-15.txt",
                ReportLibraryPanel.buildReportExportFileName("***", LocalDate.of(2026, 3, 15)));
    }
}
