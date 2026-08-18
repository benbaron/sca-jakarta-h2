package org.nonprofitbookkeeping.interchange.sclx;

/** User-visible action selected for one SCLX preview diagnostic. */
public enum SclxImportDisposition
{
    NO_CHANGE("No change"),
    IGNORE("Ignore"),
    MAKE_SUGGESTED_CORRECTION("Make suggested correction"),
    DROP_RECORD("Drop record");

    private final String displayName;

    SclxImportDisposition(String displayName)
    {
        this.displayName = displayName;
    }

    public String displayName()
    {
        return displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
