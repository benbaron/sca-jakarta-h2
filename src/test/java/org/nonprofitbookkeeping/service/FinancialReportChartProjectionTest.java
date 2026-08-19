package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialReportChartProjectionTest
{
    @Test
    void activeCompanyChartSuppliesZeroActivityRowsAndHierarchy(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("financial-report-chart")))
        {
            seed(jpa);
            FinancialReportService service = new FinancialReportService(jpa, () -> "LOCAL");

            FinancialReportService.IncomeStatementReport income = service.incomeStatement(
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 6, 30),
                    null);

            assertEquals(1, income.income().size());
            assertEquals(2, income.expenses().size());
            assertTrue(income.expenses().stream().allMatch(row -> row.amount().signum() == 0));
            FinancialReportService.StatementRow allocation = income.expenses().get(0);
            assertEquals("Configured Expense Category", allocation.parentName());
            assertEquals("Configured Expenses Root", allocation.grandparentName());
            assertTrue(income.expenses().stream().noneMatch(row -> row.accountCode().equals("9990")));
        }
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();

            Company local = company("LOCAL", "Configured Local Group");
            Company other = company("OTHER", "Other Group");
            em.persist(local);
            em.persist(other);

            ChartOfAccounts localChart = chart(local, "Local Active", ChartStatus.ACTIVE);
            ChartOfAccounts localDraft = chart(local, "Local Draft", ChartStatus.DRAFT);
            ChartOfAccounts otherChart = chart(other, "Other Active", ChartStatus.ACTIVE);
            em.persist(localChart);
            em.persist(localDraft);
            em.persist(otherChart);
            local.setActiveChartOfAccounts(localChart);
            other.setActiveChartOfAccounts(otherChart);

            Account incomeRoot = account(localChart, "4000", "Configured Income", AccountType.INCOME, false);
            Account income = account(localChart, "4010", "Configured Income Category", AccountType.INCOME, true);
            income.setParent(incomeRoot);

            Account expenseRoot = account(localChart, "5000", "Configured Expenses Root", AccountType.EXPENSE, false);
            Account expenseCategory = account(localChart, "5100", "Configured Expense Category", AccountType.EXPENSE, false);
            expenseCategory.setParent(expenseRoot);
            Account operations = account(localChart, "5110", "Configured Operations", AccountType.EXPENSE, true);
            operations.setParent(expenseCategory);
            Account activities = account(localChart, "5120", "Configured Activities", AccountType.EXPENSE, true);
            activities.setParent(expenseCategory);

            em.persist(incomeRoot);
            em.persist(income);
            em.persist(expenseRoot);
            em.persist(expenseCategory);
            em.persist(operations);
            em.persist(activities);
            em.persist(account(localDraft, "9990", "Draft Chart Noise", AccountType.EXPENSE, true));
            em.persist(account(otherChart, "9991", "Other Company Noise", AccountType.EXPENSE, true));

            em.getTransaction().commit();
        }
    }

    private static Company company(String code, String name)
    {
        Company company = new Company();
        company.setCode(code);
        company.setDisplayName(name);
        company.setLegalName(name + " Legal Entity");
        company.setParentOrganization(name + " Parent");
        company.setDefaultCurrency("USD");
        company.setActive(true);
        return company;
    }

    private static ChartOfAccounts chart(Company company, String name, ChartStatus status)
    {
        ChartOfAccounts chart = new ChartOfAccounts();
        chart.setCompany(company);
        chart.setName(name);
        chart.setVersion("1");
        chart.setStatus(status);
        return chart;
    }

    private static Account account(
            ChartOfAccounts chart,
            String code,
            String name,
            AccountType type,
            boolean posting)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setNormalBalance(type == AccountType.EXPENSE
                ? NormalBalance.DEBIT : NormalBalance.CREDIT);
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setPosting(posting);
        account.setActive(true);
        return account;
    }
}
