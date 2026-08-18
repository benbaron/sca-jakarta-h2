package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;

/** Non-mutating identity disposition for one incoming SCLX entity. */
public record SclxImportEntityPreview(
        String entityType,
        String externalId,
        String path,
        String normalizedContentHash,
        InterchangeIdentityMatch identityMatch,
        String localEntityId,
        SclxImportConflictChoice conflictChoice,
        boolean sourceChoiceAllowed,
        String conflictDetail,
        String nativePortableId)
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
        conflictDetail = conflictDetail == null || conflictDetail.isBlank() ? null : conflictDetail.trim();
        nativePortableId = nativePortableId == null || nativePortableId.isBlank()
                ? null : nativePortableId.trim();
        if (identityMatch != InterchangeIdentityMatch.CONFLICT && conflictChoice != null)
        {
            throw new IllegalArgumentException("Only conflicting identities may have a conflict choice");
        }
        if (identityMatch != InterchangeIdentityMatch.CONFLICT)
        {
            sourceChoiceAllowed = false;
            conflictDetail = null;
        }
        if (conflictChoice == SclxImportConflictChoice.TAKE_SOURCE && !sourceChoiceAllowed)
        {
            throw new IllegalArgumentException("The source choice is unavailable for this conflict");
        }
    }

    public SclxImportEntityPreview(
            String entityType,
            String externalId,
            String path,
            String normalizedContentHash,
            InterchangeIdentityMatch identityMatch,
            String localEntityId,
            SclxImportConflictChoice conflictChoice,
            boolean sourceChoiceAllowed,
            String conflictDetail)
    {
        this(entityType, externalId, path, normalizedContentHash, identityMatch, localEntityId,
                conflictChoice, sourceChoiceAllowed, conflictDetail, null);
    }

    public SclxImportEntityPreview(
            String entityType,
            String externalId,
            String path,
            String normalizedContentHash,
            InterchangeIdentityMatch identityMatch,
            String localEntityId)
    {
        this(entityType, externalId, path, normalizedContentHash, identityMatch, localEntityId,
                null, false, null, null);
    }

    public boolean conflictResolved()
    {
        return identityMatch != InterchangeIdentityMatch.CONFLICT || conflictChoice != null;
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
