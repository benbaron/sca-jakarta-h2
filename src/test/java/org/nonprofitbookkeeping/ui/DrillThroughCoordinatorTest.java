package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests canonical Journal context routing. */
@org.junit.jupiter.api.parallel.ResourceLock("ui-service-registry")
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
    @Test
    void retiredBankingRoutesShareTheWorkspaceAndRetainExactSessionOrSubpaneIntent()
    {
        AtomicReference<AppPanelId> opened=new AtomicReference<>();
        DrillThroughCoordinator.configureOpener(opened::set);
        String exact=BankImportNavigationContext.forReconciliationSession(42);
        DrillThroughCoordinator.openPanelWithContext(AppPanelId.RECONCILIATION_RUNS,exact);
        assertEquals(AppPanelId.BANKING,opened.get());
        assertEquals(exact,DrillThroughCoordinator.consumeContext(AppPanelId.BANKING));
        DrillThroughCoordinator.openPanelWithContext(AppPanelId.RECONCILIATION_RUNS,"Dashboard: reconciliation");
        assertEquals("banking:reconcile",DrillThroughCoordinator.consumeContext(AppPanelId.BANKING));
        DrillThroughCoordinator.openPanelWithContext(AppPanelId.BANK_TRANSACTIONS,"durable review rows");
        assertEquals("banking:statements",DrillThroughCoordinator.consumeContext(AppPanelId.BANKING));
        assertEquals(AppPanelId.BANKING,AppPanelId.canonical(AppPanelId.BANK_TRANSACTIONS));
    }

    @Test
    void importAddIsLiteralScopedAndConsumedOnce()
    {
        var session = MainWindow.sharedSessionState();
        var database = session.databaseSelection();
        var company = session.multiCompany();
        try
        {
            AtomicReference<AppPanelId> opened = new AtomicReference<>();
            DrillThroughCoordinator.configureOpener(opened::set);
            String literal = "January's Coronation / MiXeD & café #2";
            DrillThroughCoordinator.openForImportAdd(AppPanelId.BUDGET_CATEGORIES, literal);
            assertEquals(AppPanelId.BUDGET_CATEGORIES, opened.get());
            var handoff = DrillThroughCoordinator.consumeImportAdd(AppPanelId.BUDGET_CATEGORIES);
            assertEquals(literal, handoff.name());
            assertTrue(handoff.matchesCurrentScope());
            org.junit.jupiter.api.Assertions.assertNull(DrillThroughCoordinator.consumeImportAdd(AppPanelId.BUDGET_CATEGORIES));
            session.setMultiCompany(new org.nonprofitbookkeeping.model.MultiCompanyState("OTHER", java.util.List.of("OTHER")));
            org.junit.jupiter.api.Assertions.assertFalse(handoff.matchesCurrentScope());
            session.setMultiCompany(company);
            session.setDatabaseSelection(new org.nonprofitbookkeeping.model.DatabaseSelectionState("other-database", java.util.List.of("other-database")));
            org.junit.jupiter.api.Assertions.assertFalse(handoff.matchesCurrentScope());
        }
        finally
        {
            session.setDatabaseSelection(database);
            session.setMultiCompany(company);
            DrillThroughCoordinator.configureOpener(null);
        }
    }

    @Test
    void newerAddOrdinaryNavigationAndSessionCloseReplaceThePendingHandoff()
    {
        try
        {
            DrillThroughCoordinator.configureOpener(id -> {});
            DrillThroughCoordinator.openForImportAdd(AppPanelId.FUNDS, "First");
            DrillThroughCoordinator.openForImportAdd(AppPanelId.FUNDS, "Latest");
            assertEquals("Latest", DrillThroughCoordinator.consumeImportAdd(AppPanelId.FUNDS).name());
            DrillThroughCoordinator.openForImportAdd(AppPanelId.FUNDS, "Stale");
            DrillThroughCoordinator.openPanelWithContext(AppPanelId.FUNDS, "ordinary navigation");
            org.junit.jupiter.api.Assertions.assertNull(DrillThroughCoordinator.consumeImportAdd(AppPanelId.FUNDS));
            DrillThroughCoordinator.openForImportAdd(AppPanelId.FUNDS, "Previous session");
            DrillThroughCoordinator.configureOpener(null);
            org.junit.jupiter.api.Assertions.assertNull(DrillThroughCoordinator.consumeImportAdd(AppPanelId.FUNDS));
        }
        finally
        {
            DrillThroughCoordinator.configureOpener(null);
        }
    }

}
