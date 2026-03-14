package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainWindowStateWiringTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    public void restoresAndAppliesThemeNativeAndCompanyFromStore()
    {
        AppPreferencesState prefs = new AppPreferencesState(UiThemePreference.DARK, true, true, UserPrivilegeLevel.MANAGER);
        MultiCompanyState company = new MultiCompanyState("BARONY-GREEN", List.of("BARONY-GREEN"));

        InMemoryAppStateStore store = new InMemoryAppStateStore(Optional.of(prefs), Optional.of(company));

        MainWindow.resetSessionForTests(
                new AppPreferencesState(UiThemePreference.SYSTEM_DEFAULT, false, true, UserPrivilegeLevel.ACCOUNTANT),
                new MultiCompanyState("DEFAULT", List.of("DEFAULT")));

        MainWindow window = FxTestSupport.onFx(() -> new MainWindow(store));

        assertTrue(window.usesDarkThemeFlag());
        assertTrue(window.usesNativeDecorationsFlag());
        assertEquals("BARONY-GREEN", window.activeCompanyCode());
    }

    @Test
    public void saveActivePanel_persistsCurrentSessionState()
    {
        InMemoryAppStateStore store = new InMemoryAppStateStore(Optional.empty(), Optional.empty());

        MainWindow.resetSessionForTests(
                new AppPreferencesState(UiThemePreference.LIGHT, false, true, UserPrivilegeLevel.ADMIN),
                new MultiCompanyState("BARONY-RED", List.of("BARONY-RED", "BARONY-BLUE")));

        MainWindow window = FxTestSupport.onFx(() -> new MainWindow(store));
        FxTestSupport.onFx(() -> {
            window.saveActivePanel();
            return null;
        });

        assertEquals(UiThemePreference.LIGHT, store.savedPreferences.themePreference());
        assertEquals("BARONY-RED", store.savedCompany.activeCompanyCode());
    }

    private static final class InMemoryAppStateStore implements AppStateStore
    {
        private Optional<AppPreferencesState> preferences;
        private Optional<MultiCompanyState> multiCompany;

        private AppPreferencesState savedPreferences;
        private MultiCompanyState savedCompany;

        private InMemoryAppStateStore(Optional<AppPreferencesState> preferences,
                                      Optional<MultiCompanyState> multiCompany)
        {
            this.preferences = preferences;
            this.multiCompany = multiCompany;
        }

        @Override
        public Optional<AppPreferencesState> loadPreferences()
        {
            return preferences;
        }

        @Override
        public Optional<MultiCompanyState> loadMultiCompany()
        {
            return multiCompany;
        }

        @Override
        public void savePreferences(AppPreferencesState state)
        {
            savedPreferences = state;
            preferences = Optional.of(state);
        }

        @Override
        public void saveMultiCompany(MultiCompanyState state)
        {
            savedCompany = state;
            multiCompany = Optional.of(state);
        }
    }
}
