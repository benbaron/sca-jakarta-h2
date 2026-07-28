package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.NormalBalance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SclxCoreSnapshotAssemblerTest
{
    private final SclxCoreSnapshotAssembler assembler = new SclxCoreSnapshotAssembler();

    @Test
    void assemblesCompanyOwnedCoreDataInCodeOrder()
    {
        Company company = company("TEST");
        ChartOfAccounts chart = activeChart(company);
        Account expense = account(chart, "6100", "Expense", AccountType.EXPENSE);
        Account cash = account(chart, "1010", "Cash", AccountType.ASSET);
        Fund general = fund(company, "GENERAL");

        SclxExportDocument document = assembler.assemble(
                company, List.of(expense, cash), List.of(general),
                Instant.parse("2026-07-26T21:30:00Z"));

        assertEquals("organization:TEST", document.organization().organizationId());
        assertEquals(List.of("1010", "6100"),
                document.chartOfAccounts().stream().map(SclxExportDocument.Account::code).toList());
        assertEquals("fund:TEST:GENERAL", document.funds().get(0).fundId());
        assertEquals("USD", document.chartOfAccounts().get(0).currency());
    }

    @Test
    void rejectsAccountFromAnotherChart()
    {
        Company company = company("TEST");
        activeChart(company);
        Company other = company("OTHER");
        ChartOfAccounts otherChart = activeChart(other);
        Account foreignAccount = account(otherChart, "1010", "Cash", AccountType.ASSET);

        assertThrows(IllegalArgumentException.class,
                () -> assembler.assemble(company, List.of(foreignAccount), List.of(), Instant.EPOCH));
    }

    @Test
    void rejectsFundFromAnotherCompany()
    {
        Company company = company("TEST");
        activeChart(company);
        Fund foreignFund = fund(company("OTHER"), "GENERAL");

        assertThrows(IllegalArgumentException.class,
                () -> assembler.assemble(company, List.of(), List.of(foreignFund), Instant.EPOCH));
    }


    @Test
    void rejectsActivityFromAnotherCompany()
    {
        Company company = company("TEST");
        activeChart(company);
        Activity foreignActivity = activity(company("OTHER"), "EVENT");

        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(
                company,
                List.of(),
                List.of(),
                List.of(foreignActivity),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Instant.EPOCH));
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

    private static ChartOfAccounts activeChart(Company company)
    {
        ChartOfAccounts chart = new ChartOfAccounts();
        chart.setCompany(company);
        chart.setName("Standard");
        chart.setVersion("1");
        company.setActiveChartOfAccounts(chart);
        return chart;
    }

    private static Account account(ChartOfAccounts chart, String code, String name, AccountType type)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setNormalBalance(type == AccountType.ASSET ? NormalBalance.DEBIT : NormalBalance.DEBIT);
        account.setOpeningBalance(BigDecimal.ZERO);
        return account;
    }


    private static Activity activity(Company company, String code)
    {
        Activity activity = new Activity();
        activity.setCompany(company);
        activity.setCode(code);
        activity.setName(code + " Activity");
        return activity;
    }

    private static Fund fund(Company company, String code)
    {
        Fund fund = new Fund();
        fund.setCompany(company);
        fund.setCode(code);
        fund.setName("General Fund");
        fund.setFundType(FundType.UNRESTRICTED);
        return fund;
    }
}
