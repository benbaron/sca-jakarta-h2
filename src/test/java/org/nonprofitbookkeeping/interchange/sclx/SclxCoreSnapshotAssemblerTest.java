package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.CounterpartyKind;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.Merchant;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;

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


    @Test
    void renamedActivityUsesCurrentCodeConsistentlyAcrossMasterAndTransactionReferences()
    {
        Company company = company("TEST");
        ChartOfAccounts chart = activeChart(company);
        Account cash = account(chart, "1010", "Cash", AccountType.ASSET);
        Account expense = account(chart, "6100", "Expense", AccountType.EXPENSE);
        Fund general = fund(company, "GENERAL");
        Activity activity = activity(company, "OLD-CODE");

        // Stable database identity is outside the SCLX DTO. Renaming changes the
        // current portable company+code address, and every export reference must
        // use that new address consistently.
        activity.setCode("NEW-CODE");
        activity.setName("Renamed Activity");

        Txn txn = new Txn();
        txn.setCompany(company);
        txn.setTxnDate(java.time.LocalDate.of(2026, 9, 1));
        txn.setMemo("Activity rename export");

        TxnSplit debit = new TxnSplit();
        debit.setTxn(txn);
        debit.setAccount(cash);
        debit.setFund(general);
        debit.setActivity(activity);
        debit.setAmountSigned(BigDecimal.TEN);

        TxnSplit credit = new TxnSplit();
        credit.setTxn(txn);
        credit.setAccount(expense);
        credit.setFund(general);
        credit.setAmountSigned(BigDecimal.TEN.negate());

        SclxExportDocument document = assembler.assemble(
                company,
                List.of(cash, expense),
                List.of(general),
                List.of(activity),
                List.of(),
                List.of(),
                List.of(txn),
                List.of(debit, credit),
                Instant.parse("2026-09-07T23:30:00Z"));

        SclxActivityExtension.Entry exported = SclxActivityExtension.entries(document.extensions()).get(0);
        assertEquals("activity:TEST:NEW-CODE", exported.activityId());
        assertEquals("NEW-CODE", exported.code());
        assertEquals("activity:TEST:NEW-CODE", document.transactions().get(0).lines().stream()
                .map(SclxExportDocument.TransactionLine::activityId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow());
    }

    @Test
    void rejectsCounterpartyFromAnotherCompany()
    {
        Company company = company("TEST");
        activeChart(company);
        Counterparty foreign = counterparty(company("OTHER"), "Foreign Vendor");

        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(
                company,
                List.of(),
                List.of(),
                List.of(),
                List.of(foreign),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Instant.EPOCH));
    }

    @Test
    void rejectsMerchantFromAnotherCompany()
    {
        Company company = company("TEST");
        activeChart(company);
        Merchant foreign = merchant(company("OTHER"), "Foreign Store");

        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(
                company,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(foreign),
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

    private static Counterparty counterparty(Company company, String name)
    {
        Counterparty counterparty = new Counterparty();
        counterparty.setCompany(company);
        counterparty.setDisplayName(name);
        counterparty.setKind(CounterpartyKind.ORG);
        return counterparty;
    }

    private static Merchant merchant(Company company, String name)
    {
        Merchant merchant = new Merchant();
        merchant.setCompany(company);
        merchant.setName(name);
        return merchant;
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
