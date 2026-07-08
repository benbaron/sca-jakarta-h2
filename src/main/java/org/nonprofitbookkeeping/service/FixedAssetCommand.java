package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.FixedAsset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Command for creating or updating a fixed asset register record. */
public record FixedAssetCommand(String companyCode,
                                Long assetAccountId,
                                Long accumulatedDepreciationAccountId,
                                Long depreciationExpenseAccountId,
                                Long fundId,
                                String name,
                                LocalDate acquisitionDate,
                                BigDecimal acquisitionCost,
                                BigDecimal salvageValue,
                                int usefulLifeMonths,
                                FixedAsset.DepreciationMethod depreciationMethod,
                                BigDecimal openingAccumulatedDepreciation,
                                FixedAsset.Status status,
                                String notes)
{
}
