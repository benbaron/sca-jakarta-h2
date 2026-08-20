package org.nonprofitbookkeeping.report;

import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.InventoryItem;

/** Typed, immutable domain filters retained by preview, export, and drill-through. */
public sealed interface ReportDomainFilter
        permits ReportDomainFilter.None,
        ReportDomainFilter.AccountSelection,
        ReportDomainFilter.FixedAssetSelection,
        ReportDomainFilter.InventorySelection
{
    None NONE = new None();

    default String summary()
    {
        return "";
    }

    record None() implements ReportDomainFilter
    {
    }

    record AccountSelection(Long accountId) implements ReportDomainFilter
    {
        public AccountSelection
        {
            requirePositive(accountId, "accountId");
        }

        @Override
        public String summary()
        {
            return "account=" + value(accountId);
        }
    }

    record FixedAssetSelection(
            Long assetId,
            Long accountId,
            FixedAsset.Status status) implements ReportDomainFilter
    {
        public FixedAssetSelection
        {
            requirePositive(assetId, "assetId");
            requirePositive(accountId, "accountId");
        }

        @Override
        public String summary()
        {
            return "asset=" + value(assetId) + ", account=" + value(accountId)
                    + ", status=" + (status == null ? "ALL" : status.name());
        }
    }

    record InventorySelection(
            Long itemId,
            Long accountId,
            InventoryItem.Status status) implements ReportDomainFilter
    {
        public InventorySelection
        {
            requirePositive(itemId, "itemId");
            requirePositive(accountId, "accountId");
        }

        @Override
        public String summary()
        {
            return "item=" + value(itemId) + ", account=" + value(accountId)
                    + ", status=" + (status == null ? "ALL" : status.name());
        }
    }

    private static void requirePositive(Long value, String label)
    {
        if (value != null && value <= 0)
        {
            throw new IllegalArgumentException(label + " must be positive when selected.");
        }
    }

    private static String value(Long value)
    {
        return value == null ? "ALL" : value.toString();
    }
}
