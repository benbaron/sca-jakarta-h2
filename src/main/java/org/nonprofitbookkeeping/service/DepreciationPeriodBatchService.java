package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.FixedAsset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Accounting-period orchestration around the authoritative per-asset depreciation operation.
 *
 * Each committed asset remains an independently atomic {@link FixedAssetService#runMonthlyDepreciation(long, LocalDate, String)}
 * operation. The batch does not create a second transaction writer or batch persistence model.
 */
public final class DepreciationPeriodBatchService
{
    private final Function<String, List<FixedAssetView>> assetLoader;
    private final Function<String, List<DepreciationRunView>> runLoader;
    private final DepreciationRunner runner;

    public DepreciationPeriodBatchService(FixedAssetService fixedAssets)
    {
        Objects.requireNonNull(fixedAssets, "fixedAssets");
        this.assetLoader = fixedAssets::listAssets;
        this.runLoader = fixedAssets::listDepreciationRuns;
        this.runner = fixedAssets::runMonthlyDepreciation;
    }

    DepreciationPeriodBatchService(
            Function<String, List<FixedAssetView>> assetLoader,
            Function<String, List<DepreciationRunView>> runLoader,
            DepreciationRunner runner)
    {
        this.assetLoader = Objects.requireNonNull(assetLoader, "assetLoader");
        this.runLoader = Objects.requireNonNull(runLoader, "runLoader");
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    public Preview preview(String companyCode, LocalDate periodStart, LocalDate periodEnd)
    {
        String company = requireCompanyCode(companyCode);
        requirePeriod(periodStart, periodEnd);

        Map<Long, DepreciationRunView> completedInPeriod = new LinkedHashMap<>();
        for (DepreciationRunView run : runLoader.apply(company))
        {
            if (run.runDate() != null
                    && !run.runDate().isBefore(periodStart)
                    && !run.runDate().isAfter(periodEnd))
            {
                completedInPeriod.putIfAbsent(run.fixedAssetId(), run);
            }
        }

        List<Item> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (FixedAssetView asset : assetLoader.apply(company))
        {
            DepreciationRunView completed = completedInPeriod.get(asset.id());
            Item item = classify(asset, completed, periodEnd);
            items.add(item);
            if (item.disposition() == Disposition.ELIGIBLE && item.proposedAmount() != null)
            {
                total = total.add(item.proposedAmount());
            }
        }
        return new Preview(company, periodStart, periodEnd, periodEnd, List.copyOf(items), total);
    }

    public Result execute(Preview requestedPreview, String notes)
    {
        Objects.requireNonNull(requestedPreview, "requestedPreview");
        Preview current = preview(
                requestedPreview.companyCode(),
                requestedPreview.periodStart(),
                requestedPreview.periodEnd());
        Map<Long, Item> currentByAsset = new LinkedHashMap<>();
        for (Item item : current.items())
        {
            currentByAsset.put(item.assetId(), item);
        }

        List<ExecutionItem> results = new ArrayList<>();
        for (Item requested : requestedPreview.items())
        {
            if (requested.disposition() != Disposition.ELIGIBLE)
            {
                results.add(new ExecutionItem(
                        requested.assetId(),
                        requested.assetName(),
                        Outcome.SKIPPED,
                        null,
                        requested.reason()));
                continue;
            }

            Item now = currentByAsset.get(requested.assetId());
            if (now == null)
            {
                results.add(new ExecutionItem(
                        requested.assetId(),
                        requested.assetName(),
                        Outcome.SKIPPED,
                        null,
                        "Asset is no longer present in the active company."));
                continue;
            }
            if (now.disposition() != Disposition.ELIGIBLE)
            {
                results.add(new ExecutionItem(
                        now.assetId(),
                        now.assetName(),
                        Outcome.SKIPPED,
                        null,
                        "Preview changed before commit: " + now.reason()));
                continue;
            }
            if (requested.proposedAmount() == null
                    || now.proposedAmount() == null
                    || requested.proposedAmount().compareTo(now.proposedAmount()) != 0)
            {
                results.add(new ExecutionItem(
                        now.assetId(),
                        now.assetName(),
                        Outcome.SKIPPED,
                        null,
                        "Preview changed before commit: proposed depreciation changed from "
                                + requested.proposedAmount() + " to " + now.proposedAmount() + "."));
                continue;
            }

            try
            {
                DepreciationRunView completed = runner.run(
                        requested.assetId(),
                        requestedPreview.postingDate(),
                        notes);
                results.add(new ExecutionItem(
                        requested.assetId(),
                        requested.assetName(),
                        Outcome.COMMITTED,
                        completed,
                        "Committed transaction " + completed.transactionId() + "."));
            }
            catch (RuntimeException ex)
            {
                results.add(new ExecutionItem(
                        requested.assetId(),
                        requested.assetName(),
                        Outcome.FAILED,
                        null,
                        safeMessage(ex)));
            }
        }
        return new Result(requestedPreview, List.copyOf(results));
    }

    private static Item classify(
            FixedAssetView asset,
            DepreciationRunView completed,
            LocalDate postingDate)
    {
        if (completed != null)
        {
            return new Item(
                    asset.id(), asset.name(), asset.status(), asset.acquisitionDate(),
                    asset.currentBookValue(), asset.nextDepreciationAmount(),
                    Disposition.ALREADY_RUN,
                    "Completed on " + completed.runDate() + " in transaction "
                            + completed.transactionId() + ".");
        }
        if (asset.status() != FixedAsset.Status.ACTIVE)
        {
            return new Item(
                    asset.id(), asset.name(), asset.status(), asset.acquisitionDate(),
                    asset.currentBookValue(), asset.nextDepreciationAmount(),
                    Disposition.INACTIVE,
                    "Asset status is " + asset.status() + ".");
        }
        if (asset.acquisitionDate() != null && asset.acquisitionDate().isAfter(postingDate))
        {
            return new Item(
                    asset.id(), asset.name(), asset.status(), asset.acquisitionDate(),
                    asset.currentBookValue(), asset.nextDepreciationAmount(),
                    Disposition.NOT_ACQUIRED,
                    "Acquisition date is after the accounting period end." );
        }
        if (asset.nextDepreciationAmount() == null
                || asset.nextDepreciationAmount().compareTo(BigDecimal.ZERO) <= 0)
        {
            return new Item(
                    asset.id(), asset.name(), asset.status(), asset.acquisitionDate(),
                    asset.currentBookValue(), asset.nextDepreciationAmount(),
                    Disposition.NO_REMAINING_BASIS,
                    "No remaining depreciable amount." );
        }
        return new Item(
                asset.id(), asset.name(), asset.status(), asset.acquisitionDate(),
                asset.currentBookValue(), asset.nextDepreciationAmount(),
                Disposition.ELIGIBLE,
                "Will be revalidated by the authoritative per-asset service before commit." );
    }

    private static String requireCompanyCode(String companyCode)
    {
        String normalized = companyCode == null ? "" : companyCode.strip();
        if (normalized.isBlank())
        {
            throw new IllegalArgumentException("companyCode is required");
        }
        return normalized;
    }

    private static void requirePeriod(LocalDate periodStart, LocalDate periodEnd)
    {
        if (periodStart == null || periodEnd == null)
        {
            throw new IllegalArgumentException("period start and end are required");
        }
        if (periodStart.isAfter(periodEnd))
        {
            throw new IllegalArgumentException("period start must not be after period end");
        }
    }

    private static String safeMessage(RuntimeException ex)
    {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    interface DepreciationRunner
    {
        DepreciationRunView run(long assetId, LocalDate runDate, String notes);
    }

    public enum Disposition
    {
        ELIGIBLE,
        ALREADY_RUN,
        INACTIVE,
        NOT_ACQUIRED,
        NO_REMAINING_BASIS
    }

    public enum Outcome
    {
        COMMITTED,
        SKIPPED,
        FAILED
    }

    public record Item(
            Long assetId,
            String assetName,
            FixedAsset.Status status,
            LocalDate acquisitionDate,
            BigDecimal bookValue,
            BigDecimal proposedAmount,
            Disposition disposition,
            String reason)
    {
    }

    public record Preview(
            String companyCode,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate postingDate,
            List<Item> items,
            BigDecimal proposedTotal)
    {
        public long eligibleCount()
        {
            return items.stream().filter(item -> item.disposition() == Disposition.ELIGIBLE).count();
        }
    }

    public record ExecutionItem(
            Long assetId,
            String assetName,
            Outcome outcome,
            DepreciationRunView completedRun,
            String message)
    {
    }

    public record Result(Preview requestedPreview, List<ExecutionItem> items)
    {
        public long committedCount()
        {
            return items.stream().filter(item -> item.outcome() == Outcome.COMMITTED).count();
        }

        public long skippedCount()
        {
            return items.stream().filter(item -> item.outcome() == Outcome.SKIPPED).count();
        }

        public long failedCount()
        {
            return items.stream().filter(item -> item.outcome() == Outcome.FAILED).count();
        }
    }
}
