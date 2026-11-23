package org.nonprofitbookkeeping.ui;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextContrastPolicyTest
{
    @Test
    void choosesTextFromSurfaceRatherThanThemeName()
    {
        for (Color background : new Color[]{Color.WHITE, Color.LIGHTGRAY, Color.web("#fffdf2"),
                Color.web("#cfe2f3"), Color.web("#e2f0d9")})
        {
            assertEquals(Color.BLACK, WorkspaceTextContrastEnforcer.contrastingText(background));
        }
        for (Color background : new Color[]{Color.BLACK, Color.web("#24292f"), Color.web("#282a36")})
        {
            assertEquals(Color.WHITE, WorkspaceTextContrastEnforcer.contrastingText(background));
        }
    }
    @Test
    void importGreyMeetsBodyContrastOnNeutralAndSelectionSurfaces()
    {
        assertEquals(Color.web("#5f6b7a"), WorkspaceTextContrastEnforcer.mutedText(Color.WHITE));
        for (int component = 0; component <= 255; component++)
        {
            assertBodyContrast(Color.rgb(component, component, component));
        }
        for (String hex : new String[] {"#cfe2f3", "#d0e8ff", "#0969da", "#24292f", "#282a36", "#4c566a"})
        {
            Color background = Color.web(hex);
            Color grey = WorkspaceTextContrastEnforcer.mutedText(background);
            assertNotEquals(Color.BLACK, grey);
            assertNotEquals(Color.WHITE, grey);
            assertBodyContrast(background);
        }
    }

    private static void assertBodyContrast(Color background)
    {
        Color foreground = WorkspaceTextContrastEnforcer.mutedText(background);
        double first = luminance(foreground);
        double second = luminance(background);
        double ratio = (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
        assertTrue(ratio >= 4.5, "Import grey " + foreground + " on " + background + " has contrast " + ratio);
    }

    private static double luminance(Color color)
    {
        return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) + .0722 * linear(color.getBlue());
    }

    private static double linear(double value)
    {
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
