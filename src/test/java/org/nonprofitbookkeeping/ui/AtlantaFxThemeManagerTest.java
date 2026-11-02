package org.nonprofitbookkeeping.ui;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.UiThemePreference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlantaFxThemeManagerTest
{
    @Test
    void exposesCompleteJava17AtlantaFxThemeSetWithLegacyMappings()
    {
        Map<UiThemePreference, Class<?>> expected = Map.of(
                UiThemePreference.LIGHT, PrimerLight.class,
                UiThemePreference.DARK, PrimerDark.class,
                UiThemePreference.NORD_LIGHT, NordLight.class,
                UiThemePreference.NORD_DARK, NordDark.class,
                UiThemePreference.CUPERTINO_LIGHT, CupertinoLight.class,
                UiThemePreference.CUPERTINO_DARK, CupertinoDark.class,
                UiThemePreference.DRACULA, Dracula.class);

        assertEquals(8, UiThemePreference.values().length);
        expected.forEach((preference, type) ->
        {
            assertInstanceOf(type, AtlantaFxThemeManager.themeFor(preference));
            assertFalse(AtlantaFxThemeManager.themeFor(preference)
                    .getUserAgentStylesheet().isBlank());
        });
        assertNull(AtlantaFxThemeManager.themeFor(UiThemePreference.SYSTEM_DEFAULT));
        assertEquals("Primer Light", UiThemePreference.LIGHT.toString());
        assertEquals("Primer Dark", UiThemePreference.DARK.toString());
    }

    @Test
    void classifiesDarkVariantsForShellAdaptation()
    {
        assertTrue(AtlantaFxThemeManager.isDark(UiThemePreference.DARK));
        assertTrue(AtlantaFxThemeManager.isDark(UiThemePreference.NORD_DARK));
        assertTrue(AtlantaFxThemeManager.isDark(UiThemePreference.CUPERTINO_DARK));
        assertTrue(AtlantaFxThemeManager.isDark(UiThemePreference.DRACULA));
        assertFalse(AtlantaFxThemeManager.isDark(UiThemePreference.LIGHT));
        assertFalse(AtlantaFxThemeManager.isDark(UiThemePreference.NORD_LIGHT));
        assertFalse(AtlantaFxThemeManager.isDark(UiThemePreference.CUPERTINO_LIGHT));
    }

    @Test
    void shellAdapterUsesAtlantaFxForegroundAndSurfaceVariables() throws Exception
    {
        String styles = Files.readString(Path.of("src/main/resources/ui/styles.css"));
        int start = styles.indexOf(".production-workspace.theme-atlantafx");
        assertTrue(start >= 0);
        String adapter = styles.substring(start);
        assertTrue(adapter.contains("-color-fg-default"));
        assertTrue(adapter.contains("-color-bg-default"));
        assertTrue(adapter.contains("-color-bg-subtle"));
        assertTrue(adapter.contains("-color-border-default"));
        assertTrue(adapter.contains("-color-accent-subtle"));
    }
}
