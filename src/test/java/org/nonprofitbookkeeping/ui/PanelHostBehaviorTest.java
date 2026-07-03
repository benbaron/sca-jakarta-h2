package org.nonprofitbookkeeping.ui;

import javafx.scene.control.Tab;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
}
