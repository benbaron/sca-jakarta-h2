package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.interchange.InterchangeOperationCounts;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;

import java.util.List;

/** Result of the caller-owned, one-transaction SCLX import boundary. */
public record SclxImportResult(
        boolean committed,
        boolean rolledBack,
        String targetLabel,
        String sourceSha256,
        List<SclxImportEntityPreview> items,
        List<InterchangeValidationMessage> messages,
        InterchangeOperationCounts counts)
{
    public SclxImportResult
    {
        if (committed && rolledBack)
        {
            throw new IllegalArgumentException("An import cannot be committed and rolled back.");
        }
        targetLabel = targetLabel == null ? "" : targetLabel.trim();
        sourceSha256 = sourceSha256 == null ? "" : sourceSha256.trim().toLowerCase();
        items = items == null ? List.of() : List.copyOf(items);
        messages = messages == null ? List.of() : List.copyOf(messages);
        counts = counts == null ? InterchangeOperationCounts.ZERO : counts;
    }
}
