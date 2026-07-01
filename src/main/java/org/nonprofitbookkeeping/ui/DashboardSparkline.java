package org.nonprofitbookkeeping.ui;

import javafx.collections.ObservableList;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Compact responsive surplus/deficit trend used by the dashboard KPI card. */
final class DashboardSparkline extends Region
{
    private final Polyline line = new Polyline();
    private final Line zeroLine = new Line();
    private List<BigDecimal> values = List.of();

    DashboardSparkline()
    {
        getStyleClass().add("dashboard-sparkline");
        line.getStyleClass().add("dashboard-sparkline-line");
        zeroLine.getStyleClass().add("dashboard-sparkline-zero");
        getChildren().addAll(zeroLine, line);
        setMinHeight(34);
        setPrefHeight(46);
        setMaxHeight(58);
    }

    void setValues(List<BigDecimal> nextValues)
    {
        values = nextValues == null ? List.of() : new ArrayList<>(nextValues);
        requestLayout();
    }

    @Override
    protected void layoutChildren()
    {
        double width = Math.max(0, getWidth() - snappedLeftInset() - snappedRightInset());
        double height = Math.max(0, getHeight() - snappedTopInset() - snappedBottomInset());
        double x = snappedLeftInset();
        double y = snappedTopInset();

        if (width <= 0 || height <= 0 || values.isEmpty())
        {
            line.getPoints().clear();
            zeroLine.setVisible(false);
            return;
        }

        BigDecimal minimum = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maximum = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (minimum.compareTo(BigDecimal.ZERO) > 0)
        {
            minimum = BigDecimal.ZERO;
        }
        if (maximum.compareTo(BigDecimal.ZERO) < 0)
        {
            maximum = BigDecimal.ZERO;
        }

        BigDecimal range = maximum.subtract(minimum);
        if (range.compareTo(BigDecimal.ZERO) == 0)
        {
            range = BigDecimal.ONE;
        }

        double zeroY = y + height - BigDecimal.ZERO.subtract(minimum)
                .divide(range, 8, java.math.RoundingMode.HALF_UP)
                .doubleValue() * height;
        zeroLine.setStartX(x);
        zeroLine.setEndX(x + width);
        zeroLine.setStartY(zeroY);
        zeroLine.setEndY(zeroY);
        zeroLine.setVisible(true);

        ObservableList<Double> points = line.getPoints();
        points.clear();
        for (int index = 0; index < values.size(); index++)
        {
            double pointX = values.size() == 1
                    ? x + width / 2.0
                    : x + width * index / (values.size() - 1.0);
            double normalized = values.get(index).subtract(minimum)
                    .divide(range, 8, java.math.RoundingMode.HALF_UP)
                    .doubleValue();
            double pointY = y + height - normalized * height;
            points.addAll(pointX, pointY);
        }
    }
}
