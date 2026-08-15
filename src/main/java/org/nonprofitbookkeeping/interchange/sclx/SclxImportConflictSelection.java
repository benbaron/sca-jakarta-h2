package org.nonprofitbookkeeping.interchange.sclx;

/** User-selected winner for one conflicting durable SCLX identity. */
public record SclxImportConflictSelection(
        String entityType,
        String externalId,
        SclxImportConflictChoice choice)
{
    public SclxImportConflictSelection
    {
        entityType = requireText(entityType, "entityType");
        externalId = requireText(externalId, "externalId");
        if (choice == null)
        {
            throw new IllegalArgumentException("conflict choice is required");
        }
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
