package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialReportExportAdapterTest
{
    @Test
    void pdfAdapter_producesPdfHeader()
    {
        JasperPdfFinancialReportAdapter adapter = new JasperPdfFinancialReportAdapter();
        byte[] bytes = adapter.render("Trial Balance", "Trial Balance\nLine", "");
        String header = new String(bytes, 0, Math.min(bytes.length, 8), StandardCharsets.US_ASCII);
        assertTrue(header.startsWith("%PDF-1."));
    }

    @Test
    void xlsxAdapter_producesZipWorkbookEntries() throws Exception
    {
        PoiXlsxFinancialReportAdapter adapter = new PoiXlsxFinancialReportAdapter();
        byte[] bytes = adapter.render("Trial Balance", "", "a,b\n1,2\n");

        boolean contentTypes = false;
        boolean workbook = false;
        boolean sheet = false;
        try (ZipInputStream zin = new ZipInputStream(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8))
        {
            java.util.zip.ZipEntry e;
            while ((e = zin.getNextEntry()) != null)
            {
                if ("[Content_Types].xml".equals(e.getName())) contentTypes = true;
                if ("xl/workbook.xml".equals(e.getName())) workbook = true;
                if ("xl/worksheets/sheet1.xml".equals(e.getName())) sheet = true;
            }
        }

        assertTrue(contentTypes);
        assertTrue(workbook);
        assertTrue(sheet);
    }
}
