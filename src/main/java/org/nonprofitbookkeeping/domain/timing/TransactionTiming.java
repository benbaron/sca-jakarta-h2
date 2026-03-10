package org.nonprofitbookkeeping.domain.timing;

import java.util.Objects;

/**
 * Two-dimensional timing descriptor for a financial action.
 * <p>
 * Bank timing captures settlement relative to posting date, while budget timing captures
 * recognition relative to reporting period.
 */
public record TransactionTiming(TimingPosition bankTiming, TimingPosition budgetTiming)
{
    public TransactionTiming
    {
        bankTiming = Objects.requireNonNull(bankTiming, "bankTiming");
        budgetTiming = Objects.requireNonNull(budgetTiming, "budgetTiming");
    }

    public static TransactionTiming of(TimingPosition bankTiming, TimingPosition budgetTiming)
    {
        return new TransactionTiming(bankTiming, budgetTiming);
    }

    public boolean hasBankMovement()
    {
        return bankTiming != TimingPosition.NONE;
    }

    public boolean hasBudgetImpact()
    {
        return budgetTiming != TimingPosition.NONE;
    }

    public boolean isPureInternalReallocation()
    {
        return bankTiming == TimingPosition.NONE && budgetTiming == TimingPosition.NOW;
    }
}
