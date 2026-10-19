package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.util.UUID;

/** Frozen non-mutating proposal. Final acceptance rebuilds eligibility and remaining source capacity. */
public record AssetAcquisitionPreview(UUID requestId, AssetAcquisitionCommand command,
                                     TransactionCommand transaction, String assetAccount, String sourceLabel,
                                     BigDecimal sourceDebit, BigDecimal alreadyClaimed, BigDecimal remaining,
                                     Long acceptedAssetId)
{
}
