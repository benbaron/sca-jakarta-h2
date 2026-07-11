package org.nonprofitbookkeeping.service;

import java.time.Instant;
import java.util.UUID;

/** Factual close/reopen history row. */
public record PeriodCloseEventView(
        UUID id,
        UUID closeRangeId,
        String companyCode,
        String eventType,
        String actor,
        String reason,
        Instant eventAt)
{
}
