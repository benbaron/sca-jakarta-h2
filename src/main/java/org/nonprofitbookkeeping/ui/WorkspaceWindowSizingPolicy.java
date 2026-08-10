package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.WorkspaceWindowState;

/**
 * Calculates a laptop-friendly startup window inside the usable screen area.
 */
public final class WorkspaceWindowSizingPolicy
{
    private static final double PREFERRED_WIDTH = 1180.0;
    private static final double PREFERRED_HEIGHT = 760.0;
    private static final double MAXIMUM_SCREEN_FRACTION = 0.90;
    private static final double MINIMUM_WIDTH_LIMIT = 900.0;
    private static final double MINIMUM_HEIGHT_LIMIT = 600.0;
    private static final double MINIMUM_SCREEN_FRACTION = 0.72;

    private WorkspaceWindowSizingPolicy()
    {
    }

    /**
     * Calculates startup size, minimum size, and centered position.
     *
     * @param visualMinX left edge of the usable screen
     * @param visualMinY top edge of the usable screen
     * @param visualWidth usable screen width
     * @param visualHeight usable screen height
     * @return immutable startup geometry
     */
    public static WindowGeometry forVisualBounds(
            double visualMinX,
            double visualMinY,
            double visualWidth,
            double visualHeight)
    {
        requireFinite(visualMinX, "visualMinX");
        requireFinite(visualMinY, "visualMinY");
        requirePositive(visualWidth, "visualWidth");
        requirePositive(visualHeight, "visualHeight");

        double width = Math.min(PREFERRED_WIDTH, visualWidth * MAXIMUM_SCREEN_FRACTION);
        double height = Math.min(PREFERRED_HEIGHT, visualHeight * MAXIMUM_SCREEN_FRACTION);
        double minimumWidth = Math.min(
                MINIMUM_WIDTH_LIMIT,
                visualWidth * MINIMUM_SCREEN_FRACTION);
        double minimumHeight = Math.min(
                MINIMUM_HEIGHT_LIMIT,
                visualHeight * MINIMUM_SCREEN_FRACTION);
        double x = visualMinX + (visualWidth - width) / 2.0;
        double y = visualMinY + (visualHeight - height) / 2.0;

        return new WindowGeometry(
                width,
                height,
                minimumWidth,
                minimumHeight,
                x,
                y);
    }

    /**
     * Restores remembered geometry inside the current usable screen, falling
     * back to the standard laptop-safe geometry when no state is supplied.
     */
    public static WindowGeometry forRememberedState(
            double visualMinX,
            double visualMinY,
            double visualWidth,
            double visualHeight,
            WorkspaceWindowState remembered)
    {
        WindowGeometry defaults = forVisualBounds(
                visualMinX, visualMinY, visualWidth, visualHeight);
        if (remembered == null)
        {
            return defaults;
        }

        double width = clamp(remembered.width(), defaults.minimumWidth(), visualWidth);
        double height = clamp(remembered.height(), defaults.minimumHeight(), visualHeight);
        double x = clamp(remembered.x(), visualMinX, visualMinX + visualWidth - width);
        double y = clamp(remembered.y(), visualMinY, visualMinY + visualHeight - height);
        return new WindowGeometry(
                width,
                height,
                defaults.minimumWidth(),
                defaults.minimumHeight(),
                x,
                y);
    }

    private static double clamp(double value, double minimum, double maximum)
    {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void requirePositive(double value, String name)
    {
        requireFinite(value, name);
        if (value <= 0.0)
        {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireFinite(double value, String name)
    {
        if (!Double.isFinite(value))
        {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    /** Startup dimensions and position in JavaFX screen coordinates. */
    public record WindowGeometry(
            double width,
            double height,
            double minimumWidth,
            double minimumHeight,
            double x,
            double y)
    {
    }
}
