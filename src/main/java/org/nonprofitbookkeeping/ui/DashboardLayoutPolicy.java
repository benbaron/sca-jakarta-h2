package org.nonprofitbookkeeping.ui;

import javafx.scene.layout.GridPane;

/** Compatibility wrapper for the active dashboard layout policy. */
public final class DashboardLayoutPolicy
{
    public static final double WIDE_BREAKPOINT = DashboardWorkspaceLayoutPolicy.WIDE_BREAKPOINT;
    public static final double MEDIUM_BREAKPOINT = DashboardWorkspaceLayoutPolicy.MEDIUM_BREAKPOINT;

    private DashboardLayoutPolicy()
    {
    }

    public static LayoutMode modeFor(double width)
    {
        return switch (DashboardWorkspaceLayoutPolicy.modeFor(width))
        {
            case WIDE -> LayoutMode.WIDE;
            case MEDIUM -> LayoutMode.MEDIUM;
            case NARROW -> LayoutMode.NARROW;
        };
    }

    public static void apply(GridPane grid, double width)
    {
        DashboardWorkspaceLayoutPolicy.apply(grid, width);
    }

    public enum LayoutMode
    {
        WIDE,
        MEDIUM,
        NARROW
    }
}
