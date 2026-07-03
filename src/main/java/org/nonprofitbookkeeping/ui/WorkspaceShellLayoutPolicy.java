package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.WorkspaceDividerState;

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
    private static final double CENTER_MINIMUM = 320.0;

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

    public static WorkspaceDividerState safeDividerState(
            double workspaceWidth,
            WorkspaceDividerState requested)
    {
        ShellGeometry fallback = forWidth(workspaceWidth);
        if (requested == null)
        {
            return fallback.dividerState();
        }

        double navigationWidth = requested.leftDividerPosition() * workspaceWidth;
        double inspectorWidth = (1.0 - requested.rightDividerPosition()) * workspaceWidth;
        double centerWidth = workspaceWidth - navigationWidth - inspectorWidth;
        if (navigationWidth < NAVIGATION_MINIMUM
                || inspectorWidth < INSPECTOR_MINIMUM
                || centerWidth < CENTER_MINIMUM)
        {
            return fallback.dividerState();
        }
        return requested;
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
        public WorkspaceDividerState dividerState()
        {
            return new WorkspaceDividerState(leftDividerPosition, rightDividerPosition);
        }
    }
}
