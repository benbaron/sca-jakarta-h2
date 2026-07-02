package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProductionWorkspaceCommandRoutingTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    public void shellRoutesTypedCloseAllTabsCommand()
    {
        ProductionWorkspaceWindow window = FxTestSupport.onFx(() -> new ProductionWorkspaceWindow(
                UserAppStateStore.create(),
                path -> Path.of("data/sca-ledger.mv.db")));

        FxTestSupport.onFx(() -> {
            window.openPanel(AppPanelId.LEDGER_REGISTER);
            assertTrue(window.panelHost().isOpen(AppPanelId.LEDGER_REGISTER));

            AppPanel.RunCommandResult result = window.executeCommand(AppCommand.CLOSE_ALL_TABS);

            assertTrue(result.handled());
            assertFalse(window.panelHost().isOpen(AppPanelId.LEDGER_REGISTER));
            assertEquals(AppPanelId.DASHBOARD, window.panelHost().activePanelId());
            return null;
        });
    }

    @Test
    public void panelHostRoutesTypedPostValidateCommand()
    {
        AppPanel.RunCommandResult result = FxTestSupport.onFx(() -> {
            PanelHost host = new PanelHost();
            host.show(AppPanelId.TXN_EDITOR);
            return host.runCommandActive(AppCommand.POST_VALIDATE);
        });

        assertTrue(result.handled());
        assertTrue(result.message().contains("delegated"));
    }
}
