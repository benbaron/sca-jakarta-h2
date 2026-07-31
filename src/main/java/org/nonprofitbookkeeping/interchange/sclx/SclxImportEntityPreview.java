package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;

/** Non-mutating identity disposition for one incoming SCLX entity. */
public record SclxImportEntityPreview(
        String entityType,
        String externalId,
        String path,
        String normalizedContentHash,
        InterchangeIdentityMatch identityMatch,
        String localEntityId)
{
    public SclxImportEntityPreview
    {
        entityType = requireText(entityType, "entityType");
        externalId = requireText(externalId, "externalId");
        path = requireText(path, "path");
        normalizedContentHash = requireText(normalizedContentHash, "normalizedContentHash");
        if (normalizedContentHash.length() != 64)
        {
            throw new IllegalArgumentException("normalizedContentHash must be SHA-256");
        }
        if (identityMatch == null)
        {
            throw new IllegalArgumentException("identityMatch is required");
        }
        localEntityId = localEntityId == null || localEntityId.isBlank() ? null : localEntityId.trim();
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
