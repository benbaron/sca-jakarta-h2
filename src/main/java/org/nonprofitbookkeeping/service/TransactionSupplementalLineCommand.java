package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Command DTO for a supplemental detail row attached to a transaction. */
public record TransactionSupplementalLineCommand(String kind,
                                                 String entryRef,
                                                 String counterparty,
                                                 String description,
                                                 String reference,
                                                 BigDecimal amount,
                                                 LocalDate dueDate,
                                                 LocalDate startDate,
                                                 LocalDate endDate,
                                                 String notes,
                                                 Integer lineOrder)
{
    public TransactionSupplementalLineCommand(
            String kind,
            String entryRef,
            String counterparty,
            String description,
            String reference,
            BigDecimal amount,
            LocalDate dueDate,
            LocalDate startDate,
            LocalDate endDate,
            String notes)
    {
        this(kind, entryRef, counterparty, description, reference, amount,
                dueDate, startDate, endDate, notes, null);
    }

    public TransactionSupplementalLineCommand
    {
        amount = amount == null ? BigDecimal.ZERO : amount;
    }
}
