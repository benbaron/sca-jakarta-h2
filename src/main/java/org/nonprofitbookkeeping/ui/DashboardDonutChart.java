package org.nonprofitbookkeeping.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;

/** Compact three-segment donut chart for budget performance. */
final class DashboardDonutChart extends Pane
{
    private final Arc onTrackArc = arc("dashboard-donut-on-track");
    private final Arc underArc = arc("dashboard-donut-under");
    private final Arc overArc = arc("dashboard-donut-over");
    private final Arc emptyArc = arc("dashboard-donut-empty");
    private final Label centerValue = new Label();
    private final Label centerCaption = new Label("on track");

    private double onTrack;
    private double under;
    private double over;

    DashboardDonutChart()
    {
        getStyleClass().add("dashboard-donut");
        centerValue.getStyleClass().add("dashboard-donut-value");
        centerCaption.getStyleClass().add("dashboard-donut-caption");
        getChildren().addAll(emptyArc, onTrackArc, underArc, overArc, centerValue, centerCaption);
        setMinSize(120, 120);
        setPrefSize(145, 145);
        setMaxSize(180, 180);
        setValues(0, 0, 0);
    }

    void setValues(double onTrack, double under, double over)
    {
        this.onTrack = Math.max(0, onTrack);
        this.under = Math.max(0, under);
        this.over = Math.max(0, over);

        double total = this.onTrack + this.under + this.over;
        int percentage = total == 0 ? 0 : (int) Math.round(this.onTrack * 100.0 / total);
        centerValue.setText(total == 0 ? "—" : percentage + "%");
        centerCaption.setText(total == 0 ? "no budget" : "on track");
        requestLayout();
    }

    @Override
    protected void layoutChildren()
    {
        double width = getWidth();
        double height = getHeight();
        double size = Math.max(0, Math.min(width, height) - 12);
        double radius = size / 2.0;
        double centerX = width / 2.0;
        double centerY = height / 2.0;
        double strokeWidth = Math.max(10, size * 0.13);

        configureArc(emptyArc, centerX, centerY, radius, strokeWidth, 90, -360);

        double total = onTrack + under + over;
        if (total <= 0)
        {
            onTrackArc.setVisible(false);
            underArc.setVisible(false);
            overArc.setVisible(false);
        }
        else
        {
            onTrackArc.setVisible(onTrack > 0);
            underArc.setVisible(under > 0);
            overArc.setVisible(over > 0);

            double start = 90;
            double onTrackLength = -360 * onTrack / total;
            configureArc(onTrackArc, centerX, centerY, radius, strokeWidth, start, onTrackLength);
            start += onTrackLength;

            double underLength = -360 * under / total;
            configureArc(underArc, centerX, centerY, radius, strokeWidth, start, underLength);
            start += underLength;

            double overLength = -360 * over / total;
            configureArc(overArc, centerX, centerY, radius, strokeWidth, start, overLength);
        }

        centerValue.autosize();
        centerCaption.autosize();
        double combinedHeight = centerValue.getHeight() + centerCaption.getHeight() - 2;
        centerValue.relocate(
                centerX - centerValue.getWidth() / 2.0,
                centerY - combinedHeight / 2.0);
        centerCaption.relocate(
                centerX - centerCaption.getWidth() / 2.0,
                centerY - combinedHeight / 2.0 + centerValue.getHeight() - 2);
    }

    private static Arc arc(String styleClass)
    {
        Arc arc = new Arc();
        arc.setType(ArcType.OPEN);
        arc.setFill(null);
        arc.getStyleClass().add(styleClass);
        return arc;
    }

    private static void configureArc(
            Arc arc,
            double centerX,
            double centerY,
            double radius,
            double strokeWidth,
            double start,
            double length)
    {
        arc.setCenterX(centerX);
        arc.setCenterY(centerY);
        arc.setRadiusX(radius - strokeWidth / 2.0);
        arc.setRadiusY(radius - strokeWidth / 2.0);
        arc.setStrokeWidth(strokeWidth);
        arc.setStartAngle(start);
        arc.setLength(length);
    }
}
