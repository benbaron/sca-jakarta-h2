package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.FundTransferTestFixture.line;

class BalanceSheetContraAccountTest
{
    @Test
    void contraAssetsLiabilitiesAndEquityReduceTheirSectionsWhileTrialKeepsNaturalSides(@TempDir Path directory)
    {
        try (Jpa jpa = new Jpa(directory.resolve("contra")))
        {
            var f = AssetAcquisitionTestFixture.seed(jpa); var base = f.base();
            long contraLiability, contraEquity, liability;
            try (var em = jpa.em())
            {
                em.getTransaction().begin(); var chart = em.find(Account.class, base.bank()).getChart();
                var debt = account(chart, "2000", AccountType.LIABILITY, NormalBalance.CREDIT); em.persist(debt);
                var discount = account(chart, "2090", AccountType.LIABILITY, NormalBalance.DEBIT); em.persist(discount);
                var treasury = account(chart, "3090", AccountType.EQUITY, NormalBalance.DEBIT); em.persist(treasury);
                em.flush(); liability = debt.getId(); contraLiability = discount.getId(); contraEquity = treasury.getId(); em.getTransaction().commit();
            }
            var date = LocalDate.of(2026, 4, 1);
            new TransactionEntryService(jpa).enter(new TransactionCommand(date, null, "Synthetic contra balances", null, List.of(
                    line(f.asset(), base.source(), "100", "0"), line(f.accumulated(), base.source(), "0", "10"),
                    line(contraLiability, base.source(), "5", "0"), line(contraEquity, base.source(), "3", "0"),
                    line(liability, base.source(), "0", "98"))));
            var service = new FinancialReportService(jpa, () -> "DEFAULT");
            var balance = service.balanceSheet(date, null); assertTrue(balance.isBalanced());
            assertEquals(0, new BigDecimal("290").compareTo(balance.totalAssets()));
            assertEquals(0, new BigDecimal("93").compareTo(balance.totalLiabilities()));
            assertEquals(0, new BigDecimal("197").compareTo(balance.totalEquity()));
            var trial = service.trialBalance(date, null); assertTrue(trial.isBalanced());
            assertEquals(0, BigDecimal.TEN.compareTo(trial.rows().stream().filter(r -> r.accountCode().equals("1690")).findFirst().orElseThrow().credit()));
            assertTrue(service.balanceSheet(date.minusDays(1), null).isBalanced());
            assertTrue(service.balanceSheet(date, "GENERAL").isBalanced());
        }
    }

    private static Account account(ChartOfAccounts chart, String code, AccountType type, NormalBalance normal)
    {
        var account = new Account(); account.setChart(chart); account.setCode(code); account.setName(code);
        account.setAccountType(type); account.setNormalBalance(normal); account.setPosting(true); account.setActive(true); return account;
    }
}
