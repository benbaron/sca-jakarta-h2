package org.nonprofitbookkeeping.interchange.bank;

import java.util.List;
import java.util.UUID;

/** Exact mapped-CSV preview bound to one durable profile revision and review target. */
public record BankCsvReviewPreview(
        long profileId,
        UUID profilePortableId,
        String profileHash,
        String profileName,
        BankStatementReviewPreview review,
        List<BankCsvParser.OriginalRow> originalRows)
{
    public BankCsvReviewPreview
    {
        originalRows = List.copyOf(originalRows);
    }
}
