package org.nonprofitbookkeeping.ui;

public final class UiErrors
{
    private UiErrors() {}

    public static String safeMessage(Throwable ex)
    {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank())
        {
            return "unknown error";
        }
        return ex.getMessage();
    }
}
