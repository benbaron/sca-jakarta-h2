package org.nonprofitbookkeeping.repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Persisted reconciliation workflow run record.
 */
public record ReconciliationRunRecord(UUID id,
                                      String groupCode,
                                      LocalDate statementEndingOn,
                                      String bankFormat,
                                      int importedTransactionCount,
                                      String status,
                                      String notes)
{
}
