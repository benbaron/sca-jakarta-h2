package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NavigationPaneInspectorBodyTest
{
    @Test
    public void inspectorBody_includesPanelIdForWorkspaceItem()
    {
        String body = NavigationPane.inspectorBody(new NavigationPane.NavItem(AppPanelId.DIAGNOSTICS, "Diagnostics"));
        assertTrue(body.contains("Diagnostics"));
        assertTrue(body.contains("DIAGNOSTICS"));
    }

    @Test
    public void inspectorBody_handlesGroupItem()
    {
        String body = NavigationPane.inspectorBody(new NavigationPane.NavItem(null, "Operations"));
        assertTrue(body.contains("group"));
    }
}
