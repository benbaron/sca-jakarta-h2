package org.nonprofitbookkeeping.service;

/** Factual result of one deliberate legacy ownership assignment. */
public record CompanyOwnershipRepairResult(
        long issueId,
        String entityType,
        String entityId,
        long companyId,
        String companyCode,
        int remainingOpenIssues)
{
}
