package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
