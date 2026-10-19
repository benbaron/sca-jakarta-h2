package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.AssetAcquisitionSource;

/** One explicit register/acquisition operation; existingAssetId binds without recreating an asset. */
public record AssetAcquisitionCommand(Long existingAssetId, FixedAssetCommand asset,
                                      AssetAcquisitionSource source, Long bankAccountId, Long splitId)
{
}
