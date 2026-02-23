package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;

/**
 * Defines the AppPanel contract in the nonprofit bookkeeping application.
 */
public interface AppPanel
{
    String title();
    Node root();

    default void onSave() {}
    default void onNew() {}
    default void onCopy() {}
    default void onPaste() {}
}
