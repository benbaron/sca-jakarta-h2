package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.TransactionInformation;
import org.nonprofitbookkeeping.model.InterchangeIdentity;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.UserManualProcedureFixture.*;

@DisplayName("Manual: Company setup → Journal → Banking → Reports")
class UserManualBookkeepingProcedureTest
{
    @TempDir Path directory;

    @Test
    void setUpCompanyThenEnterIncomeExpenseAndBankTransferAndVerifyReports() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("setup")))
        {
            var f = setUp(jpa, directory);
            var company = new CompanyAdminService(jpa).requireActiveCompany(COMPANY);
            assertEquals("Manual Example Company", company.displayName());
            assertEquals(7, company.fiscalYearStartMonth());
            assertEquals("USD", company.defaultCurrency());
            assertTrue(new CompanyAdminService(jpa).listCompanyCharts(f.company()).stream()
                    .anyMatch(c -> c.id() == f.chart() && c.activeForCompany()));
            assertEquals(0, count(jpa, "Txn"), "Company/chart/Fund/bank setup does not post opening entries");

            f.enter(jpa, START, "Receive income", f.checking(), f.income(), "100.00");
            f.enter(jpa, START.plusDays(1), "Pay expense", f.expense(), f.checking(), "50.00");
            f.enter(jpa, START.plusDays(2), "Move money", f.savings(), f.checking(), "100.00");
            assertEquals(3, count(jpa, "Txn"));
            var bank = new BankingWorkspaceQuery(jpa);
            money("-50", bank.accounts(COMPANY, END).stream()
                    .filter(a -> a.id() == f.checkingBank()).findFirst().orElseThrow().bookBalance());
            money("100", bank.accounts(COMPANY, END).stream()
                    .filter(a -> a.id() == f.savingsBank()).findFirst().orElseThrow().bookBalance());
            assertEquals(3, bank.journal(COMPANY, f.checkingBank(), END).size());
            assertEquals(1, bank.journal(COMPANY, f.savingsBank(), END).size());
            var reports = new FinancialReportService(jpa, () -> COMPANY);
            var trial = reports.trialBalance(END, null);
            assertTrue(trial.isBalanced());
            money("150", trial.totalDebits());
            money("50", reports.incomeStatement(START, END, null).netIncome());
            assertTrue(new FinancialReportService(jpa, () -> "DEFAULT").trialBalance(END, null).rows().isEmpty());
        }
    }

    @Test
    void manualStatementExampleUsesReviewedBankOpeningAndCarriesOutstandingJournalIntoNextPeriod() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("manual-statement")))
        {
            var f = setUp(jpa, directory);
            f.enter(jpa, START.minusDays(10), "Reviewed opening books", f.checking(), f.equity(), "1000");
            f.enter(jpa, START.minusDays(2), "Unpresented check", f.expense(), f.checking(), "100");
            var deposit = f.enter(jpa, START.plusDays(4), "Deposit", f.checking(), f.income(), "300");
            var withdrawal = f.enter(jpa, START.plusDays(5), "Withdrawal", f.expense(), f.checking(), "50");
            var reconciliation = new BankReconciliationWorkspaceService(jpa);
            long session = start(reconciliation, f.checkingBank(), "1250");
            assertFalse(reconciliation.opening(session).confirmed());
            assertThrows(IllegalStateException.class, () -> reconciliation.save(session, true));
            long before = count(jpa, "Txn");
            reconciliation.confirmOpening(session, START, new BigDecimal("1000"), true);
            money("900", reconciliation.opening(session).bookOpening());
            money("1000", reconciliation.opening(session).clearedOpening());
            assertEquals(before, count(jpa, "Txn"));
            long depositRow = manual(reconciliation, session, START.plusDays(8), "300", "DEP-1");
            long withdrawalRow = manual(reconciliation, session, START.plusDays(9), "-50", "CHK-1");
            assertEquals(before, count(jpa, "Txn"), "Manual statement rows are bank facts, not Journal entries");
            var query = new BankingWorkspaceQuery(jpa);
            reconciliation.matchBankItem(session, depositRow, item(query, f.checkingBank(), deposit.id()).anchorSplitId());
            reconciliation.matchBankItem(session, withdrawalRow, item(query, f.checkingBank(), withdrawal.id()).anchorSplitId());
            money("0", reconciliation.load(session).balances().difference());
            money("1150", reconciliation.load(session).balances().bookBalanceAllTransactions());
            money("1250", reconciliation.load(session).balances().bookBalanceClearedOnly());
            assertEquals(BankReconciliationWorkspaceService.SessionStatus.FINALIZED, reconciliation.save(session, true).status());
            assertThrows(IllegalStateException.class, () -> reconciliation.unmatchBankItem(session, depositRow,
                    item(query, f.checkingBank(), deposit.id()).anchorSplitId()));

            long next = reconciliation.start(new BankReconciliationWorkspaceService.StartCommand(COMPANY,
                    f.checkingBank(), LocalDate.of(2026, 4, 30), new BigDecimal("1250"), null, null)).sessionId();
            assertEquals(session, reconciliation.opening(next).sourceSessionId());
            money("1250", reconciliation.opening(next).clearedOpening());
            money("1150", reconciliation.opening(next).bookOpening());
            assertFalse(query.journal(COMPANY, f.checkingBank(), END).stream()
                    .filter(i -> i.description().equals("Unpresented check")).findFirst().orElseThrow().cleared());
            assertEquals(before, count(jpa, "Txn"), "Opening confirmation and clearing never change journal amounts/counts");
        }
    }

    @Test
    void transferBetweenTwoBanksIsReconciledIndependentlyAtEachBank() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("two-banks")))
        {
            var f = setUp(jpa, directory);
            f.enter(jpa, START.minusDays(1), "Checking opening", f.checking(), f.equity(), "1000");
            f.enter(jpa, START.minusDays(1), "Savings opening", f.savings(), f.equity(), "500");
            var transfer = f.enter(jpa, START.plusDays(2), "Interbank transfer", f.savings(), f.checking(), "100");
            var service = new BankReconciliationWorkspaceService(jpa);
            long checking = start(service, f.checkingBank(), "900");
            long savings = start(service, f.savingsBank(), "600");
            service.confirmOpening(checking, START, new BigDecimal("1000"), true);
            assertFalse(service.opening(savings).confirmed());
            service.confirmOpening(savings, START, new BigDecimal("500"), true);
            long out = manual(service, checking, START.plusDays(4), "-100", "TRANSFER");
            long in = manual(service, savings, START.plusDays(6), "100", "TRANSFER");
            var query = new BankingWorkspaceQuery(jpa);
            long checkingSplit = item(query, f.checkingBank(), transfer.id()).anchorSplitId();
            long savingsSplit = item(query, f.savingsBank(), transfer.id()).anchorSplitId();
            assertThrows(IllegalArgumentException.class, () -> service.matchBankItem(checking, in, checkingSplit));
            assertThrows(IllegalArgumentException.class, () -> service.matchBankItem(checking, out, savingsSplit));
            service.matchBankItem(checking, out, checkingSplit);
            assertFalse(item(query, f.savingsBank(), transfer.id()).cleared());
            service.save(checking, true);
            assertThrows(IllegalStateException.class, () -> service.save(savings, true));
            service.matchBankItem(savings, in, savingsSplit);
            service.save(savings, true);
            assertEquals(START.plusDays(4), item(query, f.checkingBank(), transfer.id()).clearedOn());
            assertEquals(START.plusDays(6), item(query, f.savingsBank(), transfer.id()).clearedOn());
            assertEquals(3, count(jpa, "Txn"));
        }
    }

    @Test
    void informationalClearedDateDoesNotClearJournalAndInvalidSaveCanBeCorrectedWithoutDuplicates() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("journal-save")))
        {
            var f = setUp(jpa, directory);
            var journal = f.journal(jpa);
            var info = new TransactionInformation("TRANSFER-42", START.plusDays(4), START.minusDays(1));
            var invalid = new TransactionCommand(START, null, "Correctable draft", null,
                    List.of(line(f.checking(), f.general(), "100", "0"), line(f.income(), f.general(), "0", "99.99")), List.of(), info);
            assertThrows(PostingException.class, () -> journal.enter(invalid));
            assertEquals(0, count(jpa, "Txn"));
            assertEquals(0, count(jpa, "TxnSplit"));
            var saved = journal.enter(new TransactionCommand(START, null, invalid.memo(), null,
                    List.of(line(f.checking(), f.general(), "100", "0"), line(f.income(), f.general(), "0", "100")), List.of(), info));
            assertEquals(info, journal.load(saved.id()).information());
            assertEquals(START, journal.load(saved.id()).date());
            var item = item(new BankingWorkspaceQuery(jpa), f.checkingBank(), saved.id());
            assertFalse(item.cleared());
            assertNull(item.clearedOn());
            assertEquals("", item.reference(), "Bank instruments have their own payment reference");
            assertEquals(1, count(jpa, "Txn"));
        }
    }

    @Test
    void changingActiveChartDoesNotMoveHistoryOrRelinkExistingBankAccount() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("chart-identity")))
        {
            var f = setUp(jpa, directory);
            var original = f.enter(jpa, START, "Old chart income", f.checking(), f.income(), "100");
            long newChart = importChart(jpa, directory.resolve("replacement.json"), "Replacement Chart");
            new CompanyAdminService(jpa).assignActiveChart(f.company(), newChart);
            long newChecking = accountId(jpa, newChart, "1000");
            long newIncome = accountId(jpa, newChart, "4000");
            assertNotEquals(f.checking(), newChecking);
            var fresh = f.enter(jpa, START, "New chart income", newChecking, newIncome, "25");
            var query = new BankingWorkspaceQuery(jpa);
            assertEquals(List.of(original.id()), query.journal(COMPANY, f.checkingBank(), END).stream()
                    .map(BankingWorkspaceQuery.BankItem::transactionId).toList());
            var linked = query.accounts(COMPANY, END).stream().filter(a -> a.id() == f.checkingBank()).findFirst().orElseThrow();
            assertEquals(f.checking(), linked.ledgerId());
            assertTrue(linked.chart().contains("Manual Chart"));
            assertEquals(f.checking(), f.journal(jpa).load(original.id()).lines().get(0).accountId());
            assertEquals(newChecking, f.journal(jpa).load(fresh.id()).lines().get(0).accountId());
        }
    }

    @Test
    void importingAnotherChartPreservesLegacyUnscopedAccountReceipts() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("legacy-chart-receipts")))
        {
            var f = setUp(jpa, directory);
            List<InterchangeIdentity> legacy;
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                legacy = em.createQuery("from InterchangeIdentity i where i.formatCode='COA_JSON' and i.entityType='ACCOUNT'",
                        InterchangeIdentity.class).getResultList();
                assertEquals(2, legacy.size());
                for (var identity : legacy)
                {
                    // Reproduce receipts written by the released importer before chart scoping.
                    String source = identity.getSourceSystem();
                    identity.setSourceSystem(source.substring(0, source.indexOf("/chart/")));
                }
                em.getTransaction().commit();
            }
            long replacement = importChart(jpa, directory.resolve("new-chart.json"), "Additional Chart");
            assertNotEquals(f.chart(), replacement);
            try (var em = jpa.em())
            {
                for (var before : legacy)
                {
                    var after = em.find(InterchangeIdentity.class, before.getId());
                    assertEquals(before.getSourceSystem(), after.getSourceSystem());
                    assertEquals(before.getExternalId(), after.getExternalId());
                    assertEquals(before.getLocalEntityId(), after.getLocalEntityId());
                    assertEquals(before.getNormalizedContentHash(), after.getNormalizedContentHash());
                    assertEquals(before.getUpdatedAt(), after.getUpdatedAt());
                }
            }
            assertEquals(2, new CompanyAdminService(jpa).listCompanyCharts(f.company()).size());
            assertTrue(new CompanyAdminService(jpa).listCompanyCharts(f.company()).stream()
                    .anyMatch(c -> c.id() == f.chart() && c.activeForCompany()), "Import does not activate the new chart");
        }
    }

    @Test
    void reportsRespectCutoffRangeFundCompanyAndExportsDoNotChangeJournal() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("reports")))
        {
            var f = setUp(jpa, directory);
            f.enter(jpa, START.minusDays(1), "Prior opening", f.checking(), f.equity(), "1000");
            f.enter(jpa, START, "Current income", f.checking(), f.income(), "300");
            f.enter(jpa, START.plusDays(1), "Current expense", f.expense(), f.checking(), "50");
            f.journal(jpa).enter(new TransactionCommand(START, null, "Designated income", null, List.of(
                    line(f.checking(), f.designated(), "20", "0"), line(f.income(), f.designated(), "0", "20"))));
            f.enter(jpa, END.plusDays(1), "Next period income", f.checking(), f.income(), "700");
            var reports = new FinancialReportService(jpa, () -> COMPANY);
            money("1000", reports.trialBalance(START.minusDays(1), null).totalDebits());
            money("270", reports.incomeStatement(START, END, null).netIncome());
            money("250", reports.incomeStatement(START, END, "GENERAL").netIncome());
            money("20", reports.incomeStatement(START, END, "DESIGNATED").netIncome());
            assertEquals(6, reports.generalLedgerDetail(START, END, null, 100).size());
            var trial = reports.trialBalance(END, null);
            assertTrue(trial.isBalanced());
            money("1320", trial.totalDebits());
            String csv = FinancialReportRenderer.renderTrialBalanceCsv(trial);
            String text = FinancialReportRenderer.renderTrialBalanceText(trial);
            assertTrue(csv.contains("Operating Checking"));
            assertTrue(text.contains("Operating Checking"));
            assertEquals(5, count(jpa, "Txn"));
            assertTrue(new FinancialReportService(jpa, () -> "DEFAULT").incomeStatement(START, END, null).income().isEmpty());
        }
    }

    private static long start(BankReconciliationWorkspaceService service, long bank, String ending)
    {
        return service.start(new BankReconciliationWorkspaceService.StartCommand(COMPANY, bank, END,
                new BigDecimal(ending), null, null)).sessionId();
    }

    private static long manual(BankReconciliationWorkspaceService service, long session, LocalDate date, String amount, String reference)
    {
        return service.addManualLine(new BankReconciliationWorkspaceService.ManualStatementLineCommand(
                session, date, new BigDecimal(amount), reference, reference)).statementEntries().stream()
                .filter(s -> reference.equals(s.reference())).findFirst().orElseThrow().statementLineId();
    }

    private static BankingWorkspaceQuery.BankItem item(BankingWorkspaceQuery query, long bank, long transaction)
    {
        return query.journal(COMPANY, bank, END).stream().filter(i -> i.transactionId() == transaction).findFirst().orElseThrow();
    }

    static void money(String expected, BigDecimal actual)
    {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "Expected " + expected + ", got " + actual);
    }
}
