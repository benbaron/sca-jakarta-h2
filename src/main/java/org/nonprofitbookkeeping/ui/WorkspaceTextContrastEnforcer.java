package org.nonprofitbookkeeping.ui;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;

/** Keeps workspace text independent of platform and theme accent foregrounds. */
final class WorkspaceTextContrastEnforcer
{
    private static final Object INSTALLED = new Object();
    private static final Object FOREGROUND = new Object();

    private WorkspaceTextContrastEnforcer()
    {
    }

    static void install(Parent root)
    {
        observe(root, foreground(root));
    }

    static void apply(Parent root, boolean dark)
    {
        Paint foreground = dark ? Color.WHITE : Color.BLACK;
        root.getProperties().put(FOREGROUND, foreground);
        observe(root, foreground);
    }

    private static void observe(Node node, Paint foreground)
    {
        enforce(node, foreground);
        if (node instanceof Parent parent)
        {
            parent.getProperties().put(FOREGROUND, foreground);
            if (parent.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) == null)
            {
                parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change ->
                {
                    while (change.next())
                    {
                        if (change.wasAdded())
                        {
                            change.getAddedSubList().forEach(child -> observe(child, foreground(parent)));
                        }
                    }
                });
            }
            parent.getChildrenUnmodifiable().forEach(child -> observe(child, foreground));
        }
    }

    private static void enforce(Node node, Paint foreground)
    {
        String color = Color.WHITE.equals(foreground) ? "#ffffff" : "#000000";
        if (node instanceof Labeled labeled)
        {
            if (labeled.styleProperty().isBound())
            {
                // MenuBar skin buttons bind their inline style to a Menu.
                // Bind the paint rather than breaking that skin-owned binding.
                labeled.textFillProperty().bind(
                        new javafx.beans.property.SimpleObjectProperty<>(foreground));
            }
            else
            {
                clampStyle(labeled, "-fx-text-fill", color);
            }
        }
        if (node instanceof Text text)
        {
            clampStyle(text, "-fx-fill", color);
        }
        if (node instanceof javafx.scene.control.TextInputControl input)
        {
            clampStyle(input, "-fx-text-fill", color);
            clampStyle(input, "-fx-text-inner-color", color);
        }
    }

    private static void clampStyle(Node node, String property, String color)
    {
        if (node.styleProperty().isBound())
        {
            return;
        }
        // Inline CSS outranks both application and user-agent stylesheets.
        // Retain unrelated panel styles, including font, alignment and padding.
        String currentStyle = node.getStyle();
        String style = (currentStyle == null ? "" : currentStyle).replaceAll(
                "(?i)(?:^|;)\\s*" + property + "\\s*:[^;]*(?:;|$)", ";");
        node.setStyle(style.replaceAll("^;+|;+$", "") + ";" + property + ": " + color + ";");
    }

    private static Paint foreground(Parent parent)
    {
        Object foreground = parent.getProperties().get(FOREGROUND);
        return foreground instanceof Paint paint ? paint : Color.BLACK;
    }
}
