package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.FinancialReportExportFormat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportLibraryPanelExportIntegrationTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    void exportSelection_textCsvPdfXlsx_writesExpectedBytes() throws Exception
    {
        Path text = Files.createTempFile("report-library", ".txt");
        Path csv = Files.createTempFile("report-library", ".csv");
        Path pdf = Files.createTempFile("report-library", ".pdf");
        Path xlsx = Files.createTempFile("report-library", ".xlsx");

        FxTestSupport.onFx(() -> {
            ReportLibraryPanel panel = new ReportLibraryPanel();

            panel.setExportFormatForTests(FinancialReportExportFormat.TEXT);
            panel.exportReportToPathForTests(text);

            panel.setExportFormatForTests(FinancialReportExportFormat.CSV);
            panel.exportReportToPathForTests(csv);

            panel.setExportFormatForTests(FinancialReportExportFormat.PDF);
            panel.exportReportToPathForTests(pdf);

            panel.setExportFormatForTests(FinancialReportExportFormat.XLSX);
            panel.exportReportToPathForTests(xlsx);
            return null;
        });

        String textBody = Files.readString(text, StandardCharsets.UTF_8);
        String csvBody = Files.readString(csv, StandardCharsets.UTF_8);
        byte[] pdfBytes = Files.readAllBytes(pdf);
        byte[] xlsxBytes = Files.readAllBytes(xlsx);

        assertTrue(textBody.contains("Trial Balance") || textBody.contains("General Ledger") || textBody.contains("Balance Sheet") || textBody.contains("Income Statement"));
        assertTrue(csvBody.startsWith("account_code") || csvBody.startsWith("txn_date") || csvBody.startsWith("section"));

        String pdfHeader = new String(pdfBytes, 0, Math.min(pdfBytes.length, 8), StandardCharsets.US_ASCII);
        assertTrue(pdfHeader.startsWith("%PDF-1."));
        assertTrue(xlsxBytes.length > 4);
        assertTrue(xlsxBytes[0] == 'P' && xlsxBytes[1] == 'K');
    }
}
