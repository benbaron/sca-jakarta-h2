package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests canonical Journal context routing. */
public class DrillThroughCoordinatorTest
{
    @Test
    public void openLedgerWithContext_routesToJournalAndExposesContextOnce()
    {
        AtomicReference<AppPanelId> opened = new AtomicReference<>();
        DrillThroughCoordinator.configureOpener(opened::set);

        DrillThroughCoordinator.openLedgerWithContext("Report drill-through: Balance Sheet");

        assertEquals(AppPanelId.JOURNAL_PANE, opened.get());
        assertEquals("Report drill-through: Balance Sheet", DrillThroughCoordinator.consumeContext());
        assertEquals("Report drill-through: Balance Sheet", DrillThroughCoordinator.consumeContext(AppPanelId.JOURNAL_PANE));
        assertEquals("", DrillThroughCoordinator.consumeContext(AppPanelId.JOURNAL_PANE));
        assertEquals("", DrillThroughCoordinator.consumeContext());
    }

    @Test
    public void transactionEditorAliasStoresContextUnderJournal()
    {
        AtomicReference<AppPanelId> opened = new AtomicReference<>();
        DrillThroughCoordinator.configureOpener(opened::set);

        DrillThroughCoordinator.openPanelWithContext(AppPanelId.TXN_EDITOR, "Edit Txn #41");

        assertEquals(AppPanelId.JOURNAL_PANE, opened.get());
        assertEquals("Edit Txn #41", DrillThroughCoordinator.consumeContext(AppPanelId.LEDGER_REGISTER));
        assertEquals("", DrillThroughCoordinator.consumeContext(AppPanelId.JOURNAL_PANE));
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
