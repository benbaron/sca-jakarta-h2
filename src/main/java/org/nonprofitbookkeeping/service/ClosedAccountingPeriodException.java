package org.nonprofitbookkeeping.service;

import java.time.LocalDate;

/**
 * Raised when an accounting operation would alter or create authoritative data
 * in a closed accounting period.
 */
public class ClosedAccountingPeriodException extends IllegalStateException
{
    private final long accountingPeriodId;
    private final LocalDate transactionDate;
    private final String operation;

    public ClosedAccountingPeriodException(
            long accountingPeriodId,
            LocalDate transactionDate,
            String operation)
    {
        super("Cannot " + operation + " on " + transactionDate
                + " because accounting period " + accountingPeriodId + " is closed");
        this.accountingPeriodId = accountingPeriodId;
        this.transactionDate = transactionDate;
        this.operation = operation;
    }

    public long getAccountingPeriodId()
    {
        return accountingPeriodId;
    }

    public LocalDate getTransactionDate()
    {
        return transactionDate;
    }

    public String getOperation()
    {
        return operation;
    }
}
