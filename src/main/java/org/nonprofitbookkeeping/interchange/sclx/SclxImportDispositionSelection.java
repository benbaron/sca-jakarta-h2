package org.nonprofitbookkeeping.interchange.sclx;

/** One message-level disposition reapplied by fresh preview and exact-source commit validation. */
public record SclxImportDispositionSelection(
        String code,
        String path,
        SclxImportDisposition disposition)
{
    public SclxImportDispositionSelection
    {
        code = requireText(code, "code");
        path = path == null ? "" : path.trim();
        if (disposition == null)
        {
            throw new IllegalArgumentException("disposition is required");
        }
    }

    public String key()
    {
        return key(code, path);
    }

    public static String key(String code, String path)
    {
        return requireText(code, "code") + "\u0000" + (path == null ? "" : path.trim());
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
