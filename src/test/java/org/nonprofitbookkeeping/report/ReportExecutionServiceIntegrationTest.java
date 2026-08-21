package org.nonprofitbookkeeping.report;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;
import org.nonprofitbookkeeping.service.FinancialReportService;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportExecutionServiceIntegrationTest
{
    @Test
    void requestUsesSelectedFundAndCompanyDisplayFormatWhileCsvStaysRaw(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("report-execution")))
        {
            LedgerSeed seed = seedTwoFundLedger(jpa);
            FinancialReportDisplayFormat format = new FinancialReportDisplayFormat()
            {
                @Override
                public String formatDate(LocalDate value)
                {
                    return "DATE[" + value + "]";
                }

                @Override
                public String formatMoney(BigDecimal value)
                {
                    return "MONEY[" + value.setScale(2) + "]";
                }
            };
            ReportExecutionService service = new ReportExecutionService(
                    new FinancialReportService(jpa),
                    format);
            ReportRequest request = new ReportRequest(
                    ReportDefinition.GENERAL_LEDGER_DETAIL,
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    ReportFundOption.from(seed.generalFund()),
                    100,
                    new ReportDomainFilter.AccountSelection(seed.incomeAccountId()));

            ReportResult result = service.execute(request);

            assertTrue(result.text().contains("DATE[2026-03-05]"));
            assertTrue(result.text().contains("MONEY[100.00]"));
            assertTrue(result.text().contains("GEN"));
            assertFalse(result.text().contains("RES"));
            assertTrue(result.csv().contains("2026-03-05"));
            assertTrue(result.csv().contains("GEN"));
            assertFalse(result.csv().contains("MONEY["));
            assertFalse(result.csv().contains("RES"));
            assertTrue(result.tabular());
            assertTrue(result.tableModel().columns().stream()
                    .anyMatch(column -> "Memo".equals(column.label())));
            assertTrue(result.tableModel().rows().stream()
                    .anyMatch(row -> "General donation".equals(row.value("memo"))));
            assertTrue(result.tableModel().rows().stream()
                    .allMatch(row -> "4000".equals(row.value("account"))));
            assertFalse(result.text().contains("Cash"));
        }
    }

    private static LedgerSeed seedTwoFundLedger(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();

            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setName("Reporting Chart");
            chart.setVersion("1");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);

            Fund general = fund("GEN", "General");
            Fund restricted = fund("RES", "Restricted");
            em.persist(general);
            em.persist(restricted);

            Account cash = account(chart, "1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT);
            Account income = account(chart, "4000", "Income", AccountType.INCOME, NormalBalance.CREDIT);
            em.persist(cash);
            em.persist(income);

            Txn generalTxn = transaction(LocalDate.of(2026, 3, 5), "General donation");
            Txn restrictedTxn = transaction(LocalDate.of(2026, 3, 6), "Restricted donation");
            em.persist(generalTxn);
            em.persist(restrictedTxn);

            em.persist(split(generalTxn, cash, general, new BigDecimal("100.00")));
            em.persist(split(generalTxn, income, general, new BigDecimal("-100.00")));
            em.persist(split(restrictedTxn, cash, restricted, new BigDecimal("250.00")));
            em.persist(split(restrictedTxn, income, restricted, new BigDecimal("-250.00")));

            em.getTransaction().commit();
            return new LedgerSeed(general, income.getId());
        }
    }

    private static Fund fund(String code, String name)
    {
        Fund fund = new Fund();
        fund.setCode(code);
        fund.setName(name);
        fund.setFundType(FundType.UNRESTRICTED);
        fund.setActive(true);
        return fund;
    }

    private static Account account(
            ChartOfAccounts chart,
            String code,
            String name,
            AccountType type,
            NormalBalance normal)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setNormalBalance(normal);
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setPosting(true);
        account.setActive(true);
        return account;
    }

    private static Txn transaction(LocalDate date, String memo)
    {
        Txn txn = new Txn();
        txn.setTxnDate(date);
        txn.setMemo(memo);
        return txn;
    }

    private static TxnSplit split(
            Txn txn,
            Account account,
            Fund fund,
            BigDecimal amount)
    {
        TxnSplit split = new TxnSplit();
        split.setTxn(txn);
        split.setAccount(account);
        split.setFund(fund);
        split.setAmountSigned(amount);
        return split;
    }

    private record LedgerSeed(Fund generalFund, Long incomeAccountId)
    {
    }
}
