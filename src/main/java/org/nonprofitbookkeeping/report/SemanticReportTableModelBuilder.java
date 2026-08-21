package org.nonprofitbookkeeping.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.nonprofitbookkeeping.report.template.SemanticReportValueSet;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Converts semantic JavaFX report content to the shared structured export model. */
final class SemanticReportTableModelBuilder
{
    private static final double CHARACTER_WIDTH = 8.0;
    private static final double CELL_PADDING = 28.0;

    private SemanticReportTableModelBuilder()
    {
    }

    static ReportTableModel build(
            JsonNode template,
            SemanticReportValueSet values,
            FinancialReportDisplayFormat displayFormat)
    {
        FinancialReportDisplayFormat format = displayFormat == null
                ? FinancialReportDisplayFormat.plain()
                : displayFormat;
        String templateId = template.path("templateId").asText("semantic-report");
        String id = "semantic-" + templateId.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        String title = template.path("title").asText(templateId);
        String subtitle = template.path("subtitle").asText("");
        return "tableReport".equals(template.path("type").asText("sectionReport"))
                ? tableReport(id, title, subtitle, template, values, format)
                : sectionReport(id, title, subtitle, template, values, format);
    }

    private static ReportTableModel tableReport(
            String id,
            String title,
            String subtitle,
            JsonNode template,
            SemanticReportValueSet values,
            FinancialReportDisplayFormat displayFormat)
    {
        List<ReportTableModel.Column> columns = new ArrayList<>();
        JsonNode definitions = template.path("columns");
        for (int index = 0; index < definitions.size(); index++)
        {
            JsonNode definition = definitions.get(index);
            String field = definition.path("field").asText("column" + index);
            String semanticFormat = definition.path("format").asText("text");
            int characters = Math.max(10, Math.max(
                    definition.path("width").asInt(10),
                    Math.max(field.length(), definition.path("label").asText("").length())));
            columns.add(new ReportTableModel.Column(
                    field,
                    definition.path("label").asText(field),
                    valueFormat(semanticFormat),
                    characters * CHARACTER_WIDTH + CELL_PADDING));
        }

        List<ReportTableModel.Row> rows = new ArrayList<>();
        for (Map<String, Object> source : values.table(template.path("tableKey").asText()))
        {
            Map<String, Object> row = new LinkedHashMap<>();
            for (JsonNode definition : definitions)
            {
                String field = definition.path("field").asText();
                row.put(field, format(
                        source.get(field),
                        definition.path("format").asText("text"),
                        displayFormat));
            }
            rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.DETAIL, row));
        }
        if (rows.isEmpty())
        {
            rows.add(new ReportTableModel.Row(
                    ReportTableModel.RowStyle.NOTE,
                    Map.of(columns.get(0).key(), "No rows for the selected reporting period.")));
        }
        return new ReportTableModel(id, title, subtitle, columns, rows);
    }

    private static ReportTableModel sectionReport(
            String id,
            String title,
            String subtitle,
            JsonNode template,
            SemanticReportValueSet values,
            FinancialReportDisplayFormat displayFormat)
    {
        List<ReportTableModel.Column> columns = List.of(
                new ReportTableModel.Column("line", "Line", ReportTableModel.ValueFormat.TEXT, 80),
                new ReportTableModel.Column(
                        "description", "Description", ReportTableModel.ValueFormat.TEXT, 420),
                new ReportTableModel.Column(
                        "amount", "Amount", ReportTableModel.ValueFormat.NUMBER, 140),
                new ReportTableModel.Column("notes", "Notes", ReportTableModel.ValueFormat.TEXT, 300));
        List<ReportTableModel.Row> rows = new ArrayList<>();
        for (JsonNode section : template.path("sections"))
        {
            rows.add(new ReportTableModel.Row(
                    ReportTableModel.RowStyle.SECTION,
                    Map.of("description", section.path("title").asText())));
            for (JsonNode definition : section.path("rows"))
            {
                if ("spacer".equals(definition.path("type").asText("valueRow")))
                {
                    rows.add(new ReportTableModel.Row(
                            ReportTableModel.RowStyle.DETAIL,
                            Map.of()));
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("line", definition.path("line").asText(""));
                row.put("description", definition.path("label").asText(""));
                if (definition.hasNonNull("valueKey"))
                {
                    row.put("amount", format(
                            values.get(definition.path("valueKey").asText()),
                            definition.path("format").asText("text"),
                            displayFormat));
                }
                row.put("notes", definition.path("note").asText(""));
                ReportTableModel.RowStyle style =
                        "totalRow".equals(definition.path("type").asText("valueRow"))
                                ? ReportTableModel.RowStyle.TOTAL
                                : ReportTableModel.RowStyle.DETAIL;
                rows.add(new ReportTableModel.Row(style, row));
            }
        }
        return new ReportTableModel(id, title, subtitle, columns, rows);
    }

    private static ReportTableModel.ValueFormat valueFormat(String format)
    {
        return switch (format)
        {
            case "currency" -> ReportTableModel.ValueFormat.MONEY;
            case "date" -> ReportTableModel.ValueFormat.DATE;
            case "number" -> ReportTableModel.ValueFormat.NUMBER;
            default -> ReportTableModel.ValueFormat.TEXT;
        };
    }

    private static String format(
            Object value,
            String format,
            FinancialReportDisplayFormat displayFormat)
    {
        if (value == null)
        {
            return "currency".equals(format) ? "-" : "";
        }
        if ("currency".equals(format))
        {
            if (value instanceof BigDecimal amount)
            {
                return amount.signum() == 0 ? "-" : displayFormat.formatMoney(amount);
            }
            if (value instanceof Number number)
            {
                return number.doubleValue() == 0.0 ? "-" : String.valueOf(number);
            }
        }
        if ("date".equals(format) && value instanceof LocalDate date)
        {
            return displayFormat.formatDate(date);
        }
        return String.valueOf(value);
    }
}
