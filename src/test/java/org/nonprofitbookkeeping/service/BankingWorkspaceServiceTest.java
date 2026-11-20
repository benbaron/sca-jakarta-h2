package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.model.TxnSplit;
import java.nio.file.Path;
import java.time.LocalDate;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class BankingWorkspaceServiceTest
{
    @TempDir Path directory;
    private static final LocalDate START = LocalDate.of(2026, 3, 1);
    private static final LocalDate END = LocalDate.of(2026, 3, 31);

    @Test
    void firstOpeningIsExplicitAndReviewedThenMatchingChangesOnlyClearingAndCompletionIsLocked()
    {
        try (Jpa jpa = new Jpa(directory.resolve("first")))
        {
            BankReconciliationMutationIntegrityTest.seed(jpa);
            var service = new BankReconciliationWorkspaceService(jpa);
            var session = service.start(start(3001, "-25.75"));
            long id = session.sessionId();
            assertFalse(service.opening(id).confirmed());
            assertThrows(IllegalStateException.class, () -> service.matchBankItem(id, 7001, 5001));
            assertThrows(IllegalStateException.class, () -> service.save(id, true));
            assertThrows(IllegalArgumentException.class, () -> service.confirmOpening(id, START, BigDecimal.ZERO, false));
            service.confirmOpening(id, START, BigDecimal.ZERO, true);
            var before = new BankingWorkspaceQuery(jpa).journal("SCA", 3001, END).get(0);
            var matched = service.matchBankItem(id, 7001, 5001);
            assertEquals(0, new BigDecimal("-25.75").compareTo(matched.balances().bookBalanceClearedOnly()));
            var after = new BankingWorkspaceQuery(jpa).journal("SCA", 3001, END).get(0);
            assertEquals(before.amount(), after.amount());
            assertTrue(after.cleared());
            assertEquals(LocalDate.of(2026, 3, 16), after.clearedOn());
            assertEquals(0, matched.balances().difference().signum());
            service.save(id, true);
            assertThrows(IllegalStateException.class, () -> service.unmatchBankItem(id, 7001, 5001));
            assertThrows(IllegalStateException.class, () -> service.confirmOpening(id, START, BigDecimal.ONE, true));
        }
    }

    @Test
    void fundAllocationsMatchAsOneInstrumentAndUnmatchRestoresTheWholeGroup()
    {
        try (Jpa jpa = new Jpa(directory.resolve("funds")))
        {
            BankReconciliationMutationIntegrityTest.seed(jpa);
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("insert into fund(id,company_id,code,name,fund_type) values(2002,200,'EVENT','Event Fund','UNRESTRICTED')").executeUpdate();
                em.createNativeQuery("update txn_split set amount_signed=-10 where id=5001").executeUpdate();
                em.createNativeQuery("insert into txn_split(id,txn_id,account_id,fund_id,amount_signed) values(5009,4001,1001,2002,-15.75)").executeUpdate();
                em.getTransaction().commit();
            }
            var service = new BankReconciliationWorkspaceService(jpa);
            long id = service.start(start(3001, "-25.75")).sessionId();
            service.confirmOpening(id, START, BigDecimal.ZERO, true);
            var items = new BankingWorkspaceQuery(jpa).journal("SCA", 3001, END);
            assertEquals(1, items.size());
            assertEquals(2, items.get(0).splitIds().size());
            service.matchBankItem(id, 7001, 5001);
            service.matchBankItem(id, 7001, 5009); // exact retry, including a different group anchor
            assertTrue(new BankingWorkspaceQuery(jpa).journal("SCA", 3001, END).get(0).cleared());
            service.unmatchBankItem(id, 7001, 5009);
            try (var em = jpa.em())
            {
                for (long split : new long[] {5001, 5009})
                {
                    var line = em.find(TxnSplit.class, split);
                    assertFalse(line.isBankCleared());
                    assertNull(line.getBankClearedOn());
                    assertNull(line.getMatchedBankStatementLine());
                }
            }
            assertTrue(service.load(id).statementEntries().stream().allMatch(line -> line.matchedLedgerSplitId() == null));
        }
    }

    @Test
    void outstandingItemsDoNotBlockAndLaterClearingDoesNotChangeAnEarlierPeriod()
    {
        try (Jpa jpa = new Jpa(directory.resolve("outstanding")))
        {
            BankReconciliationMutationIntegrityTest.seed(jpa);
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("insert into txn(id,company_id,txn_date,memo,status) values(4010,200,DATE '2026-02-25','Older outstanding','ENTERED')").executeUpdate();
                em.createNativeQuery("insert into txn_split(id,txn_id,account_id,fund_id,amount_signed) values(5010,4010,1001,2001,-10),(5011,4010,1002,2001,10)").executeUpdate();
                em.getTransaction().commit();
            }
            var service = new BankReconciliationWorkspaceService(jpa);
            long id = service.start(start(3001, "-25.75")).sessionId();
            service.confirmOpening(id, START, BigDecimal.ZERO, true);
            assertEquals(0, service.opening(id).bookOpening().compareTo(new BigDecimal("-10")));
            service.matchBankItem(id, 7001, 5001);
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                var old = em.find(TxnSplit.class, 5010L);
                old.setBankCleared(true);
                old.setBankClearedOn(LocalDate.of(2026, 4, 15));
                em.getTransaction().commit();
            }
            var saved = service.save(id, true);
            assertEquals(0, saved.balances().bookBalanceAllTransactions().compareTo(new BigDecimal("-35.75")));
            assertEquals(0, saved.balances().bookBalanceClearedOnly().compareTo(new BigDecimal("-25.75")));
            long successor = service.start(new BankReconciliationWorkspaceService.StartCommand("SCA", 3001L, LocalDate.of(2026, 4,
                                           30), new BigDecimal("-25.75"), null, null)).sessionId();
            assertTrue(service.opening(successor).confirmed());
            assertEquals(id, service.opening(successor).sourceSessionId());
            assertEquals(END, service.opening(successor).sourceEndingDate());
            assertEquals(0, service.opening(successor).clearedOpening().compareTo(new BigDecimal("-25.75")));
        }
    }

    @Test
    void differentAccountsNeverShareMatchesOrOpeningsAndRejectedMismatchLeavesNoWrites()
    {
        try (Jpa jpa = new Jpa(directory.resolve("scope")))
        {
            BankReconciliationMutationIntegrityTest.seed(jpa);
            var service = new BankReconciliationWorkspaceService(jpa);
            long checking = service.start(start(3001, "-25.75")).sessionId();
            long savings = service.start(start(3002, "-7")).sessionId();
            service.confirmOpening(checking, START, BigDecimal.ZERO, true);
            assertFalse(service.opening(savings).confirmed());
            assertThrows(IllegalArgumentException.class, () -> service.matchBankItem(checking, 7002, 5001));
            assertThrows(IllegalArgumentException.class, () -> service.matchBankItem(checking, 7001, 5005));
            assertFalse(new BankingWorkspaceQuery(jpa).journal("SCA", 3001, END).get(0).cleared());
            assertTrue(service.load(checking).statementEntries().stream().allMatch(line->line.matchedLedgerSplitId() == null));
        }
    }

    @Test
    void referenceEditsPreserveFundAllocationsMoneyAndInformationAndRejectStaleOrClearedWrites()
    {
        try (Jpa jpa = new Jpa(directory.resolve("references")))
        {
            BankReconciliationMutationIntegrityTest.seed(jpa);
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("insert into fund(id,company_id,code,name,fund_type) values(2002,200,'EVENT','Event Fund','UNRESTRICTED')").executeUpdate();
                em.createNativeQuery("update txn_split set amount_signed=-10 where id=5001").executeUpdate();
                em.createNativeQuery("insert into txn_split(id,txn_id,account_id,fund_id,amount_signed) values(5009,4001,1001,2002,-15.75)").executeUpdate();
                em.createNativeQuery("update txn set transfer_or_check_reference='Header-42',source_statement_date=DATE '2026-03-18' where id=4001").executeUpdate();
                em.getTransaction().commit();
            }
            var service = new TransactionEntryService(jpa, ()->"SCA");
            service.updateBankingReference(3001, 5001, "", "000042");
            var query = new BankingWorkspaceQuery(jpa);
            var item = query.journal("SCA", 3001, END).get(0);
            assertEquals("000042", item.reference());
            assertEquals(2, item.splitIds().size());
            assertEquals(0, new BigDecimal("-25.75").compareTo(item.amount()));
            assertEquals("Header-42", service.load(4001).information().transferOrCheckReference());
            assertEquals(LocalDate.of(2026, 3, 18), service.load(4001).information().statementDate());
            assertThrows(PostingException.class, ()->service.updateBankingReference(3001, 5001, "", "stale"));
            assertThrows(PostingException.class, ()->service.updateBankingReference(3002, 5001, "000042", "wrong-bank"));
            var reconciliation = new BankReconciliationWorkspaceService(jpa);
            long id = reconciliation.start(start(3001, "-25.75")).sessionId();
            reconciliation.confirmOpening(id, START, BigDecimal.ZERO, true);
            reconciliation.matchBankItem(id, 7001, 5001);
            assertThrows(PostingException.class, ()->service.updateBankingReference(3001, 5001, "000042", "changed"));
            assertEquals("000042", query.journal("SCA", 3001, END).get(0).reference());
        }
    }

    @Test
    void transfersClearIndependentlyAndWrongAmountRollsBackWithoutPartialFundClearing()
    {
        try (Jpa jpa = new Jpa(directory.resolve("transfers")))
        {
            BankReconciliationMutationIntegrityTest.seed(jpa);
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("insert into txn(id,company_id,txn_date,memo,status) values(4020,200,DATE '2026-03-10','Interbank transfer','ENTERED')").executeUpdate();
                em.createNativeQuery("insert into txn_split(id,txn_id,account_id,fund_id,amount_signed) values(5020,4020,1001,2001,-200),(5021,4020,1003,2001,200)").executeUpdate();
                em.createNativeQuery("insert into bank_statement_line(id,batch_id,company_id,bank_account_id,source_row_number,deterministic_fingerprint,transaction_date,posted_date,amount,status) values(7020,6001,200,3001,2,'transfer-out',DATE '2026-03-15',DATE '2026-03-16',-200,'IMPORTED'),(7021,6002,200,3002,2,'transfer-in',DATE '2026-03-15',DATE '2026-03-17',200,'IMPORTED')").executeUpdate();
                em.getTransaction().commit();
            }
            var service = new BankReconciliationWorkspaceService(jpa);
            long checking = service.start(start(3001, "-225.75")).sessionId();
            long savings = service.start(start(3002, "193")).sessionId();
            service.confirmOpening(checking, START, BigDecimal.ZERO, true);
            service.confirmOpening(savings, START, BigDecimal.ZERO, true);
            assertThrows(IllegalArgumentException.class, ()->service.matchBankItem(checking, 7001, 5020));
            service.matchBankItem(checking, 7020, 5020);
            try (var em = jpa.em())
            {
                assertTrue(em.find(TxnSplit.class, 5020L).isBankCleared());
                assertFalse(em.find(TxnSplit.class, 5021L).isBankCleared());
            }
            service.matchBankItem(savings, 7021, 5021);
            try (var em = jpa.em())
            {
                assertEquals(LocalDate.of(2026, 3, 16), em.find(TxnSplit.class, 5020L).getBankClearedOn());
                assertEquals(LocalDate.of(2026, 3, 17), em.find(TxnSplit.class, 5021L).getBankClearedOn());
            }
            service.unmatchBankItem(checking, 7020, 5020);
            try (var em = jpa.em())
            {
                assertFalse(em.find(TxnSplit.class, 5020L).isBankCleared());
                assertTrue(em.find(TxnSplit.class, 5021L).isBankCleared());
            }
        }
    }

    @Test
    void zeroDifferenceCannotHideUnmatchedOffsettingStatementsAndInactiveHistoryRemainsReadable()
    {
        try (Jpa jpa = new Jpa(directory.resolve("completion")))
        {
            BankReconciliationMutationIntegrityTest.seed(jpa);
            var service = new BankReconciliationWorkspaceService(jpa);
            long id = service.start(start(3001, "0")).sessionId();
            service.confirmOpening(id, START, BigDecimal.ZERO, true);
            service.addManualLine(new BankReconciliationWorkspaceService.ManualStatementLineCommand(id, END,
                                  new BigDecimal("25.75"), "Offset", "offset"));
            assertEquals(0, service.load(id).balances().difference().signum());
            assertThrows(IllegalStateException.class, ()->service.save(id, true));
            assertThrows(IllegalArgumentException.class, ()->service.confirmOpening(id, START, new BigDecimal("0.001"), true));
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("update company_bank_account set is_active=false where id=3001").executeUpdate();
                em.getTransaction().commit();
            }
            assertEquals(id, service.load(id).sessionId());
            assertThrows(IllegalStateException.class, ()->service.save(id, false));
            assertFalse(new BankingWorkspaceQuery(jpa).accounts("SCA",
                        END).stream().filter(row->row.id() == 3001).findFirst().orElseThrow().active());
        }
    }

    @Test
    void manualStatementRemovalRetainsSourceAndAuditAndCannotBypassMatchProtection()
    {
        try (Jpa jpa = new Jpa(directory.resolve("manual-removal")))
        {
            BankReconciliationMutationIntegrityTest.seed(jpa);
            var service = new BankReconciliationWorkspaceService(jpa);
            long id = service.start(start(3001, "-25.75")).sessionId();
            service.confirmOpening(id, START, BigDecimal.ZERO, true);
            var snapshot = service.addManualLine(new BankReconciliationWorkspaceService.ManualStatementLineCommand(id, END,
                                                 new BigDecimal("4.00"), "Mistaken manual fee", "manual"));
            long manual = snapshot.statementEntries().stream().filter(
                              BankReconciliationWorkspaceService.StatementEntryView::manual).findFirst().orElseThrow().statementLineId();
            assertThrows(IllegalStateException.class, ()->service.removeManualLine(id, 7001));
            service.removeManualLine(id, manual);
            assertEquals(1, service.load(id).statementEntries().size());
            try (var em = jpa.em())
            {
                assertEquals("REJECTED", em.find(org.nonprofitbookkeeping.model.BankStatementLine.class, manual).getStatus().name());
                assertEquals(1L, em.createQuery("select count(a) from AuditEvent a where a.actionType='MANUAL_STATEMENT_REMOVED'",
                                                Long.class).getSingleResult());
                em.getTransaction().begin();
                em.createNativeQuery("update bank_statement_line set status='ERROR' where id=7001").executeUpdate();
                em.getTransaction().commit();
            }
            assertEquals(1, service.load(id).statementEntries().size(), "Source import errors remain visible and block completion");
            assertThrows(IllegalStateException.class, ()->service.saveStatement(id, BigDecimal.ZERO, true));
        }
    }

    private static BankReconciliationWorkspaceService.StartCommand start(long bank, String ending)
    {
        return new BankReconciliationWorkspaceService.StartCommand("SCA", bank, END, new BigDecimal(ending),
                BankReconciliationWorkspaceService.ClearedStatePolicy.OVERWRITE_LEDGER_CLEARED_STATE, null);
    }
}
