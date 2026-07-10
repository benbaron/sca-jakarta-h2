package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read projection for a supplemental detail row attached to a transaction. */
public record TransactionSupplementalLineView(Long id,
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
    public TransactionSupplementalLineView
    {
        amount = amount == null ? BigDecimal.ZERO : amount;
    }
}
