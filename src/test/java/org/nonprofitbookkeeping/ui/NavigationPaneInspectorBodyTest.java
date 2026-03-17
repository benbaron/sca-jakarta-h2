package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NavigationPaneInspectorBodyTest component.
 */
public class NavigationPaneInspectorBodyTest
{
    @Test
    public void inspectorBody_includesPanelIdAndContextMetadataForWorkspaceItem()
    {
        String body = NavigationPane.inspectorBody(
                new NavigationPane.NavItem(AppPanelId.DIAGNOSTICS, "Diagnostics"),
                new NavigationPane.InspectorContext("BARONY-RED", "2026-01-01..2026-01-31", "Health checks"));
        assertTrue(body.contains("Diagnostics"));
        assertTrue(body.contains("DIAGNOSTICS"));
        assertTrue(body.contains("BARONY-RED"));
        assertTrue(body.contains("2026-01-01..2026-01-31"));
        assertTrue(body.contains("Capabilities: Health checks"));
    }

    @Test
    public void inspectorBody_handlesGroupItemWithContext()
    {
        String body = NavigationPane.inspectorBody(
                new NavigationPane.NavItem(null, "Operations"),
                new NavigationPane.InspectorContext("BARONY-BLUE", "ALL", "n/a"));
        assertTrue(body.contains("group"));
        assertTrue(body.contains("BARONY-BLUE"));
        assertTrue(body.contains("Date range: ALL"));
    }
}
