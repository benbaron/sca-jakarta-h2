package org.nonprofitbookkeeping.ui.experiment;

/**
 * Calculates startup and minimum window dimensions from the usable screen area.
 */
public final class WindowSizingPolicy
{
    private static final double PREFERRED_WIDTH = 1440.0;
    private static final double PREFERRED_HEIGHT = 900.0;
    private static final double MAXIMUM_SCREEN_FRACTION = 0.90;
    private static final double MINIMUM_WIDTH_LIMIT = 900.0;
    private static final double MINIMUM_HEIGHT_LIMIT = 600.0;
    private static final double MINIMUM_SCREEN_FRACTION = 0.75;

    private WindowSizingPolicy()
    {
    }

    public static WindowDimensions forVisualBounds(double visualWidth, double visualHeight)
    {
        if (visualWidth <= 0.0 || visualHeight <= 0.0)
        {
            throw new IllegalArgumentException("Visual bounds must be positive");
        }

        double width = Math.min(PREFERRED_WIDTH, visualWidth * MAXIMUM_SCREEN_FRACTION);
        double height = Math.min(PREFERRED_HEIGHT, visualHeight * MAXIMUM_SCREEN_FRACTION);
        double minimumWidth = Math.min(MINIMUM_WIDTH_LIMIT, visualWidth * MINIMUM_SCREEN_FRACTION);
        double minimumHeight = Math.min(MINIMUM_HEIGHT_LIMIT, visualHeight * MINIMUM_SCREEN_FRACTION);

        return new WindowDimensions(width, height, minimumWidth, minimumHeight);
    }

    /**
     * Immutable window geometry values used by the JavaFX launcher.
     */
    public record WindowDimensions(double width, double height, double minimumWidth, double minimumHeight)
    {
    }
}
