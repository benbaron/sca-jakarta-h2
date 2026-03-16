package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AppPanelConsistencyTest component.
 */
public class AppPanelConsistencyTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    public void panelHost_hasFactoryForEveryPanelId()
    {
        assertEquals(EnumSet.allOf(AppPanelId.class), PanelHost.supportedPanelIds());
    }

    @Test
    public void navigationIndexesEveryPanelId()
    {
        EnumSet<AppPanelId> indexed = FxTestSupport.onFx(() -> {
            NavigationPane nav = new NavigationPane(id -> { }, (t, b) -> { },
                    () -> new NavigationPane.InspectorContext("TEST", "ALL", "default"));
            return nav.indexedPanelIds();
        });

        assertEquals(EnumSet.allOf(AppPanelId.class), indexed);
    }

    @Test
    public void everyPanelCanBeShownWithTitleAndRoot()
    {
        FxTestSupport.onFx(() -> {
            PanelHost host = new PanelHost();
            for (AppPanelId id : AppPanelId.values())
            {
                host.show(id);
                assertNotNull(host.getCenter(), "center root missing for " + id);
                assertFalse(host.getActiveTitle().isBlank(), "blank title for " + id);
            }
            return null;
        });
    }
}
