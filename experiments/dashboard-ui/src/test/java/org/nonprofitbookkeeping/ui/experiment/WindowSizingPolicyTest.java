package org.nonprofitbookkeeping.ui.experiment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowSizingPolicyTest
{
    @Test
    void usesPreferredSizeOnLargeDisplays()
    {
        WindowSizingPolicy.WindowDimensions dimensions =
                WindowSizingPolicy.forVisualBounds(1920.0, 1080.0);

        assertEquals(1440.0, dimensions.width());
        assertEquals(900.0, dimensions.height());
        assertEquals(900.0, dimensions.minimumWidth());
        assertEquals(600.0, dimensions.minimumHeight());
    }

    @Test
    void constrainsWindowToNinetyPercentOfSmallDisplay()
    {
        WindowSizingPolicy.WindowDimensions dimensions =
                WindowSizingPolicy.forVisualBounds(1280.0, 720.0);

        assertEquals(1152.0, dimensions.width());
        assertEquals(648.0, dimensions.height());
        assertEquals(900.0, dimensions.minimumWidth());
        assertEquals(540.0, dimensions.minimumHeight());
    }

    @Test
    void rejectsNonPositiveVisualBounds()
    {
        assertThrows(IllegalArgumentException.class,
                () -> WindowSizingPolicy.forVisualBounds(0.0, 720.0));
    }
}
