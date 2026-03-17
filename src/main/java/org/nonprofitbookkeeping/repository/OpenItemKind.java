package org.nonprofitbookkeeping.repository;

import java.util.Locale;

/**
 * Canonical persistence enum for open-item projection categories.
 */
public enum OpenItemKind
{
    OUTSTANDING_BANK_ITEM,
    RECEIVABLE,
    PREPAID_EXPENSE,
    DEFERRED_REVENUE,
    PAYABLE,
    ASSET;

    /**
     * Parses a persisted token into an enum value.
     *
     * @param value persisted token
     * @return enum value
     */
    public static OpenItemKind parse(String value)
    {
        try
        {
            return OpenItemKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (RuntimeException ex)
        {
            throw new IllegalArgumentException("Unsupported open-item kind: " + value, ex);
        }
    }
}
