package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Synthetic directly entered facts, never production policy or donor ledger data. */
public record NmrItemTestFixture(long cash, long income, long liability, long fund, long event, long recipient)
{
    public static final String COMPANY = "NMR_DETAIL";
    public static final LocalDate DATE = LocalDate.of(2026, 6, 1);

    public static NmrItemTestFixture seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode(COMPANY); company.setDisplayName("Synthetic NMR detail"); company.setDefaultCurrency("USD");
            em.persist(company);
            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company); chart.setName("Synthetic chart"); chart.setVersion("1"); chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart); company.setActiveChartOfAccounts(chart);
            Account cash = account(em, chart, "1000", AccountType.ASSET, AccountSubtype.CASH, NormalBalance.DEBIT);
            Account income = account(em, chart, "4000", AccountType.INCOME, null, NormalBalance.CREDIT);
            Account liability = account(em, chart, "2300", AccountType.LIABILITY, AccountSubtype.OTHER_LIABILITY, NormalBalance.CREDIT);
            Fund fund = new Fund(); fund.setCompany(company); fund.setCode("GENERAL"); fund.setName("General");
            fund.setFundType(FundType.UNRESTRICTED); fund.setActive(true); em.persist(fund);
            Activity event = new Activity(); event.setCompany(company); event.setCode("EVENT"); event.setName("Synthetic event");
            event.setActive(true); em.persist(event);
            Counterparty recipient = new Counterparty(); recipient.setCompany(company); recipient.setDisplayName("Synthetic recipient");
            em.persist(recipient);
            em.getTransaction().commit();
            return new NmrItemTestFixture(cash.getId(), income.getId(), liability.getId(), fund.getId(), event.getId(), recipient.getId());
        }
    }

    private static Account account(EntityManager em, ChartOfAccounts chart, String code,
            AccountType type, AccountSubtype subtype, NormalBalance normal)
    {
        Account account = new Account(); account.setChart(chart); account.setCode(code); account.setName(code);
        account.setAccountType(type); account.setSubtype(subtype); account.setNormalBalance(normal);
        account.setPosting(true); account.setActive(true); em.persist(account); return account;
    }

    public TransactionLineCommand line(long account, String debit, String credit, Long activity)
    {
        return new TransactionLineCommand(account, fund, null, activity, null,
                new BigDecimal(debit), new BigDecimal(credit), account == liability, "Explicit fact");
    }

    public TransactionSupplementalLineCommand allocation(UUID item, String effect, int index, String amount, Long party)
    {
        return new TransactionSupplementalLineCommand("OTHER_LIABILITY", "NMR-1", "Narrative only",
                "Explicit item", "external/reference", new BigDecimal(amount), null, null, null,
                "Synthetic evidence", 0, item, effect, index, party);
    }

    public TransactionCommand receipt(UUID item, Long party, Long activity)
    {
        return new TransactionCommand(DATE, null, "Mixed receipt", null, List.of(
                line(cash, "100", "0", activity), line(income, "0", "80", activity),
                line(liability, "0", "20", activity)), List.of(allocation(item, "INCREASE", 2, "20", party)));
    }

    public TransactionCommand payment(UUID item, String amount, Long party, Long activity)
    {
        return new TransactionCommand(DATE.plusDays(1), null, "Partial payment", null, List.of(
                line(liability, amount, "0", activity), line(cash, "0", amount, activity)),
                List.of(allocation(item, "DECREASE", 0, amount, party)));
    }

    public TransactionEntryService entry(Jpa jpa)
    {
        return new TransactionEntryService(jpa, () -> COMPANY);
    }
}
