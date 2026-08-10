package org.nonprofitbookkeeping.model;

/** User-machine window geometry restored only when the shell preference permits it. */
public record WorkspaceWindowState(
        double x,
        double y,
        double width,
        double height,
        boolean maximized)
{
    public WorkspaceWindowState
    {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requirePositive(width, "width");
        requirePositive(height, "height");
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
}
