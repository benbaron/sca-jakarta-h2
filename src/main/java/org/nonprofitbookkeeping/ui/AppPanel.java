package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;

public interface AppPanel
{
    String title();
    Node root();

    default void onSave() {}
    default void onNew() {}
    default void onCopy() {}
    default void onPaste() {}
}
