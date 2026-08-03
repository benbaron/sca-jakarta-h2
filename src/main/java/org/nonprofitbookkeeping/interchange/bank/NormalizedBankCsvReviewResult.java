package org.nonprofitbookkeeping.interchange.bank;

import java.util.List;

/** Atomic normalized bank CSV commit or identical no-op result. */
public record NormalizedBankCsvReviewResult(
        List<Long> batchIds,
        boolean created,
        int batchCount,
        int totalLineCount,
        int reviewableLineCount,
        int matchedLineCount,
        int duplicateLineCount,
        int issueCount)
{
    public NormalizedBankCsvReviewResult
    {
        batchIds = batchIds == null ? List.of() : List.copyOf(batchIds);
    }
}
