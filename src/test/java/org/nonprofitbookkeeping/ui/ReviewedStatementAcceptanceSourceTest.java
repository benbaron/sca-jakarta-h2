package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReviewedStatementAcceptanceSourceTest
{
    @Test
    public void bankTransactionsOffersOnlyExplicitReviewedRowAcceptance() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/BankTransactionsPanel.java"));

        assertTrue(source.contains("Create Transaction from Reviewed Row…"));
        assertTrue(source.contains("acceptanceService.preview"));
        assertTrue(source.contains("acceptanceService.accept"));
        assertTrue(source.contains("ReviewedStatementAcceptanceDialog"));
        assertTrue(source.contains("DrillThroughCoordinator.openLedgerWithContext"));
        assertFalse(source.contains("em.persist"));
        assertFalse(source.contains("new Txn("));
        assertFalse(source.contains("new TxnSplit("));
        assertFalse(source.contains("autoPost"));
    }

    @Test
    public void acceptanceAuthorityUsesCanonicalTransactionEntryAndDurableAcceptedLink() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/service/ReviewedStatementAcceptanceService.java"));

        assertTrue(source.contains("transactionEntry.enter("));
        assertTrue(source.contains("setAcceptedTransaction(txn)"));
        assertTrue(source.contains("setStatus(BankStatementLine.Status.ACCEPTED)"));
        assertTrue(source.contains("BANK_STATEMENT_ROW_ACCEPTED"));
        assertTrue(source.contains("PESSIMISTIC_WRITE"));
        assertTrue(source.contains("PROBABLE_DUPLICATE"));
        assertTrue(source.contains("FINALIZED"));
        assertFalse(source.contains("setMatchedTransaction(txn)"));
        assertFalse(source.contains("setBankCleared(true)"));
    }

    @Test
    public void workspaceCompositionInjectsAcceptanceAuthority() throws Exception
    {
        String panelFactory = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/PanelFactory.java"));
        String workspaceServices = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/WorkspaceServices.java"));

        assertTrue(panelFactory.contains("services::reviewedStatementAcceptanceService"));
        assertTrue(panelFactory.contains("services::transactionReferenceDataService"));
        assertTrue(workspaceServices.contains("Supplier<ReviewedStatementAcceptanceService>"));
        assertTrue(workspaceServices.contains("Supplier<TransactionReferenceDataService>"));
    }
}
