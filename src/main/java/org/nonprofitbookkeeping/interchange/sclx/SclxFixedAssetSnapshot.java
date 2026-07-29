package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetDepreciationRun;

import java.util.List;
import java.util.Objects;

/** Bounded selected-company fixed-asset graph used only for SCLX snapshot assembly. */
record SclxFixedAssetSnapshot(
        List<FixedAsset> assets,
        List<FixedAssetDepreciationRun> depreciationRuns)
{
    SclxFixedAssetSnapshot
    {
        assets = List.copyOf(Objects.requireNonNull(assets, "assets"));
        depreciationRuns = List.copyOf(Objects.requireNonNull(depreciationRuns, "depreciationRuns"));
    }

    static SclxFixedAssetSnapshot empty()
    {
        return new SclxFixedAssetSnapshot(List.of(), List.of());
    }
}
