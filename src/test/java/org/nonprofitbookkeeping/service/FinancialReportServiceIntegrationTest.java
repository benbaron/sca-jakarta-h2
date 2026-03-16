package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.CounterpartyKind;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialReportServiceIntegrationTest
{
    @Test
    void m1_trialBalanceAndGeneralLedgerSlice_areDeterministic() throws Exception
    {
        Path db = Files.createTempFile("reporting-m1", ".mv.db");
        runMigrations(db);

        Jpa jpa = new Jpa(db);
        try
        {
            seedLedger(jpa);
            FinancialReportService service = new FinancialReportService(jpa);

            FinancialReportService.TrialBalanceReport trial = service.trialBalance(LocalDate.of(2026, 3, 31), null);
            assertTrue(trial.isBalanced());
            assertEquals(new BigDecimal("1800.00"), trial.totalDebits().setScale(2));
            assertEquals(new BigDecimal("1800.00"), trial.totalCredits().setScale(2));

            List<FinancialReportService.GeneralLedgerRow> lines = service.generalLedgerDetail(
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null, 100);
            assertEquals(6, lines.size());
            assertTrue(lines.stream().anyMatch(row -> row.accountCode().equals("4000") && row.credit().compareTo(new BigDecimal("1000.00")) == 0));
            assertTrue(lines.stream().anyMatch(row -> row.accountCode().equals("5000") && row.debit().compareTo(new BigDecimal("300.00")) == 0));
        }
        finally
        {
            jpa.close();
        }
    }

    @Test
    void m2_balanceSheetAndIncomeStatementSlice_reconciles() throws Exception
    {
        Path db = Files.createTempFile("reporting-m2", ".mv.db");
        runMigrations(db);

        Jpa jpa = new Jpa(db);
        try
        {
            seedLedger(jpa);
            FinancialReportService service = new FinancialReportService(jpa);

            FinancialReportService.BalanceSheetReport balanceSheet = service.balanceSheet(LocalDate.of(2026, 3, 31), null);
            assertTrue(balanceSheet.isBalanced());
            assertEquals(new BigDecimal("1300.00"), balanceSheet.totalAssets().setScale(2));
            assertEquals(new BigDecimal("1300.00"), balanceSheet.liabilitiesAndEquity().setScale(2));

            FinancialReportService.IncomeStatementReport income = service.incomeStatement(
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null);
            assertEquals(new BigDecimal("1000.00"), income.totalIncome().setScale(2));
            assertEquals(new BigDecimal("500.00"), income.totalExpense().setScale(2));
            assertEquals(new BigDecimal("500.00"), income.netIncome().setScale(2));
        }
        finally
        {
            jpa.close();
        }
    }

    private static void seedLedger(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();

            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setName("Default Chart");
            chart.setVersion("v1");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);

            Fund fund = new Fund();
            fund.setCode("GEN");
            fund.setName("General Fund");
            fund.setFundType(FundType.UNRESTRICTED);
            fund.setActive(true);
            em.persist(fund);

            Counterparty donor = new Counterparty();
            donor.setKind(CounterpartyKind.ORG);
            donor.setDisplayName("Donor A");
            donor.setActive(true);
            em.persist(donor);

            Counterparty vendor = new Counterparty();
            vendor.setKind(CounterpartyKind.ORG);
            vendor.setDisplayName("Vendor B");
            vendor.setActive(true);
            em.persist(vendor);

            Account bank = account(chart, "1000", "Operating Cash", AccountType.BANK, NormalBalance.DEBIT, new BigDecimal("500.00"));
            Account payable = account(chart, "2000", "Accounts Payable", AccountType.LIABILITY, NormalBalance.CREDIT, BigDecimal.ZERO);
            Account netAssets = account(chart, "3000", "Net Assets", AccountType.EQUITY, NormalBalance.CREDIT, new BigDecimal("500.00"));
            Account donations = account(chart, "4000", "Contributions", AccountType.INCOME, NormalBalance.CREDIT, BigDecimal.ZERO);
            Account expense = account(chart, "5000", "Program Expense", AccountType.EXPENSE, NormalBalance.DEBIT, BigDecimal.ZERO);
            em.persist(bank);
            em.persist(payable);
            em.persist(netAssets);
            em.persist(donations);
            em.persist(expense);

            Txn t1 = txn(LocalDate.of(2026, 3, 5), donor, "Donation", bank);
            Txn t2 = txn(LocalDate.of(2026, 3, 10), vendor, "Program supplies paid", bank);
            Txn t3 = txn(LocalDate.of(2026, 3, 20), vendor, "Accrued expense", bank);
            em.persist(t1);
            em.persist(t2);
            em.persist(t3);

            em.persist(split(t1, bank, fund, new BigDecimal("1000.00")));
            em.persist(split(t1, donations, fund, new BigDecimal("1000.00")));

            em.persist(split(t2, expense, fund, new BigDecimal("200.00")));
            em.persist(split(t2, bank, fund, new BigDecimal("-200.00")));

            em.persist(split(t3, expense, fund, new BigDecimal("300.00")));
            em.persist(split(t3, payable, fund, new BigDecimal("300.00")));

            em.getTransaction().commit();
        }
    }

    private static Account account(ChartOfAccounts chart,
                                   String code,
                                   String name,
                                   AccountType type,
                                   NormalBalance normal,
                                   BigDecimal opening)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setNormalBalance(normal);
        account.setOpeningBalance(opening);
        account.setPosting(true);
        account.setActive(true);
        return account;
    }

    private static Txn txn(LocalDate date, Counterparty payee, String memo, Account bank)
    {
        Txn txn = new Txn();
        txn.setTxnDate(date);
        txn.setPayee(payee);
        txn.setMemo(memo);
        txn.setBankAccount(bank);
        return txn;
    }

    private static TxnSplit split(Txn txn, Account account, Fund fund, BigDecimal amount)
    {
        TxnSplit split = new TxnSplit();
        split.setTxn(txn);
        split.setAccount(account);
        split.setFund(fund);
        split.setAmountSigned(amount);
        return split;
    }

    private static void runMigrations(Path databaseFile)
    {
        String raw = databaseFile.toString();
        String normalized = raw.endsWith(".mv.db") ? raw.substring(0, raw.length() - 6) : raw;
        String jdbc = "jdbc:h2:file:" + normalized + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        Flyway.configure().dataSource(jdbc, "sa", "").load().migrate();
    }
}
