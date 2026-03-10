package org.nonprofitbookkeeping.repository;

import org.nonprofitbookkeeping.domain.core.JournalTransaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Journal persistence abstraction.
 */
public interface JournalTransactionRepository
{
    void append(JournalTransaction transaction);

    Optional<JournalTransaction> findById(UUID transactionId);

    List<JournalTransaction> findByGroupAndDateRange(String groupCode, LocalDate fromDate, LocalDate toDate);
}
