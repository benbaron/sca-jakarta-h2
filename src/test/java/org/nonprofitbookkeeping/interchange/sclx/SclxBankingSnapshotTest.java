package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.ImportIssue;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxBankingSnapshotTest
{
    @Test
    void exportsConfigurationReviewedFactsClearanceAndReconciliation()
    {
        Fixture fixture = fixture("ALPHA");
        Bank bank = bank(fixture.company(), "Outlands Credit Union");
        CompanyBankAccount bankAccount = bankAccount(fixture.company(), bank, fixture.cash());
        BankImportBatch batch = batch(fixture.company(), bankAccount);
        BankStatementLine statementLine = statementLine(
                fixture.company(), bankAccount, batch, fixture.transaction());
        ImportIssue issue = issue(batch, statementLine);
        fixture.cashLine().setBankCleared(true);
        fixture.cashLine().setBankClearedOn(LocalDate.of(2026, 7, 31));
        fixture.cashLine().setMatchedBankStatementLine(statementLine);

        UUID sessionId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        SclxBankingSnapshot banking = new SclxBankingSnapshot(
                List.of(bank),
                List.of(bankAccount),
                List.of(batch),
                List.of(statementLine),
                List.of(issue),
                List.of(new SclxBankingSnapshot.ReconciliationSession(
                        sessionId,
                        bankAccount.getPortableId(),
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 31),
                        new BigDecimal("1125.0000"),
                        "WARN_ONLY",
                        "FINALIZED",
                        "July statement",
                        new BigDecimal("1000.0000"),
                        new BigDecimal("1125.0000"),
                        new BigDecimal("1125.0000"),
                        BigDecimal.ZERO,
                        Instant.parse("2026-08-01T01:00:00Z"),
                        Instant.parse("2026-08-01T01:30:00Z"))),
                List.of(new SclxBankingSnapshot.ReconciliationMatch(
                        UUID.fromString("77777777-7777-7777-7777-777777777777"),
                        sessionId,
                        statementLine.getPortableId(),
                        null,
                        "MATCHED",
                        "Exact statement match",
                        Instant.parse("2026-08-01T01:10:00Z"),
                        Instant.parse("2026-08-01T01:10:00Z"))));

        SclxExportDocument document = new SclxCoreSnapshotAssembler().assemble(
                fixture.company(),
                fixture.accounts(),
                List.of(fixture.fund()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(fixture.transaction()),
                fixture.splits(),
                List.of(),
                banking,
                Instant.parse("2026-08-01T02:00:00Z"));

        SclxBankConfigurationExtension.Data configuration =
                SclxBankConfigurationExtension.data(document.extensions());
        SclxBankStatementFactsExtension.Data facts =
                SclxBankStatementFactsExtension.data(document.extensions());
        SclxReconciliationExtension.Data reconciliation =
                SclxReconciliationExtension.data(document.extensions());

        assertEquals(1, configuration.banks().size());
        assertEquals("Outlands Credit Union", configuration.banks().get(0).name());
        assertEquals(SclxPortableIdentity.account("ALPHA", "1010"),
                configuration.accounts().get(0).ledgerAccountId());
        assertEquals("OFX", configuration.accounts().get(0).statementImportFormat());

        assertEquals(1, facts.importBatches().size());
        assertEquals("statement.ofx", facts.importBatches().get(0).sourceName());
        assertEquals("FITID-100", facts.statementLines().get(0).sourceTransactionId());
        assertEquals(document.transactions().get(0).transactionId(),
                facts.statementLines().get(0).acceptedTransactionId());
        assertEquals(1, facts.issues().size());
        assertEquals(1, facts.transactionLineClearance().size());
        assertTrue(facts.transactionLineClearance().get(0).bankCleared());
        assertEquals(facts.statementLines().get(0).statementLineId(),
                facts.transactionLineClearance().get(0).statementLineId());

        assertEquals(1, reconciliation.sessions().size());
        assertEquals("FINALIZED", reconciliation.sessions().get(0).status());
        assertEquals(1, reconciliation.matches().size());
        assertEquals(facts.statementLines().get(0).statementLineId(),
                reconciliation.matches().get(0).statementLineId());

        SclxExportCounts counts = SclxExportCounts.from(document, 0L, 0L);
        assertEquals(1, counts.banks());
        assertEquals(1, counts.bankAccounts());
        assertEquals(1, counts.importBatches());
        assertEquals(1, counts.statementLines());
        assertEquals(1, counts.importIssues());
        assertEquals(1, counts.reconciliationSessions());
        assertEquals(1, counts.reconciliationMatches());

        String json = new String(new SclxJsonSerializer().serialize(document), StandardCharsets.UTF_8);
        assertFalse(json.contains("/private/source/statement.ofx"));
        assertTrue(json.contains("FITID-100"));
    }

    @Test
    void validatorRejectsUnresolvedBankAndReconciliationReferences()
    {
        Fixture fixture = fixture("ALPHA");
        SclxExportDocument base = new SclxCoreSnapshotAssembler().assemble(
                fixture.company(),
                fixture.accounts(),
                List.of(fixture.fund()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(fixture.transaction()),
                fixture.splits(),
                List.of(),
                Instant.EPOCH);

        Map<String, Object> values = new LinkedHashMap<>(base.extensions().scaJakartaH2());
        values.put(SclxBankConfigurationExtension.KEY, SclxBankConfigurationExtension.value(
                List.of(),
                List.of(SclxBankConfigurationExtension.accountEntry(
                        "bank-account:missing",
                        "bank:missing",
                        SclxPortableIdentity.account("ALPHA", "1010"),
                        "Missing bank",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.ZERO,
                        true,
                        null))));
        SclxExportDocument invalid = copy(base, values);
        assertThrows(IllegalArgumentException.class,
                () -> new SclxExportDocumentValidator().validate(invalid));
    }

    private static SclxExportDocument copy(
            SclxExportDocument base,
            Map<String, Object> extensionValues)
    {
        return new SclxExportDocument(
                base.format(),
                base.version(),
                base.exportedAt(),
                base.organization(),
                base.chartOfAccounts(),
                base.funds(),
                base.budgets(),
                base.transactions(),
                new SclxExportDocument.Extensions(1, extensionValues));
    }

    private static Bank bank(Company company, String name)
    {
        Bank bank = new Bank();
        bank.setCompany(company);
        bank.setName(name);
        bank.setRoutingNumber("123456789");
        bank.setContactEmail("treasurer@example.org");
        bank.setNotes("Primary operating institution");
        return bank;
    }

    private static CompanyBankAccount bankAccount(Company company, Bank bank, Account cash)
    {
        CompanyBankAccount account = new CompanyBankAccount();
        account.setCompany(company);
        account.setBank(bank);
        account.setAccount(cash);
        account.setName("Operating Checking");
        account.setNickname("Operating");
        account.setInstitutionName(bank.getName());
        account.setAccountType("CHECKING");
        account.setLastFour("1234");
        account.setMaskedAccountNumber("****1234");
        account.setOpeningDate(LocalDate.of(2026, 1, 1));
        account.setStatementImportFormat(BankingDataFormat.OFX);
        account.setOfxBankId("BANK-001");
        account.setOfxAccountId("ACCOUNT-1234");
        account.setOpeningBalance(new BigDecimal("1000.0000"));
        return account;
    }

    private static BankImportBatch batch(Company company, CompanyBankAccount account)
    {
        BankImportBatch batch = new BankImportBatch();
        batch.setCompany(company);
        batch.setBankAccount(account);
        batch.setSourceName("statement.ofx");
        batch.setSourcePath("/private/source/statement.ofx");
        batch.setSourceHash("abc123");
        batch.setSourceFormat(BankImportBatch.SourceFormat.OFX);
        batch.setStatus(BankImportBatch.Status.ACCEPTED);
        batch.setTotalLineCount(1);
        batch.setAcceptedLineCount(1);
        batch.setIssueCount(1);
        batch.setNotes("Reviewed statement");
        return batch;
    }

    private static BankStatementLine statementLine(
            Company company,
            CompanyBankAccount account,
            BankImportBatch batch,
            Txn transaction)
    {
        BankStatementLine line = new BankStatementLine();
        line.setBatch(batch);
        line.setCompany(company);
        line.setBankAccount(account);
        line.setSourceRowNumber(1);
        line.setSourceTransactionId("FITID-100");
        line.setDeterministicFingerprint("fingerprint-100");
        line.setStatementAccountIdentifier("ACCOUNT-1234");
        line.setTransactionDate(LocalDate.of(2026, 7, 15));
        line.setPostedDate(LocalDate.of(2026, 7, 16));
        line.setAmount(new BigDecimal("125.0000"));
        line.setTransactionType("CREDIT");
        line.setName("Donation processor");
        line.setMemo("July donation settlement");
        line.setCheckNumber("100");
        line.setReference("REF-100");
        line.setStatus(BankStatementLine.Status.ACCEPTED);
        line.setDispositionNote("Accepted into the canonical ledger");
        line.setAcceptedTransaction(transaction);
        return line;
    }

    private static ImportIssue issue(BankImportBatch batch, BankStatementLine line)
    {
        ImportIssue issue = new ImportIssue();
        issue.setBatch(batch);
        issue.setStatementLine(line);
        issue.setSourceRowNumber(1);
        issue.setSeverity(ImportIssue.Severity.WARNING);
        issue.setCode("MEMO_REVIEWED");
        issue.setMessage("Memo was reviewed before acceptance");
        return issue;
    }

    private static Fixture fixture(String code)
    {
        Company company = new Company();
        company.setCode(code);
        company.setDisplayName(code + " Company");
        company.setDefaultCurrency("USD");
        company.setFiscalYearStartMonth(1);
        company.setFiscalYearStartDay(1);

        ChartOfAccounts chart = new ChartOfAccounts();
        chart.setCompany(company);
        chart.setName("Standard");
        chart.setVersion("1");
        company.setActiveChartOfAccounts(chart);

        Account cash = account(chart, "1010", "Cash");
        Account income = account(chart, "4100", "Donation income");
        Fund fund = new Fund();
        fund.setCompany(company);
        fund.setCode("GENERAL");
        fund.setName("General Fund");
        fund.setFundType(FundType.UNRESTRICTED);

        Txn transaction = new Txn();
        transaction.setCompany(company);
        transaction.setPortableId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        transaction.setTxnDate(LocalDate.of(2026, 7, 15));
        transaction.setMemo("Donation settlement");
        TxnSplit cashLine = split(transaction, cash, fund, new BigDecimal("125.0000"));
        TxnSplit incomeLine = split(transaction, income, fund, new BigDecimal("-125.0000"));
        return new Fixture(
                company,
                List.of(cash, income),
                cash,
                fund,
                transaction,
                cashLine,
                List.of(cashLine, incomeLine));
    }

    private static Account account(ChartOfAccounts chart, String code, String name)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(AccountType.ASSET);
        account.setNormalBalance(NormalBalance.DEBIT);
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setPosting(true);
        account.setActive(true);
        return account;
    }

    private static TxnSplit split(Txn transaction, Account account, Fund fund, BigDecimal amount)
    {
        TxnSplit split = new TxnSplit();
        split.setTxn(transaction);
        split.setAccount(account);
        split.setFund(fund);
        split.setAmountSigned(amount);
        return split;
    }

    private record Fixture(
            Company company,
            List<Account> accounts,
            Account cash,
            Fund fund,
            Txn transaction,
            TxnSplit cashLine,
            List<TxnSplit> splits)
    {
    }
}
