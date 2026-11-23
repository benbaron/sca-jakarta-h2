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

/** Enforces surface contrast after layout, retaining semantic grey for unresolved import text. */
final class WorkspaceTextContrastEnforcer
{
    private static final Object INSTALLED = new Object();
    private static final Object DARK = new Object();
    private static final Object BOUND_PAINT = new Object();
    private static final Color IMPORT_GREY_LIGHT = Color.web("#5f6b7a");
    private static final Color IMPORT_GREY_DARK = Color.web("#b6beca");

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
        boolean mutedImport = false;
        for (int index = ancestors.size() - 1; index >= 0; index--)
        {
            fallback = surface(ancestors.get(index), fallback);
            mutedImport |= hasImportGreyRole(ancestors.get(index));
        }
        refresh(root, fallback, mutedImport);
    }

    private static void refresh(Node node, Color inherited, boolean inheritedImportGrey)
    {
        boolean mutedImport = inheritedImportGrey || hasImportGreyRole(node);
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
        Color foreground = mutedImport ? mutedText(textBackground) : contrastingText(textBackground);
        String color = foreground.equals(Color.BLACK) ? "#000000" : foreground.equals(Color.WHITE) ? "#ffffff"
                : String.format(java.util.Locale.ROOT, "#%02x%02x%02x",
                Math.round(foreground.getRed() * 255), Math.round(foreground.getGreen() * 255),
                Math.round(foreground.getBlue() * 255));
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
                refresh(child, background, mutedImport);
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

    private static boolean hasImportGreyRole(Node node)
    {
        return node.getStyleClass().contains("import-source-suggestion")
                || node.getStyleClass().contains("import-entry-invalid");
    }

    static Color contrastingText(Color background)
    {
        double luminance = luminance(background);
        return (luminance + 0.05) / 0.05 >= 1.05 / (luminance + 0.05)
                ? Color.BLACK : Color.WHITE;
    }

    /** Retains the import grey, darkening/lightening only as needed for body-text contrast. */
    static Color mutedText(Color background)
    {
        Color normal = contrastingText(background);
        Color grey = normal.equals(Color.BLACK) ? IMPORT_GREY_LIGHT : IMPORT_GREY_DARK;
        if (contrast(grey, background) >= 4.5)
        {
            return grey;
        }
        // Search quantized CSS colors so the painted RGB value also meets the threshold.
        int low = 0;
        int high = 255;
        while (high - low > 1)
        {
            int middle = (low + high) / 2;
            if (contrast(blend(grey, normal, middle), background) >= 4.5)
            {
                high = middle;
            }
            else
            {
                low = middle;
            }
        }
        return blend(grey, normal, high);
    }

    private static Color blend(Color grey, Color normal, int fraction)
    {
        Color mixed = grey.interpolate(normal, fraction / 255.0);
        return Color.rgb((int) Math.round(mixed.getRed() * 255), (int) Math.round(mixed.getGreen() * 255),
                (int) Math.round(mixed.getBlue() * 255));
    }

    private static double contrast(Color foreground, Color background)
    {
        double first = luminance(foreground);
        double second = luminance(background);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double luminance(Color color)
    {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
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
