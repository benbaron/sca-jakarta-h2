package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

/** Chooses black or white from the rendered surface after CSS and layout complete. */
final class WorkspaceTextContrastEnforcer
{
    private static final Object INSTALLED = new Object();
    private static final Object DARK = new Object();
    private static final Object BOUND_PAINT = new Object();

    private WorkspaceTextContrastEnforcer()
    {
    }

    static void install(Parent root)
    {
        if (root.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null)
        {
            return;
        }
        Runnable refresh = () -> refresh(root);
        root.sceneProperty().addListener((observable, oldScene, newScene) ->
        {
            if (oldScene != null)
            {
                oldScene.removePostLayoutPulseListener(refresh);
            }
            if (newScene != null)
            {
                newScene.addPostLayoutPulseListener(refresh);
                Platform.requestNextPulse();
            }
        });
        Scene scene = root.getScene();
        if (scene != null)
        {
            scene.addPostLayoutPulseListener(refresh);
        }
    }

    static void apply(Parent root, boolean dark)
    {
        root.getProperties().put(DARK, dark);
        Platform.requestNextPulse();
    }

    /** Also used by focused CSS tests after explicitly completing CSS/layout. */
    static void refresh(Parent root)
    {
        Color fallback = Boolean.TRUE.equals(root.getProperties().get(DARK)) ? Color.BLACK : Color.WHITE;
        // A workspace can be nested inside the authenticated application root.
        java.util.ArrayList<Node> ancestors = new java.util.ArrayList<>();
        for (Node parent = root.getParent(); parent != null; parent = parent.getParent())
        {
            ancestors.add(parent);
        }
        for (int index = ancestors.size() - 1; index >= 0; index--)
        {
            fallback = surface(ancestors.get(index), fallback);
        }
        refresh(root, fallback);
    }

    private static void refresh(Node node, Color inherited)
    {
        Color background = surface(node, inherited);
        Color textBackground = background;
        if (node instanceof TextInputControl input)
        {
            Node content = input.lookup(".content");
            var path = new java.util.ArrayList<Node>();
            for (Node current = content; current != null && current != input; current = current.getParent())
            {
                path.add(current);
            }
            for (int index = path.size() - 1; index >= 0; index--)
            {
                textBackground = surface(path.get(index), textBackground);
            }
        }
        Color foreground = contrastingText(textBackground);
        String color = foreground.equals(Color.BLACK) ? "#000000" : "#ffffff";
        if (node instanceof Labeled || node instanceof TextInputControl)
        {
            // Skin-owned styles stay bound. Their Text children are handled
            // after construction, without changing skin property bindings.
            clampStyle(node, "-fx-text-fill", color);
            if (node instanceof Labeled labeled && labeled.styleProperty().isBound())
            {
                @SuppressWarnings("unchecked")
                var paint = (javafx.beans.property.ObjectProperty<javafx.scene.paint.Paint>)
                        node.getProperties().get(BOUND_PAINT);
                if (paint == null)
                {
                    paint = new javafx.beans.property.SimpleObjectProperty<>(foreground);
                    node.getProperties().put(BOUND_PAINT, paint);
                    labeled.textFillProperty().bind(paint);
                }
                paint.set(foreground);
            }
        }
        if (node instanceof Text)
        {
            clampStyle(node, "-fx-fill", color);
        }
        if (node instanceof Parent parent)
        {
            // Snapshot protects traversal from skin changes caused by CSS.
            for (Node child : java.util.List.copyOf(parent.getChildrenUnmodifiable()))
            {
                refresh(child, background);
            }
        }
    }

    private static void clampStyle(Node node, String property, String color)
    {
        if (node.styleProperty().isBound())
        {
            return;
        }
        String current = node.getStyle();
        String declaration = property + ": " + color + ";";
        if (current != null && current.endsWith(declaration))
        {
            return;
        }
        String style = (current == null ? "" : current).replaceAll(
                "(?i)(?:^|;)\\s*" + property + "\\s*:[^;]*(?:;|$)", ";");
        String next = style.replaceAll("^;+|;+$", "") + ";" + declaration;
        if (!next.equals(current))
        {
            node.setStyle(next);
        }
    }

    static Color contrastingText(Color background)
    {
        double luminance = 0.2126 * linear(background.getRed())
                + 0.7152 * linear(background.getGreen())
                + 0.0722 * linear(background.getBlue());
        return (luminance + 0.05) / 0.05 >= 1.05 / (luminance + 0.05)
                ? Color.BLACK : Color.WHITE;
    }

    private static double linear(double component)
    {
        return component <= 0.04045 ? component / 12.92
                : Math.pow((component + 0.055) / 1.055, 2.4);
    }

    private static Color surface(Node node, Color inherited)
    {
        Color background = inherited;
        if (node instanceof Region region && region.getBackground() != null)
        {
            for (var fill : region.getBackground().getFills())
            {
                if (fill.getFill() instanceof Color color)
                {
                    double alpha = color.getOpacity();
                    background = new Color(
                            color.getRed() * alpha + background.getRed() * (1 - alpha),
                            color.getGreen() * alpha + background.getGreen() * (1 - alpha),
                            color.getBlue() * alpha + background.getBlue() * (1 - alpha), 1);
                }
            }
        }
        return background;
    }
}
