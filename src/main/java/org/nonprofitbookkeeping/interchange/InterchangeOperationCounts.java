package org.nonprofitbookkeeping.interchange;

/** Immutable operation counts shared by previews and results. */
public record InterchangeOperationCounts(
        long total,
        long created,
        long updated,
        long identical,
        long skipped,
        long warnings,
        long errors)
{
    public static final InterchangeOperationCounts ZERO = new InterchangeOperationCounts(0, 0, 0, 0, 0, 0, 0);

    public InterchangeOperationCounts
    {
        if (total < 0 || created < 0 || updated < 0 || identical < 0 || skipped < 0 || warnings < 0 || errors < 0)
        {
            throw new IllegalArgumentException("Interchange counts cannot be negative.");
        }
    }

    public InterchangeOperationCounts plus(InterchangeOperationCounts other)
    {
        if (other == null)
        {
            return this;
        }
        return new InterchangeOperationCounts(
                total + other.total,
                created + other.created,
                updated + other.updated,
                identical + other.identical,
                skipped + other.skipped,
                warnings + other.warnings,
                errors + other.errors);
    }
}
