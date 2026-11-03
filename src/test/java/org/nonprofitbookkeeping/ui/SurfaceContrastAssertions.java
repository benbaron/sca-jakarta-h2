package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Assertions;

final class SurfaceContrastAssertions
{
    private SurfaceContrastAssertions()
    {
    }

    static void assertReadable(Label label)
    {
        Color background = Color.WHITE;
        var chain = new java.util.ArrayList<Node>();
        for (Node current = label; current != null; current = current.getParent())
        {
            chain.add(current);
        }
        java.util.Collections.reverse(chain);
        for (Node node : chain)
        {
            if (node instanceof Region region && region.getBackground() != null)
            {
                for (var fill : region.getBackground().getFills())
                {
                    if (fill.getFill() instanceof Color color)
                    {
                        background = color.interpolate(background, 1 - color.getOpacity());
                    }
                }
            }
        }
        Color text = (Color) label.getTextFill();
        double light = luminance(background);
        double dark = luminance(text);
        double ratio = (Math.max(light, dark) + .05) / (Math.min(light, dark) + .05);
        Assertions.assertTrue(ratio >= 4.5,
                label.getText() + " has contrast " + ratio + " on " + background + " with " + text);
    }

    private static double luminance(Color color)
    {
        double[] components = {color.getRed(), color.getGreen(), color.getBlue()};
        for (int i = 0; i < components.length; i++)
        {
            components[i] = components[i] <= .04045 ? components[i] / 12.92
                    : Math.pow((components[i] + .055) / 1.055, 2.4);
        }
        return .2126 * components[0] + .7152 * components[1] + .0722 * components[2];
    }
}
