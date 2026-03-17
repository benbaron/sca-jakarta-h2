package org.nonprofitbookkeeping.domain.timing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TransactionTimingTest component.
 */
public class TransactionTimingTest
{
    @Test
    public void internalReallocation_hasNoBankMovement_andHasBudgetImpact()
    {
        TransactionTiming timing = TransactionTiming.of(TimingPosition.NONE, TimingPosition.NOW);

        assertFalse(timing.hasBankMovement());
        assertTrue(timing.hasBudgetImpact());
        assertTrue(timing.isPureInternalReallocation());
    }

    @Test
    public void prepaidExpense_nowBank_futureBudget()
    {
        TransactionTiming timing = TransactionTiming.of(TimingPosition.NOW, TimingPosition.FUTURE);

        assertTrue(timing.hasBankMovement());
        assertTrue(timing.hasBudgetImpact());
        assertFalse(timing.isPureInternalReallocation());
    }
}
