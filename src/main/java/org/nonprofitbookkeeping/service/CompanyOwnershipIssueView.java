package org.nonprofitbookkeeping.service;

import java.time.Instant;

public record CompanyOwnershipIssueView(
        Long id,
        String entityType,
        String entityId,
        String issueCode,
        int candidateCompanyCount,
        String details,
        Instant detectedAt)
{
}
