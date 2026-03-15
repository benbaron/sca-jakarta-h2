package org.nonprofitbookkeeping.repository;

import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Persisted reconciliation workflow run record.
 */
public record ReconciliationRunRecord(UUID id,
                                      String groupCode,
                                      LocalDate statementEndingOn,
                                      BankingDataFormat bankFormat,
                                      int importedTransactionCount,
                                      WorkflowRunStatus status,
                                      String notes)
{
}
