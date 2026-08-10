package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalClearedStateSourceTest
{
    @Test
    void journalRendersServiceProjectionAndOffersExactReconciliationDrillThrough() throws Exception
    {
        String journal = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/JournalWorkspacePanel.java"));
        String service = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/service/TransactionEntryService.java"));
        String reconciliation = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ReconciliationRunsPanel.java"));

        assertTrue(service.contains("split.isBankCleared()"));
        assertTrue(service.contains("split.getBankClearedOn()"));
        assertTrue(service.contains("reconciliationSessions.get(split.getId())"));
        assertTrue(service.contains("s.company_id = t.company_id"));
        assertTrue(service.contains("cba.account_id = ts.account_id"));
        assertTrue(journal.contains("view.clearedState().displayText()"));
        assertTrue(journal.contains("line.clearedDisplay()"));
        assertTrue(journal.contains("line.bankClearedOn()"));
        assertTrue(journal.contains("Open Selected Line Reconciliation"));
        assertTrue(journal.contains("BankImportNavigationContext.forReconciliationSession(sessionId)"));
        assertTrue(reconciliation.contains("parseReconciliationSession(context)"));
        assertFalse(journal.contains("return view.bankAccountId() == null ? \"\" : \"Uncleared\""));
    }
}
