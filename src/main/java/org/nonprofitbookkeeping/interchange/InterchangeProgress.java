package org.nonprofitbookkeeping.interchange;

/** Immutable bounded progress snapshot. */
public record InterchangeProgress(
        String stage,
        long completedUnits,
        long totalUnits,
        boolean cancelable,
        boolean commitStarted)
{
    public InterchangeProgress
    {
        stage = stage == null ? "" : stage.trim();
        if (completedUnits < 0 || totalUnits < 0 || completedUnits > totalUnits)
        {
            throw new IllegalArgumentException("Invalid progress bounds.");
        }
        if (commitStarted)
        {
            cancelable = false;
        }
    }

    public double fraction()
    {
        return totalUnits == 0 ? 0.0 : (double) completedUnits / (double) totalUnits;
    }
}
