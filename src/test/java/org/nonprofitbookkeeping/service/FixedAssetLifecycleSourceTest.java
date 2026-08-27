package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source guardrails for P17-C7 fixed-asset lifecycle serialization. */
class FixedAssetLifecycleSourceTest
{
    @Test
    void interactiveStatusMetadataAndDepreciationShareTheAssetLock() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/service/FixedAssetService.java"));
        String token = "FixedAsset.class, assetId, LockModeType.PESSIMISTIC_WRITE";
        int occurrences = source.split(Pattern.quote(token), -1).length - 1;

        assertTrue(occurrences >= 3, "update, status change, and depreciation must lock the asset");
        assertTrue(source.contains("Fixed asset status changes use the explicit lifecycle action"));
        assertTrue(source.contains("FIXED_ASSET_STATUS_CHANGED"));
        assertTrue(source.contains("public FixedAsset createForImport("));
        assertTrue(source.contains("asset.initializeImportMetadata(portableId, createdAt, updatedAt)"));
    }
}
