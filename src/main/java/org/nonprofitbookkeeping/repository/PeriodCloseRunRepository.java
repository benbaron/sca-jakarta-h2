package org.nonprofitbookkeeping.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contract for PeriodCloseRunRepository.
 */
public interface PeriodCloseRunRepository
{
    void append(PeriodCloseRunRecord record);

    Optional<PeriodCloseRunRecord> findById(UUID id);

    List<PeriodCloseRunRecord> findByGroupAndDateRange(String groupCode, LocalDate fromDate, LocalDate toDate);
}
