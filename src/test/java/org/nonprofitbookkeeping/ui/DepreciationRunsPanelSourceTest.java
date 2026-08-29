package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source guardrails for accounting-period depreciation orchestration. */
class DepreciationRunsPanelSourceTest
{
    @Test
    void depreciationRunsUsesActivePeriodPreviewAndBatchOrchestrator() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/DepreciationRunsPanel.java"));

        assertTrue(source.contains("depreciationPeriodPreviewButton"));
        assertTrue(source.contains("runPeriodDepreciationButton"));
        assertTrue(source.contains("openDepreciationReportButton"));
        assertTrue(source.contains("DepreciationPeriodBatchService"));
        assertTrue(source.contains("UiAsync.run(\"depreciation-period-preview\""));
        assertTrue(source.contains("UiAsync.run(\"period-depreciation-run\""));
        assertTrue(source.contains("confirmBatch(preview)"));
        assertTrue(source.contains("ActivePeriodContext.periodStartFor("));
        assertTrue(source.contains("start.plusMonths(1).minusDays(1)"));
        assertTrue(source.contains("DateRangeContext.set(new DateRange(preview.periodStart(), preview.periodEnd()))"));
        assertTrue(source.contains("AppPanelId.REPORT_LIBRARY"));
        assertTrue(source.contains("Successful asset runs remain committed"));
        assertFalse(source.contains("new DatePicker(LocalDate.now())"));
        assertFalse(source.contains("runMonthlyDepreciationButton"));
    }

    @Test
    void batchServiceDelegatesActualWritesToAtomicFixedAssetOperation() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/service/DepreciationPeriodBatchService.java"));
        String registry = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/UiServiceRegistry.java"));

        assertTrue(source.contains("this.runner = fixedAssets::runMonthlyDepreciation"));
        assertTrue(source.contains("Disposition.ALREADY_RUN"));
        assertTrue(source.contains("Disposition.LATER_RUN_EXISTS"));
        assertTrue(source.contains("Preview changed before commit"));
        assertTrue(registry.contains(
                "new FixedAssetService(jpa, transactionEntry, UiServiceRegistry::activeCompanyCode)"));
        assertFalse(source.contains("EntityManager"));
        assertFalse(source.contains("new TransactionCommand"));
    }
}
