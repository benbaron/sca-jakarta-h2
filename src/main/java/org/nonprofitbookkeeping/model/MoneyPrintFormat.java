package org.nonprofitbookkeeping.model;

/** Company-owned presentation choice for money values. */
public enum MoneyPrintFormat
{
    SYMBOL_PREFIX("Symbol before amount"),
    SYMBOL_SUFFIX("Symbol after amount"),
    NUMBER_ONLY("Number only");

    private final String label;

    MoneyPrintFormat(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
