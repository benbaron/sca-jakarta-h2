package org.nonprofitbookkeeping.model;

/** Explicit register provenance; null on older records means unknown/unlinked history. */
public enum AssetAcquisitionSource
{
    REGISTER_ONLY("Opening / register only — no accounting"),
    NEW_PURCHASE("New bank purchase"),
    EXISTING_ENTRY("Link existing entry");

    private final String label;

    AssetAcquisitionSource(String label)
    {
        this.label = label;
    }

    public String label()
    {
        return label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
