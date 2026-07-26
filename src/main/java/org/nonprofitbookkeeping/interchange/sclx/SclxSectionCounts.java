package org.nonprofitbookkeeping.interchange.sclx;

/** Immutable counts for governed SCLX sections validated before import or export. */
public record SclxSectionCounts(
        long accounts,
        long funds,
        long budgets,
        long transactions,
        long transactionLines,
        long totalEntities)
{
    public SclxSectionCounts
    {
        if (accounts < 0L || funds < 0L || budgets < 0L || transactions < 0L
                || transactionLines < 0L || totalEntities < 0L)
        {
            throw new IllegalArgumentException("SCLX section counts must not be negative");
        }
    }
}
