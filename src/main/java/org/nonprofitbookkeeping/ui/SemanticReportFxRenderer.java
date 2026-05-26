package org.nonprofitbookkeeping.ui;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.report.template.SemanticReportValueSet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** JavaFX renderer for compact semantic workbook-style report templates. */
public class SemanticReportFxRenderer
{
    private static final double CHAR_WIDTH = 8.0;
    private static final double CELL_PADDING = 28.0;
    private static final double MIN_COLUMN_WIDTH = 10 * CHAR_WIDTH + CELL_PADDING;

    public Node render(JsonNode template, SemanticReportValueSet values)
    {
        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: white;");

        Label title = new Label(template.path("title").asText(template.path("templateId").asText("Report")));
        title.getStyleClass().addAll("panel-title", "workbook-report-title");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        root.getChildren().add(title);

        if (template.hasNonNull("subtitle"))
        {
            Label subtitle = new Label(template.path("subtitle").asText());
            subtitle.getStyleClass().add("muted");
            subtitle.setStyle("-fx-text-fill: #5f6b7a;");
            root.getChildren().add(subtitle);
        }

        Node body = "tableReport".equals(template.path("type").asText("sectionReport"))
                ? renderTable(template, values)
                : renderSections(template, values);
        root.getChildren().add(body);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);
        scroll.setPannable(true);
        scroll.getStyleClass().add("workbook-report-scroll");
        scroll.setStyle("-fx-background-color: white;");
        return scroll;
    }

    private Node renderSections(JsonNode template, SemanticReportValueSet values)
    {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("workbook-report-grid");
        grid.setStyle("-fx-background-color: white;");
        setColumnWidths(grid, 80, 420, 140, 300);

        int row = 0;
        addCell(grid, 0, row, "Line", "workbook-header-cell");
        addCell(grid, 1, row, "Description", "workbook-header-cell");
        addCell(grid, 2, row, "Amount", "workbook-header-cell", "workbook-number-cell");
        addCell(grid, 3, row, "Notes", "workbook-header-cell");
        row++;

        for (JsonNode section : template.path("sections"))
        {
            addMergedCell(grid, row++, section.path("title").asText(), "workbook-section-cell");
            for (JsonNode item : section.path("rows"))
            {
                if ("spacer".equals(item.path("type").asText("valueRow")))
                {
                    row++;
                    continue;
                }
                boolean total = "totalRow".equals(item.path("type").asText("valueRow"));
                String rowClass = total ? "workbook-total-cell" : "workbook-value-cell";
                addCell(grid, 0, row, item.path("line").asText(""), rowClass);
                addCell(grid, 1, row, item.path("label").asText(""), rowClass);
                String value = item.hasNonNull("valueKey")
                        ? format(values.get(item.path("valueKey").asText()), item.path("format").asText("text"))
                        : "";
                addCell(grid, 2, row, value, rowClass, "workbook-number-cell");
                addCell(grid, 3, row, item.path("note").asText(""), rowClass);
                row++;
            }
        }
        return grid;
    }

    private Node renderTable(JsonNode template, SemanticReportValueSet values)
    {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("workbook-report-grid");
        grid.setStyle("-fx-background-color: white;");
        JsonNode columns = template.path("columns");
        setTableColumnWidths(grid, columns);

        int row = 0;
        for (int c = 0; c < columns.size(); c++)
        {
            addCell(grid, c, row, columns.get(c).path("label").asText(), "workbook-header-cell");
        }
        row++;

        List<Map<String, Object>> rows = values.table(template.path("tableKey").asText());
        for (Map<String, Object> data : rows)
        {
            for (int c = 0; c < columns.size(); c++)
            {
                JsonNode col = columns.get(c);
                String display = format(data.get(col.path("field").asText()), col.path("format").asText("text"));
                boolean numeric = "currency".equals(col.path("format").asText("text"));
                addCell(grid, c, row, display, "workbook-value-cell", numeric ? "workbook-number-cell" : "workbook-text-cell");
            }
            row++;
        }

        if (rows.isEmpty())
        {
            addMergedCell(grid, row, "No rows for the selected reporting period.", "workbook-note-cell");
        }
        return grid;
    }

    private void setColumnWidths(GridPane grid, double... widths)
    {
        grid.getColumnConstraints().clear();
        for (double width : widths)
        {
            ColumnConstraints cc = new ColumnConstraints();
            double adjusted = Math.max(MIN_COLUMN_WIDTH, width);
            cc.setMinWidth(adjusted);
            cc.setPrefWidth(adjusted);
            cc.setHgrow(Priority.NEVER);
            grid.getColumnConstraints().add(cc);
        }
    }

    private void setTableColumnWidths(GridPane grid, JsonNode columns)
    {
        grid.getColumnConstraints().clear();
        for (JsonNode col : columns)
        {
            int requested = col.path("width").asInt(10);
            int label = col.path("label").asText("").length();
            int field = col.path("field").asText("").length();
            int chars = Math.max(10, Math.max(requested, Math.max(label, field)));
            ColumnConstraints cc = new ColumnConstraints();
            double width = chars * CHAR_WIDTH + CELL_PADDING;
            cc.setMinWidth(width);
            cc.setPrefWidth(width);
            cc.setHgrow(Priority.NEVER);
            grid.getColumnConstraints().add(cc);
        }
    }

    private void addMergedCell(GridPane grid, int row, String text, String styleClass)
    {
        Label label = label(text, styleClass);
        label.setMinWidth(totalWidth(grid));
        grid.add(label, 0, row, Math.max(1, grid.getColumnConstraints().size()), 1);
    }

    private void addCell(GridPane grid, int col, int row, String text, String... styleClasses)
    {
        Label label = label(text, styleClasses);
        label.setMinWidth(columnWidth(grid, col));
        grid.add(label, col, row);
    }

    private Label label(String text, String... styleClasses)
    {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("workbook-cell");
        label.getStyleClass().addAll(styleClasses);
        label.setWrapText(true);
        label.setMinHeight(26);
        label.setAlignment(hasStyle(styleClasses, "workbook-number-cell") ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        label.setStyle(styleFor(styleClasses));
        return label;
    }

    private String styleFor(String... styleClasses)
    {
        String base = "-fx-padding: 4 6 4 6; -fx-border-color: #8c98a6; -fx-border-width: 0.5;";
        if (hasStyle(styleClasses, "workbook-header-cell"))
        {
            return base + " -fx-background-color: #d9eaf7; -fx-font-weight: bold; -fx-alignment: center;";
        }
        if (hasStyle(styleClasses, "workbook-section-cell"))
        {
            return base + " -fx-background-color: #cfe2f3; -fx-font-weight: bold;";
        }
        if (hasStyle(styleClasses, "workbook-total-cell"))
        {
            return base + " -fx-background-color: #eef3f8; -fx-font-weight: bold; -fx-border-width: 1 0.5 1 0.5;";
        }
        if (hasStyle(styleClasses, "workbook-note-cell"))
        {
            return base + " -fx-background-color: #fffdf2; -fx-text-fill: #5f6b7a;";
        }
        return base + " -fx-background-color: white;";
    }

    private boolean hasStyle(String[] styleClasses, String style)
    {
        for (String styleClass : styleClasses)
        {
            if (style.equals(styleClass))
            {
                return true;
            }
        }
        return false;
    }

    private double columnWidth(GridPane grid, int col)
    {
        if (col >= 0 && col < grid.getColumnConstraints().size())
        {
            return Math.max(MIN_COLUMN_WIDTH, grid.getColumnConstraints().get(col).getPrefWidth());
        }
        return MIN_COLUMN_WIDTH;
    }

    private double totalWidth(GridPane grid)
    {
        double total = 0;
        for (ColumnConstraints cc : grid.getColumnConstraints())
        {
            total += Math.max(MIN_COLUMN_WIDTH, cc.getPrefWidth());
        }
        return total;
    }

    private String format(Object value, String format)
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
}
