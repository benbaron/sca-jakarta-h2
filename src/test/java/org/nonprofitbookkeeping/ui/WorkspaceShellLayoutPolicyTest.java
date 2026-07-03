package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.WorkspaceDividerState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkspaceShellLayoutPolicyTest
{
    private static final double TOLERANCE = 0.001;

    @Test
    public void referenceWidthKeepsCompactSidebarsAndBroadCenter()
    {
        WorkspaceShellLayoutPolicy.ShellGeometry geometry =
                WorkspaceShellLayoutPolicy.forWidth(1180.0);

        assertEquals(210.0, geometry.navigationWidth(), TOLERANCE);
        assertEquals(224.2, geometry.inspectorWidth(), TOLERANCE);
        assertEquals(745.8, geometry.centerWidth(), TOLERANCE);
        assertEquals(210.0 / 1180.0, geometry.leftDividerPosition(), TOLERANCE);
        assertEquals(0.81, geometry.rightDividerPosition(), TOLERANCE);
    }

    @Test
    public void laptopMinimumStillAllocatesAllThreePanes()
    {
        WorkspaceShellLayoutPolicy.ShellGeometry geometry =
                WorkspaceShellLayoutPolicy.forWidth(900.0);

        assertEquals(162.0, geometry.navigationWidth(), TOLERANCE);
        assertEquals(180.0, geometry.inspectorWidth(), TOLERANCE);
        assertEquals(558.0, geometry.centerWidth(), TOLERANCE);
        assertTrue(geometry.leftDividerPosition() < geometry.rightDividerPosition());
    }

    @Test
    public void veryNarrowWindowUsesSidebarMinimumsRatherThanClippingThemAway()
    {
        WorkspaceShellLayoutPolicy.ShellGeometry geometry =
                WorkspaceShellLayoutPolicy.forWidth(700.0);

        assertEquals(160.0, geometry.navigationWidth(), TOLERANCE);
        assertEquals(180.0, geometry.inspectorWidth(), TOLERANCE);
        assertEquals(360.0, geometry.centerWidth(), TOLERANCE);
    }

    @Test
    public void unsafeRememberedDividersFallBackToResponsiveDefaults()
    {
        WorkspaceDividerState state = WorkspaceShellLayoutPolicy.safeDividerState(
                900.0,
                new WorkspaceDividerState(0.10, 0.95));

        assertEquals(0.18, state.leftDividerPosition(), TOLERANCE);
        assertEquals(0.80, state.rightDividerPosition(), TOLERANCE);
    }

    @Test
    public void safeRememberedDividersArePreserved()
    {
        WorkspaceDividerState requested = new WorkspaceDividerState(0.22, 0.76);

        assertEquals(requested, WorkspaceShellLayoutPolicy.safeDividerState(1180.0, requested));
    }

    @Test
    public void rejectsInvalidWorkspaceWidth()
    {
        assertThrows(IllegalArgumentException.class, () ->
                WorkspaceShellLayoutPolicy.forWidth(0.0));
        assertThrows(IllegalArgumentException.class, () ->
                WorkspaceShellLayoutPolicy.forWidth(Double.POSITIVE_INFINITY));
    }
}
