package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PanelHostBehaviorTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    public void openingSamePanelReusesExistingTab()
    {
        FxTestSupport.onFx(() -> {
            PanelHost host = new PanelHost();
            host.show(AppPanelId.LEDGER_REGISTER);
            Tab first = host.getSelectionModel().getSelectedItem();

            host.show(AppPanelId.DASHBOARD);
            host.show(AppPanelId.LEDGER_REGISTER);
            Tab second = host.getSelectionModel().getSelectedItem();

            assertSame(first, second);
            assertEquals(2, host.openPanelCount());
            return null;
        });
    }

    @Test
    public void dashboardIsPermanentAndOtherPanelsAreClosable()
    {
        FxTestSupport.onFx(() -> {
            PanelHost host = new PanelHost();
            host.show(AppPanelId.DASHBOARD);
            host.show(AppPanelId.LEDGER_REGISTER);

            assertFalse(host.isClosable(AppPanelId.DASHBOARD));
            assertTrue(host.isClosable(AppPanelId.LEDGER_REGISTER));
            return null;
        });
    }

    @Test
    public void activePanelTracksTabSelection()
    {
        FxTestSupport.onFx(() -> {
            PanelHost host = new PanelHost();
            host.show(AppPanelId.DASHBOARD);
            Tab dashboard = host.getSelectionModel().getSelectedItem();
            host.show(AppPanelId.FUNDS);

            host.getSelectionModel().select(dashboard);

            assertEquals(AppPanelId.DASHBOARD, host.activePanelId());
            assertEquals("Dashboard", host.getActiveTitle());
            return null;
        });
    }

    @Test
    public void selectingAnExistingTabInvokesItsRefreshHook()
    {
        FxTestSupport.onFx(() -> {
            PanelHost host = new PanelHost();
            CountingPanel journal = new CountingPanel("Journal");
            CountingPanel help = new CountingPanel("Help");

            host.showReplacement(AppPanelId.JOURNAL_PANE, journal);
            Tab journalTab = host.getSelectionModel().getSelectedItem();
            host.showReplacement(AppPanelId.HELP, help);
            int shownBeforeReselection = journal.shownCount();

            host.getSelectionModel().select(journalTab);

            assertEquals(shownBeforeReselection + 1, journal.shownCount());
            assertEquals(AppPanelId.JOURNAL_PANE, host.activePanelId());
            return null;
        });
    }

    @Test
    public void refreshOpenPanelsPreservesOpenDestinationsAndActivePanel()
    {
        FxTestSupport.onFx(() -> {
            PanelHost host = new PanelHost();
            host.show(AppPanelId.DASHBOARD);
            host.show(AppPanelId.HELP);
            Tab originalHelpTab = host.getSelectionModel().getSelectedItem();

            host.refreshOpenPanels();

            assertEquals(2, host.openPanelCount());
            assertTrue(host.isOpen(AppPanelId.DASHBOARD));
            assertTrue(host.isOpen(AppPanelId.HELP));
            assertEquals(AppPanelId.HELP, host.activePanelId());
            assertNotSame(originalHelpTab, host.getSelectionModel().getSelectedItem());
            return null;
        });
    }

    private static final class CountingPanel implements AppPanel
    {
        private final String title;
        private final AtomicInteger shown = new AtomicInteger();

        private CountingPanel(String title)
        {
            this.title = title;
        }

        @Override
        public String title()
        {
            return title;
        }

        @Override
        public Node root()
        {
            return new Label(title);
        }

        @Override
        public void onPanelShown()
        {
            shown.incrementAndGet();
        }

        private int shownCount()
        {
            return shown.get();
        }
    }
}
