package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P05-C8 authority guardrails for Bank Transactions. */
class BankLedgerActivitySourceTest
{
    @Test
    void bankTransactionsSeparatesCanonicalLedgerActivityFromStatementReview() throws Exception
    {
        String panel = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/BankTransactionsPanel.java"));
        String repository = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/repository/JpaLedgerQueryRepository.java"));

        assertTrue(panel.contains("new Tab(\"Ledger Activity\""));
        assertTrue(panel.contains("new Tab(\"Statement Review\""));
        assertTrue(panel.indexOf("new Tab(\"Ledger Activity\"")
                < panel.indexOf("new Tab(\"Statement Review\""));
        assertTrue(panel.contains("ledgerQuery.listBankLedgerActivity("));
        assertTrue(panel.contains("BankReviewQueryService.ReviewRow"));
        assertTrue(panel.contains("Create Transaction from Reviewed Row…"));
        assertTrue(panel.contains("Drill to Journal"));
        assertTrue(panel.contains("CompanySplitPaneStateBinder.bind(splitPane, \"bank-transactions-ledger-activity\""));
        assertTrue(panel.contains("CompanySplitPaneStateBinder.bind(splitPane, \"bank-transactions-statement-review\""));
        assertFalse(panel.contains("BankTransactionRecord"));

        assertTrue(repository.contains("from TxnSplit s"));
        assertTrue(repository.contains("join CompanyBankAccount cba"));
        assertTrue(repository.contains("a.accountType = :assetType"));
        assertTrue(repository.contains("a.accountFunction = :bankFunction"));
        assertTrue(repository.contains("a.normalBalance = :debitNormal"));
        assertTrue(repository.contains("s.bankCleared, s.bankClearedOn"));
        assertFalse(repository.contains("BankStatementLine"));
    }
}
