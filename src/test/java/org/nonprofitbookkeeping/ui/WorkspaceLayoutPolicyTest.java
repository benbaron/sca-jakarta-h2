package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkspaceLayoutPolicyTest
{
    @Test
    public void dashboardIsTheOnlyPermanentTab()
    {
        assertTrue(WorkspaceLayoutPolicy.isPermanentTab(AppPanelId.DASHBOARD));
        assertFalse(WorkspaceLayoutPolicy.isPermanentTab(AppPanelId.LEDGER_REGISTER));
        assertFalse(WorkspaceLayoutPolicy.isPermanentTab(AppPanelId.TXN_EDITOR));
    }

    @Test
    public void dividerPositionsSupportCollapsedSidebars()
    {
        assertArrayEquals(new double[] {0.20, 0.80}, WorkspaceLayoutPolicy.dividerPositions(3));
        assertArrayEquals(new double[] {0.25}, WorkspaceLayoutPolicy.dividerPositions(2));
        assertArrayEquals(new double[0], WorkspaceLayoutPolicy.dividerPositions(1));
    }

    @Test
    public void dividerPositionsRejectUnsupportedPaneCounts()
    {
        assertThrows(IllegalArgumentException.class, () -> WorkspaceLayoutPolicy.dividerPositions(0));
        assertThrows(IllegalArgumentException.class, () -> WorkspaceLayoutPolicy.dividerPositions(4));
    }
}
