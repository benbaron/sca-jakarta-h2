package org.nonprofitbookkeeping.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contract for ReconciliationRunRepository.
 */
public interface ReconciliationRunRepository
{
    void append(ReconciliationRunRecord record);

    Optional<ReconciliationRunRecord> findById(UUID id);

    List<ReconciliationRunRecord> findByGroupAndDateRange(String groupCode, LocalDate fromDate, LocalDate toDate);
}
