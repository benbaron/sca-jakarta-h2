package org.nonprofitbookkeeping.interchange.sclx;

/** User-approved source-to-target account or fund choice used by a fresh SCLX preview. */
public record SclxImportMappingSelection(
        SclxImportMappingRequirement.Kind kind,
        String sourceId,
        String targetCode)
{
    public SclxImportMappingSelection
    {
        if (kind == null)
        {
            throw new IllegalArgumentException("mapping kind is required");
        }
        sourceId = requireText(sourceId, "sourceId");
        targetCode = requireText(targetCode, "targetCode");
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
