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
        if (root.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null)
        {
            return;
        }
        root.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change ->
        {
            while (change.next())
            {
                if (change.wasAdded())
                {
                    change.getAddedSubList().forEach(child -> observe(child, foreground(root)));
                }
            }
        });
        root.getChildrenUnmodifiable().forEach(child -> observe(child, foreground(root)));
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
            install(parent);
            parent.getProperties().put(FOREGROUND, foreground);
            parent.getChildrenUnmodifiable().forEach(child -> observe(child, foreground));
        }
    }

    private static void enforce(Node node, Paint foreground)
    {
        if (node instanceof Labeled labeled)
        {
            labeled.setTextFill(foreground);
        }
        if (node instanceof Text text)
        {
            text.setFill(foreground);
        }
    }

    private static Paint foreground(Parent parent)
    {
        Object foreground = parent.getProperties().get(FOREGROUND);
        return foreground instanceof Paint paint ? paint : Color.BLACK;
    }
}
