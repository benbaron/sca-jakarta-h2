package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source guardrails for committed-fact-only monthly depreciation UI behavior. */
class DepreciationRunsPanelSourceTest
{
    @Test
    void monthlyDepreciationUsesOneBusyAsyncOperationAndRefreshesAfterEitherOutcome() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/DepreciationRunsPanel.java"));

        assertTrue(source.contains("runMonthlyDepreciationButton"));
        assertTrue(source.contains("run.disableProperty().bind(busy.or("));
        assertTrue(source.contains("UiAsync.run(\"monthly-depreciation-run\""));
        assertTrue(source.contains("completed -> reload(\"Created committed depreciation transaction #\""));
        assertTrue(source.contains("ex -> reload(\"Could not run depreciation:"));
        assertTrue(source.contains("No partial depreciation activity was retained."));
        assertFalse(source.contains("DepreciationRunView run = UiServiceRegistry.fixedAssets().runMonthlyDepreciation"));
    }

    @Test
    void productionRegistrySuppliesTheActiveCompanyToTheAtomicService() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/UiServiceRegistry.java"));

        assertTrue(source.contains("new FixedAssetService(jpa, transactionEntry, UiServiceRegistry::activeCompanyCode)"));
    }
}
