package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.interchange.InterchangePreview;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Complete non-mutating SCLX preview for one source and one explicit target company. */
public record SclxImportPreview(
        InterchangePreview<SclxImportEntityPreview> operation,
        SclxVersion version,
        Instant exportedAt,
        String sourceOrganizationId,
        String sourceOrganizationCode,
        String sourceOrganizationName,
        String sourceSystem,
        String targetCompanyCode,
        String targetCompanyName,
        boolean targetPopulated,
        SclxAccountMode recommendedAccountMode,
        SclxImportPreviewCounts sectionCounts,
        List<SclxImportMappingRequirement> mappings,
        List<SclxImportTransactionPreview> transactions)
{
    public SclxImportPreview
    {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(version, "version");
        sourceOrganizationId = requireText(sourceOrganizationId, "sourceOrganizationId");
        sourceOrganizationCode = requireText(sourceOrganizationCode, "sourceOrganizationCode");
        sourceOrganizationName = requireText(sourceOrganizationName, "sourceOrganizationName");
        sourceSystem = requireText(sourceSystem, "sourceSystem");
        targetCompanyCode = requireText(targetCompanyCode, "targetCompanyCode");
        targetCompanyName = requireText(targetCompanyName, "targetCompanyName");
        Objects.requireNonNull(recommendedAccountMode, "recommendedAccountMode");
        Objects.requireNonNull(sectionCounts, "sectionCounts");
        mappings = List.copyOf(Objects.requireNonNull(mappings, "mappings"));
        transactions = List.copyOf(Objects.requireNonNull(transactions, "transactions"));
    }

    public boolean hasBlockingErrors()
    {
        return operation.hasBlockingErrors();
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
