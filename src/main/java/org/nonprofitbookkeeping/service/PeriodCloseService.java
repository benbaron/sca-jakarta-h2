package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.repository.PeriodCloseRunRecord;
import org.nonprofitbookkeeping.repository.PeriodCloseRunRepository;
import org.nonprofitbookkeeping.repository.WorkflowRunStatus;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists deterministic period-close workflow run artifacts.
 */
public class PeriodCloseService
{
    private final PeriodCloseRunRepository runRepository;

    public PeriodCloseService(PeriodCloseRunRepository runRepository)
    {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository");
    }

    public PeriodCloseRunRecord recordCompletedClose(String groupCode,
                                                     LocalDate closeDate,
                                                     UUID producedTransactionId,
                                                     String notes)
    {
        PeriodCloseRunRecord run = new PeriodCloseRunRecord(
                UUID.randomUUID(),
                groupCode,
                closeDate,
                WorkflowRunStatus.COMPLETED,
                producedTransactionId,
                notes);
        runRepository.append(run);
        return run;
    }
}
