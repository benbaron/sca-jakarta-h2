package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;

/** Keeps an entire work pane reachable when the viewport is smaller than its controls. */
final class PanelViewport extends ScrollPane
{
    private PanelViewport(Node content, boolean wholePanel)
    {
        BorderPane surface = new BorderPane(content)
        {
            @Override
            protected double computeMinWidth(double height)
            {
                return Math.max(wholePanel ? 480 : 0, super.computeMinWidth(height));
            }

            @Override
            protected double computeMinHeight(double width)
            {
                return Math.max(wholePanel ? 320 : 0, super.computeMinHeight(width));
            }
        };
        setContent(surface);
        setFitToWidth(true);
        setFitToHeight(true);
        setMinSize(0, 0);
        setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        getStyleClass().add("panel-viewport");
        setId("panelViewport");
    }

    static PanelViewport wrap(Node content)
    {
        return new PanelViewport(content, true);
    }

    static PanelViewport section(Node content)
    {
        return new PanelViewport(content, false);
    }

    /** Apply overflow access to every logical tab, including inactive subpanes. */
    static void prepare(Node node)
    {
        prepare(node, node.getId() == null ? node.getClass().getSimpleName() : node.getId());
    }

    static void prepare(Node node, String key)
    {
        PanelTextLayout.prepare(node);
        ResizablePanelHeader.prepare(node, key);
        if (node instanceof TabPane tabs)
        {
            for (var tab : tabs.getTabs())
            {
                Node content = tab.getContent();
                if (content != null)
                {
                    prepare(content, key + "-tab-" + tab.getText());
                    if (!(content instanceof ScrollPane))
                    {
                        // Tab writes its effective disabled state onto its content.
                        // Transfer that ownership to the new viewport so the old
                        // root does not stay disabled after the tab is enabled.
                        if (tab.isDisabled() && !content.disableProperty().isBound())
                        {
                            content.setDisable(false);
                        }
                        tab.setContent(section(content));
                    }
                }
            }
        }
        else if (node instanceof ScrollPane scroll)
        {
            scroll.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
            scroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
            if (scroll.getContent() != null)
            {
                prepare(scroll.getContent(), key + "-content");
            }
        }
        else if (node instanceof SplitPane split)
        {
            for (int i = 0; i < split.getItems().size(); i++)
            {
                prepare(split.getItems().get(i), key + "-pane-" + i);
            }
        }
        else if (node instanceof javafx.scene.control.TableView<?> table)
        {
            HorizontalOverflowTableSkin.install(table);
        }
        else if (node instanceof Parent parent && !(node instanceof javafx.scene.control.Control))
        {
            var children = java.util.List.copyOf(parent.getChildrenUnmodifiable());
            for (int i = 0; i < children.size(); i++)
            {
                Node child = children.get(i);
                prepare(child, key + "-" + (child.getId() == null ? "child-" + i : child.getId()));
            }
        }
    }
}
