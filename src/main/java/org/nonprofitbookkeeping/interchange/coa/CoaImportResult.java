package org.nonprofitbookkeeping.interchange.coa;

import org.nonprofitbookkeeping.interchange.InterchangeOperationCounts;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;

import java.util.List;

/** Result of the one-transaction Chart of Accounts JSON commit boundary. */
public record CoaImportResult(
        boolean committed,
        boolean rolledBack,
        String targetLabel,
        String sourceSha256,
        List<CoaPreviewItem> items,
        List<InterchangeValidationMessage> messages,
        InterchangeOperationCounts counts)
{
    public CoaImportResult
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
