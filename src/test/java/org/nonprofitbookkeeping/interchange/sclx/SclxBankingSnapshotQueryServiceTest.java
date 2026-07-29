package org.nonprofitbookkeeping.interchange.sclx;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.ImportIssue;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxBankingSnapshotQueryServiceTest
{
    @TempDir
    Path tempDir;

    @Test
    void loadsOnlySelectedCompanyBankingAndReconciliationFacts()
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("banking-snapshot")))
        {
            seed(jpa);

            SclxExportDocument document = new SclxCoreSnapshotQueryService(jpa, () -> "ALPHA")
                    .query(Instant.parse("2026-08-02T01:00:00Z"));

            SclxBankConfigurationExtension.Data configuration =
                    SclxBankConfigurationExtension.data(document.extensions());
            SclxBankStatementFactsExtension.Data facts =
                    SclxBankStatementFactsExtension.data(document.extensions());
            SclxReconciliationExtension.Data reconciliation =
                    SclxReconciliationExtension.data(document.extensions());

            assertEquals(List.of("Alpha Bank"),
                    configuration.banks().stream().map(SclxBankConfigurationExtension.BankEntry::name).toList());
            assertEquals(List.of("Alpha Checking"),
                    configuration.accounts().stream().map(SclxBankConfigurationExtension.AccountEntry::name).toList());
            assertEquals(List.of("alpha.ofx"),
                    facts.importBatches().stream().map(SclxBankStatementFactsExtension.ImportBatchEntry::sourceName).toList());
            assertEquals(List.of("ALPHA-FITID"),
                    facts.statementLines().stream()
                            .map(SclxBankStatementFactsExtension.StatementLineEntry::sourceTransactionId)
                            .toList());
            assertEquals(List.of("ALPHA_REVIEW"),
                    facts.issues().stream().map(SclxBankStatementFactsExtension.IssueEntry::code).toList());
            assertEquals(1, facts.transactionLineClearance().size());
            assertTrue(facts.transactionLineClearance().get(0).bankCleared());
            assertEquals(1, reconciliation.sessions().size());
            assertEquals("FINALIZED", reconciliation.sessions().get(0).status());
            assertEquals(1, reconciliation.matches().size());
            assertTrue(reconciliation.matches().get(0).statementLineId() != null);
            assertTrue(reconciliation.matches().get(0).lineId() != null);

            String json = new String(new SclxJsonSerializer().serialize(document),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(json.contains("beta.ofx"));
            assertFalse(json.contains("Beta Bank"));
            assertFalse(json.contains("/private/alpha.ofx"));
        }
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            seedCompany(em, "ALPHA", "Alpha Bank", "Alpha Checking", "alpha.ofx", "ALPHA-FITID", true);
            seedCompany(em, "BETA", "Beta Bank", "Beta Checking", "beta.ofx", "BETA-FITID", false);
            em.getTransaction().commit();
        }
    }

    private static void seedCompany(
            EntityManager em,
            String code,
            String bankName,
            String configuredAccountName,
            String sourceName,
            String fitId,
            boolean reconcile)
    {
        Company company = company(code);
        em.persist(company);
        ChartOfAccounts chart = chart(company);
        em.persist(chart);
        company.setActiveChartOfAccounts(chart);
        Account cash = account(chart, "1010", code + " Cash", AccountType.ASSET);
        Account income = account(chart, "4100", code + " Income", AccountType.INCOME);
        em.persist(cash);
        em.persist(income);
        Fund fund = fund(company);
        em.persist(fund);

        Txn txn = new Txn();
        txn.setCompany(company);
        txn.setTxnDate(LocalDate.of(2026, 7, 15));
        txn.setMemo(code + " bank transaction");
        em.persist(txn);
        TxnSplit cashLine = split(txn, cash, fund, "125.0000");
        TxnSplit incomeLine = split(txn, income, fund, "125.0000");
        em.persist(cashLine);
        em.persist(incomeLine);

        Bank bank = new Bank();
        bank.setCompany(company);
        bank.setName(bankName);
        em.persist(bank);

        CompanyBankAccount bankAccount = new CompanyBankAccount();
        bankAccount.setCompany(company);
        bankAccount.setBank(bank);
        bankAccount.setAccount(cash);
        bankAccount.setName(configuredAccountName);
        bankAccount.setAccountType("CHECKING");
        bankAccount.setStatementImportFormat(BankingDataFormat.OFX);
        bankAccount.setOpeningBalance(new BigDecimal("1000.0000"));
        em.persist(bankAccount);

        BankImportBatch batch = new BankImportBatch();
        batch.setCompany(company);
        batch.setBankAccount(bankAccount);
        batch.setSourceName(sourceName);
        batch.setSourcePath("/private/" + sourceName);
        batch.setSourceHash(code.toLowerCase() + "-hash");
        batch.setSourceFormat(BankImportBatch.SourceFormat.OFX);
        batch.setStatus(BankImportBatch.Status.ACCEPTED);
        batch.setTotalLineCount(1);
        batch.setAcceptedLineCount(1);
        batch.setIssueCount(1);
        em.persist(batch);

        BankStatementLine statementLine = new BankStatementLine();
        statementLine.setBatch(batch);
        statementLine.setCompany(company);
        statementLine.setBankAccount(bankAccount);
        statementLine.setSourceRowNumber(1);
        statementLine.setSourceTransactionId(fitId);
        statementLine.setDeterministicFingerprint(code.toLowerCase() + "-fingerprint");
        statementLine.setStatementAccountIdentifier(code + "-ACCOUNT");
        statementLine.setTransactionDate(LocalDate.of(2026, 7, 15));
        statementLine.setPostedDate(LocalDate.of(2026, 7, 16));
        statementLine.setAmount(new BigDecimal("125.0000"));
        statementLine.setTransactionType("CREDIT");
        statementLine.setName(code + " Processor");
        statementLine.setMemo(code + " settlement");
        statementLine.setStatus(BankStatementLine.Status.ACCEPTED);
        statementLine.setAcceptedTransaction(txn);
        em.persist(statementLine);

        ImportIssue issue = new ImportIssue();
        issue.setBatch(batch);
        issue.setStatementLine(statementLine);
        issue.setSourceRowNumber(1);
        issue.setSeverity(ImportIssue.Severity.WARNING);
        issue.setCode(code + "_REVIEW");
        issue.setMessage(code + " statement was reviewed");
        em.persist(issue);

        if (reconcile)
        {
            cashLine.setBankCleared(true);
            cashLine.setBankClearedOn(LocalDate.of(2026, 7, 31));
            cashLine.setMatchedBankStatementLine(statementLine);
            em.flush();

            UUID sessionPortableId = UUID.fromString("88888888-8888-8888-8888-888888888888");
            UUID matchPortableId = UUID.fromString("99999999-9999-9999-9999-999999999999");
            em.createNativeQuery("""
                    insert into bank_reconciliation_session
                        (id, company_id, bank_account_id, statement_start_date, statement_end_date,
                         statement_ending_balance, mismatch_policy, status, beginning_balance,
                         book_balance_all, book_balance_cleared, difference_amount, portable_id)
                    values
                        (8801, ?1, ?2, DATE '2026-07-01', DATE '2026-07-31',
                         1125.0000, 'WARN_ONLY', 'FINALIZED', 1000.0000,
                         1125.0000, 1125.0000, 0.0000, ?3)
                    """)
                    .setParameter(1, company.getId())
                    .setParameter(2, bankAccount.getId())
                    .setParameter(3, sessionPortableId)
                    .executeUpdate();
            em.createNativeQuery("""
                    insert into bank_reconciliation_match
                        (id, session_id, statement_line_id, txn_split_id, match_status,
                         resolution_note, portable_id)
                    values
                        (9901, 8801, ?1, ?2, 'MATCHED', 'Exact match', ?3)
                    """)
                    .setParameter(1, statementLine.getId())
                    .setParameter(2, cashLine.getId())
                    .setParameter(3, matchPortableId)
                    .executeUpdate();
        }
    }

    private static Company company(String code)
    {
        Company company = new Company();
        company.setCode(code);
        company.setDisplayName(code + " Company");
        company.setDefaultCurrency("USD");
        company.setFiscalYearStartMonth(1);
        company.setFiscalYearStartDay(1);
        return company;
    }

    private static ChartOfAccounts chart(Company company)
    {
        ChartOfAccounts chart = new ChartOfAccounts();
        chart.setCompany(company);
        chart.setName(company.getCode() + " Chart");
        chart.setVersion("1");
        chart.setStatus(ChartStatus.ACTIVE);
        return chart;
    }

    private static Account account(
            ChartOfAccounts chart,
            String code,
            String name,
            AccountType type)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setNormalBalance(type == AccountType.INCOME ? NormalBalance.CREDIT : NormalBalance.DEBIT);
        account.setOpeningBalance(BigDecimal.ZERO);
        return account;
    }

    private static Fund fund(Company company)
    {
        Fund fund = new Fund();
        fund.setCompany(company);
        fund.setCode("GENERAL");
        fund.setName(company.getCode() + " General Fund");
        fund.setFundType(FundType.UNRESTRICTED);
        return fund;
    }

    private static TxnSplit split(Txn txn, Account account, Fund fund, String amount)
    {
        TxnSplit split = new TxnSplit();
        split.setTxn(txn);
        split.setAccount(account);
        split.setFund(fund);
        split.setAmountSigned(new BigDecimal(amount));
        return split;
    }
}
