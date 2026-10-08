package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Company-owned canonical ledger fixture shared by transfer service and real UI tests. */
public record FundTransferTestFixture(long source, long destination, long bank, long income, long allocation, long equity, long expense)
{
    public static final LocalDate OPENING = LocalDate.of(2026, 1, 1);
    public static final LocalDate DATE = LocalDate.of(2026, 2, 1);

    public static FundTransferTestFixture seed(Jpa jpa)
    {
        FundTransferTestFixture fixture;
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = em.createQuery("from Company c where c.code = 'DEFAULT'", Company.class).getSingleResult();
            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName("Transfer test chart");
            chart.setVersion("1");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            company.setActiveChartOfAccounts(chart);
            Fund source = fund(em, company, "GENERAL", FundType.UNRESTRICTED);
            Fund destination = fund(em, company, "DESIGNATED", FundType.DESIGNATED);
            Account bank = account(em, chart, "1000", AccountType.ASSET, NormalBalance.DEBIT);
            bank.setAccountFunction(AccountFunction.BANK);
            Account income = account(em, chart, "4000", AccountType.INCOME, NormalBalance.CREDIT);
            Account allocation = account(em, chart, "1500", AccountType.ASSET, NormalBalance.DEBIT);
            Account equity = account(em, chart, "3000", AccountType.EQUITY, NormalBalance.CREDIT);
            Account expense = account(em, chart, "5000", AccountType.EXPENSE, NormalBalance.DEBIT);
            em.flush();
            fixture = new FundTransferTestFixture(source.getId(), destination.getId(), bank.getId(), income.getId(), allocation.getId(), equity.getId(), expense.getId());
            em.getTransaction().commit();
        }
        new TransactionEntryService(jpa).enter(new TransactionCommand(OPENING, null, "Opening resources", null,
                List.of(line(fixture.bank, fixture.source, "200", "0"), line(fixture.income, fixture.source, "0", "200"))));
        return fixture;
    }

    public FundTransferCommand command(java.util.UUID request, String amount)
    {
        return new FundTransferCommand(request, DATE, source, destination, allocation, equity, new BigDecimal(amount), "Allocate to designated purpose");
    }

    public static TransactionLineCommand line(long account, long fund, String debit, String credit)
    {
        return new TransactionLineCommand(account, fund, null, null, null, new BigDecimal(debit), new BigDecimal(credit), false, null);
    }

    private static Fund fund(EntityManager em, Company company, String code, FundType type)
    {
        Fund fund = new Fund();
        fund.setCompany(company);
        fund.setCode(code);
        fund.setName(code);
        fund.setFundType(type);
        fund.setActive(true);
        em.persist(fund);
        return fund;
    }

    private static Account account(EntityManager em, ChartOfAccounts chart, String code, AccountType type, NormalBalance normal)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(code);
        account.setAccountType(type);
        account.setNormalBalance(normal);
        account.setPosting(true);
        account.setActive(true);
        em.persist(account);
        return account;
    }
}
