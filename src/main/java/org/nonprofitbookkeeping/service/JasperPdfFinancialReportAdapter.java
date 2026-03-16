package org.nonprofitbookkeeping.service;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PDF adapter using JasperReports directly.
 */
public class JasperPdfFinancialReportAdapter implements FinancialReportExportAdapter
{
    @Override
    public FinancialReportExportFormat format()
    {
        return FinancialReportExportFormat.PDF;
    }

    @Override
    public byte[] render(String reportName, String textPreview, String csvBody)
    {
        try
        {
            JasperDesign design = JRXmlLoader.load(new ByteArrayInputStream(template().getBytes(StandardCharsets.UTF_8)));
            var report = JasperCompileManager.compileReport(design);

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("REPORT_TITLE", reportName == null ? "Report" : reportName);
            params.put("REPORT_BODY", textPreview == null ? "" : textPreview);

            JRDataSource ds = new JREmptyDataSource(1);
            JasperPrint print = JasperFillManager.fillReport(report, params, ds);
            return JasperExportManager.exportReportToPdf(print);
        }
        catch (JRException ex)
        {
            throw new IllegalStateException("Could not render PDF via JasperReports.", ex);
        }
    }

    private static String template()
    {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports"
                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              xsi:schemaLocation="http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd"
                              name="financial_report"
                              pageWidth="595" pageHeight="842" columnWidth="515"
                              leftMargin="40" rightMargin="40" topMargin="30" bottomMargin="30"
                              uuid="a6df5f0f-1f4d-4ccf-9f40-7341b9281264">
                  <parameter name="REPORT_TITLE" class="java.lang.String"/>
                  <parameter name="REPORT_BODY" class="java.lang.String"/>
                  <title>
                    <band height="24">
                      <textField>
                        <reportElement x="0" y="0" width="515" height="20"/>
                        <textElement>
                          <font size="14" isBold="true"/>
                        </textElement>
                        <textFieldExpression><![CDATA[$P{REPORT_TITLE}]]></textFieldExpression>
                      </textField>
                    </band>
                  </title>
                  <detail>
                    <band height="760">
                      <textField isStretchWithOverflow="true">
                        <reportElement x="0" y="0" width="515" height="740"/>
                        <textElement>
                          <font size="10"/>
                        </textElement>
                        <textFieldExpression><![CDATA[$P{REPORT_BODY}]]></textFieldExpression>
                      </textField>
                    </band>
                  </detail>
                </jasperReport>
                """;
    }
}
