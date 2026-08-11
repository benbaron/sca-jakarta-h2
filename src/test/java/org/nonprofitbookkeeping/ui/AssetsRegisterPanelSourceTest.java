package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Fund;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guardrails for Asset Register selector display labels. */
class AssetsRegisterPanelSourceTest
{
    @Test
    void accountAndFundLabelsUseBusinessCodesAndNames()
    {
        Account account = new Account();
        account.setCode("1500");
        account.setName("Equipment");
        Fund fund = new Fund();
        fund.setCode("GEN");
        fund.setName("General Fund");

        assertEquals("1500 — Equipment", AssetsRegisterPanel.accountLabel(account));
        assertEquals("GEN — General Fund", AssetsRegisterPanel.fundLabel(fund));
    }

    @Test
    void lifecycleActionsUseFrozenAsyncDomainWorkflowAndHistory() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/AssetsRegisterPanel.java"));

        assertTrue(source.contains("recordFixedAssetLifecycleButton"));
        assertTrue(source.contains("reverseFixedAssetLifecycleButton"));
        assertTrue(source.contains("fixed-asset-lifecycle-preview"));
        assertTrue(source.contains("fixed-asset-lifecycle-commit"));
        assertTrue(source.contains("fixed-asset-lifecycle-reversal-preview"));
        assertTrue(source.contains("fixed-asset-lifecycle-reversal-commit"));
        assertTrue(source.contains("previewLifecycleEvent("));
        assertTrue(source.contains("recordLifecycleEvent("));
        assertTrue(source.contains("previewLifecycleReversal("));
        assertTrue(source.contains("reverseLifecycleEvent("));
        assertTrue(source.contains("assetRegisterHistorySplit"));
        assertTrue(source.contains("No partial accounting was retained."));
        assertFalse(source.contains("FixedAsset.Status.DISPOSED, FixedAsset.Status.ACTIVE"));
    }
}
