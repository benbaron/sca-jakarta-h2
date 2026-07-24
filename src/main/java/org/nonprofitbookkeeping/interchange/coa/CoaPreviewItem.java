package org.nonprofitbookkeeping.interchange.coa;

/** One normalized account and its proposed import disposition. */
public record CoaPreviewItem(
        CoaAccountData account,
        String targetCode,
        Disposition disposition,
        Long existingAccountId,
        boolean hasHistory)
{
    public CoaPreviewItem
    {
        if (account == null)
        {
            throw new IllegalArgumentException("account is required");
        }
        targetCode = targetCode == null ? "" : targetCode.trim();
        if (disposition == null)
        {
            throw new IllegalArgumentException("disposition is required");
        }
    }

    public enum Disposition
    {
        CREATE,
        UPDATE,
        IDENTICAL,
        BLOCKED
    }
}
