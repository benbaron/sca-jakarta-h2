package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.report.ReportTableModel;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Renders structured core-report projections as workbook-styled JavaFX tables. */
final class FormattedReportFxRenderer
{
    private static final List<String> ROW_STYLE_CLASSES = List.of(
            "formatted-report-detail-row",
            "formatted-report-section-row",
            "formatted-report-total-row",
            "formatted-report-success-row",
            "formatted-report-warning-row",
            "formatted-report-note-row");

    private final FinancialReportDisplayFormat displayFormat;

    FormattedReportFxRenderer(FinancialReportDisplayFormat displayFormat)
    {
        this.displayFormat = displayFormat == null
                ? FinancialReportDisplayFormat.plain()
                : displayFormat;
    }

    Node render(ReportTableModel model)
    {
        VBox root = new VBox(8);
        root.setPadding(new Insets(12));
        root.getStyleClass().add("formatted-report-preview");

        Label title = new Label(model.title());
        title.getStyleClass().addAll("panel-title", "workbook-report-title");
        title.setWrapText(true);
        title.setTooltip(new Tooltip(model.title()));
        Label subtitle = new Label(model.subtitle());
        subtitle.getStyleClass().addAll("muted", "formatted-report-subtitle");
        subtitle.setWrapText(true);
        subtitle.setTooltip(model.subtitle().isBlank() ? null : new Tooltip(model.subtitle()));

        TableView<ReportTableModel.Row> table = new TableView<>();
        table.setId("report-table-" + model.id());
        table.getStyleClass().add("formatted-report-table");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(-1.0);
        table.setPlaceholder(new Label("No rows for the selected report parameters."));
        table.setRowFactory(ignored -> styledRow());

        for (ReportTableModel.Column column : model.columns())
        {
            table.getColumns().add(tableColumn(column));
        }
        table.getItems().setAll(model.rows());

        VBox.setVgrow(table, Priority.ALWAYS);
        if (!model.headerLines().isEmpty())
        {
            root.getChildren().add(metadataHeader(model.headerLines()));
        }
        root.getChildren().addAll(title, subtitle, table);
        return root;
    }

    private static GridPane metadataHeader(List<ReportTableModel.HeaderLine> lines)
    {
        GridPane header = new GridPane();
        header.getStyleClass().add("formatted-report-metadata");
        header.setHgap(16.0);
        header.setVgap(3.0);

        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(68.0);
        left.setHgrow(Priority.ALWAYS);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(32.0);
        right.setHgrow(Priority.ALWAYS);
        header.getColumnConstraints().addAll(left, right);

        for (int row = 0; row < lines.size(); row++)
        {
            ReportTableModel.HeaderLine line = lines.get(row);
            Label leftLabel = metadataLabel(line.left(), line.style(), Pos.CENTER_LEFT);
            Label rightLabel = metadataLabel(line.right(), line.style(), Pos.CENTER_RIGHT);
            header.add(leftLabel, 0, row);
            header.add(rightLabel, 1, row);
        }
        return header;
    }

    private static Label metadataLabel(
            String value,
            ReportTableModel.HeaderStyle style,
            Pos alignment)
    {
        Label label = new Label(value);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(alignment);
        label.getStyleClass().add(style == ReportTableModel.HeaderStyle.PRIMARY
                ? "formatted-report-metadata-primary"
                : "formatted-report-metadata-secondary");
        label.setTooltip(value == null || value.isBlank() ? null : new Tooltip(value));
        return label;
    }

    private TableColumn<ReportTableModel.Row, Object> tableColumn(
            ReportTableModel.Column definition)
    {
        TableColumn<ReportTableModel.Row, Object> column =
                new TableColumn<>(definition.label());
        column.setId(definition.key());
        column.setUserData(definition.key());
        column.setPrefWidth(definition.preferredWidth());
        column.setMinWidth(70.0);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        column.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().value(definition.key())));
        column.setCellFactory(ignored -> new FormattedCell(definition));
        return column;
    }

    private static TableRow<ReportTableModel.Row> styledRow()
    {
        return new TableRow<>()
        {
            @Override
            protected void updateItem(ReportTableModel.Row item, boolean empty)
            {
                super.updateItem(item, empty);
                getStyleClass().removeAll(ROW_STYLE_CLASSES);
                if (!empty && item != null)
                {
                    getStyleClass().add(styleClass(item.style()));
                }
            }
        };
    }

    private static String styleClass(ReportTableModel.RowStyle style)
    {
        return switch (style)
        {
            case DETAIL -> "formatted-report-detail-row";
            case SECTION -> "formatted-report-section-row";
            case TOTAL -> "formatted-report-total-row";
            case STATUS_SUCCESS -> "formatted-report-success-row";
            case STATUS_WARNING -> "formatted-report-warning-row";
            case NOTE -> "formatted-report-note-row";
        };
    }

    private final class FormattedCell extends TableCell<ReportTableModel.Row, Object>
    {
        private final ReportTableModel.Column definition;
        private final Label content = new Label();

        private FormattedCell(ReportTableModel.Column definition)
        {
            this.definition = definition;
            content.setWrapText(definition.format() == ReportTableModel.ValueFormat.TEXT);
            content.setMaxWidth(Double.MAX_VALUE);
            content.maxWidthProperty().bind(widthProperty().subtract(14.0));
            setAlignment(isNumeric(definition.format()) ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            content.setAlignment(isNumeric(definition.format()) ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(Object item, boolean empty)
        {
            super.updateItem(item, empty);
            if (empty)
            {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }

            String display = display(item, definition.format());
            content.setText(display);
            setText(null);
            setGraphic(content);
            setTooltip(display.isBlank() ? null : new Tooltip(display));
        }

        @Override
        protected double computePrefHeight(double width)
        {
            double available = Math.max(1.0,
                    (width > 0.0 ? width : definition.preferredWidth()) - 14.0);
            return Math.max(27.0, content.prefHeight(available) + 8.0);
        }
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
}
