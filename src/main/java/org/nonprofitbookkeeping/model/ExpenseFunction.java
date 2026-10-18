package org.nonprofitbookkeeping.model;

/** Owner-adopted expense purpose, independent of account, Fund, Event and Budget. */
public enum ExpenseFunction
{
    AR("Events"), OA("Administration"), FR("Fundraising");

    private final String label;

    ExpenseFunction(String label)
    {
        this.label = label;
    }

    public String label()
    {
        return name() + " — " + label;
    }

    public static ExpenseFunction fromCode(String code)
    {
        return code == null || code.isBlank() ? null : valueOf(code.trim());
    }
}
