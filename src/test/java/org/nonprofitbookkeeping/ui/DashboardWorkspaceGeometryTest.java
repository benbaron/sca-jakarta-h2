package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DashboardWorkspaceGeometryTest
{
    @Test
    public void evaluatesDefaultWorkspaceGeometry()
    {
        DashboardWorkspaceLayoutPolicy.GeometryAssessment result =
                DashboardWorkspaceLayoutPolicy.assess(
                        1400,
                        760,
                        222,
                        246,
                        2,
                        4,
                        900,
                        180,
                        720,
                        1120);

        assertEquals(924.0, result.centerViewportWidth());
        assertEquals(DashboardWorkspaceLayoutPolicy.LayoutMode.MEDIUM, result.layoutMode());
        assertEquals(442.0, result.cardWidth());
        assertTrue(result.cardMinimumSatisfied());
        assertEquals(896.0, result.transactionViewportWidth());
        assertTrue(result.transactionMinimumSatisfied());
        assertTrue(result.transactionHorizontalScrollRequired());
        assertTrue(result.outerVerticalScrollRequired());
    }

    @Test
    public void evaluatesExpandedCenterGeometry()
    {
        DashboardWorkspaceLayoutPolicy.GeometryAssessment result =
                DashboardWorkspaceLayoutPolicy.assess(
                        1400,
                        900,
                        0,
                        0,
                        0,
                        4,
                        820,
                        180,
                        720,
                        1120);

        assertEquals(1400.0, result.centerViewportWidth());
        assertEquals(DashboardWorkspaceLayoutPolicy.LayoutMode.WIDE, result.layoutMode());
        assertTrue(result.cardMinimumSatisfied());
        assertTrue(result.transactionMinimumSatisfied());
        assertFalse(result.transactionHorizontalScrollRequired());
        assertFalse(result.outerVerticalScrollRequired());
    }
}
