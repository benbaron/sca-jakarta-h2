package org.nonprofitbookkeeping.interchange;

import java.util.List;

/** Immutable non-mutating preview envelope; format-specific item DTOs remain separate. */
public record InterchangePreview<T>(
        InterchangeFormat format,
        InterchangeOperationMode mode,
        String sourceName,
        String targetLabel,
        String sourceSha256,
        List<T> items,
        List<InterchangeValidationMessage> messages,
        List<InterchangeConfirmation> confirmations,
        InterchangeOperationCounts counts)
{
    public InterchangePreview
    {
        if (format == null || mode == null)
        {
            throw new IllegalArgumentException("format and mode are required");
        }
        sourceName = sourceName == null ? "" : sourceName.trim();
        targetLabel = targetLabel == null ? "" : targetLabel.trim();
        sourceSha256 = sourceSha256 == null ? "" : sourceSha256.trim().toLowerCase();
        items = items == null ? List.of() : List.copyOf(items);
        messages = messages == null ? List.of() : List.copyOf(messages);
        confirmations = confirmations == null ? List.of() : List.copyOf(confirmations);
        counts = counts == null ? InterchangeOperationCounts.ZERO : counts;
    }

    public boolean hasBlockingErrors()
    {
        return messages.stream().anyMatch(InterchangeValidationMessage::blocking);
    }

    public boolean confirmationsSatisfied()
    {
        return confirmations.stream().allMatch(InterchangeConfirmation::satisfied);
    }
}
