package org.nonprofitbookkeeping.report.template;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Renders compact semantic report templates to text and CSV. */
public class SemanticReportRenderer
{
    public RenderedSemanticReport render(JsonNode template, SemanticReportValueSet values)
    {
        String type = template.path("type").asText("sectionReport");
        if ("tableReport".equals(type))
        {
            return renderTable(template, values);
        }
        return renderSections(template, values);
    }

    private RenderedSemanticReport renderSections(JsonNode template, SemanticReportValueSet values)
    {
        StringBuilder text = new StringBuilder();
        StringBuilder csv = new StringBuilder("template_id,section,line,label,value,note,source_cell\n");
        String templateId = template.path("templateId").asText("");
        text.append(template.path("title").asText(templateId)).append('\n');
        if (template.hasNonNull("subtitle"))
        {
            text.append(template.path("subtitle").asText()).append('\n');
        }
        text.append('\n');

        for (JsonNode section : template.path("sections"))
        {
            String sectionTitle = section.path("title").asText();
            text.append(sectionTitle).append('\n');
            text.append(String.format("%-10s %-48s %14s  %s%n", "Line", "Description", "Amount", "Notes"));
            for (JsonNode row : section.path("rows"))
            {
                String rowType = row.path("type").asText("valueRow");
                if ("spacer".equals(rowType))
                {
                    text.append('\n');
                    continue;
                }
                String line = row.path("line").asText("");
                String label = row.path("label").asText("");
                String note = row.path("note").asText("");
                String sourceCell = row.path("sourceCell").asText("");
                String value = "";
                if (row.hasNonNull("valueKey"))
                {
                    value = format(values.get(row.path("valueKey").asText()), row.path("format").asText("text"));
                }
                text.append(String.format("%-10s %-48s %14s  %s%n",
                        line,
                        truncate(label, 48),
                        value,
                        note));
                csv.append(csv(templateId)).append(',')
                        .append(csv(sectionTitle)).append(',')
                        .append(csv(line)).append(',')
                        .append(csv(label)).append(',')
                        .append(csv(value)).append(',')
                        .append(csv(note)).append(',')
                        .append(csv(sourceCell)).append('\n');
            }
            text.append('\n');
        }
        return new RenderedSemanticReport(text.toString(), csv.toString());
    }

    private RenderedSemanticReport renderTable(JsonNode template, SemanticReportValueSet values)
    {
        StringBuilder text = new StringBuilder();
        StringBuilder csv = new StringBuilder();
        String templateId = template.path("templateId").asText("");
        text.append(template.path("title").asText(templateId)).append('\n');
        if (template.hasNonNull("subtitle"))
        {
            text.append(template.path("subtitle").asText()).append('\n');
        }
        text.append('\n');

        JsonNode columns = template.path("columns");
        int[] widths = columnWidths(columns);
        for (int i = 0; i < columns.size(); i++)
        {
            if (i > 0) csv.append(',');
            String label = columns.get(i).path("label").asText();
            text.append(pad(label, widths[i])).append(' ');
            csv.append(csv(label));
        }
        text.append('\n');
        csv.append('\n');

        List<Map<String, Object>> rows = values.table(template.path("tableKey").asText());
        for (Map<String, Object> row : rows)
        {
            for (int i = 0; i < columns.size(); i++)
            {
                JsonNode col = columns.get(i);
                String display = format(row.get(col.path("field").asText()), col.path("format").asText("text"));
                text.append(pad(truncate(display, widths[i]), widths[i])).append(' ');
                if (i > 0) csv.append(',');
                csv.append(csv(display));
            }
            text.append('\n');
            csv.append('\n');
        }
        if (rows.isEmpty())
        {
            text.append("No rows for the selected reporting period.\n");
        }
        return new RenderedSemanticReport(text.toString(), csv.toString());
    }

    private int[] columnWidths(JsonNode columns)
    {
        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++)
        {
            JsonNode col = columns.get(i);
            int requested = col.path("width").asInt(0);
            int label = col.path("label").asText("").length();
            int field = col.path("field").asText("").length();
            widths[i] = Math.max(10, Math.max(requested, Math.max(label, field)));
        }
        return widths;
    }

    private static String format(Object value, String format)
    {
        if (value == null)
        {
            return "currency".equals(format) ? "-" : "";
        }
        if ("currency".equals(format))
        {
            if (value instanceof BigDecimal bd)
            {
                return bd.signum() == 0 ? "-" : bd.toPlainString();
            }
            if (value instanceof Number n)
            {
                return n.doubleValue() == 0.0 ? "-" : String.valueOf(n);
            }
        }
        if ("date".equals(format) && value instanceof LocalDate date)
        {
            return date.toString();
        }
        return String.valueOf(value);
    }

    private static String truncate(String value, int max)
    {
        if (value == null)
        {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String pad(String value, int width)
    {
        String text = value == null ? "" : value;
        if (text.length() >= width)
        {
            return text;
        }
        return text + " ".repeat(width - text.length());
    }

    private static String csv(String value)
    {
        if (value == null)
        {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\""))
        {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
