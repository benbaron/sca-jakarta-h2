package org.nonprofitbookkeeping.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Read projection for an authoritative company period-close range. */
public record PeriodCloseRangeView(
        UUID id,
        String companyCode,
        LocalDate startDate,
        LocalDate endDate,
        String rangeKind,
        String status,
        Instant closedAt,
        String closedBy,
        String closeReason,
        Instant reopenedAt,
        String reopenedBy,
        String reopenReason)
{
    public boolean closed()
    {
        return "CLOSED".equals(status);
    }
}
