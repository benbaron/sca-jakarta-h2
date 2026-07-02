package org.nonprofitbookkeeping.ui;

/** Calculates initial widths for the navigation, center, and inspector panes. */
public final class WorkspaceShellLayoutPolicy
{
    public static final double FALLBACK_WORKSPACE_WIDTH = 1180.0;
    private static final double NAVIGATION_FRACTION = 0.18;
    private static final double INSPECTOR_FRACTION = 0.19;
    private static final double NAVIGATION_MINIMUM = 160.0;
    private static final double NAVIGATION_PREFERRED = 210.0;
    private static final double INSPECTOR_MINIMUM = 180.0;
    private static final double INSPECTOR_PREFERRED = 235.0;

    private WorkspaceShellLayoutPolicy()
    {
    }

    public static ShellGeometry forWidth(double workspaceWidth)
    {
        if (!Double.isFinite(workspaceWidth) || workspaceWidth <= 0.0)
        {
            throw new IllegalArgumentException("workspaceWidth must be finite and positive");
        }

        double navigationWidth = clamp(
                workspaceWidth * NAVIGATION_FRACTION,
                NAVIGATION_MINIMUM,
                NAVIGATION_PREFERRED);
        double inspectorWidth = clamp(
                workspaceWidth * INSPECTOR_FRACTION,
                INSPECTOR_MINIMUM,
                INSPECTOR_PREFERRED);
        double centerWidth = Math.max(0.0, workspaceWidth - navigationWidth - inspectorWidth);

        return new ShellGeometry(
                navigationWidth,
                centerWidth,
                inspectorWidth,
                navigationWidth / workspaceWidth,
                (workspaceWidth - inspectorWidth) / workspaceWidth);
    }

    private static double clamp(double value, double minimum, double maximum)
    {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record ShellGeometry(
            double navigationWidth,
            double centerWidth,
            double inspectorWidth,
            double leftDividerPosition,
            double rightDividerPosition)
    {
    }
}
