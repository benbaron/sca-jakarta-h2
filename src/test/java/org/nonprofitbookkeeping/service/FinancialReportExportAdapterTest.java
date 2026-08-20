package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.report.ReportTableModel;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void pdfAdapterRendersTheTypedTableWithCompanyFormattingAndWorkbookStyles()
    {
        FinancialReportDisplayFormat format = new FinancialReportDisplayFormat()
        {
            @Override
            public String formatDate(LocalDate value)
            {
                return "DATE[" + value + "]";
            }

            @Override
            public String formatMoney(BigDecimal value)
            {
                return "USD " + value.setScale(2);
            }
        };
        ReportTableModel model = new ReportTableModel(
                "styled-pdf",
                "Styled Report",
                "A subtitle that should remain visible",
                List.of(new ReportTableModel.HeaderLine(
                        "Parent Organization",
                        "Q2 Report",
                        ReportTableModel.HeaderStyle.PRIMARY)),
                List.of(
                        new ReportTableModel.Column(
                                "description", "Description", ReportTableModel.ValueFormat.TEXT, 240),
                        new ReportTableModel.Column(
                                "date", "Date", ReportTableModel.ValueFormat.DATE, 110),
                        new ReportTableModel.Column(
                                "amount", "Amount", ReportTableModel.ValueFormat.MONEY, 120)),
                List.of(
                        new ReportTableModel.Row(
                                ReportTableModel.RowStyle.SECTION,
                                Map.of("description", "ASSETS")),
                        new ReportTableModel.Row(
                                ReportTableModel.RowStyle.TOTAL,
                                Map.of(
                                        "description", "Total Assets",
                                        "date", LocalDate.of(2026, 6, 30),
                                        "amount", new BigDecimal("1234.5"))),
                        new ReportTableModel.Row(
                                ReportTableModel.RowStyle.STATUS_SUCCESS,
                                Map.of("description", "Balanced"))));

        JasperPdfFinancialReportAdapter adapter = new JasperPdfFinancialReportAdapter(format);
        byte[] bytes = adapter.render("ignored", "plain text", "", model);
        String header = new String(bytes, 0, Math.min(bytes.length, 8), StandardCharsets.US_ASCII);
        String template = adapter.structuredTemplateForTests(model);
        List<Map<String, ?>> rows = adapter.structuredRowsForTests(model);

        assertTrue(header.startsWith("%PDF-1."));
        assertTrue(template.contains("Description"));
        assertTrue(template.contains("#D9EAF7"));
        assertTrue(template.contains("#CFE2F3"));
        assertTrue(template.contains("#E2F0D9"));
        assertTrue(template.contains("ContainerHeight"));
        assertEquals("TOTAL", rows.get(1).get("ROW_STYLE"));
        assertEquals("DATE[2026-06-30]", rows.get(1).get("c1"));
        assertEquals("USD 1234.50", rows.get(1).get("c2"));
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
