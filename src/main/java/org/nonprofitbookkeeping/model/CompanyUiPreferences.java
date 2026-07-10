package org.nonprofitbookkeeping.model;

/** Company-owned display and editing preferences used by production JavaFX panels. */
public record CompanyUiPreferences(String currencySymbol,
                                   MoneyPrintFormat moneyPrintFormat,
                                   DateDisplayFormat dateDisplayFormat)
{
    public CompanyUiPreferences
    {
        currencySymbol = currencySymbol == null || currencySymbol.isBlank() ? "$" : currencySymbol.trim();
        if (currencySymbol.length() > 8)
        {
            throw new IllegalArgumentException("currencySymbol must be at most 8 characters");
        }
        moneyPrintFormat = moneyPrintFormat == null ? MoneyPrintFormat.SYMBOL_PREFIX : moneyPrintFormat;
        dateDisplayFormat = dateDisplayFormat == null ? DateDisplayFormat.MONTH_DAY_YEAR : dateDisplayFormat;
    }

    public static CompanyUiPreferences defaults()
    {
        return new CompanyUiPreferences("$", MoneyPrintFormat.SYMBOL_PREFIX, DateDisplayFormat.MONTH_DAY_YEAR);
    }
}
