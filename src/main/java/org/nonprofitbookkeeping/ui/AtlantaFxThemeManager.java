package org.nonprofitbookkeeping.ui;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import javafx.application.Application;
import org.nonprofitbookkeeping.model.UiThemePreference;

import java.util.Objects;

/** Applies the Java-17-compatible AtlantaFX theme catalog to the JavaFX application. */
final class AtlantaFxThemeManager
{
    private AtlantaFxThemeManager()
    {
    }

    static void apply(UiThemePreference preference)
    {
        Theme theme = themeFor(preference);
        Application.setUserAgentStylesheet(theme == null
                ? Application.STYLESHEET_MODENA
                : theme.getUserAgentStylesheet());
    }

    static Theme themeFor(UiThemePreference preference)
    {
        return switch (Objects.requireNonNull(preference, "preference"))
        {
            case LIGHT -> new PrimerLight();
            case DARK -> new PrimerDark();
            case NORD_LIGHT -> new NordLight();
            case NORD_DARK -> new NordDark();
            case CUPERTINO_LIGHT -> new CupertinoLight();
            case CUPERTINO_DARK -> new CupertinoDark();
            case DRACULA -> new Dracula();
            case SYSTEM_DEFAULT -> null;
        };
    }

    static boolean isDark(UiThemePreference preference)
    {
        return switch (preference)
        {
            case DARK, NORD_DARK, CUPERTINO_DARK, DRACULA -> true;
            case LIGHT, NORD_LIGHT, CUPERTINO_LIGHT, SYSTEM_DEFAULT -> false;
        };
    }
}
