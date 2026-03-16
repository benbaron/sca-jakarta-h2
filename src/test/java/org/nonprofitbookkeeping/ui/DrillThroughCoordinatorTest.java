package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DrillThroughCoordinatorTest component.
 */
public class DrillThroughCoordinatorTest
{
    @Test
    public void openLedgerWithContext_invokesOpenerAndExposesContextOnce()
    {
        AtomicReference<AppPanelId> opened = new AtomicReference<>();
        DrillThroughCoordinator.configureOpener(opened::set);

        DrillThroughCoordinator.openLedgerWithContext("Report drill-through: Balance Sheet");

        assertEquals(AppPanelId.LEDGER_REGISTER, opened.get());
        assertEquals("Report drill-through: Balance Sheet", DrillThroughCoordinator.consumeContext());
        assertEquals("", DrillThroughCoordinator.consumeContext());
    }

    @Test
    public void openPanelWithContext_scopesContextByPanel()
    {
        AtomicReference<AppPanelId> opened = new AtomicReference<>();
        DrillThroughCoordinator.configureOpener(opened::set);

        DrillThroughCoordinator.openPanelWithContext(AppPanelId.CHART_OF_ACCOUNTS, "Check duplicate account codes");

        assertEquals(AppPanelId.CHART_OF_ACCOUNTS, opened.get());
        assertEquals("Check duplicate account codes", DrillThroughCoordinator.consumeContext(AppPanelId.CHART_OF_ACCOUNTS));
        assertEquals("", DrillThroughCoordinator.consumeContext(AppPanelId.CHART_OF_ACCOUNTS));
        assertTrue(DrillThroughCoordinator.consumeContext(AppPanelId.FUNDS).isBlank());
    }
}
