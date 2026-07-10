package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Consistency checks for routable and visible workspace panels. */
public class AppPanelConsistencyTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    public void panelHost_recognizesEveryStablePanelIdIncludingAliases()
    {
        assertEquals(EnumSet.allOf(AppPanelId.class), PanelHost.supportedPanelIds());
    }

    @Test
    public void navigationExposesOneJournalDestinationAndNoRetiredAliases()
    {
        EnumSet<AppPanelId> indexed = FxTestSupport.onFx(() -> {
            NavigationPane nav = new NavigationPane(id -> { }, (t, b) -> { },
                    () -> new NavigationPane.InspectorContext("TEST", "ALL", "default"));
            return nav.indexedPanelIds();
        });

        EnumSet<AppPanelId> expected = EnumSet.allOf(AppPanelId.class);
        expected.remove(AppPanelId.LEDGER_REGISTER);
        expected.remove(AppPanelId.TXN_EDITOR);
        expected.remove(AppPanelId.SCHEDULES);
        assertEquals(expected, indexed);
    }

    @Test
    public void everyRoutablePanelCanBeShownWithTitleAndRoot()
    {
        FxTestSupport.onFx(() -> {
            PanelHost host = new PanelHost();
            for (AppPanelId id : AppPanelId.values())
            {
                if (id == AppPanelId.SCHEDULES)
                {
                    continue;
                }
                host.show(id);
                assertNotNull(host.activeRoot(), "active root missing for " + id);
                assertFalse(host.getActiveTitle().isBlank(), "blank title for " + id);
            }
            assertEquals(AppPanelId.JOURNAL_PANE, PanelHost.canonicalPanelId(AppPanelId.LEDGER_REGISTER));
            assertEquals(AppPanelId.JOURNAL_PANE, PanelHost.canonicalPanelId(AppPanelId.TXN_EDITOR));
            return null;
        });
    }
}
