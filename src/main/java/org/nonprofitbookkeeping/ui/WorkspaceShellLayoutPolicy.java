package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.WorkspaceDividerState;

/** Allocates a resizable navigation column and the remaining width to the work pane. */
public final class WorkspaceShellLayoutPolicy
{
    public static final double FALLBACK_WORKSPACE_WIDTH = 1180.0;
    // Preserve the two-field preference format while retiring the right divider.
    public static final double RETIRED_RIGHT_DIVIDER = 0.999;
    private static final double NAVIGATION_MINIMUM = 160.0;
    private static final double WORK_MINIMUM = 320.0;

    private WorkspaceShellLayoutPolicy()
    {
    }

    public static ShellGeometry forWidth(double workspaceWidth)
    {
        if (!Double.isFinite(workspaceWidth) || workspaceWidth <= 0.0)
        {
            throw new IllegalArgumentException("workspaceWidth must be finite and positive");
        }
        double navigationWidth = Math.min(workspaceWidth * 0.4,
                Math.max(NAVIGATION_MINIMUM, Math.min(210.0, workspaceWidth * 0.18)));
        return new ShellGeometry(navigationWidth, workspaceWidth - navigationWidth,
                navigationWidth / workspaceWidth);
    }

    public static WorkspaceDividerState safeDividerState(double workspaceWidth, WorkspaceDividerState requested)
    {
        ShellGeometry fallback = forWidth(workspaceWidth);
        if (requested == null)
        {
            return fallback.dividerState();
        }
        double navigationWidth = requested.leftDividerPosition() * workspaceWidth;
        if (navigationWidth < NAVIGATION_MINIMUM || workspaceWidth - navigationWidth < WORK_MINIMUM)
        {
            return fallback.dividerState();
        }
        return new WorkspaceDividerState(requested.leftDividerPosition(), RETIRED_RIGHT_DIVIDER);
    }

    public record ShellGeometry(double navigationWidth, double centerWidth, double leftDividerPosition)
    {
        public WorkspaceDividerState dividerState()
        {
            return new WorkspaceDividerState(leftDividerPosition, RETIRED_RIGHT_DIVIDER);
        }
    }
}
