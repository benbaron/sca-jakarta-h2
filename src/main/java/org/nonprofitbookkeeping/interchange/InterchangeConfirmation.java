package org.nonprofitbookkeeping.interchange;

/** Explicit confirmation required before a risky commit boundary. */
public record InterchangeConfirmation(String code, String label, boolean required, boolean accepted)
{
    public InterchangeConfirmation
    {
        code = requireText(code, "code");
        label = requireText(label, "label");
    }

    public boolean satisfied()
    {
        return !required || accepted;
    }

    private static String requireText(String value, String field)
    {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty())
        {
            throw new IllegalArgumentException(field + " is required");
        }
        return text;
    }
}
