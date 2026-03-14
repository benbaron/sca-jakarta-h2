package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.repository.ReconciliationRunRecord;
import org.nonprofitbookkeeping.repository.ReconciliationRunRepository;
import org.nonprofitbookkeeping.repository.WorkflowRunStatus;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists deterministic reconciliation workflow run artifacts.
 */
public class ReconciliationService
{
    private final ReconciliationRunRepository runRepository;

    public ReconciliationService(ReconciliationRunRepository runRepository)
    {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository");
    }

    public ReconciliationRunRecord recordCompletedRun(String groupCode,
                                                      LocalDate statementEndingOn,
                                                      BankingDataFormat bankFormat,
                                                      int importedTransactionCount,
                                                      String notes)
    {
        ReconciliationRunRecord run = new ReconciliationRunRecord(
                UUID.randomUUID(),
                groupCode,
                statementEndingOn,
                bankFormat,
                importedTransactionCount,
                WorkflowRunStatus.COMPLETED,
                notes);
        runRepository.append(run);
        return run;
    }
}
