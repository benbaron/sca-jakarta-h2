package org.nonprofitbookkeeping.service;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.nonprofitbookkeeping.report.ReportTableModel;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF adapter using JasperReports directly.
 */
public class JasperPdfFinancialReportAdapter implements FinancialReportExportAdapter
{
    private static final int A4_LANDSCAPE_WIDTH = 842;
    private static final int A4_LANDSCAPE_HEIGHT = 595;
    private static final int A3_LANDSCAPE_WIDTH = 1191;
    private static final int A3_LANDSCAPE_HEIGHT = 842;
    private static final int PAGE_MARGIN = 28;

    private final FinancialReportDisplayFormat displayFormat;

    public JasperPdfFinancialReportAdapter()
    {
        this(FinancialReportDisplayFormat.plain());
    }

    public JasperPdfFinancialReportAdapter(FinancialReportDisplayFormat displayFormat)
    {
        this.displayFormat = displayFormat == null
                ? FinancialReportDisplayFormat.plain()
                : displayFormat;
    }

    @Override
    public FinancialReportExportFormat format()
    {
        return FinancialReportExportFormat.PDF;
    }

    @Override
    public byte[] render(String reportName, String textPreview, String csvBody)
    {
        return renderLegacy(reportName, textPreview);
    }

    @Override
    public byte[] render(
            String reportName,
            String textPreview,
            String csvBody,
            ReportTableModel tableModel)
    {
        if (tableModel == null)
        {
            return renderLegacy(reportName, textPreview);
        }
        try
        {
            JasperDesign design = load(structuredTemplate(tableModel));
            var report = JasperCompileManager.compileReport(design);
            JRDataSource dataSource = new JRMapCollectionDataSource(structuredRows(tableModel));
            JasperPrint print = JasperFillManager.fillReport(
                    report,
                    new LinkedHashMap<>(),
                    dataSource);
            return JasperExportManager.exportReportToPdf(print);
        }
        catch (JRException ex)
        {
            throw new IllegalStateException("Could not render structured PDF via JasperReports.", ex);
        }
    }

    private byte[] renderLegacy(String reportName, String textPreview)
    {
        try
        {
            JasperDesign design = load(legacyTemplate());
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

    String structuredTemplateForTests(ReportTableModel tableModel)
    {
        return structuredTemplate(tableModel);
    }

    List<Map<String, ?>> structuredRowsForTests(ReportTableModel tableModel)
    {
        return List.copyOf(structuredRows(tableModel));
    }

    private static JasperDesign load(String jrxml) throws JRException
    {
        return JRXmlLoader.load(new ByteArrayInputStream(jrxml.getBytes(StandardCharsets.UTF_8)));
    }

    private String structuredTemplate(ReportTableModel model)
    {
        boolean useA3 = model.columns().size() > 8
                || model.columns().stream().mapToDouble(ReportTableModel.Column::preferredWidth).sum() > 900.0;
        int pageWidth = useA3 ? A3_LANDSCAPE_WIDTH : A4_LANDSCAPE_WIDTH;
        int pageHeight = useA3 ? A3_LANDSCAPE_HEIGHT : A4_LANDSCAPE_HEIGHT;
        int contentWidth = pageWidth - (PAGE_MARGIN * 2);
        List<Integer> widths = normalizedWidths(model.columns(), contentWidth);
        int titleHeight = 72 + (model.headerLines().size() * 28);

        StringBuilder xml = new StringBuilder(12_000);
        xml.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports"
                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              xsi:schemaLocation="http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd"
                              name="structured_financial_report"
                              whenNoDataType="AllSectionsNoDetail"
                """);
        xml.append(" pageWidth=\"").append(pageWidth)
                .append("\" pageHeight=\"").append(pageHeight)
                .append("\" orientation=\"Landscape\" columnWidth=\"").append(contentWidth)
                .append("\" leftMargin=\"").append(PAGE_MARGIN)
                .append("\" rightMargin=\"").append(PAGE_MARGIN)
                .append("\" topMargin=\"24\" bottomMargin=\"24\">\n");
        appendCellStyle(xml);
        xml.append("  <field name=\"ROW_STYLE\" class=\"java.lang.String\"/>\n");
        for (int i = 0; i < model.columns().size(); i++)
        {
            xml.append("  <field name=\"c").append(i)
                    .append("\" class=\"java.lang.String\"/>\n");
        }

        xml.append("  <title>\n    <band height=\"").append(titleHeight).append("\">\n");
        int y = 0;
        for (ReportTableModel.HeaderLine line : model.headerLines())
        {
            int leftWidth = (int) Math.round(contentWidth * 0.66);
            int rightWidth = contentWidth - leftWidth;
            boolean primary = line.style() == ReportTableModel.HeaderStyle.PRIMARY;
            appendStaticText(xml, 0, y, leftWidth, 26, line.left(),
                    primary ? 10 : 9, primary, "Left", null);
            appendStaticText(xml, leftWidth, y, rightWidth, 26, line.right(),
                    primary ? 10 : 9, primary, "Right", null);
            y += 28;
        }
        appendStaticText(xml, 0, y, contentWidth, 32, model.title(), 14, true, "Left", null);
        y += 34;
        appendStaticText(xml, 0, y, contentWidth, 26, model.subtitle(), 9, false, "Left", null);
        xml.append("    </band>\n  </title>\n");

        xml.append("  <columnHeader>\n    <band height=\"36\">\n");
        int x = 0;
        for (int i = 0; i < model.columns().size(); i++)
        {
            ReportTableModel.Column column = model.columns().get(i);
            String alignment = isNumeric(column.format()) ? "Right" : "Left";
            appendStaticText(xml, x, 0, widths.get(i), 36, column.label(),
                    8, true, alignment, "#D9EAF7");
            x += widths.get(i);
        }
        xml.append("    </band>\n  </columnHeader>\n");

        xml.append("  <detail>\n    <band height=\"22\" splitType=\"Stretch\">\n");
        x = 0;
        for (int i = 0; i < model.columns().size(); i++)
        {
            ReportTableModel.Column column = model.columns().get(i);
            String alignment = isNumeric(column.format()) ? "Right" : "Left";
            xml.append("      <textField isStretchWithOverflow=\"true\" isBlankWhenNull=\"true\">\n")
                    .append("        <reportElement style=\"ReportCell\" x=\"").append(x)
                    .append("\" y=\"0\" width=\"").append(widths.get(i))
                    .append("\" height=\"22\" stretchType=\"ContainerHeight\"/>\n")
                    .append("        <textElement textAlignment=\"").append(alignment)
                    .append("\" verticalAlignment=\"Middle\"><font size=\"8\"/></textElement>\n")
                    .append("        <textFieldExpression><![CDATA[$F{c").append(i)
                    .append("}]]></textFieldExpression>\n")
                    .append("      </textField>\n");
            x += widths.get(i);
        }
        xml.append("    </band>\n  </detail>\n");
        xml.append("  <pageFooter>\n    <band height=\"16\">\n")
                .append("      <textField><reportElement x=\"0\" y=\"2\" width=\"")
                .append(contentWidth).append("\" height=\"12\"/>\n")
                .append("        <textElement textAlignment=\"Right\"><font size=\"8\"/></textElement>\n")
                .append("        <textFieldExpression><![CDATA[\"Page \" + $V{PAGE_NUMBER}]]></textFieldExpression>\n")
                .append("      </textField>\n    </band>\n  </pageFooter>\n")
                .append("</jasperReport>\n");
        return xml.toString();
    }

    private static void appendCellStyle(StringBuilder xml)
    {
        xml.append("""
                  <style name="ReportCell" mode="Opaque" backcolor="#FFFFFF" forecolor="#000000">
                    <box leftPadding="3" rightPadding="3" topPadding="2" bottomPadding="2">
                      <pen lineWidth="0.25" lineColor="#B7C5D1"/>
                    </box>
                    <conditionalStyle>
                      <conditionExpression><![CDATA["SECTION".equals($F{ROW_STYLE})]]></conditionExpression>
                      <style mode="Opaque" backcolor="#CFE2F3" isBold="true"/>
                    </conditionalStyle>
                    <conditionalStyle>
                      <conditionExpression><![CDATA["TOTAL".equals($F{ROW_STYLE})]]></conditionExpression>
                      <style mode="Opaque" backcolor="#EEF3F8" isBold="true"/>
                    </conditionalStyle>
                    <conditionalStyle>
                      <conditionExpression><![CDATA["STATUS_SUCCESS".equals($F{ROW_STYLE})]]></conditionExpression>
                      <style mode="Opaque" backcolor="#E2F0D9" forecolor="#245524" isBold="true"/>
                    </conditionalStyle>
                    <conditionalStyle>
                      <conditionExpression><![CDATA["STATUS_WARNING".equals($F{ROW_STYLE})]]></conditionExpression>
                      <style mode="Opaque" backcolor="#FFF2CC" forecolor="#6B4D00" isBold="true"/>
                    </conditionalStyle>
                    <conditionalStyle>
                      <conditionExpression><![CDATA["NOTE".equals($F{ROW_STYLE})]]></conditionExpression>
                      <style mode="Opaque" backcolor="#FFFDF2" forecolor="#5F6B7A"/>
                    </conditionalStyle>
                  </style>
                """);
    }

    private static void appendStaticText(
            StringBuilder xml,
            int x,
            int y,
            int width,
            int height,
            String value,
            int fontSize,
            boolean bold,
            String alignment,
            String background)
    {
        xml.append("      <staticText>\n        <reportElement x=\"").append(x)
                .append("\" y=\"").append(y)
                .append("\" width=\"").append(width)
                .append("\" height=\"").append(height).append("\"");
        if (background != null)
        {
            xml.append(" mode=\"Opaque\" backcolor=\"").append(background).append("\"");
        }
        xml.append("/>\n");
        if (background != null)
        {
            xml.append("        <box leftPadding=\"3\" rightPadding=\"3\" topPadding=\"2\" bottomPadding=\"2\">\n")
                    .append("          <pen lineWidth=\"0.25\" lineColor=\"#8C98A6\"/>\n")
                    .append("        </box>\n");
        }
        xml.append("        <textElement textAlignment=\"").append(alignment)
                .append("\" verticalAlignment=\"Middle\"><font size=\"").append(fontSize)
                .append("\" isBold=\"").append(bold).append("\"/></textElement>\n")
                .append("        <text>").append(xml(value)).append("</text>\n")
                .append("      </staticText>\n");
    }

    private List<Map<String, ?>> structuredRows(ReportTableModel model)
    {
        List<Map<String, ?>> output = new ArrayList<>();
        for (ReportTableModel.Row row : model.rows())
        {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("ROW_STYLE", row.style().name());
            for (int i = 0; i < model.columns().size(); i++)
            {
                ReportTableModel.Column column = model.columns().get(i);
                values.put("c" + i, display(row.value(column.key()), column.format()));
            }
            output.add(values);
        }
        return output;
    }

    private String display(Object value, ReportTableModel.ValueFormat format)
    {
        if (value == null)
        {
            return "";
        }
        return switch (format)
        {
            case DATE -> value instanceof LocalDate date
                    ? displayFormat.formatDate(date)
                    : String.valueOf(value);
            case MONEY -> value instanceof BigDecimal amount
                    ? displayFormat.formatMoney(amount)
                    : String.valueOf(value);
            case NUMBER, TEXT -> String.valueOf(value);
        };
    }

    private static boolean isNumeric(ReportTableModel.ValueFormat format)
    {
        return format == ReportTableModel.ValueFormat.MONEY
                || format == ReportTableModel.ValueFormat.NUMBER;
    }

    private static List<Integer> normalizedWidths(
            List<ReportTableModel.Column> columns,
            int availableWidth)
    {
        double total = columns.stream()
                .mapToDouble(ReportTableModel.Column::preferredWidth)
                .sum();
        List<Integer> widths = new ArrayList<>();
        int allocated = 0;
        for (int i = 0; i < columns.size(); i++)
        {
            int width = i == columns.size() - 1
                    ? availableWidth - allocated
                    : (int) Math.round(availableWidth
                            * (columns.get(i).preferredWidth() / total));
            widths.add(width);
            allocated += width;
        }
        return widths;
    }

    private static String xml(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String legacyTemplate()
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
