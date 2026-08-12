package org.nonprofitbookkeeping.interchange.sclx;

import java.util.Objects;

/** One explicit, reviewable compatibility decision applied while reading donor SCLX. */
public record SclxCompatibilityNotice(
        String code,
        String path,
        String message,
        boolean blocking)
{
    public SclxCompatibilityNotice
    {
        code = requireText(code, "code");
        path = path == null ? "" : path.trim();
        message = requireText(message, "message");
    }

    private static String requireText(String value, String label)
    {
        String text = Objects.requireNonNull(value, label).trim();
        if (text.isEmpty())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return text;
    }
}
