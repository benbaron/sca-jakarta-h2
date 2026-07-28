package org.nonprofitbookkeeping.interchange.sclx;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.model.TxnSupplementalLine;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SclxSupplementalDetailQueryServiceTest
{
    @TempDir
    Path tempDir;

    @Test
    void loadsOnlySelectedCompanySupplementalDetails()
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("supplemental-detail-query")))
        {
            seedCompany(jpa, "ALPHA", "Alpha receivable",
                    UUID.fromString("11111111-1111-1111-1111-111111111111"));
            seedCompany(jpa, "BETA", "Beta payable",
                    UUID.fromString("22222222-2222-2222-2222-222222222222"));

            SclxExportDocument document = new SclxCoreSnapshotQueryService(jpa, () -> "ALPHA")
                    .query(Instant.parse("2026-07-28T05:00:00Z"));

            List<SclxSupplementalDetailExtension.Entry> details =
                    SclxSupplementalDetailExtension.entries(document.extensions());
            assertEquals(1, details.size());
            assertEquals("Alpha receivable", details.get(0).description());
            assertEquals(document.transactions().get(0).transactionId(), details.get(0).transactionId());
        }
    }

    private static void seedCompany(Jpa jpa, String code, String detailDescription, UUID transactionUuid)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();

            Company company = new Company();
            company.setCode(code);
            company.setDisplayName(code + " Company");
            company.setDefaultCurrency("USD");
            company.setFiscalYearStartMonth(1);
            company.setFiscalYearStartDay(1);
            em.persist(company);

            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName(code + " Chart");
            chart.setVersion("1");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            company.setActiveChartOfAccounts(chart);

            Account cash = account(chart, "1010", "Cash", AccountType.ASSET);
            Account expense = account(chart, "6100", "Expense", AccountType.EXPENSE);
            em.persist(cash);
            em.persist(expense);

            Fund fund = new Fund();
            fund.setCompany(company);
            fund.setCode("GENERAL");
            fund.setName("General Fund");
            fund.setFundType(FundType.UNRESTRICTED);
            em.persist(fund);

            Txn transaction = new Txn();
            transaction.setCompany(company);
            transaction.setPortableId(transactionUuid);
            transaction.setTxnDate(LocalDate.of(2026, 7, 28));
            transaction.setMemo(code + " transaction");
            em.persist(transaction);

            em.persist(split(transaction, expense, fund, new BigDecimal("25.0000")));
            em.persist(split(transaction, cash, fund, new BigDecimal("-25.0000")));

            TxnSupplementalLine detail = new TxnSupplementalLine();
            detail.setTxn(transaction);
            detail.setLineOrder(0);
            detail.setKind(code.equals("ALPHA") ? "RECEIVABLE" : "PAYABLE");
            detail.setCounterparty(code + " Counterparty");
            detail.setDescription(detailDescription);
            detail.setAmount(new BigDecimal("25.0000"));
            detail.setDueDate(LocalDate.of(2026, 8, 31));
            em.persist(detail);

            em.getTransaction().commit();
        }
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
}
