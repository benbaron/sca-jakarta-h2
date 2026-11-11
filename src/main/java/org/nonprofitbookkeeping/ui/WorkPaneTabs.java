package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/** Named work panes share their owning controller, selection and unsaved draft. */
final class WorkPaneTabs extends TabPane
{
    WorkPaneTabs(String id)
    {
        setId(id);
        setMinSize(0, 0);
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        getStyleClass().add("work-pane-tabs");
    }

    WorkPaneTabs addPane(String title, Node content)
    {
        getTabs().add(new Tab(title, content instanceof javafx.scene.control.ScrollPane
                ? content : PanelViewport.section(content)));
        return this;
    }
}
