package org.nonprofitbookkeeping.repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Persisted period-close workflow run record.
 */
public record PeriodCloseRunRecord(UUID id,
                                   String groupCode,
                                   LocalDate closeDate,
                                   WorkflowRunStatus status,
                                   UUID producedTransactionId,
                                   String notes)
{
}
