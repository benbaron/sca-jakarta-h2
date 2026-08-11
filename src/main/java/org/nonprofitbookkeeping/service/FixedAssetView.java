package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.FixedAsset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Projection for Asset Register rows. */
public record FixedAssetView(Long id,
                             String companyCode,
                             Long assetAccountId,
                             String assetAccountCode,
                             String assetAccountName,
                             Long accumulatedDepreciationAccountId,
                             String accumulatedDepreciationAccountCode,
                             String accumulatedDepreciationAccountName,
                             Long depreciationExpenseAccountId,
                             String depreciationExpenseAccountCode,
                             String depreciationExpenseAccountName,
                             Long fundId,
                             String fundCode,
                             String fundName,
                             String name,
                             LocalDate acquisitionDate,
                             BigDecimal acquisitionCost,
                             BigDecimal salvageValue,
                             int usefulLifeMonths,
                             FixedAsset.DepreciationMethod depreciationMethod,
                             BigDecimal openingAccumulatedDepreciation,
                             BigDecimal accumulatedDepreciation,
                             BigDecimal accumulatedImpairment,
                             BigDecimal currentBookValue,
                             BigDecimal nextDepreciationAmount,
                             FixedAsset.Status status,
                             String notes)
{
}
