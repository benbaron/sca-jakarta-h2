package org.nonprofitbookkeeping.model;

import java.time.LocalDate;

/** Optional source facts, independent of payment issuance and reconciliation authority. */
public record TransactionInformation(String transferOrCheckReference, LocalDate statementDate, LocalDate incomingDate)
{
    public TransactionInformation
    {
        transferOrCheckReference = transferOrCheckReference == null || transferOrCheckReference.isBlank()
                ? null : transferOrCheckReference;

    }

    public static TransactionInformation empty()
    {
        return new TransactionInformation(null, null, null);
    }

    public boolean isEmpty()
    {
        return transferOrCheckReference == null && statementDate == null && incomingDate == null;
    }
}
