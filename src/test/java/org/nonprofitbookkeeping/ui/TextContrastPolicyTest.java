package org.nonprofitbookkeeping.ui;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
