package org.nonprofitbookkeeping.interchange;

/** Immutable validation fact independent of JavaFX and JPA. */
public record InterchangeValidationMessage(
        InterchangeMessageSeverity severity,
        String code,
        String path,
        String message,
        boolean blocking)
{
    public InterchangeValidationMessage
    {
        if (severity == null)
        {
            throw new IllegalArgumentException("severity is required");
        }
        code = requireText(code, "code", 80);
        path = path == null ? "" : path.trim();
        message = requireText(message, "message", 1000);
        if (severity == InterchangeMessageSeverity.ERROR)
        {
            blocking = true;
        }
    }

    private static String requireText(String value, String label, int maxLength)
    {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        if (text.length() > maxLength)
        {
            throw new IllegalArgumentException(label + " exceeds " + maxLength + " characters");
        }
        return text;
    }
}
