package org.nonprofitbookkeeping.service;

/** Summary of a persisted bank import review batch. */
public record BankImportReviewResult(long batchId,
                                     int totalLineCount,
                                     int issueCount,
                                     int errorLineCount,
                                     int duplicateLineCount)
{
}
