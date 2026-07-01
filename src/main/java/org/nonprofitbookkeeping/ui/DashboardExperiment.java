package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;

/**
 * Compatibility entry point for the rebuilt dashboard workspace.
 *
 * <p>The previous implementation was removed. All layout and presentation now
 * live in {@link DashboardWorkspacePanel}.</p>
 */
public final class DashboardExperiment implements AppPanel
{
    private final DashboardWorkspacePanel delegate = new DashboardWorkspacePanel();

    @Override
    public String title()
    {
        return delegate.title();
    }

    @Override
    public Node root()
    {
        return delegate.root();
    }

    @Override
    public void onNew()
    {
        delegate.onNew();
    }
}
