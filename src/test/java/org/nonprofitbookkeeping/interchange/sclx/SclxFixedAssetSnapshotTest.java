package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetDepreciationRun;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxFixedAssetSnapshotTest
{
    @Test
    void exportsSelectedCompanyAssetsAndCompletedRunsWithPortableReferences()
    {
        Fixture fixture = fixture("ALPHA");
        SclxExportDocument document = assemble(fixture, true);

        SclxFixedAssetsExtension.Data data = SclxFixedAssetsExtension.data(document.extensions());
        assertEquals(1, data.assets().size());
        assertEquals(1, data.depreciationRuns().size());

        SclxFixedAssetsExtension.AssetEntry asset = data.assets().get(0);
        assertEquals(SclxPortableIdentity.fixedAsset(
                "ALPHA", fixture.asset().getPortableId().toString()), asset.assetId());
        assertEquals(SclxPortableIdentity.account("ALPHA", "1500"), asset.assetAccountId());
        assertEquals(SclxPortableIdentity.account("ALPHA", "1590"),
                asset.accumulatedDepreciationAccountId());
        assertEquals(SclxPortableIdentity.account("ALPHA", "6100"),
                asset.depreciationExpenseAccountId());
        assertEquals(SclxPortableIdentity.fund("ALPHA", "GENERAL"), asset.fundId());
        assertEquals("Storage Pavilion", asset.name());
        assertEquals(new BigDecimal("1250.0000"), asset.acquisitionCost());
        assertEquals(new BigDecimal("50.0000"), asset.salvageValue());
        assertEquals(60, asset.usefulLifeMonths());
        assertEquals("STRAIGHT_LINE", asset.depreciationMethod());
        assertEquals("ACTIVE", asset.status());

        SclxFixedAssetsExtension.DepreciationRunEntry run = data.depreciationRuns().get(0);
        assertEquals(SclxPortableIdentity.fixedAssetDepreciationRun(
                "ALPHA", fixture.run().getPortableId().toString()), run.depreciationRunId());
        assertEquals(asset.assetId(), run.assetId());
        assertEquals(document.transactions().get(0).transactionId(), run.transactionId());
        assertEquals(new BigDecimal("20.0000"), run.depreciationAmount());

        SclxExportCounts counts = SclxExportCounts.from(document, 0L, 0L);
        assertEquals(1, counts.fixedAssets());
        assertEquals(1, counts.depreciationRuns());

        String json = new String(new SclxJsonSerializer().serialize(document), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"fixedAssets\""));
        assertTrue(json.contains("\"depreciationRuns\""));
        assertFalse(json.contains("fixed_asset_id"));
    }

    @Test
    void rejectsRunWhoseCanonicalTransactionIsOutsideSnapshot()
    {
        Fixture fixture = fixture("ALPHA");
        assertThrows(IllegalArgumentException.class, () -> new SclxCoreSnapshotAssembler().assemble(
                fixture.company(),
                fixture.accounts(),
                List.of(fixture.fund()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                SclxBankingSnapshot.empty(),
                List.of(fixture.asset()),
                List.of(fixture.run()),
                List.of(),
                List.of(),
                Instant.EPOCH));
    }

    @Test
    void validatorRejectsDuplicateAssetIdentityAndUnresolvedRunReference()
    {
        Fixture fixture = fixture("ALPHA");
        SclxExportDocument base = assemble(fixture, true);
        SclxFixedAssetsExtension.Data data = SclxFixedAssetsExtension.data(base.extensions());

        Map<String, Object> values = new LinkedHashMap<>(base.extensions().scaJakartaH2());
        values.put(SclxFixedAssetsExtension.KEY, SclxFixedAssetsExtension.value(
                List.of(
                        SclxFixedAssetsExtension.assetEntry(
                                data.assets().get(0).assetId(),
                                "First", LocalDate.of(2026, 1, 1),
                                new BigDecimal("100.0000"), BigDecimal.ZERO, 36,
                                "STRAIGHT_LINE", BigDecimal.ZERO, "ACTIVE", null,
                                data.assets().get(0).assetAccountId(),
                                data.assets().get(0).accumulatedDepreciationAccountId(),
                                data.assets().get(0).depreciationExpenseAccountId(),
                                data.assets().get(0).fundId(),
                                Instant.EPOCH, Instant.EPOCH),
                        SclxFixedAssetsExtension.assetEntry(
                                data.assets().get(0).assetId(),
                                "Second", LocalDate.of(2026, 1, 2),
                                new BigDecimal("200.0000"), BigDecimal.ZERO, 36,
                                "STRAIGHT_LINE", BigDecimal.ZERO, "ACTIVE", null,
                                data.assets().get(0).assetAccountId(),
                                data.assets().get(0).accumulatedDepreciationAccountId(),
                                data.assets().get(0).depreciationExpenseAccountId(),
                                data.assets().get(0).fundId(),
                                Instant.EPOCH, Instant.EPOCH)),
                List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new SclxExportDocumentValidator().validate(copy(base, values)));

        values.put(SclxFixedAssetsExtension.KEY, SclxFixedAssetsExtension.value(
                List.of(),
                List.of(SclxFixedAssetsExtension.depreciationRunEntry(
                        "depreciation-run:ALPHA:" + UUID.randomUUID(),
                        "fixed-asset:ALPHA:missing",
                        LocalDate.of(2026, 2, 28),
                        new BigDecimal("10.0000"),
                        base.transactions().get(0).transactionId(),
                        null,
                        Instant.EPOCH))));
        assertThrows(IllegalArgumentException.class,
                () -> new SclxExportDocumentValidator().validate(copy(base, values)));
    }

    private static SclxExportDocument assemble(Fixture fixture, boolean includeRun)
    {
        return new SclxCoreSnapshotAssembler().assemble(
                fixture.company(),
                fixture.accounts(),
                List.of(fixture.fund()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(fixture.transaction()),
                fixture.splits(),
                List.of(),
                SclxBankingSnapshot.empty(),
                List.of(fixture.asset()),
                includeRun ? List.of(fixture.run()) : List.of(),
                List.of(),
                List.of(),
                Instant.parse("2026-03-01T00:00:00Z"));
    }

    private static SclxExportDocument copy(
            SclxExportDocument base,
            Map<String, Object> extensionValues)
    {
        return new SclxExportDocument(
                base.format(), base.version(), base.exportedAt(), base.organization(),
                base.chartOfAccounts(), base.funds(), base.budgets(), base.transactions(),
                new SclxExportDocument.Extensions(1, extensionValues));
    }

    private static Fixture fixture(String code)
    {
        Company company = new Company();
        company.setCode(code);
        company.setDisplayName(code + " Company");
        company.setDefaultCurrency("USD");
        company.setFiscalYearStartMonth(1);
        company.setFiscalYearStartDay(1);

        ChartOfAccounts chart = new ChartOfAccounts();
        chart.setCompany(company);
        chart.setName("Standard");
        chart.setVersion("1");
        company.setActiveChartOfAccounts(chart);

        Account assetAccount = account(chart, "1500", "Equipment", AccountType.ASSET,
                AccountSubtype.FIXED_ASSET, NormalBalance.DEBIT);
        Account accumulated = account(chart, "1590", "Accumulated depreciation", AccountType.ASSET,
                AccountSubtype.FIXED_ASSET, NormalBalance.CREDIT);
        Account expense = account(chart, "6100", "Depreciation expense", AccountType.EXPENSE,
                null, NormalBalance.DEBIT);
        Fund fund = new Fund();
        fund.setCompany(company);
        fund.setCode("GENERAL");
        fund.setName("General Fund");
        fund.setFundType(FundType.UNRESTRICTED);

        Txn transaction = new Txn();
        transaction.setCompany(company);
        transaction.setPortableId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        transaction.setTxnDate(LocalDate.of(2026, 2, 28));
        transaction.setMemo("February depreciation");
        TxnSplit debit = split(transaction, expense, fund, new BigDecimal("20.0000"));
        TxnSplit credit = split(transaction, accumulated, fund, new BigDecimal("20.0000"));

        FixedAsset asset = new FixedAsset();
        asset.setCompany(company);
        asset.setAssetAccount(assetAccount);
        asset.setAccumulatedDepreciationAccount(accumulated);
        asset.setDepreciationExpenseAccount(expense);
        asset.setFund(fund);
        asset.setName("Storage Pavilion");
        asset.setAcquisitionDate(LocalDate.of(2026, 1, 15));
        asset.setAcquisitionCost(new BigDecimal("1250.0000"));
        asset.setSalvageValue(new BigDecimal("50.0000"));
        asset.setUsefulLifeMonths(60);
        asset.setDepreciationMethod(FixedAsset.DepreciationMethod.STRAIGHT_LINE);
        asset.setOpeningAccumulatedDepreciation(new BigDecimal("100.0000"));
        asset.setStatus(FixedAsset.Status.ACTIVE);
        asset.setNotes("Fictional test asset");

        FixedAssetDepreciationRun run = new FixedAssetDepreciationRun();
        run.setFixedAsset(asset);
        run.setRunDate(LocalDate.of(2026, 2, 28));
        run.setDepreciationAmount(new BigDecimal("20.0000"));
        run.setTransaction(transaction);
        run.setNotes("February completed run");

        return new Fixture(
                company,
                List.of(assetAccount, accumulated, expense),
                fund,
                transaction,
                List.of(debit, credit),
                asset,
                run);
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

    private record Fixture(
            Company company,
            List<Account> accounts,
            Fund fund,
            Txn transaction,
            List<TxnSplit> splits,
            FixedAsset asset,
            FixedAssetDepreciationRun run)
    {
    }
}
