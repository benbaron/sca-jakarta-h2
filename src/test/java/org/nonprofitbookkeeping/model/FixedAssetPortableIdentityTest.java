package org.nonprofitbookkeeping.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FixedAssetPortableIdentityTest
{
    @Test
    void newFixedAssetsReceiveDistinctPortableIdentities()
    {
        assertDistinct(new FixedAsset().getPortableId(), new FixedAsset().getPortableId());
    }

    @Test
    void newDepreciationRunsReceiveDistinctPortableIdentities()
    {
        assertDistinct(new FixedAssetDepreciationRun().getPortableId(),
                new FixedAssetDepreciationRun().getPortableId());
    }

    private static void assertDistinct(UUID first, UUID second)
    {
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
    }
}
