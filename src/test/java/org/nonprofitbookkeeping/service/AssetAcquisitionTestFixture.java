package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetAcquisitionTestFixture(FundTransferTestFixture base, long asset, long accumulated)
{
    public static final String COMPANY = "DEFAULT";
    public static final LocalDate DATE = LocalDate.of(2026, 4, 1);

    public static AssetAcquisitionTestFixture seed(Jpa jpa)
    {
        var base = FundTransferTestFixture.seed(jpa);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            var chart = em.find(Account.class, base.bank()).getChart();
            Account asset = new Account();
            asset.setChart(chart); asset.setCode("1600"); asset.setName("Equipment");
            asset.setAccountType(AccountType.ASSET); asset.setSubtype(AccountSubtype.FIXED_ASSET);
            asset.setNormalBalance(NormalBalance.DEBIT); asset.setPosting(true); asset.setActive(true); em.persist(asset);
            Account accumulated = new Account();
            accumulated.setChart(chart); accumulated.setCode("1690"); accumulated.setName("Accumulated depreciation");
            accumulated.setAccountType(AccountType.ASSET); accumulated.setNormalBalance(NormalBalance.CREDIT);
            accumulated.setPosting(true); accumulated.setActive(true); em.persist(accumulated);
            em.getTransaction().commit();
            return new AssetAcquisitionTestFixture(base, asset.getId(), accumulated.getId());
        }
    }

    public FixedAssetCommand command(String name, String cost)
    {
        return new FixedAssetCommand(COMPANY, asset, accumulated, base.expense(), base.source(), name,
                DATE, new BigDecimal(cost), BigDecimal.ZERO, 60, FixedAsset.DepreciationMethod.STRAIGHT_LINE,
                BigDecimal.ZERO, FixedAsset.Status.ACTIVE, "Synthetic acquisition");
    }

    public FixedAssetService service(Jpa jpa)
    {
        return new FixedAssetService(jpa, new TransactionEntryService(jpa, () -> COMPANY), () -> COMPANY);
    }
}
