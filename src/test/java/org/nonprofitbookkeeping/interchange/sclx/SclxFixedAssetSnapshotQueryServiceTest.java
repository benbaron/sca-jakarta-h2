package org.nonprofitbookkeeping.interchange.sclx;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetDepreciationRun;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SclxFixedAssetSnapshotQueryServiceTest
{
    @TempDir
    Path tempDir;

    @Test
    void loadsOnlySelectedCompanyAssetsAndCompletedRunsIncludingInactiveAssets()
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fixed-asset-snapshot")))
        {
            seed(jpa);

            SclxExportDocument document = new SclxCoreSnapshotQueryService(jpa, () -> "ALPHA")
                    .query(Instant.parse("2026-08-03T01:00:00Z"));
            SclxFixedAssetsExtension.Data data = SclxFixedAssetsExtension.data(document.extensions());

            assertEquals(List.of("Alpha Pavilion"),
                    data.assets().stream().map(SclxFixedAssetsExtension.AssetEntry::name).toList());
            assertEquals("INACTIVE", data.assets().get(0).status());
            assertEquals(1, data.depreciationRuns().size());
            assertEquals(data.assets().get(0).fixedAssetId(),
                    data.depreciationRuns().get(0).fixedAssetId());
            assertEquals(document.transactions().get(0).transactionId(),
                    data.depreciationRuns().get(0).transactionId());

            String json = new String(new SclxJsonSerializer().serialize(document),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(json.contains("Beta Pavilion"));
            assertFalse(json.contains("BETA depreciation"));
        }
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            seedCompany(em, "ALPHA", "Alpha Pavilion", FixedAsset.Status.INACTIVE);
            seedCompany(em, "BETA", "Beta Pavilion", FixedAsset.Status.ACTIVE);
            em.getTransaction().commit();
        }
    }

    private static void seedCompany(
            EntityManager em,
            String code,
            String assetName,
            FixedAsset.Status status)
    {
        Company company = company(code);
        em.persist(company);
        ChartOfAccounts chart = chart(company);
        em.persist(chart);
        company.setActiveChartOfAccounts(chart);

        Account assetAccount = account(chart, "1500", code + " Equipment",
                AccountType.ASSET, AccountSubtype.FIXED_ASSET, NormalBalance.DEBIT);
        Account accumulated = account(chart, "1590", code + " Accumulated Depreciation",
                AccountType.ASSET, AccountSubtype.FIXED_ASSET, NormalBalance.CREDIT);
        Account expense = account(chart, "6100", code + " Depreciation Expense",
                AccountType.EXPENSE, null, NormalBalance.DEBIT);
        em.persist(assetAccount);
        em.persist(accumulated);
        em.persist(expense);
        Fund fund = fund(company);
        em.persist(fund);

        Txn txn = new Txn();
        txn.setCompany(company);
        txn.setTxnDate(LocalDate.of(2026, 6, 30));
        txn.setMemo(code + " depreciation");
        em.persist(txn);
        em.persist(split(txn, expense, fund, "25.0000"));
        em.persist(split(txn, accumulated, fund, "25.0000"));

        FixedAsset asset = new FixedAsset();
        asset.setCompany(company);
        asset.setAssetAccount(assetAccount);
        asset.setAccumulatedDepreciationAccount(accumulated);
        asset.setDepreciationExpenseAccount(expense);
        asset.setFund(fund);
        asset.setName(assetName);
        asset.setAcquisitionDate(LocalDate.of(2026, 1, 15));
        asset.setAcquisitionCost(new BigDecimal("1800.0000"));
        asset.setSalvageValue(new BigDecimal("300.0000"));
        asset.setUsefulLifeMonths(60);
        asset.setDepreciationMethod(FixedAsset.DepreciationMethod.STRAIGHT_LINE);
        asset.setOpeningAccumulatedDepreciation(BigDecimal.ZERO);
        asset.setStatus(status);
        asset.setNotes(code + " fictional asset");
        em.persist(asset);

        FixedAssetDepreciationRun run = new FixedAssetDepreciationRun();
        run.setFixedAsset(asset);
        run.setRunDate(LocalDate.of(2026, 6, 30));
        run.setDepreciationAmount(new BigDecimal("25.0000"));
        run.setTransaction(txn);
        run.setNotes(code + " completed run");
        em.persist(run);
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

    private static ChartOfAccounts chart(Company company)
    {
        ChartOfAccounts chart = new ChartOfAccounts();
        chart.setCompany(company);
        chart.setName(company.getCode() + " Chart");
        chart.setVersion("1");
        chart.setStatus(ChartStatus.ACTIVE);
        return chart;
    }

    private static Account account(
            ChartOfAccounts chart,
            String code,
            String name,
            AccountType type,
            AccountSubtype subtype,
            NormalBalance normalBalance)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setSubtype(subtype);
        account.setNormalBalance(normalBalance);
        account.setOpeningBalance(BigDecimal.ZERO);
        return account;
    }

    private static Fund fund(Company company)
    {
        Fund fund = new Fund();
        fund.setCompany(company);
        fund.setCode("GENERAL");
        fund.setName(company.getCode() + " General Fund");
        fund.setFundType(FundType.UNRESTRICTED);
        return fund;
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
