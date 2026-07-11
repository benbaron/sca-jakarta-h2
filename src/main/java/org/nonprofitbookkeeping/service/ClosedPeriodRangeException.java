package org.nonprofitbookkeeping.service;

import java.time.LocalDate;
import java.util.UUID;

/** Raised when a canonical accounting operation targets an authoritative closed date range. */
public class ClosedPeriodRangeException extends IllegalStateException
{
    private final UUID closeRangeId;
    private final String companyCode;
    private final LocalDate transactionDate;
    private final String operation;

    public ClosedPeriodRangeException(
            UUID closeRangeId,
            String companyCode,
            LocalDate transactionDate,
            String operation)
    {
        super("Cannot " + operation + " on " + transactionDate
                + " because the date is closed for company " + companyCode
                + " (close range " + closeRangeId + ")");
        this.closeRangeId = closeRangeId;
        this.companyCode = companyCode;
        this.transactionDate = transactionDate;
        this.operation = operation;
    }

    public UUID getCloseRangeId()
    {
        return closeRangeId;
    }

    public String getCompanyCode()
    {
        return companyCode;
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
