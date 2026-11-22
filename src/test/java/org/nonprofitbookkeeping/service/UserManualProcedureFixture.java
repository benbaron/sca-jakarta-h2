package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.interchange.coa.*;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Synthetic records created through the manual's supported setup commands. */
record UserManualProcedureFixture(long company, long chart, long general, long designated,
        long checking, long savings, long income, long expense, long equity,
        long checkingBank, long savingsBank)
{
    static final String COMPANY = "MANUAL";
    static final LocalDate START = LocalDate.of(2026, 3, 1);
    static final LocalDate END = LocalDate.of(2026, 3, 31);

    static UserManualProcedureFixture setUp(Jpa jpa, Path directory) throws Exception
    {
        var companies = new CompanyAdminService(jpa);
        var company = companies.save(new CompanyCommand(null, COMPANY, "Manual Example Company",
                "Manual Example Company", "Practice", "Example Organization", "12-3456789",
                true, 7, 1, "USD"), "DEFAULT");
        long chart = importChart(jpa, directory.resolve("chart.json"), "Manual Chart");
        companies.assignActiveChart(company.id(), chart);
        var funds = new FundAdminService(jpa, () -> COMPANY);
        long general = funds.upsert("GENERAL", "General Fund", FundType.UNRESTRICTED, true).getId();
        long designated = funds.upsert("DESIGNATED", "Designated Fund", FundType.DESIGNATED, true).getId();
        var accounts = new AccountAdminService(jpa, () -> COMPANY);
        long checking = accountId(jpa, chart, "1000");
        long income = accountId(jpa, chart, "4000");
        long savings = accounts.upsert("1010", "Savings", AccountType.ASSET, AccountFunction.BANK,
                NormalBalance.DEBIT, AccountSubtype.CASH, null, true).getId();
        long expense = accounts.upsert("5000", "Expense", AccountType.EXPENSE,
                NormalBalance.DEBIT, null, null, true).getId();
        long equity = accounts.upsert("3000", "Opening net assets", AccountType.EQUITY,
                NormalBalance.CREDIT, null, null, true).getId();
        var banks = new BankConfigurationService(jpa);
        long checkingBank = configureBank(banks, "Example Checking Bank", checking);
        long savingsBank = configureBank(banks, "Example Savings Bank", savings);
        return new UserManualProcedureFixture(company.id(), chart, general, designated,
                checking, savings, income, expense, equity, checkingBank, savingsBank);
    }

    static long importChart(Jpa jpa, Path source, String name) throws Exception
    {
        Files.writeString(source, """
                {"format":"SCA-COA","version":"1.0",
                 "chart":{"name":"Practice","chartVersion":"2026","status":"DRAFT","currency":"USD"},
                 "accounts":[
                   {"code":"1000","name":"Operating Checking","type":"BANK","subtype":"CASH",
                    "normalBalance":"DEBIT","posting":true,"active":true,"openingBalance":"0.00"},
                   {"code":"4000","name":"Income","type":"INCOME",
                    "normalBalance":"CREDIT","posting":true,"active":true,"openingBalance":"0.00"}]}
                """);
        var preview = new ChartOfAccountsJsonService(jpa, () -> COMPANY).preview(
                new CoaImportRequest(source, CoaImportMode.CREATE_NEW_CHART, name, "2026", Map.of(), true));
        var result = new ChartOfAccountsJsonImportService(jpa, () -> COMPANY).commit(preview);
        if (!result.committed())
        {
            throw new AssertionError("Manual setup chart import failed: " + result.messages());
        }
        try (var em = jpa.em())
        {
            return em.createQuery("select c.id from ChartOfAccounts c where c.company.code=:company and c.name=:name", Long.class)
                    .setParameter("company", COMPANY).setParameter("name", name).getSingleResult();
        }
    }

    private static long configureBank(BankConfigurationService banks, String name, long account)
    {
        long bank = banks.createBank(new BankCommand(COMPANY, name, null, null, null,
                null, null, null, null, true)).getId();
        // Configuration metadata deliberately differs from a reviewed reconciliation opening.
        return banks.createBankAccount(new BankAccountCommand(COMPANY, bank, account, "****1234", name,
                LocalDate.of(2026, 1, 1), new BigDecimal("999.00"), BankingDataFormat.CSV,
                null, null, "Synthetic configuration", true)).getId();
    }

    static long accountId(Jpa jpa, long chart, String code)
    {
        try (var em = jpa.em())
        {
            return em.createQuery("select a.id from Account a where a.chart.id=:chart and a.code=:code", Long.class)
                    .setParameter("chart", chart).setParameter("code", code).getSingleResult();
        }
    }

    TransactionEntryService journal(Jpa jpa)
    {
        return new TransactionEntryService(jpa, () -> COMPANY);
    }

    TransactionView enter(Jpa jpa, LocalDate date, String memo, long debit, long credit, String amount)
    {
        return journal(jpa).enter(new TransactionCommand(date, null, memo, null,
                List.of(line(debit, general, amount, "0"), line(credit, general, "0", amount))));
    }

    static TransactionLineCommand line(long account, long fund, String debit, String credit)
    {
        return new TransactionLineCommand(account, fund, null, null, null,
                new BigDecimal(debit), new BigDecimal(credit), false, null);
    }

    static long count(Jpa jpa, String entity)
    {
        try (var em = jpa.em())
        {
            return em.createQuery("select count(x) from " + entity + " x", Long.class).getSingleResult();
        }
    }
}
