package org.nonprofitbookkeeping.interchange.sclx;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.BudgetLine;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxFinancialSnapshotQueryServiceTest
{
    @TempDir
    Path tempDir;

    @Test
    void mapsSelectedCompanyBudgetsAndCanonicalTransactions()
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("financial-snapshot")))
        {
            seed(jpa);

            SclxExportDocument document = new SclxCoreSnapshotQueryService(jpa, () -> "ALPHA")
                    .query(Instant.parse("2026-07-27T02:00:00Z"));

            assertEquals(1, document.budgets().size());
            SclxExportDocument.Budget budget = document.budgets().get(0);
            assertEquals("budget:ALPHA:2026:ADOPTED", budget.budgetId());
            assertTrue(budget.active());
            assertEquals(List.of("2026-07", "2026-08"),
                    budget.lines().stream().map(SclxExportDocument.BudgetLine::periodMonth).toList());
            assertNotEquals(budget.lines().get(0).lineId(), budget.lines().get(1).lineId());

            List<SclxActivityExtension.Entry> activities = SclxActivityExtension.entries(document.extensions());
            assertEquals(List.of("EVENT", "OLD"),
                    activities.stream().map(SclxActivityExtension.Entry::code).toList());
            assertTrue(activities.get(0).active());
            assertFalse(activities.get(1).active());

            assertEquals(2, document.transactions().size());
            SclxExportDocument.Transaction original = document.transactions().get(0);
            SclxExportDocument.Transaction reversal = document.transactions().get(1);
            assertEquals("Office supplies", original.description());
            assertEquals("ENTERED", original.status());
            assertEquals(List.of("1010", "6100"), original.lines().stream()
                    .map(SclxExportDocument.TransactionLine::accountId)
                    .map(id -> id.substring(id.lastIndexOf(':') + 1))
                    .toList());
            assertEquals(new BigDecimal("25.0000"), original.lines().get(0).credit());
            assertEquals(new BigDecimal("25.0000"), original.lines().get(1).debit());
            assertEquals("activity:ALPHA:EVENT", original.lines().get(1).activityId());
            assertEquals("REVERSAL", reversal.correctionType());
            assertEquals(original.transactionId(), reversal.correctionOfTransactionId());

            assertTrue(document.budgets().stream().noneMatch(item -> item.name().contains("BETA")));
            assertTrue(document.transactions().stream().noneMatch(item -> item.description().contains("BETA")));
            assertTrue(activities.stream().noneMatch(item -> item.code().contains("BETA")));
        }
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company alpha = company("ALPHA");
            Company beta = company("BETA");
            em.persist(alpha);
            em.persist(beta);

            ChartOfAccounts alphaChart = chart(alpha, "Alpha Chart");
            ChartOfAccounts betaChart = chart(beta, "Beta Chart");
            em.persist(alphaChart);
            em.persist(betaChart);
            alpha.setActiveChartOfAccounts(alphaChart);
            beta.setActiveChartOfAccounts(betaChart);

            Account alphaCash = account(alphaChart, "1010", "Cash", AccountType.ASSET, NormalBalance.DEBIT);
            Account alphaExpense = account(alphaChart, "6100", "Supplies", AccountType.EXPENSE, NormalBalance.DEBIT);
            Account betaCash = account(betaChart, "1010", "BETA Cash", AccountType.ASSET, NormalBalance.DEBIT);
            Account betaExpense = account(betaChart, "6100", "BETA Supplies", AccountType.EXPENSE, NormalBalance.DEBIT);
            em.persist(alphaCash);
            em.persist(alphaExpense);
            em.persist(betaCash);
            em.persist(betaExpense);

            Fund alphaFund = fund(alpha, "GENERAL");
            Fund betaFund = fund(beta, "GENERAL");
            em.persist(alphaFund);
            em.persist(betaFund);

            Activity alphaEvent = activity(alpha, "EVENT", "Annual Event", true);
            Activity alphaOld = activity(alpha, "OLD", "Retired Event", false);
            Activity betaEvent = activity(beta, "BETA-EVENT", "BETA Event", true);
            em.persist(alphaEvent);
            em.persist(alphaOld);
            em.persist(betaEvent);

            BudgetCategory alphaCategory = category(alpha, "SUPPLIES");
            BudgetCategory betaCategory = category(beta, "SUPPLIES");
            em.persist(alphaCategory);
            em.persist(betaCategory);

            BudgetPlan alphaBudget = budget(alpha, "2026 Operating", "ADOPTED");
            BudgetPlan betaBudget = budget(beta, "BETA 2026", "ADOPTED");
            em.persist(alphaBudget);
            em.persist(betaBudget);
            em.persist(budgetLine(alphaBudget, alphaCategory, alphaFund, YearMonth.of(2026, 8), "600.0000"));
            em.persist(budgetLine(alphaBudget, alphaCategory, alphaFund, YearMonth.of(2026, 7), "500.0000"));
            em.persist(budgetLine(betaBudget, betaCategory, betaFund, YearMonth.of(2026, 7), "999.0000"));

            Txn original = transaction(alpha, LocalDate.of(2026, 7, 1), "Office supplies");
            em.persist(original);
            em.persist(split(original, alphaCash, alphaFund, "-25.0000"));
            TxnSplit originalExpense = split(original, alphaExpense, alphaFund, "25.0000");
            originalExpense.setActivity(alphaEvent);
            em.persist(originalExpense);

            Txn reversal = transaction(alpha, LocalDate.of(2026, 7, 2), "Office supplies reversal");
            reversal.setReversalOf(original);
            em.persist(reversal);
            em.persist(split(reversal, alphaCash, alphaFund, "25.0000"));
            em.persist(split(reversal, alphaExpense, alphaFund, "-25.0000"));

            Txn betaTransaction = transaction(beta, LocalDate.of(2026, 7, 1), "BETA transaction");
            em.persist(betaTransaction);
            em.persist(split(betaTransaction, betaCash, betaFund, "-10.0000"));
            em.persist(split(betaTransaction, betaExpense, betaFund, "10.0000"));

            em.getTransaction().commit();
        }
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

    private static ChartOfAccounts chart(Company company, String name)
    {
        ChartOfAccounts chart = new ChartOfAccounts();
        chart.setCompany(company);
        chart.setName(name);
        chart.setVersion("1");
        chart.setStatus(ChartStatus.ACTIVE);
        return chart;
    }

    private static Account account(
            ChartOfAccounts chart,
            String code,
            String name,
            AccountType type,
            NormalBalance normalBalance)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setNormalBalance(normalBalance);
        account.setOpeningBalance(BigDecimal.ZERO);
        return account;
    }

    private static Fund fund(Company company, String code)
    {
        Fund fund = new Fund();
        fund.setCompany(company);
        fund.setCode(code);
        fund.setName(code + " Fund");
        fund.setFundType(FundType.UNRESTRICTED);
        return fund;
    }

    private static Activity activity(
            Company company,
            String code,
            String name,
            boolean active)
    {
        Activity activity = new Activity();
        activity.setCompany(company);
        activity.setCode(code);
        activity.setName(name);
        activity.setActive(active);
        return activity;
    }

    private static BudgetCategory category(Company company, String code)
    {
        BudgetCategory category = new BudgetCategory();
        category.setCompany(company);
        category.setCode(code);
        category.setName(code);
        return category;
    }

    private static BudgetPlan budget(Company company, String name, String version)
    {
        BudgetPlan plan = new BudgetPlan();
        plan.setCompany(company);
        plan.setName(name);
        plan.setFiscalYear(2026);
        plan.setVersionCode(version);
        plan.setStatus(BudgetPlan.Status.ACTIVE);
        plan.setActivatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        plan.setPeriodStart(LocalDate.of(2026, 1, 1));
        plan.setPeriodEnd(LocalDate.of(2026, 12, 31));
        return plan;
    }

    private static BudgetLine budgetLine(
            BudgetPlan plan,
            BudgetCategory category,
            Fund fund,
            YearMonth month,
            String amount)
    {
        BudgetLine line = new BudgetLine();
        line.setBudgetPlan(plan);
        line.setBudgetCategory(category);
        line.setFund(fund);
        line.setPeriodMonth(month);
        line.setAmount(new BigDecimal(amount));
        return line;
    }

    private static Txn transaction(Company company, LocalDate date, String memo)
    {
        Txn txn = new Txn();
        txn.setCompany(company);
        txn.setTxnDate(date);
        txn.setMemo(memo);
        txn.setStatus("ENTERED");
        return txn;
    }

    private static TxnSplit split(Txn txn, Account account, Fund fund, String amount)
    {
        TxnSplit split = new TxnSplit();
        split.setTxn(txn);
        split.setAccount(account);
        split.setFund(fund);
        split.setAmountSigned(new BigDecimal(amount));
        return split;
    }
}
