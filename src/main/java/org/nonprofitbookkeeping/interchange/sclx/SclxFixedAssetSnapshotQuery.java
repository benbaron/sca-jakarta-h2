package org.nonprofitbookkeeping.interchange.sclx;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetDepreciationRun;

import java.util.List;
import java.util.Objects;

/** Loads the selected company's fixed assets and completed depreciation runs. */
final class SclxFixedAssetSnapshotQuery
{
    SclxFixedAssetSnapshot query(EntityManager em, Company company)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");

        List<FixedAsset> assets = em.createQuery(
                        "select a from FixedAsset a "
                                + "join fetch a.company "
                                + "join fetch a.assetAccount assetAccount "
                                + "join fetch assetAccount.chart "
                                + "join fetch a.accumulatedDepreciationAccount accumulatedAccount "
                                + "join fetch accumulatedAccount.chart "
                                + "join fetch a.depreciationExpenseAccount expenseAccount "
                                + "join fetch expenseAccount.chart "
                                + "join fetch a.fund "
                                + "where a.company = :company order by a.portableId",
                        FixedAsset.class)
                .setParameter("company", company)
                .getResultList();
        List<FixedAssetDepreciationRun> runs = em.createQuery(
                        "select r from FixedAssetDepreciationRun r "
                                + "join fetch r.fixedAsset a "
                                + "join fetch a.company "
                                + "join fetch r.transaction t "
                                + "where a.company = :company order by r.portableId",
                        FixedAssetDepreciationRun.class)
                .setParameter("company", company)
                .getResultList();
        return new SclxFixedAssetSnapshot(assets, runs);
    }
}
