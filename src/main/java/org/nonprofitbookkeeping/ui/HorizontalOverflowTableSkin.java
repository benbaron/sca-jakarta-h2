package org.nonprofitbookkeeping.ui;

import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.skin.TableViewSkin;

import java.util.ArrayList;
import java.util.List;

/** Keeps empty column headers scrollable; populated tables retain their native row/column scrolling. */
final class HorizontalOverflowTableSkin<T> extends TableViewSkin<T>
{
    private final ScrollBar emptyHorizontal = new ScrollBar();
    private final List<TableColumn<T, ?>> watched = new ArrayList<>();
    private final InvalidationListener widthChanged = ignored -> getSkinnable().requestLayout();
    private final ListChangeListener<TableColumn<T, ?>> columnsChanged = ignored -> watchColumns();

    private HorizontalOverflowTableSkin(TableView<T> table)
    {
        super(table);
        emptyHorizontal.setOrientation(Orientation.HORIZONTAL);
        emptyHorizontal.getStyleClass().add("empty-table-horizontal-bar");
        emptyHorizontal.setVisible(false);
        emptyHorizontal.setManaged(false);
        emptyHorizontal.valueProperty().addListener(widthChanged);
        getChildren().add(emptyHorizontal);
        table.getVisibleLeafColumns().addListener(columnsChanged);
        watchColumns();
    }

    static <T> void install(TableView<T> table)
    {
        if (table.getPlaceholder() instanceof Label placeholder) PanelTextLayout.prepare(placeholder);
        if (!(table.getSkin() instanceof HorizontalOverflowTableSkin<?>))
        {
            table.setSkin(new HorizontalOverflowTableSkin<>(table));
        }
    }

    private void watchColumns()
    {
        watched.forEach(column -> column.widthProperty().removeListener(widthChanged));
        watched.clear();
        watched.addAll(getSkinnable().getVisibleLeafColumns());
        watched.forEach(column -> column.widthProperty().addListener(widthChanged));
        getSkinnable().requestLayout();
    }

    @Override
    protected void layoutChildren(double x, double y, double width, double height)
    {
        TableView<T> table = getSkinnable();
        double columns = table.getVisibleLeafColumns().stream().mapToDouble(TableColumn::getWidth).sum();
        boolean empty = table.getItems() == null || table.getItems().isEmpty();
        boolean needed = empty && columns > width + .5;
        double barHeight = needed ? emptyHorizontal.prefHeight(width) : 0;
        emptyHorizontal.setVisible(needed);
        emptyHorizontal.setMin(0);
        emptyHorizontal.setMax(Math.max(0, columns - width));
        double range = Math.max(0, columns - width);
        emptyHorizontal.setValue(Math.max(0, Math.min(range, emptyHorizontal.getValue())));
        emptyHorizontal.setVisibleAmount(columns > 0 ? range * width / columns : 0);
        emptyHorizontal.setBlockIncrement(Math.max(1, width * .8));
        emptyHorizontal.setUnitIncrement(16);
        super.layoutChildren(x, y, width, Math.max(0, height - barHeight));
        if (needed) emptyHorizontal.resizeRelocate(x, y + height - barHeight, width, barHeight);
        getTableHeaderRow().getRootHeader().setTranslateX(needed ? -emptyHorizontal.getValue() : 0);
    }

    @Override
    public void dispose()
    {
        getSkinnable().getVisibleLeafColumns().removeListener(columnsChanged);
        watched.forEach(column -> column.widthProperty().removeListener(widthChanged));
        emptyHorizontal.valueProperty().removeListener(widthChanged);
        super.dispose();
    }
}
