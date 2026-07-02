package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkspaceWindowSizingPolicyTest
{
    private static final double TOLERANCE = 0.001;

    @Test
    public void laptopVisualBoundsProduceCompactCenteredWindow()
    {
        WorkspaceWindowSizingPolicy.WindowGeometry geometry =
                WorkspaceWindowSizingPolicy.forVisualBounds(0.0, 0.0, 1366.0, 728.0);

        assertEquals(1180.0, geometry.width(), TOLERANCE);
        assertEquals(655.2, geometry.height(), TOLERANCE);
        assertEquals(900.0, geometry.minimumWidth(), TOLERANCE);
        assertEquals(524.16, geometry.minimumHeight(), TOLERANCE);
        assertEquals(93.0, geometry.x(), TOLERANCE);
        assertEquals(36.4, geometry.y(), TOLERANCE);
    }

    @Test
    public void smallVisualBoundsKeepEveryDimensionInsideUsableScreen()
    {
        WorkspaceWindowSizingPolicy.WindowGeometry geometry =
                WorkspaceWindowSizingPolicy.forVisualBounds(10.0, 20.0, 1024.0, 600.0);

        assertEquals(921.6, geometry.width(), TOLERANCE);
        assertEquals(540.0, geometry.height(), TOLERANCE);
        assertEquals(737.28, geometry.minimumWidth(), TOLERANCE);
        assertEquals(432.0, geometry.minimumHeight(), TOLERANCE);
        assertTrue(geometry.x() >= 10.0);
        assertTrue(geometry.y() >= 20.0);
        assertTrue(geometry.x() + geometry.width() <= 1034.0 + TOLERANCE);
        assertTrue(geometry.y() + geometry.height() <= 620.0 + TOLERANCE);
    }

    @Test
    public void desktopVisualBoundsDoNotOpenAnOversizedWindow()
    {
        WorkspaceWindowSizingPolicy.WindowGeometry geometry =
                WorkspaceWindowSizingPolicy.forVisualBounds(0.0, 0.0, 1920.0, 1040.0);

        assertEquals(1180.0, geometry.width(), TOLERANCE);
        assertEquals(760.0, geometry.height(), TOLERANCE);
        assertEquals(900.0, geometry.minimumWidth(), TOLERANCE);
        assertEquals(600.0, geometry.minimumHeight(), TOLERANCE);
    }

    @Test
    public void rejectsInvalidVisualBounds()
    {
        assertThrows(IllegalArgumentException.class, () ->
                WorkspaceWindowSizingPolicy.forVisualBounds(0.0, 0.0, 0.0, 700.0));
        assertThrows(IllegalArgumentException.class, () ->
                WorkspaceWindowSizingPolicy.forVisualBounds(0.0, 0.0, 1200.0, Double.NaN));
    }
}
