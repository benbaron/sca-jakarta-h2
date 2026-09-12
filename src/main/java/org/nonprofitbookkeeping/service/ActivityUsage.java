package org.nonprofitbookkeeping.service;

import java.util.ArrayList;
import java.util.List;

/** Authoritative references that determine whether an Activity can be physically deleted. */
public record ActivityUsage(
        long transactionSplits,
        long interchangeIdentities)
{
    public long totalReferences()
    {
        return transactionSplits + interchangeIdentities;
    }

    public boolean canDelete()
    {
        return totalReferences() == 0L;
    }

    public String describeReferences()
    {
        List<String> parts = new ArrayList<>();
        add(parts, transactionSplits, "transaction split", "transaction splits");
        add(parts, interchangeIdentities, "interchange identity", "interchange identities");
        return parts.isEmpty() ? "no references" : String.join(", ", parts);
    }

    private static void add(List<String> parts, long count, String singular, String plural)
    {
        if (count > 0L)
        {
            parts.add(count + " " + (count == 1L ? singular : plural));
        }
    }
}
