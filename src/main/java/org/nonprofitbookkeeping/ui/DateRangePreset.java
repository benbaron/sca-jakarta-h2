package org.nonprofitbookkeeping.ui;

/**
 * Presets requested for Excel-era comfort:
 * - default is ALL
 * - quarter/year/fiscal-year options
 * - custom arbitrary date pickers
 */
public enum DateRangePreset
{
    ALL("All"),
    CURRENT_QUARTER("Current quarter"),
    Q1("1st quarter"),
    Q2("2nd quarter"),
    Q3("3rd quarter"),
    Q4("4th quarter"),
    CURRENT_YEAR("Current year"),
    CURRENT_FISCAL_YEAR("Current fiscal year"),
    PAST_FISCAL_YEAR("Past fiscal year"),
    CUSTOM("Arbitrary dates…");

    private final String label;

    DateRangePreset(String label) { this.label = label; }

    public String label() { return label; }

    @Override public String toString() { return label; }
}
