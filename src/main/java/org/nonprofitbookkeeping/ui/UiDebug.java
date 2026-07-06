package org.nonprofitbookkeeping.ui;

/**
 * Lightweight UI diagnostics for temporary desktop troubleshooting.
 *
 * <p>Diagnostics are enabled by default while the P03 drill-through bug is
 * under investigation. They can be disabled globally with
 * {@code -Dsca.ui.debug=false} or {@code SCA_UI_DEBUG=false}; a specific area
 * can be overridden with {@code -Dsca.ui.debug.ledger-register=false} or
 * {@code SCA_UI_DEBUG_LEDGER_REGISTER=false}. Area-specific settings override
 * the global setting.</p>
 */
final class UiDebug
{
    private static final String PROPERTY_PREFIX = "sca.ui.debug";
    private static final String ENV_PREFIX = "SCA_UI_DEBUG";

    private UiDebug()
    {
    }

    static void log(String area, String message)
    {
        if (isEnabled(area))
        {
            System.err.println("[NPBK][" + area + "] " + message);
        }
    }

    static boolean isEnabled(String area)
    {
        String normalizedArea = normalizeArea(area);
        String areaSetting = firstPresent(
                System.getProperty(PROPERTY_PREFIX + "." + normalizedArea),
                System.getenv(ENV_PREFIX + "_" + envArea(normalizedArea)));
        if (areaSetting != null)
        {
            return parseBoolean(areaSetting, true);
        }

        String globalSetting = firstPresent(
                System.getProperty(PROPERTY_PREFIX),
                System.getenv(ENV_PREFIX));
        return globalSetting == null || parseBoolean(globalSetting, true);
    }

    private static String firstPresent(String propertyValue, String environmentValue)
    {
        if (propertyValue != null && !propertyValue.isBlank())
        {
            return propertyValue;
        }
        if (environmentValue != null && !environmentValue.isBlank())
        {
            return environmentValue;
        }
        return null;
    }

    private static boolean parseBoolean(String value, boolean defaultValue)
    {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized)
        {
            case "1", "true", "yes", "y", "on", "enable", "enabled" -> true;
            case "0", "false", "no", "n", "off", "disable", "disabled" -> false;
            default -> defaultValue;
        };
    }

    private static String normalizeArea(String area)
    {
        return area == null || area.isBlank()
                ? "general"
                : area.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String envArea(String area)
    {
        return area.replaceAll("[^a-z0-9]+", "_").toUpperCase(java.util.Locale.ROOT);
    }
}
