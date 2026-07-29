package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetDepreciationRun;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.Txn;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Maps the selected-company fixed-asset graph into the governed extension value. */
final class SclxFixedAssetSnapshotAssembler
{
    Map<String, Object> assemble(
            String companyCode,
            Company company,
            ChartOfAccounts activeChart,
            SclxFixedAssetSnapshot snapshot,
            Set<Txn> includedTransactions,
            Map<Txn, String> exportedTransactionIds)
    {
        Objects.requireNonNull(companyCode, "companyCode");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(activeChart, "activeChart");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(includedTransactions, "includedTransactions");
        Objects.requireNonNull(exportedTransactionIds, "exportedTransactionIds");

        Set<FixedAsset> includedAssets = identitySet(snapshot.assets());
        List<Map<String, Object>> assets = snapshot.assets().stream()
                .peek(asset -> requireAssetOwnership(asset, company, activeChart))
                .sorted(Comparator.comparing(asset -> asset.getPortableId().toString()))
                .map(asset -> SclxFixedAssetsExtension.assetEntry(
                        SclxPortableIdentity.fixedAsset(companyCode, asset.getPortableId().toString()),
                        accountId(companyCode, asset.getAssetAccount()),
                        accountId(companyCode, asset.getAccumulatedDepreciationAccount()),
                        accountId(companyCode, asset.getDepreciationExpenseAccount()),
                        fundId(companyCode, asset.getFund()),
                        asset.getName(),
                        asset.getAcquisitionDate(),
                        asset.getAcquisitionCost(),
                        asset.getSalvageValue(),
                        asset.getUsefulLifeMonths(),
                        Objects.requireNonNull(asset.getDepreciationMethod(), "depreciationMethod").name(),
                        asset.getOpeningAccumulatedDepreciation(),
                        Objects.requireNonNull(asset.getStatus(), "fixed asset status").name(),
                        asset.getNotes(),
                        asset.getCreatedAt(),
                        asset.getUpdatedAt()))
                .toList();

        List<Map<String, Object>> runs = snapshot.depreciationRuns().stream()
                .peek(run -> requireRunOwnership(
                        run, company, includedAssets, includedTransactions, exportedTransactionIds))
                .sorted(Comparator.comparing(run -> run.getPortableId().toString()))
                .map(run -> SclxFixedAssetsExtension.depreciationRunEntry(
                        SclxPortableIdentity.depreciationRun(
                                companyCode,
                                run.getPortableId().toString()),
                        SclxPortableIdentity.fixedAsset(
                                companyCode,
                                run.getFixedAsset().getPortableId().toString()),
                        run.getRunDate(),
                        run.getDepreciationAmount(),
                        Objects.requireNonNull(
                                exportedTransactionIds.get(run.getTransaction()),
                                "exported depreciation transaction identity"),
                        run.getNotes(),
                        run.getCreatedAt()))
                .toList();

        return SclxFixedAssetsExtension.value(assets, runs);
    }

    private static void requireAssetOwnership(
            FixedAsset asset,
            Company company,
            ChartOfAccounts activeChart)
    {
        Objects.requireNonNull(asset, "fixed asset");
        if (asset.getCompany() != company)
        {
            throw new IllegalArgumentException("fixed asset is outside the selected company");
        }
        Objects.requireNonNull(asset.getPortableId(), "fixed asset portableId");
        requireAccountOwnership(asset.getAssetAccount(), activeChart, "asset account");
        requireAccountOwnership(
                asset.getAccumulatedDepreciationAccount(), activeChart, "accumulated depreciation account");
        requireAccountOwnership(
                asset.getDepreciationExpenseAccount(), activeChart, "depreciation expense account");
        Fund fund = Objects.requireNonNull(asset.getFund(), "fixed asset fund");
        if (fund.getCompany() != company)
        {
            throw new IllegalArgumentException("fixed asset fund is outside the selected company");
        }
    }

    private static void requireRunOwnership(
            FixedAssetDepreciationRun run,
            Company company,
            Set<FixedAsset> includedAssets,
            Set<Txn> includedTransactions,
            Map<Txn, String> exportedTransactionIds)
    {
        Objects.requireNonNull(run, "depreciation run");
        Objects.requireNonNull(run.getPortableId(), "depreciation run portableId");
        FixedAsset asset = Objects.requireNonNull(run.getFixedAsset(), "depreciation run fixed asset");
        if (!includedAssets.contains(asset) || asset.getCompany() != company)
        {
            throw new IllegalArgumentException(
                    "depreciation run references a fixed asset outside the exported snapshot");
        }
        Txn transaction = Objects.requireNonNull(run.getTransaction(), "depreciation run transaction");
        if (!includedTransactions.contains(transaction) || transaction.getCompany() != company)
        {
            throw new IllegalArgumentException(
                    "depreciation run references a transaction outside the exported snapshot");
        }
        if (!exportedTransactionIds.containsKey(transaction))
        {
            throw new IllegalArgumentException(
                    "depreciation run transaction has no exported portable identity");
        }
    }

    private static void requireAccountOwnership(
            Account account,
            ChartOfAccounts activeChart,
            String field)
    {
        Objects.requireNonNull(account, field);
        if (account.getChart() != activeChart)
        {
            throw new IllegalArgumentException(field + " is outside the selected company's active chart");
        }
    }

    private static String accountId(String companyCode, Account account)
    {
        return SclxPortableIdentity.account(
                companyCode,
                Objects.requireNonNull(account, "account").getCode());
    }

    private static String fundId(String companyCode, Fund fund)
    {
        return SclxPortableIdentity.fund(
                companyCode,
                Objects.requireNonNull(fund, "fund").getCode());
    }

    private static <T> Set<T> identitySet(List<T> values)
    {
        Set<T> result = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(values);
        return result;
    }
}
