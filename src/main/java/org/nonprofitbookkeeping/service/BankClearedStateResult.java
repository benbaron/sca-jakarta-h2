package org.nonprofitbookkeeping.service;

import java.time.LocalDate;

/** Result of mapping an imported bank statement line to a canonical ledger bank split. */
public record BankClearedStateResult(long statementLineId,
                                     long transactionId,
                                     long splitId,
                                     LocalDate clearedOn)
{
}
