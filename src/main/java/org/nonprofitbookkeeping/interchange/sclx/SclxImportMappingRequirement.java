package org.nonprofitbookkeeping.interchange.sclx;

import java.util.List;

/** Explicit source-to-target account or fund resolution shown before any SCLX commit. */
public record SclxImportMappingRequirement(
        Kind kind,
        String sourceId,
        String sourceCode,
        String targetId,
        String targetCode,
        boolean used,
        Resolution resolution,
        String detail,
        boolean blocking,
        List<String> compatibleTargetCodes)
{
    public enum Kind { ACCOUNT, FUND }
    public enum Resolution { AS_IS, CREATE, MAPPED, CONFLICT, UNRESOLVED }

    public SclxImportMappingRequirement
    {
        if (kind == null || resolution == null)
        {
            throw new IllegalArgumentException("mapping kind and resolution are required");
        }
        sourceId = requireText(sourceId, "sourceId");
        sourceCode = requireText(sourceCode, "sourceCode");
        targetId = optional(targetId);
        targetCode = optional(targetCode);
        detail = requireText(detail, "detail");
        compatibleTargetCodes = List.copyOf(compatibleTargetCodes == null
                ? List.of()
                : compatibleTargetCodes);
        if ((resolution == Resolution.AS_IS || resolution == Resolution.CREATE
                || resolution == Resolution.MAPPED)
                && (targetCode == null || targetId == null))
        {
            throw new IllegalArgumentException("resolved mappings require a target identity and code");
        }
        if (resolution == Resolution.UNRESOLVED || resolution == Resolution.CONFLICT)
        {
            blocking = true;
        }
    }

    public SclxImportMappingRequirement(
            Kind kind,
            String sourceId,
            String sourceCode,
            String targetId,
            String targetCode,
            boolean used,
            Resolution resolution,
            String detail,
            boolean blocking)
    {
        this(kind, sourceId, sourceCode, targetId, targetCode, used,
                resolution, detail, blocking, List.of());
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static String optional(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
