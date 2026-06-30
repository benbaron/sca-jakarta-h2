package org.nonprofitbookkeeping.service;

/**
 * Raised when organization policy forbids direct reopening and requires a
 * formal adjustment workflow instead.
 */
public class FormalAdjustmentRequiredException extends IllegalStateException
{
    private final long accountingPeriodId;

    public FormalAdjustmentRequiredException(long accountingPeriodId)
    {
        super("Accounting period " + accountingPeriodId
                + " requires a formal adjustment workflow and cannot be reopened directly");
        this.accountingPeriodId = accountingPeriodId;
    }

    public long getAccountingPeriodId()
    {
        return accountingPeriodId;
    }
}
