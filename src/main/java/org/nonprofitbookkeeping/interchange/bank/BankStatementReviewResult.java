package org.nonprofitbookkeeping.interchange.bank;

/** Durable bank-statement review commit or identical no-op result. */
public record BankStatementReviewResult(
        long batchId,
        boolean created,
        int totalLineCount,
        int reviewableLineCount,
        int errorLineCount,
        int duplicateLineCount,
        int issueCount)
{
}
