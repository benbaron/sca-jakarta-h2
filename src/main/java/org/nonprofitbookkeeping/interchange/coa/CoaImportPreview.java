package org.nonprofitbookkeeping.interchange.coa;

import org.nonprofitbookkeeping.interchange.InterchangeConfirmation;
import org.nonprofitbookkeeping.interchange.InterchangeOperationCounts;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;

import java.nio.file.Path;
import java.util.List;

/** Complete non-mutating Chart of Accounts JSON preview. */
public record CoaImportPreview(
        Path sourceFile,
        String sourceSha256,
        long sourceBytes,
        CoaImportRequest request,
        CoaChartData chart,
        String targetLabel,
        List<CoaPreviewItem> items,
        List<InterchangeValidationMessage> messages,
        List<InterchangeConfirmation> confirmations,
        InterchangeOperationCounts counts)
{
    public CoaImportPreview
    {
        if (sourceFile == null || request == null || chart == null)
        {
            throw new IllegalArgumentException("sourceFile, request, and chart are required");
        }
        sourceFile = sourceFile.toAbsolutePath().normalize();
        sourceSha256 = sourceSha256 == null ? "" : sourceSha256.trim().toLowerCase();
        targetLabel = targetLabel == null ? "" : targetLabel.trim();
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
