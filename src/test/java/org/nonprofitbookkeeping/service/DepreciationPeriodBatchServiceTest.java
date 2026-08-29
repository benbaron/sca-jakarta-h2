package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.FixedAsset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepreciationPeriodBatchServiceTest
{
    private static final LocalDate START = LocalDate.of(2026, 8, 10);
    private static final LocalDate END = LocalDate.of(2026, 9, 9);

    @Test
    void previewClassifiesPeriodHistoryAndExclusionsWithoutCreatingTransactions()
    {
        List<FixedAssetView> assets = List.of(
                asset(1L, "Eligible", LocalDate.of(2026, 1, 1), "25.00", FixedAsset.Status.ACTIVE),
                asset(2L, "Already", LocalDate.of(2026, 1, 1), "20.00", FixedAsset.Status.ACTIVE),
                asset(3L, "Later", LocalDate.of(2026, 1, 1), "15.00", FixedAsset.Status.ACTIVE),
                asset(4L, "Inactive", LocalDate.of(2026, 1, 1), "10.00", FixedAsset.Status.INACTIVE),
                asset(5L, "Future", LocalDate.of(2026, 9, 10), "10.00", FixedAsset.Status.ACTIVE),
                asset(6L, "Complete", LocalDate.of(2026, 1, 1), "0.00", FixedAsset.Status.ACTIVE));
        List<DepreciationRunView> runs = List.of(
                run(20L, 2L, "Already", LocalDate.of(2026, 8, 31), "20.00", 200L),
                run(30L, 3L, "Later", LocalDate.of(2026, 10, 9), "15.00", 300L));
        AtomicInteger runCalls = new AtomicInteger();

        DepreciationPeriodBatchService service = new DepreciationPeriodBatchService(
                company -> assets,
                company -> runs,
                (assetId, date, notes) -> {
                    runCalls.incrementAndGet();
                    throw new AssertionError("preview must not run depreciation");
                });

        DepreciationPeriodBatchService.Preview preview = service.preview("DEFAULT", START, END);

        assertEquals(END, preview.postingDate());
        assertEquals(1, preview.eligibleCount());
        assertEquals(new BigDecimal("25.00"), preview.proposedTotal());
        assertEquals(DepreciationPeriodBatchService.Disposition.ELIGIBLE,
                item(preview, 1L).disposition());
        assertEquals(DepreciationPeriodBatchService.Disposition.ALREADY_RUN,
                item(preview, 2L).disposition());
        assertEquals(DepreciationPeriodBatchService.Disposition.LATER_RUN_EXISTS,
                item(preview, 3L).disposition());
        assertEquals(DepreciationPeriodBatchService.Disposition.INACTIVE,
                item(preview, 4L).disposition());
        assertEquals(DepreciationPeriodBatchService.Disposition.NOT_ACQUIRED,
                item(preview, 5L).disposition());
        assertEquals(DepreciationPeriodBatchService.Disposition.NO_REMAINING_BASIS,
                item(preview, 6L).disposition());
        assertEquals(0, runCalls.get());
    }

    @Test
    void executeKeepsIndependentSuccessesAndRetrySkipsThem()
    {
        List<FixedAssetView> assets = List.of(
                asset(1L, "One", LocalDate.of(2026, 1, 1), "25.00", FixedAsset.Status.ACTIVE),
                asset(2L, "Two", LocalDate.of(2026, 1, 1), "30.00", FixedAsset.Status.ACTIVE));
        AtomicReference<List<DepreciationRunView>> runs = new AtomicReference<>(List.of());
        AtomicInteger assetTwoAttempts = new AtomicInteger();

        DepreciationPeriodBatchService service = new DepreciationPeriodBatchService(
                company -> assets,
                company -> runs.get(),
                (assetId, date, notes) -> {
                    if (assetId == 2L && assetTwoAttempts.getAndIncrement() == 0)
                    {
                        throw new IllegalStateException("simulated asset-two failure");
                    }
                    DepreciationRunView completed = run(
                            assetId * 10,
                            assetId,
                            assetId == 1L ? "One" : "Two",
                            date,
                            assetId == 1L ? "25.00" : "30.00",
                            assetId * 100);
                    if (assetId == 1L)
                    {
                        runs.set(List.of(completed));
                    }
                    else
                    {
                        runs.set(List.of(
                                run(10L, 1L, "One", date, "25.00", 100L),
                                completed));
                    }
                    return completed;
                });

        DepreciationPeriodBatchService.Preview initial = service.preview("DEFAULT", START, END);
        DepreciationPeriodBatchService.Result first = service.execute(initial, "period run");

        assertEquals(1, first.committedCount());
        assertEquals(1, first.failedCount());
        assertEquals(0, first.skippedCount());
        assertTrue(first.items().stream().anyMatch(item ->
                item.assetId() == 2L
                        && item.outcome() == DepreciationPeriodBatchService.Outcome.FAILED
                        && item.message().contains("simulated asset-two failure")));

        DepreciationPeriodBatchService.Preview retryPreview = service.preview("DEFAULT", START, END);
        assertEquals(1, retryPreview.eligibleCount());
        assertEquals(DepreciationPeriodBatchService.Disposition.ALREADY_RUN,
                item(retryPreview, 1L).disposition());

        DepreciationPeriodBatchService.Result retry = service.execute(retryPreview, "retry");
        assertEquals(1, retry.committedCount());
        assertEquals(1, retry.skippedCount());
        assertEquals(0, retry.failedCount());
    }

    @Test
    void executeRevalidatesFrozenPreviewAndSkipsChangedAmounts()
    {
        AtomicReference<List<FixedAssetView>> assets = new AtomicReference<>(List.of(
                asset(1L, "Changed", LocalDate.of(2026, 1, 1), "25.00", FixedAsset.Status.ACTIVE)));
        AtomicInteger runCalls = new AtomicInteger();
        DepreciationPeriodBatchService service = new DepreciationPeriodBatchService(
                company -> assets.get(),
                company -> List.of(),
                (assetId, date, notes) -> {
                    runCalls.incrementAndGet();
                    return run(10L, assetId, "Changed", date, "30.00", 100L);
                });

        DepreciationPeriodBatchService.Preview preview = service.preview("DEFAULT", START, END);
        assets.set(List.of(
                asset(1L, "Changed", LocalDate.of(2026, 1, 1), "30.00", FixedAsset.Status.ACTIVE)));

        DepreciationPeriodBatchService.Result result = service.execute(preview, "stale");

        assertEquals(0, result.committedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(0, runCalls.get());
        assertTrue(result.items().get(0).message().contains("proposed depreciation changed"));
    }

    private static DepreciationPeriodBatchService.Item item(
            DepreciationPeriodBatchService.Preview preview,
            long assetId)
    {
        return preview.items().stream()
                .filter(item -> item.assetId() == assetId)
                .findFirst()
                .orElseThrow();
    }

    private static FixedAssetView asset(
            long id,
            String name,
            LocalDate acquisitionDate,
            String nextDepreciation,
            FixedAsset.Status status)
    {
        BigDecimal next = new BigDecimal(nextDepreciation);
        return new FixedAssetView(
                id,
                "DEFAULT",
                11L,
                "1500",
                "Fixed Assets",
                12L,
                "1590",
                "Accumulated Depreciation",
                13L,
                "6100",
                "Depreciation Expense",
                21L,
                "GENERAL",
                "General",
                name,
                acquisitionDate,
                new BigDecimal("1200.00"),
                BigDecimal.ZERO,
                60,
                FixedAsset.DepreciationMethod.STRAIGHT_LINE,
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("1100.00"),
                next,
                status,
                "");
    }

    private static DepreciationRunView run(
            long id,
            long assetId,
            String assetName,
            LocalDate date,
            String amount,
            long transactionId)
    {
        return new DepreciationRunView(
                id,
                assetId,
                assetName,
                date,
                new BigDecimal(amount),
                transactionId,
                "");
    }
}
