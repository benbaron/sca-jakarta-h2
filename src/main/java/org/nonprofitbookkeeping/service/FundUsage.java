package org.nonprofitbookkeeping.service;

import java.util.ArrayList;
import java.util.List;

/** Authoritative reference counts that determine whether a fund can be physically deleted. */
public record FundUsage(
        long transactionSplits,
        long budgetLines,
        long fixedAssets,
        long inventoryItems,
        long aliases,
        long transfers,
        long childFunds)
{
    public long totalReferences()
    {
        return transactionSplits + budgetLines + fixedAssets + inventoryItems + aliases + transfers + childFunds;
    }

    public boolean canDelete()
    {
        return totalReferences() == 0;
    }

    public String describeReferences()
    {
        List<String> parts = new ArrayList<>();
        add(parts, transactionSplits, "transaction split");
        add(parts, budgetLines, "budget line");
        add(parts, fixedAssets, "fixed asset");
        add(parts, inventoryItems, "inventory item");
        add(parts, aliases, "alias");
        add(parts, transfers, "fund transfer");
        add(parts, childFunds, "child fund");
        return parts.isEmpty() ? "no references" : String.join(", ", parts);
    }

    private static void add(List<String> parts, long count, String singular)
    {
        if (count > 0)
        {
            parts.add(count + " " + singular + (count == 1 ? "" : "s"));
        }
    }
}
