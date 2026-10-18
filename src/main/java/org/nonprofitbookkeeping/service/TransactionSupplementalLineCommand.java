package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
                                                 Integer lineOrder,
                                                 UUID itemId,
                                                 String itemEffect,
                                                 Integer transactionLineIndex,
                                                 Long nmrRecipientId, String nmrMovement, String nmrEvidenceReference)
{
    public TransactionSupplementalLineCommand(String kind, String entryRef, String counterparty,
            String description, String reference, BigDecimal amount, LocalDate dueDate,
            LocalDate startDate, LocalDate endDate, String notes, Integer lineOrder,
            UUID itemId, String itemEffect, Integer transactionLineIndex, Long nmrRecipientId)
    {
        this(kind, entryRef, counterparty, description, reference, amount, dueDate,
                startDate, endDate, notes, lineOrder, itemId, itemEffect, transactionLineIndex, nmrRecipientId, null, null);
    }

    /** Compatibility constructor: no explicit NMR item designation. */
    public TransactionSupplementalLineCommand(String kind, String entryRef, String counterparty,
            String description, String reference, BigDecimal amount, LocalDate dueDate,
            LocalDate startDate, LocalDate endDate, String notes, Integer lineOrder,
            UUID itemId, String itemEffect, Integer transactionLineIndex)
    {
        this(kind, entryRef, counterparty, description, reference, amount, dueDate,
                startDate, endDate, notes, lineOrder, itemId, itemEffect, transactionLineIndex, null);
    }

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
                dueDate, startDate, endDate, notes, null, null, null, null);
    }

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
            String notes,
            Integer lineOrder)
    {
        this(kind, entryRef, counterparty, description, reference, amount,
                dueDate, startDate, endDate, notes, lineOrder, null, null, null);
    }

    public TransactionSupplementalLineCommand
    {
        amount = amount == null ? BigDecimal.ZERO : amount;
    }

    public boolean hasLifecycleLink()
    {
        return itemId != null || itemEffect != null || transactionLineIndex != null;
    }
}
