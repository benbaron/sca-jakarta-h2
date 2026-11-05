package org.nonprofitbookkeeping.ui;

import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/** Applies the Windows rendering workaround before the JavaFX toolkit starts. */
final class JavaFxRenderingPolicy
{
    private JavaFxRenderingPolicy()
    {
    }

    static boolean configure(Properties properties)
    {
        Objects.requireNonNull(properties, "properties");
        if (properties.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")
                && properties.getProperty("prism.order") == null)
        {
            properties.setProperty("prism.order", "sw");
            return true;
        }
        return false;
    }
}
