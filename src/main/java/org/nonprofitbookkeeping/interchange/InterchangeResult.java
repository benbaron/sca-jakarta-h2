package org.nonprofitbookkeeping.interchange;

import java.util.List;

/** Immutable result of one documented commit boundary. */
public record InterchangeResult<T>(
        InterchangeFormat format,
        InterchangeOperationMode mode,
        boolean committed,
        boolean rolledBack,
        String targetLabel,
        String sourceSha256,
        List<T> items,
        List<InterchangeValidationMessage> messages,
        InterchangeOperationCounts counts)
{
    public InterchangeResult
    {
        if (format == null || mode == null)
        {
            throw new IllegalArgumentException("format and mode are required");
        }
        if (committed && rolledBack)
        {
            throw new IllegalArgumentException("An operation cannot be committed and rolled back.");
        }
        targetLabel = targetLabel == null ? "" : targetLabel.trim();
        sourceSha256 = sourceSha256 == null ? "" : sourceSha256.trim().toLowerCase();
        items = items == null ? List.of() : List.copyOf(items);
        messages = messages == null ? List.of() : List.copyOf(messages);
        counts = counts == null ? InterchangeOperationCounts.ZERO : counts;
    }
}
