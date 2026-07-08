package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Projection for completed fixed asset depreciation runs. */
public record DepreciationRunView(Long id,
                                  Long fixedAssetId,
                                  String assetName,
                                  LocalDate runDate,
                                  BigDecimal depreciationAmount,
                                  Long transactionId,
                                  String notes)
{
}
