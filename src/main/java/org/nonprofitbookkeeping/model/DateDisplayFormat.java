package org.nonprofitbookkeeping.model;

/** Company-owned date ordering and display preference. */
public enum DateDisplayFormat
{
    MONTH_DAY_YEAR("Month/Day/Year", "M/d/uuuu"),
    DAY_MONTH_YEAR("Day/Month/Year", "d/M/uuuu"),
    YEAR_MONTH_DAY("Year-Month-Day", "uuuu-MM-dd");

    private final String label;
    private final String pattern;

    DateDisplayFormat(String label, String pattern)
    {
        this.label = label;
        this.pattern = pattern;
    }

    public String pattern()
    {
        return pattern;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
