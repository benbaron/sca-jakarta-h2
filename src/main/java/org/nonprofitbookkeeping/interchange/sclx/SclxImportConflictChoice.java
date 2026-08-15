package org.nonprofitbookkeeping.interchange.sclx;

/** Explicit winner selected for one same-identity/different-content SCLX conflict. */
public enum SclxImportConflictChoice
{
    KEEP_TARGET("A — Keep target"),
    TAKE_SOURCE("B — Take SCLX");

    private final String label;

    SclxImportConflictChoice(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
