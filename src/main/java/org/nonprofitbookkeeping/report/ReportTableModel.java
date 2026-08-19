package org.nonprofitbookkeeping.report;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UI-neutral, immutable table presentation for a Report Library preview.
 *
 * <p>The model retains typed dates and money so the JavaFX renderer can apply
 * the active company's display preferences. Text and CSV exports remain the
 * separately governed output of the same immutable {@link ReportRequest}.</p>
 */
public record ReportTableModel(
        String id,
        String title,
        String subtitle,
        List<HeaderLine> headerLines,
        List<Column> columns,
        List<Row> rows)
{
    public ReportTableModel(
            String id,
            String title,
            String subtitle,
            List<Column> columns,
            List<Row> rows)
    {
        this(id, title, subtitle, List.of(), columns, rows);
    }

    public ReportTableModel
    {
        if (id == null || id.isBlank())
        {
            throw new IllegalArgumentException("Report table id is required.");
        }
        if (title == null || title.isBlank())
        {
            throw new IllegalArgumentException("Report table title is required.");
        }
        subtitle = subtitle == null ? "" : subtitle;
        headerLines = headerLines == null ? List.of() : List.copyOf(headerLines);
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : List.copyOf(rows);
        if (columns.isEmpty())
        {
            throw new IllegalArgumentException("At least one report table column is required.");
        }
    }

    public record HeaderLine(String left, String right, HeaderStyle style)
    {
        public HeaderLine
        {
            left = left == null ? "" : left;
            right = right == null ? "" : right;
            style = style == null ? HeaderStyle.SECONDARY : style;
        }
    }

    public record Column(
            String key,
            String label,
            ValueFormat format,
            double preferredWidth)
    {
        public Column
        {
            if (key == null || key.isBlank())
            {
                throw new IllegalArgumentException("Report table column key is required.");
            }
            if (label == null || label.isBlank())
            {
                throw new IllegalArgumentException("Report table column label is required.");
            }
            format = format == null ? ValueFormat.TEXT : format;
            preferredWidth = Math.max(70.0, preferredWidth);
        }
    }

    public record Row(RowStyle style, Map<String, Object> values)
    {
        public Row
        {
            style = style == null ? RowStyle.DETAIL : style;
            values = values == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        public Object value(String columnKey)
        {
            return values.get(columnKey);
        }
    }

    public enum ValueFormat
    {
        TEXT,
        DATE,
        MONEY,
        NUMBER
    }

    public enum HeaderStyle
    {
        PRIMARY,
        SECONDARY
    }

    public enum RowStyle
    {
        DETAIL,
        SECTION,
        TOTAL,
        STATUS_SUCCESS,
        STATUS_WARNING,
        NOTE
    }
}
