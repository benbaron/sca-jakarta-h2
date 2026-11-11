package org.nonprofitbookkeeping.ui;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Gives existing control chrome a resize handle without replacing its controller or business panes. */
final class ResizablePanelHeader
{
    private static final String STYLE = "resizable-control-header-split";

    private ResizablePanelHeader()
    {
    }

    static void prepare(Node node, String key)
    {
        if (node instanceof BorderPane border && border.getTop() != null && border.getCenter() != null
                && actionable(border.getTop()))
        {
            Node header = border.getTop(), body = border.getCenter();
            border.setTop(null);
            border.setCenter(null);
            border.setCenter(split(header, body, key));
        }
        else if (node instanceof VBox box && box.getChildren().size() == 2
                && box.getChildren().get(1) instanceof SplitPane body && actionable(box.getChildren().get(0)))
        {
            Node header = box.getChildren().get(0);
            box.getChildren().clear();
            SplitPane split = split(header, body, key);
            VBox.setVgrow(split, Priority.ALWAYS);
            box.getChildren().add(split);
        }
    }

    static boolean isHeaderSplit(SplitPane split)
    {
        return split.getStyleClass().contains(STYLE);
    }

    private static SplitPane split(Node header, Node body, String key)
    {
        ScrollPane scroll = header instanceof ScrollPane existing ? existing : new ScrollPane(header);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setMinSize(0, 0);
        scroll.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        if (scroll.getContent() instanceof Region content)
        {
            content.setMinHeight(Region.USE_PREF_SIZE);
        }
        releaseNestedScrollCaps(scroll.getContent());
        if (body instanceof Region region) region.setMinHeight(0);
        SplitPane split = new SplitPane(scroll, body);
        split.setOrientation(Orientation.VERTICAL);
        split.setMinSize(0, 0);
        split.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        String identity = java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        split.setId("panelHeaderSplit-" + identity);
        split.getProperties().put("sca.headerPath", key);
        split.getStyleClass().add(STYLE);
        CompanySplitPaneStateBinder.bindHeader(split, "control-header-" + identity, .28);
        return split;
    }

    private static void releaseNestedScrollCaps(Node node)
    {
        if (node instanceof ScrollPane scroll)
        {
            scroll.setMaxHeight(Double.MAX_VALUE);
            VBox.setVgrow(scroll, Priority.ALWAYS);
            releaseNestedScrollCaps(scroll.getContent());
        }
        else if (node instanceof Parent parent && !(node instanceof javafx.scene.control.Control))
        {
            parent.getChildrenUnmodifiable().forEach(ResizablePanelHeader::releaseNestedScrollCaps);
        }
    }

    private static boolean actionable(Node node)
    {
        if (node instanceof ButtonBase || node instanceof TextInputControl || node instanceof ComboBoxBase<?>) return true;
        if (node instanceof ScrollPane scroll) return scroll.getContent() != null && actionable(scroll.getContent());
        if (node instanceof Parent parent && !(node instanceof javafx.scene.control.Control))
        {
            return parent.getChildrenUnmodifiable().stream().anyMatch(ResizablePanelHeader::actionable);
        }
        return false;
    }
}
