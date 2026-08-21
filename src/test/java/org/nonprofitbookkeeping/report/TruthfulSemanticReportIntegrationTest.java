package org.nonprofitbookkeeping.report;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundTransfer;
import org.nonprofitbookkeeping.model.FundTransferStatus;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.report.template.SemanticReportValueSet;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;
import org.nonprofitbookkeeping.service.FinancialReportService;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TruthfulSemanticReportIntegrationTest
{
    @Test
    void bankActivityUsesOnlyCompanyScopedBankSplitsAndTotalsReturnedRows(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-activity")))
        {
            Fixture fixture = seed(jpa);
            ReportExecutionService service = service(jpa, "ALPHA");
            ReportRequest request = new ReportRequest(
                    ReportDefinition.ALL_CHECKS_TFRS,
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    ReportFundOption.from(fixture.sourceFund()),
                    100);

            ReportResult result = service.execute(request);
            List<Map<String, Object>> rows =
                    result.semanticValues().table("allChecksTfrs.rows");

            assertEquals("Bank Account Activity (SCA workbook)", request.definition().displayName());
            assertEquals(4, rows.size());
            assertTrue(rows.stream().anyMatch(row -> Long.valueOf(fixture.bankTransactionId())
                    .equals(row.get("txnId"))));
            assertTrue(rows.stream().anyMatch(row -> Long.valueOf(fixture.bankReversalId())
                    .equals(row.get("txnId"))));
            assertFalse(rows.stream().anyMatch(row -> "5000".equals(row.get("accountCode"))));
            assertFalse(rows.stream().anyMatch(row -> Long.valueOf(fixture.otherCompanyTransactionId())
                    .equals(row.get("txnId"))));
            Map<String, Object> displayedTotal = rows.get(rows.size() - 1);
            assertEquals(new BigDecimal("40.0000"), displayedTotal.get("debit"));
            assertEquals(new BigDecimal("80.0000"), displayedTotal.get("credit"));
            assertTrue(result.csv().contains("BANK split"));
            assertTrue(result.csv().contains("Displayed total"));
            assertFalse(result.csv().contains("Other company"));
        }
    }

    @Test
    void fundTransfersUseOnlyPostedLinkedFactsAndExpandBalancedPairs(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fund-transfers")))
        {
            Fixture fixture = seed(jpa);
            ReportExecutionService service = service(jpa, "ALPHA");
            ReportRequest request = new ReportRequest(
                    ReportDefinition.FUND_TRANSFERS,
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    ReportFundOption.ALL_FUNDS,
                    100);

            ReportResult result = service.execute(request);
            SemanticReportValueSet values = result.semanticValues();
            List<Map<String, Object>> rows = values.table("fundTransfers.rows");

            assertEquals(5, rows.size());
            assertEquals(List.of("Transfer source", "Transfer destination", "Fund total", "Fund total", "All funds net"),
                    rows.stream().map(row -> (String) row.get("rowType")).toList());
            assertEquals(new BigDecimal("-40.0000"), rows.get(0).get("netEffect"));
            assertEquals(new BigDecimal("40.0000"), rows.get(1).get("netEffect"));
            assertEquals(BigDecimal.ZERO.setScale(4), values.get("fundTransfers.allFundsNet"));
            assertTrue(rows.stream().allMatch(row ->
                    row.get("txnId") == null || Long.valueOf(fixture.postedTransferTransactionId())
                            .equals(row.get("txnId"))));
            assertFalse(result.csv().contains("Draft transfer"));
            assertFalse(result.csv().contains("Void transfer"));
            assertFalse(result.csv().contains("Other company transfer"));
            assertFalse(result.csv().contains("Ordinary multi-fund activity"));
            assertTrue(result.csv().contains("All funds net"));
        }
    }

    @Test
    void emptyRangesRemainEmptyExceptForExplicitZeroSummaries(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("empty-reports")))
        {
            seed(jpa);
            ReportExecutionService service = service(jpa, "ALPHA");

            ReportResult bank = service.execute(new ReportRequest(
                    ReportDefinition.ALL_CHECKS_TFRS,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 1, 31),
                    ReportFundOption.ALL_FUNDS,
                    100));
            ReportResult transfers = service.execute(new ReportRequest(
                    ReportDefinition.FUND_TRANSFERS,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 1, 31),
                    ReportFundOption.ALL_FUNDS,
                    100));

            assertEquals(1, bank.semanticValues().table("allChecksTfrs.rows").size());
            assertEquals("Displayed total",
                    bank.semanticValues().table("allChecksTfrs.rows").get(0).get("rowType"));
            assertEquals(1, transfers.semanticValues().table("fundTransfers.rows").size());
            assertEquals("All funds net",
                    transfers.semanticValues().table("fundTransfers.rows").get(0).get("rowType"));
        }
    }

    @Test
    void transactionsListCanSelectOnePostingAccount(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("transactions-list-account")))
        {
            Fixture fixture = seed(jpa);
            ReportExecutionService service = service(jpa, "ALPHA");
            ReportRequest request = new ReportRequest(
                    ReportDefinition.TRANSACTIONS_LIST,
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    ReportFundOption.from(fixture.sourceFund()),
                    100,
                    new ReportDomainFilter.AccountSelection(fixture.expenseAccountId()));

            ReportResult result = service.execute(request);
            List<Map<String, Object>> rows =
                    result.semanticValues().table("transactionsList.rows");

            assertEquals(3, rows.size());
            assertTrue(rows.stream().allMatch(row -> "5000".equals(row.get("accountCode"))));
            assertFalse(result.csv().contains("Checking"));
            assertTrue(result.tabular());
            assertEquals(3, result.tableModel().rows().size());
            assertEquals("Account", result.tableModel().columns().get(3).label());
        }
    }

    private static ReportExecutionService service(Jpa jpa, String companyCode)
    {
        return new ReportExecutionService(
                new FinancialReportService(jpa, () -> companyCode),
                FinancialReportDisplayFormat.plain(),
                new SemanticAccountingReportQueryService(jpa, () -> companyCode));
    }

    private static Fixture seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();

            Company alpha = company("ALPHA", "Alpha Branch");
            Company beta = company("BETA", "Beta Branch");
            em.persist(alpha);
            em.persist(beta);

            ChartOfAccounts alphaChart = chart(alpha, "Alpha Chart");
            ChartOfAccounts betaChart = chart(beta, "Beta Chart");
            em.persist(alphaChart);
            em.persist(betaChart);
            alpha.setActiveChartOfAccounts(alphaChart);
            beta.setActiveChartOfAccounts(betaChart);

            Fund source = fund(alpha, "SRC", "Source");
            Fund destination = fund(alpha, "DST", "Destination");
            Fund betaFund = fund(beta, "BETA", "Other Company");
            em.persist(source);
            em.persist(destination);
            em.persist(betaFund);

            Account alphaBank = account(alphaChart, "1000", "Checking", AccountType.ASSET, NormalBalance.DEBIT);
            alphaBank.setAccountFunction(AccountFunction.BANK);
            Account alphaExpense = account(alphaChart, "5000", "Expense", AccountType.EXPENSE, NormalBalance.DEBIT);
            Account betaBank = account(betaChart, "1100", "Other Checking", AccountType.ASSET, NormalBalance.DEBIT);
            betaBank.setAccountFunction(AccountFunction.BANK);
            em.persist(alphaBank);
            em.persist(alphaExpense);
            em.persist(betaBank);

            Txn bank = transaction(alpha, LocalDate.of(2026, 3, 5), "Bank payment");
            em.persist(bank);
            em.persist(split(bank, alphaBank, source, new BigDecimal("-40.0000")));
            em.persist(split(bank, alphaExpense, source, new BigDecimal("40.0000")));

            Txn reversal = transaction(alpha, LocalDate.of(2026, 3, 8), "Bank payment reversal");
            reversal.setReversalOf(bank);
            em.persist(reversal);
            em.persist(split(reversal, alphaBank, source, new BigDecimal("40.0000")));
            em.persist(split(reversal, alphaExpense, source, new BigDecimal("-40.0000")));

            Txn ordinaryMultiFund = transaction(
                    alpha, LocalDate.of(2026, 3, 10), "Ordinary multi-fund activity");
            em.persist(ordinaryMultiFund);
            em.persist(split(ordinaryMultiFund, alphaExpense, source, new BigDecimal("10.0000")));
            em.persist(split(ordinaryMultiFund, alphaExpense, destination, new BigDecimal("-10.0000")));

            Txn postedTxn = transaction(alpha, LocalDate.of(2026, 3, 12), "Posted transfer");
            em.persist(postedTxn);
            em.persist(split(postedTxn, alphaBank, source, new BigDecimal("-40.0000")));
            em.persist(split(postedTxn, alphaBank, destination, new BigDecimal("40.0000")));

            Txn betaTxn = transaction(beta, LocalDate.of(2026, 3, 15), "Other company");
            em.persist(betaTxn);
            em.persist(split(betaTxn, betaBank, betaFund, new BigDecimal("90.0000")));

            FundTransfer posted = transfer(
                    LocalDate.of(2026, 3, 12), source, destination,
                    new BigDecimal("40.0000"), "Posted transfer", FundTransferStatus.POSTED, postedTxn);
            FundTransfer draft = transfer(
                    LocalDate.of(2026, 3, 13), source, destination,
                    new BigDecimal("12.0000"), "Draft transfer", FundTransferStatus.DRAFT, null);
            FundTransfer voided = transfer(
                    LocalDate.of(2026, 3, 14), source, destination,
                    new BigDecimal("13.0000"), "Void transfer", FundTransferStatus.VOID, postedTxn);
            FundTransfer other = transfer(
                    LocalDate.of(2026, 3, 15), betaFund, betaFund,
                    new BigDecimal("90.0000"), "Other company transfer", FundTransferStatus.POSTED, betaTxn);
            em.persist(posted);
            em.persist(draft);
            em.persist(voided);
            em.persist(other);

            em.getTransaction().commit();
            return new Fixture(
                    source,
                    bank.getId(),
                    reversal.getId(),
                    betaTxn.getId(),
                    postedTxn.getId(),
                    alphaExpense.getId());
        }
    }

    private static Company company(String code, String name)
    {
        Company company = new Company();
        company.setCode(code);
        company.setDisplayName(name);
        return company;
    }

    private static ChartOfAccounts chart(Company company, String name)
    {
        ChartOfAccounts chart = new ChartOfAccounts();
        chart.setCompany(company);
        chart.setName(name);
        chart.setVersion("1");
        chart.setStatus(ChartStatus.ACTIVE);
        return chart;
    }

    private static Fund fund(Company company, String code, String name)
    {
        Fund fund = new Fund();
        fund.setCompany(company);
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

    private static Txn transaction(Company company, LocalDate date, String memo)
    {
        Txn txn = new Txn();
        txn.setCompany(company);
        txn.setTxnDate(date);
        txn.setMemo(memo);
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

    private static FundTransfer transfer(
            LocalDate date,
            Fund source,
            Fund destination,
            BigDecimal amount,
            String memo,
            FundTransferStatus status,
            Txn postedTxn)
    {
        FundTransfer transfer = new FundTransfer();
        transfer.setTransferDate(date);
        transfer.setFromFund(source);
        transfer.setToFund(destination);
        transfer.setAmount(amount);
        transfer.setMemo(memo);
        transfer.setStatus(status);
        transfer.setPostedTxn(postedTxn);
        return transfer;
    }

    private record Fixture(
            Fund sourceFund,
            long bankTransactionId,
            long bankReversalId,
            long otherCompanyTransactionId,
            long postedTransferTransactionId,
            long expenseAccountId)
    {
    }
}
