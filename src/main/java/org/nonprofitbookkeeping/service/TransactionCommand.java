package org.nonprofitbookkeeping.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Header and line input for writing a canonical Txn transaction.
 */
public record TransactionCommand(LocalDate date,
                                 Long payeeId,
                                 String memo,
                                 Long bankAccountId,
                                 List<TransactionLineCommand> lines)
{
    public TransactionCommand
    {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
