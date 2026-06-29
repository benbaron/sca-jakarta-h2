package org.nonprofitbookkeeping.ui;

/**
 * Pure layout rules shared by the production workspace and headless tests.
 */
public final class WorkspaceLayoutPolicy
{
    private WorkspaceLayoutPolicy()
    {
    }

    public static boolean isPermanentTab(AppPanelId panelId)
    {
        return panelId == AppPanelId.DASHBOARD;
    }

    public static double[] dividerPositions(int visiblePaneCount)
    {
        return switch (visiblePaneCount)
        {
            case 3 -> new double[] {0.20, 0.80};
            case 2 -> new double[] {0.25};
            case 1 -> new double[0];
            default -> throw new IllegalArgumentException("visiblePaneCount must be between 1 and 3");
        };
    }
}
